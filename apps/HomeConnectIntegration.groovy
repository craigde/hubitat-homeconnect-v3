/**
 *  Home Connect Integration v3 (Parent App)
 *
 *  Copyright 2026 Craig Dewar
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  ===========================================================================================================
 *  ARCHITECTURE OVERVIEW
 *  ===========================================================================================================
 *  
 *  This app serves as the coordinator between Home Connect, the Stream Driver, and child appliance drivers:
 *  
 *  1. OAUTH MANAGEMENT: Handles authentication with Home Connect, token storage, and refresh
 *  
 *  2. DEVICE DISCOVERY: Retrieves available appliances and creates appropriate child drivers
 *  
 *  3. EVENT ROUTING: Receives events from the Stream Driver and routes them to the correct child device
 *  
 *  4. API DELEGATION: Child drivers call parent methods (startProgram, etc.) which delegate to Stream Driver
 *  
 *  Component Relationships:
 *  ------------------------
 *  
 *  [Home Connect Cloud]
 *         ↕ (SSE + REST API)
 *  [Stream Driver] ←── API calls ──→ [Parent App] ←── Events ──→ [Child Drivers]
 *         ↓                              ↑                          (Dishwasher, etc.)
 *    SSE Events ─────────────────────────┘
 *  
 *  ===========================================================================================================
 *
 *  Version History:
 *  ----------------
 *  3.0.0  2026-01-07  Initial v3 architecture
 *                     New child deviceNetworkId prefix "HC3-<haId>"
 *                     Stream Driver handles SSE and API
 *                     Safe to run side-by-side with v1
 *  3.0.1  2026-01-08  Added lastCommandStatus feedback to child devices
 *                     Improved error handling for devices without programs
 *                     Added delayed device initialization after discovery
 *  3.0.6  2026-01-11  Fixed device creation on first install (foundDevices timing issue)
 *  3.0.7  2026-01-15  Enhanced OAuth debugging for troubleshooting authentication issues
 *                     Added detailed logging of token exchange requests/responses
 *                     Added Cooktop driver mapping
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

definition(
    name: 'Home Connect Integration v3',
    namespace: 'craigde',
    author: 'Craig Dewar',
    description: 'Integrates Home Connect smart appliances with Hubitat (v3 architecture)',
    category: 'My Apps',
    iconUrl: '',
    iconX2Url: '',
    iconX3Url: ''
)

/* ===========================================================================================================
   CONSTANTS
   =========================================================================================================== */

@Field static final List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field static final String DEFAULT_LOG_LEVEL = "warn"
@Field static final String STREAM_DRIVER_DNI = "HC3-StreamDriver"
@Field static final String APP_VERSION = "3.0.7"

// OAuth endpoints
@Field static final String OAUTH_AUTHORIZATION_URL = 'https://api.home-connect.com/security/oauth/authorize'
@Field static final String OAUTH_TOKEN_URL = 'https://api.home-connect.com/security/oauth/token'
@Field static final String API_BASE_URL = 'https://api.home-connect.com'

/* ===========================================================================================================
   SETTINGS ACCESSORS
   =========================================================================================================== */

private getClientId()     { settings.clientId?.trim() }
private getClientSecret() { settings.clientSecret?.trim() }

/* ===========================================================================================================
   LIFECYCLE METHODS
   =========================================================================================================== */

def installed() {
    logInfo("Installing Home Connect Integration v3")
    createStreamDriver()
}

def uninstalled() {
    logInfo("Uninstalling Home Connect Integration v3")
    deleteChildDevicesByDevices(getChildDevices())
}

def updated() {
    logInfo("Updating Home Connect Integration v3")
    synchronizeDevices()
}

/* ===========================================================================================================
   PREFERENCES PAGES
   =========================================================================================================== */

preferences {
    page(name: "pageIntro")
    page(name: "pageAuthentication")
    page(name: "pageDevices")
}

/**
 * Introduction page - collects Home Connect developer credentials
 */
def pageIntro() {
    logDebug("Showing Introduction Page")

    def streamDriver = getStreamDriver()
    def languages = streamDriver?.getSupportedLanguages() ?: getDefaultLanguages()
    def countriesList = flattenLanguageMap(languages)

    // Store selected region
    if (region != null) {
        atomicState.langCode = region
        atomicState.countryCode = countriesList.find { it.key == region }?.value
    }

    return dynamicPage(
        name: 'pageIntro',
        title: 'Home Connect Integration v3',
        nextPage: 'pageAuthentication',
        install: false,
        uninstall: true
    ) {
        section("Introduction") {
            paragraph """\
This application connects your Home Connect smart appliances to Hubitat.

<b>Before you begin:</b>

1. Create an account at the <a href="https://developer.home-connect.com/" target="_blank">Home Connect Developer Portal</a>

2. Create a new application with these settings:
   • <b>Application ID:</b> hubitat-homeconnect-integration
   • <b>OAuth Flow:</b> Authorization Code Grant Flow
   • <b>Redirect URI:</b> ${getFullApiServerUrl()}/oauth/callback

3. Copy your Client ID and Client Secret below

4. <b>Important:</b> Wait approximately 30 minutes after creating the application before proceeding (Home Connect requires propagation time)
"""
        }
        section('Home Connect Developer Credentials') {
            input name: 'clientId', title: 'Client ID', type: 'text', required: true
            input name: 'clientSecret', title: 'Client Secret', type: 'text', required: true
        }
        section('Region Selection') {
            input name: 'region', title: 'Select your region', type: 'enum', options: countriesList, required: true,
                  description: "This determines the language for appliance status messages"
        }
        section('Logging') {
            input name: 'logLevel', title: 'Log Level', type: 'enum', options: LOG_LEVELS, 
                  defaultValue: DEFAULT_LOG_LEVEL, required: false,
                  description: "Set to 'debug' for troubleshooting, 'warn' for normal operation"
        }
    }
}

/**
 * Authentication page - handles OAuth flow with Home Connect
 */
def pageAuthentication() {
    logDebug("Showing Authentication Page")

    // Create Hubitat access token if not exists
    if (!atomicState.accessToken) {
        atomicState.accessToken = createAccessToken()
    }

    return dynamicPage(
        name: 'pageAuthentication',
        title: 'Home Connect Authentication',
        nextPage: 'pageDevices',
        install: false,
        uninstall: false,
        refreshInterval: 0
    ) {
        section() {
            if (atomicState.oAuthAuthToken) {
                showHideNextButton(true)
                paragraph '<b>✓ Connected!</b> Your Home Connect account is linked. Press Next to continue.'
                href url: generateOAuthUrl(), style: 'external', required: false,
                     title: "Re-authenticate", description: "Tap to reconnect if needed"
            } else {
                showHideNextButton(false)
                paragraph 'Click the button below to connect your Home Connect account.'
                href url: generateOAuthUrl(), style: 'external', required: false,
                     title: "Connect to Home Connect", description: "Tap to authenticate"
            }
        }
        section("Debug Info") {
            paragraph "<small>Redirect URI: ${getOAuthRedirectUrl()}</small>"
            paragraph "<small>Client ID length: ${getClientId()?.length() ?: 0} chars</small>"
            if (atomicState.lastOAuthError) {
                paragraph "<b style='color:red'>Last OAuth Error:</b> ${atomicState.lastOAuthError}"
            }
        }
    }
}

/**
 * Device selection page - lists discovered appliances
 */
def pageDevices() {
    logDebug("Showing Devices Page")
    
    def homeConnectDevices = fetchHomeConnectDevices()
    
    def deviceList = [:]
    state.foundDevices = []

    homeConnectDevices.each { appliance ->
        deviceList << ["${appliance.haId}": "${appliance.name} (${appliance.type})"]
        state.foundDevices << [haId: appliance.haId, name: appliance.name, type: appliance.type]
    }

    return dynamicPage(
        name: 'pageDevices',
        title: 'Select Appliances',
        install: true,
        uninstall: true
    ) {
        section() {
            if (deviceList.isEmpty()) {
                paragraph '<b>No appliances found.</b> Make sure your appliances are registered in the Home Connect app.'
            } else {
                paragraph "Found ${deviceList.size()} appliance(s). Select the ones you want to control with Hubitat:"
                input name: 'devices', title: 'Appliances', type: 'enum', required: true,
                      multiple: true, options: deviceList
            }
        }
    }
}

/**
 * Fetches list of appliances from Home Connect API
 */
private List fetchHomeConnectDevices() {
    def homeConnectDevices = []
    
    try {
        def token = getOAuthToken()
        def language = getLanguage()
        
        httpGet(
            uri: "${API_BASE_URL}/api/homeappliances",
            contentType: "application/json",
            headers: [
                'Authorization': "Bearer ${token}",
                'Accept-Language': language,
                'Accept': "application/vnd.bsh.sdk.v1+json"
            ]
        ) { response ->
            if (response.data?.data?.homeappliances) {
                homeConnectDevices = response.data.data.homeappliances
                logDebug("Found ${homeConnectDevices.size()} appliance(s)")
            }
        }
    } catch (Exception e) {
        logError("Failed to fetch appliances: ${e.message}")
    }
    
    return homeConnectDevices
}

/* ===========================================================================================================
   STREAM DRIVER MANAGEMENT
   =========================================================================================================== */

/**
 * Gets the Stream Driver child device
 */
def getStreamDriver() {
    return getChildDevice(STREAM_DRIVER_DNI)
}

/**
 * Creates the Stream Driver if it doesn't exist
 */
private void createStreamDriver() {
    def existing = getChildDevice(STREAM_DRIVER_DNI)
    if (existing) {
        logDebug("Stream Driver already exists")
        return
    }
    
    logInfo("Creating Stream Driver")
    try {
        def driver = addChildDevice('craigde', 'Home Connect Stream Driver v3', STREAM_DRIVER_DNI)
        driver.initialize()
    } catch (Exception e) {
        logError("Failed to create Stream Driver: ${e.message}")
    }
}

/* ===========================================================================================================
   DEVICE SYNCHRONIZATION
   =========================================================================================================== */

/**
 * Converts Home Connect appliance ID to Hubitat device network ID
 */
private String homeConnectIdToDeviceNetworkId(String haId) {
    return "HC3-${haId}"
}

/**
 * Synchronizes child devices with selected appliances
 * Creates new devices, removes deselected ones
 */
def synchronizeDevices() {
    logDebug("Synchronizing devices")
    
    // Ensure stream driver exists
    createStreamDriver()
    
    // If foundDevices is empty, we may need to fetch them again
    // This can happen on first install due to timing
    if (!state.foundDevices || state.foundDevices.isEmpty()) {
        logDebug("foundDevices is empty - fetching appliance list")
        def homeConnectDevices = fetchHomeConnectDevices()
        state.foundDevices = homeConnectDevices.collect { appliance ->
            [haId: appliance.haId, name: appliance.name, type: appliance.type]
        }
        logDebug("Populated foundDevices with ${state.foundDevices.size()} appliance(s)")
    }
    
    def childDevices = getChildDevices()
    def childrenMap = childDevices.collectEntries { [(it.deviceNetworkId): it] }
    
    // Don't touch the stream driver
    childrenMap.remove(STREAM_DRIVER_DNI)

    def newDevices = []
    
    // Create devices for newly selected appliances
    for (homeConnectDeviceId in settings.devices) {
        def hubitatDeviceId = homeConnectIdToDeviceNetworkId(homeConnectDeviceId)

        if (childrenMap.containsKey(hubitatDeviceId)) {
            // Device exists - remove from map so it won't be deleted
            childrenMap.remove(hubitatDeviceId)
            continue
        }

        // Create new device
        def homeConnectDevice = state.foundDevices.find { it.haId == homeConnectDeviceId }
        if (!homeConnectDevice) {
            logWarn("Could not find device info for ${homeConnectDeviceId} - skipping")
            continue
        }

        def device = createApplianceDevice(homeConnectDevice.type, hubitatDeviceId)
        if (device) {
            newDevices << device
        }
    }

    // Remove devices that are no longer selected
    deleteChildDevicesByDevices(childrenMap.values())
    
    // Start SSE connection
    getStreamDriver()?.connect()
    
    // Initialize new devices after a short delay (allows SSE to connect)
    if (newDevices) {
        runIn(5, "initializeNewDevices", [data: [deviceIds: newDevices.collect { it.deviceNetworkId }]])
    }
}

/**
 * Initializes newly created devices - fetches status and available programs
 */
def initializeNewDevices(Map data) {
    logInfo("Initializing ${data.deviceIds.size()} new device(s)")
    
    data.deviceIds.each { dni ->
        def device = getChildDevice(dni)
        if (device) {
            logDebug("Initializing ${device.displayName}")
            device.initialize()
        }
    }
}

/**
 * Creates the appropriate child driver for an appliance type
 */
private def createApplianceDevice(String type, String dni) {
    def driverName = getDriverNameForType(type)
    
    if (!driverName) {
        logError("Unsupported appliance type: ${type}")
        return null
    }
    
    try {
        logInfo("Creating ${driverName} device")
        return addChildDevice('craigde', driverName, dni)
    } catch (Exception e) {
        logError("Failed to create ${driverName}: ${e.message}")
        return null
    }
}

/**
 * Maps Home Connect appliance types to driver names
 */
private String getDriverNameForType(String type) {
    def driverMap = [
        "CleaningRobot": "Home Connect CleaningRobot v3",
        "CoffeeMaker": "Home Connect CoffeeMaker v3",
        "CookProcessor": "Home Connect CookProcessor v3",
        "Cooktop": "Home Connect Cooktop v3",
        "Dishwasher": "Home Connect Dishwasher v3",
        "Dryer": "Home Connect Dryer v3",
        "Freezer": "Home Connect FridgeFreezer v3",
        "FridgeFreezer": "Home Connect FridgeFreezer v3",
        "Hob": "Home Connect Cooktop v3",
        "Hood": "Home Connect Hood v3",
        "Oven": "Home Connect Oven v3",
        "Refrigerator": "Home Connect FridgeFreezer v3",
        "Washer": "Home Connect Washer v3",
        "WasherDryer": "Home Connect WasherDryer v3",
        "WarmingDrawer": "Home Connect WarmingDrawer v3",
        "WineCooler": "Home Connect FridgeFreezer v3"
    ]
    
    return driverMap[type]
}

/**
 * Deletes a collection of child devices
 */
private void deleteChildDevicesByDevices(devices) {
    for (d in devices) {
        if (d.deviceNetworkId != STREAM_DRIVER_DNI) {
            logInfo("Removing device: ${d.displayName}")
            deleteChildDevice(d.deviceNetworkId)
        }
    }
}

/* ===========================================================================================================
   EVENT ROUTING
   =========================================================================================================== */

/**
 * Called by Stream Driver when an appliance event is received
 * Routes the event to the appropriate child device
 *
 * @param evt Map containing: haId, key, value, displayvalue, unit, eventType
 */
def handleApplianceEvent(Map evt) {
    if (!evt?.haId || !evt?.key) {
        logWarn("handleApplianceEvent: missing haId or key")
        return
    }
    
    logDebug("Routing event: ${evt.key} = ${evt.value} for ${evt.haId}")

    String childDni = "HC3-${evt.haId}"
    def child = getChildDevice(childDni)
    
    if (!child) {
        logDebug("No child device for haId ${evt.haId}")
        return
    }

    try {
        child.parseEvent(evt)
    } catch (Exception e) {
        logWarn("Error routing event to ${child.displayName}: ${e.message}")
    }
}

/**
 * Called by Stream Driver when an appliance connects/disconnects
 */
def handleApplianceConnectionEvent(String haId, String status) {
    logDebug("Appliance ${haId} connection status: ${status}")
    
    String childDni = "HC3-${haId}"
    def child = getChildDevice(childDni)
    
    if (child) {
        try {
            child.z_updateEventStreamStatus(status)
        } catch (Exception e) {
            // Method may not exist on all drivers
        }
    }
}

/**
 * Called by Stream Driver after reconnecting to refresh all device status
 */
def refreshAllDeviceStatus() {
    logInfo("Refreshing status for all devices after reconnect")
    
    getChildDevices().each { child ->
        if (child.deviceNetworkId != STREAM_DRIVER_DNI) {
            try {
                // Use checkActiveProgram=false to minimize API calls
                initializeStatus(child, false)
            } catch (Exception e) {
                logWarn("Failed to refresh ${child.displayName}: ${e.message}")
            }
        }
    }
}

/* ===========================================================================================================
   API HELPERS - Called by child drivers
   =========================================================================================================== */

/**
 * Extracts the Home Connect appliance ID from a device network ID
 */
private String getHaIdFromDevice(device) {
    return device.deviceNetworkId?.replaceFirst(/^HC3-/, "")
}

/**
 * Initializes status for a device by fetching current state from Home Connect
 *
 * @param device The child device to initialize
 * @param checkActiveProgram Whether to also fetch active program (set false to reduce API calls)
 */
def initializeStatus(device, boolean checkActiveProgram = true) {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    
    if (!streamDriver) {
        logError("Stream Driver not available")
        return
    }
    
    logDebug("Initializing status for ${haId}")

    // Fetch current status
    streamDriver.getStatus(haId) { status ->
        device.z_parseStatus(JsonOutput.toJson(status))
    }

    // Fetch current settings
    streamDriver.getSettings(haId) { settings ->
        device.z_parseSettings(JsonOutput.toJson(settings))
    }

    // Optionally fetch active program
    if (checkActiveProgram) {
        try {
            streamDriver.getActiveProgram(haId) { activeProgram ->
                device.z_parseActiveProgram(JsonOutput.toJson(activeProgram))
            }
        } catch (Exception e) {
            // No active program - this is normal when appliance is idle
        }
    }
}

/**
 * Starts a program on an appliance
 */
def startProgram(device, String programKey, def options = "") {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    
    logInfo("Starting program ${programKey} on ${haId}")
    
    try {
        streamDriver?.setActiveProgram(haId, programKey, options) { response ->
            logDebug("startProgram response: ${response}")
            device.sendEvent(name: "lastCommandStatus", value: "Program started: ${programKey}")
        }
    } catch (Exception e) {
        logWarn("Failed to start program: ${e.message}")
        device.sendEvent(name: "lastCommandStatus", value: "Failed: ${e.message}")
    }
}

/**
 * Stops the currently running program
 */
def stopProgram(device) {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    
    logInfo("Stopping program on ${haId}")
    
    try {
        streamDriver?.stopActiveProgram(haId) { response ->
            logDebug("stopProgram response: ${response}")
            device.sendEvent(name: "lastCommandStatus", value: "Program stopped")
        }
    } catch (Exception e) {
        logWarn("Failed to stop program: ${e.message}")
        device.sendEvent(name: "lastCommandStatus", value: "Failed: ${e.message}")
    }
}

/**
 * Sets the power state of an appliance
 */
def setPowerState(device, boolean state) {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    def value = state ? "BSH.Common.EnumType.PowerState.On" : "BSH.Common.EnumType.PowerState.Off"
    
    logInfo("Setting power ${state ? 'ON' : 'OFF'} for ${haId}")
    
    try {
        streamDriver?.setSetting(haId, "BSH.Common.Setting.PowerState", value) { response ->
            logDebug("setPowerState response: ${response}")
            device.sendEvent(name: "lastCommandStatus", value: "Power ${state ? 'on' : 'off'}")
        }
    } catch (Exception e) {
        logWarn("Failed to set power state: ${e.message}")
        device.sendEvent(name: "lastCommandStatus", value: "Failed: ${e.message}")
    }
}

/**
 * Gets list of available programs for a device
 * Some devices (Hob, FridgeFreezer) don't support programs - this handles that gracefully
 */
def getAvailableProgramList(device) {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    
    logDebug("Fetching available programs for ${haId}")
    
    try {
        streamDriver?.getAvailablePrograms(haId) { programs ->
            if (programs) {
                try {
                    device.z_parseAvailablePrograms(JsonOutput.toJson(programs))
                } catch (Exception e) {
                    logDebug("Device doesn't support z_parseAvailablePrograms: ${e.message}")
                }
            } else {
                logDebug("No programs available for ${haId} (device may not support programs)")
            }
        }
    } catch (Exception e) {
        // Device doesn't support programs - this is expected for some appliance types
        logDebug("Cannot fetch programs for ${haId}: ${e.message}")
    }
}

/**
 * Gets available options for a specific program
 */
def getAvailableProgramOptionsList(device, String programKey) {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    
    logDebug("Fetching options for program ${programKey} on ${haId}")
    streamDriver?.getAvailableProgram(haId, programKey) { program ->
        def optionsList = program?.options ?: []
        try {
            device.z_parseAvailableOptions(JsonOutput.toJson(optionsList))
        } catch (Exception e) {
            // Method may not exist
        }
    }
}

/**
 * Sets the selected program (without starting)
 */
def setSelectedProgram(device, String programKey, def options = "") {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    
    logDebug("Setting selected program ${programKey} on ${haId}")
    streamDriver?.setSelectedProgram(haId, programKey, options) { response ->
        logDebug("setSelectedProgram response: ${response}")
    }
}

/**
 * Sets an option on the selected program
 */
def setSelectedProgramOption(device, String optionKey, def optionValue) {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    
    logDebug("Setting option ${optionKey}=${optionValue} on ${haId}")
    streamDriver?.setSelectedProgramOption(haId, optionKey, optionValue) { response ->
        logDebug("setSelectedProgramOption response: ${response}")
    }
}

/**
 * Sends a command to an appliance
 */
def sendCommand(device, String commandKey) {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    
    logDebug("Sending command ${commandKey} to ${haId}")
    
    try {
        streamDriver?.apiPut("/api/homeappliances/${haId}/commands/${commandKey}", [data: [key: commandKey]]) { response ->
            logDebug("sendCommand response: ${response}")
            device.sendEvent(name: "lastCommandStatus", value: "Command sent: ${commandKey}")
        }
    } catch (Exception e) {
        logWarn("Failed to send command: ${e.message}")
        device.sendEvent(name: "lastCommandStatus", value: "Failed: ${e.message}")
    }
}

/* ===========================================================================================================
   OAUTH TOKEN MANAGEMENT
   =========================================================================================================== */

/**
 * Gets a valid OAuth token, refreshing if necessary
 * Called by Stream Driver for API requests
 */
def getOAuthToken() {
    // Refresh if token expires within 1 minute
    if (now() >= (atomicState.oAuthTokenExpires ?: 0) - 60_000) {
        refreshOAuthToken()
    }
    return atomicState.oAuthAuthToken
}

/**
 * Called by Stream Driver when a 401 error occurs
 * Forces token refresh and returns success status
 */
def refreshOAuthTokenAndRetry() {
    logInfo("Forcing OAuth token refresh due to 401 error")
    refreshOAuthToken()
    return atomicState.oAuthAuthToken != null
}

/**
 * Gets the configured language code
 */
def getLanguage() {
    return atomicState.langCode ?: "en-US"
}

/* ===========================================================================================================
   OAUTH AUTHENTICATION FLOW
   =========================================================================================================== */

// Map incoming OAuth callbacks to the handler method
mappings {
    path("/oauth/callback") { action: [GET: "oAuthCallback"] }
}

/**
 * Generates the OAuth authorization URL
 */
private String generateOAuthUrl() {
    def timestamp = now().toString()
    def stateValue = generateSecureState(timestamp)

    def streamDriver = getStreamDriver()
    def queryString = streamDriver?.toQueryString([
        'client_id': getClientId(),
        'redirect_uri': getOAuthRedirectUrl(),
        'response_type': 'code',
        'scope': 'IdentifyAppliance Monitor Settings Control',
        'state': stateValue
    ]) ?: ""
    
    logDebug("Generated OAuth URL with redirect: ${getOAuthRedirectUrl()}")
    
    return "${OAUTH_AUTHORIZATION_URL}?${queryString}"
}

/**
 * Generates a secure state value for OAuth CSRF protection
 */
private String generateSecureState(String timestamp) {
    def message = "${timestamp}:${getClientId()}:${getClientSecret()}"
    def hash = message.hashCode().toString()
    def stateValue = "${timestamp}:${hash}"
    return stateValue.bytes.encodeBase64().toString()
}

/**
 * Validates the OAuth state value
 */
private boolean validateSecureState(String stateValue) {
    try {
        def decoded = new String(stateValue.decodeBase64())
        def parts = decoded.split(':')

        if (parts.length != 2) {
            logError("Invalid OAuth state format")
            return false
        }

        def timestamp = parts[0]
        def receivedHash = parts[1]

        // Check state age (max 10 minutes)
        def stateAge = now() - timestamp.toLong()
        if (stateAge < 0 || stateAge > 600000) {
            logError("OAuth state expired: ${stateAge}ms old")
            return false
        }

        // Verify hash
        def message = "${timestamp}:${getClientId()}:${getClientSecret()}"
        def expectedHash = message.hashCode().toString()
        if (expectedHash != receivedHash) {
            logError("OAuth state hash mismatch")
            return false
        }

        return true
    } catch (Exception e) {
        logError("Error validating OAuth state: ${e.message}")
        return false
    }
}

/**
 * Gets the OAuth redirect URL for callbacks
 * This is the URL that Home Connect will redirect to after authentication
 */
private String getOAuthRedirectUrl() {
    return "${getFullApiServerUrl()}/oauth/callback?access_token=${atomicState.accessToken}"
}

/**
 * Gets the clean redirect URL for display/registration (without access_token)
 * Use THIS URL when registering in the Home Connect Developer Portal
 */
def getDisplayRedirectUrl() {
    return "${getFullApiServerUrl()}/oauth/callback"
}

/**
 * Handles the OAuth callback from Home Connect
 */
def oAuthCallback() {
    logInfo("=== OAuth Callback Received ===")
    logDebug("All params: ${params}")

    def code = params.code
    def oAuthState = params.state
    def error = params.error
    def errorDescription = params.error_description

    // Check for error from Home Connect
    if (error) {
        logError("OAuth error from Home Connect: ${error} - ${errorDescription}")
        atomicState.lastOAuthError = "${error}: ${errorDescription}"
        return renderOAuthFailure("${error}: ${errorDescription}")
    }

    if (!code) {
        logError("No authorization code in OAuth callback")
        atomicState.lastOAuthError = "No authorization code received"
        return renderOAuthFailure("No authorization code received")
    }

    logDebug("Received authorization code: ${code?.take(20)}...")

    if (!oAuthState || !validateSecureState(oAuthState)) {
        logError("Invalid OAuth state in callback")
        atomicState.lastOAuthError = "Invalid state - possible CSRF attack or expired link"
        return renderOAuthFailure("Invalid state parameter")
    }

    // Clear any existing tokens
    atomicState.oAuthRefreshToken = null
    atomicState.oAuthAuthToken = null
    atomicState.oAuthTokenExpires = null
    atomicState.lastOAuthError = null

    // Exchange code for tokens
    def success = acquireOAuthToken(code)

    if (!success || !atomicState.oAuthAuthToken) {
        logError("Failed to acquire OAuth token")
        return renderOAuthFailure(atomicState.lastOAuthError ?: "Failed to exchange code for token")
    }

    logInfo("OAuth authentication successful")
    return renderOAuthSuccess()
}

/**
 * Exchanges authorization code for access token
 * Returns true on success, false on failure
 */
private boolean acquireOAuthToken(String code) {
    logInfo("=== Acquiring OAuth Token ===")
    
    def redirectUri = getOAuthRedirectUrl()
    
    def body = [
        'grant_type': 'authorization_code',
        'code': code,
        'client_id': getClientId(),
        'client_secret': getClientSecret(),
        'redirect_uri': redirectUri
    ]
    
    // Log what we're sending (mask secrets)
    logDebug("Token request to: ${OAUTH_TOKEN_URL}")
    logDebug("Token request body:")
    logDebug("  grant_type: authorization_code")
    logDebug("  code: ${code?.take(20)}...")
    logDebug("  client_id: ${getClientId()?.take(20)}... (${getClientId()?.length()} chars)")
    logDebug("  client_secret: [MASKED] (${getClientSecret()?.length()} chars)")
    logDebug("  redirect_uri: ${redirectUri}")
    
    return apiRequestAccessToken(body)
}

/**
 * Refreshes the OAuth access token
 */
private void refreshOAuthToken() {
    logDebug("Refreshing OAuth token")
    
    if (!atomicState.oAuthRefreshToken) {
        logError("No refresh token available")
        return
    }
    
    apiRequestAccessToken([
        'grant_type': 'refresh_token',
        'refresh_token': atomicState.oAuthRefreshToken,
        'client_secret': getClientSecret()
    ])
}

/**
 * Makes the OAuth token request
 * Returns true on success, false on failure
 */
private boolean apiRequestAccessToken(Map body) {
    try {
        def success = false
        
        httpPost(
            uri: OAUTH_TOKEN_URL, 
            requestContentType: 'application/x-www-form-urlencoded', 
            body: body
        ) { response ->
            logDebug("Token response status: ${response.status}")
            
            if (response?.data && response.success) {
                atomicState.oAuthRefreshToken = response.data.refresh_token
                atomicState.oAuthAuthToken = response.data.access_token
                atomicState.oAuthTokenExpires = now() + (response.data.expires_in * 1000)
                logInfo("OAuth token acquired successfully, expires in ${response.data.expires_in}s")
                success = true
            } else {
                logError("Token response unsuccessful: ${response.data}")
                atomicState.lastOAuthError = "Token response unsuccessful"
            }
        }
        
        return success
        
    } catch (groovyx.net.http.HttpResponseException e) {
        def statusCode = e.getStatusCode()
        def responseBody = e.getResponse()?.getData()
        
        logError("=== OAuth Token Error ===")
        logError("HTTP Status: ${statusCode}")
        logError("Response: ${responseBody}")
        
        // Parse error details if JSON
        try {
            if (responseBody instanceof Map) {
                atomicState.lastOAuthError = "${responseBody.error}: ${responseBody.error_description}"
                logError("Error: ${responseBody.error}")
                logError("Description: ${responseBody.error_description}")
            } else {
                atomicState.lastOAuthError = "HTTP ${statusCode}: ${responseBody}"
            }
        } catch (Exception parseEx) {
            atomicState.lastOAuthError = "HTTP ${statusCode}"
        }
        
        return false
        
    } catch (Exception e) {
        logError("Token request exception: ${e.class.name}: ${e.message}")
        atomicState.lastOAuthError = e.message
        return false
    }
}

/**
 * Renders success page after OAuth
 */
private def renderOAuthSuccess() {
    render contentType: 'text/html', data: '''
    <html>
    <head><title>Success</title></head>
    <body style="font-family: sans-serif; padding: 20px;">
        <h2>✓ Connected!</h2>
        <p>Your Home Connect account is now linked to Hubitat.</p>
        <p>You can close this window and continue setup.</p>
    </body>
    </html>
    '''
}

/**
 * Renders failure page after OAuth error
 */
private def renderOAuthFailure(String errorMessage = null) {
    def errorHtml = errorMessage ? "<p><b>Error:</b> ${errorMessage}</p>" : ""
    
    render contentType: 'text/html', data: """
    <html>
    <head><title>Error</title></head>
    <body style="font-family: sans-serif; padding: 20px;">
        <h2>✗ Connection Failed</h2>
        <p>Unable to connect to Home Connect.</p>
        ${errorHtml}
        <p>Please check the following:</p>
        <ul>
            <li>Client ID and Client Secret are correct (no extra spaces)</li>
            <li>The Redirect URI in Home Connect Developer Portal matches exactly</li>
            <li>You waited 30+ minutes after creating the application</li>
        </ul>
        <p>Check the Hubitat logs for more details.</p>
    </body>
    </html>
    """
}

/* ===========================================================================================================
   UTILITY METHODS
   =========================================================================================================== */

/**
 * Shows or hides the Next button on preference pages
 */
private void showHideNextButton(boolean show) {
    if (show) {
        paragraph "<script>if(typeof jQuery !== 'undefined'){\$('button[name=\"_action_next\"]').show();}</script>"
    } else {
        paragraph "<script>if(typeof jQuery !== 'undefined'){\$('button[name=\"_action_next\"]').hide();}</script>"
    }
}

/**
 * Flattens nested language map for dropdown selection
 */
private Map flattenLanguageMap(Map m) {
    return m.collectEntries { k, v ->
        def flattened = [:]
        if (v instanceof Map) {
            v.each { k1, v1 ->
                flattened << ["${v1}": "${k} - ${k1} (${v1})"]
            }
        } else {
            flattened << ["${k}": v]
        }
        return flattened
    }
}

/**
 * Returns default language options if Stream Driver isn't available
 */
private Map getDefaultLanguages() {
    return [
        "English": ["United States": "en-US", "United Kingdom": "en-GB"],
        "German": ["Germany": "de-DE"]
    ]
}

/* ===========================================================================================================
   LOGGING METHODS
   =========================================================================================================== */

private void logDebug(String msg) {
    if (LOG_LEVELS.indexOf("debug") <= LOG_LEVELS.indexOf(logLevel ?: DEFAULT_LOG_LEVEL)) {
        log.debug "${app.name}: ${msg}"
    }
}

private void logInfo(String msg) {
    if (LOG_LEVELS.indexOf("info") <= LOG_LEVELS.indexOf(logLevel ?: DEFAULT_LOG_LEVEL)) {
        log.info "${app.name}: ${msg}"
    }
}

private void logWarn(String msg) {
    if (LOG_LEVELS.indexOf("warn") <= LOG_LEVELS.indexOf(logLevel ?: DEFAULT_LOG_LEVEL)) {
        log.warn "${app.name}: ${msg}"
    }
}

private void logError(String msg) {
    log.error "${app.name}: ${msg}"
}
