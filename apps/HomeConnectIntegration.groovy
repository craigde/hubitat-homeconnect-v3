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
 *  3.0.2  2026-01-09  Fixed OAuth redirect URI - ensure access token created before OAuth URL
 *                     Added detailed OAuth debug logging
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
    iconX3Url: '',
    oauth: true
)

/* ===========================================================================================================
   CONSTANTS
   =========================================================================================================== */

@Field static final List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field static final String DEFAULT_LOG_LEVEL = "warn"
@Field static final String STREAM_DRIVER_DNI = "HC3-StreamDriver"
@Field static final String APP_VERSION = "3.0.2"

// OAuth endpoints
@Field static final String OAUTH_AUTHORIZATION_URL = 'https://api.home-connect.com/security/oauth/authorize'
@Field static final String OAUTH_TOKEN_URL = 'https://api.home-connect.com/security/oauth/token'
@Field static final String API_BASE_URL = 'https://api.home-connect.com'

/* ===========================================================================================================
   SETTINGS ACCESSORS
   =========================================================================================================== */

private getClientId()     { settings.clientId }
private getClientSecret() { settings.clientSecret }

/* ===========================================================================================================
   LIFECYCLE METHODS
   =========================================================================================================== */

def installed() {
    logInfo("Installing Home Connect Integration v3")
    ensureAccessToken()
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

/**
 * Ensures Hubitat access token exists for OAuth callbacks
 * Must be called early to ensure redirect URI is consistent
 */
private void ensureAccessToken() {
    if (!state.accessToken) {
        try {
            state.accessToken = createAccessToken()
            log.info "${app.name}: Created new Hubitat access token"
        } catch (Exception e) {
            log.error "${app.name}: Failed to create access token: ${e.message}"
            log.error "${app.name}: Make sure OAuth is enabled for this app"
        }
    } else {
        log.debug "${app.name}: Hubitat access token already exists"
    }
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
    
    // Ensure access token exists before showing redirect URI
    ensureAccessToken()

    def streamDriver = getStreamDriver()
    def languages = streamDriver?.getSupportedLanguages() ?: getDefaultLanguages()
    def countriesList = flattenLanguageMap(languages)

    // Store selected region
    if (region != null) {
        atomicState.langCode = region
        atomicState.countryCode = countriesList.find { it.key == region }?.value
    }
    
    def redirectUri = getOAuthRedirectUrl()

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
   • <b>Redirect URI:</b> <code>${redirectUri}</code>

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

    // Ensure access token exists
    ensureAccessToken()
    
    def oauthUrl = generateOAuthUrl()
    logDebug("OAuth URL: ${oauthUrl}")

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
                href url: oauthUrl, style: 'external', required: false,
                     title: "Re-authenticate", description: "Tap to reconnect if needed"
            } else {
                showHideNextButton(false)
                paragraph 'Click the button below to connect your Home Connect account.'
                href url: oauthUrl, style: 'external', required: false,
                     title: "Connect to Home Connect", description: "Tap to authenticate"
            }
        }
        section("Troubleshooting") {
            paragraph """<small>
<b>Redirect URI for Home Connect Developer Portal:</b><br/>
<code>${getOAuthRedirectUrl()}</code>

If authentication fails, verify this URI matches exactly in your Home Connect application settings.
</small>"""
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
        if (!homeConnectDevice) continue

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
        "Dishwasher": "Home Connect Dishwasher v3",
        "Dryer": "Home Connect Dryer v3",
        "Freezer": "Home Connect FridgeFreezer v3",
        "FridgeFreezer": "Home Connect FridgeFreezer v3",
        "Hob": "Home Connect Hob v3",
        "Hood": "Home Connect Hood v3",
        "Oven": "Home Connect Oven v3",
        "Refrigerator": "Home Connect FridgeFreezer v3",
        "Washer": "Home Connect Washer v3",
        "WasherDryer": "Home Connect WasherDryer v3",
        "WineCooler": "Home Connect WineCooler v3"
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
    // Ensure we have an access token first
    ensureAccessToken()
    
    def timestamp = now().toString()
    def stateValue = generateSecureState(timestamp)
    def redirectUri = getOAuthRedirectUrl()
    def clientId = getClientId()

    // Debug logging for OAuth parameters
    log.debug "${app.name}: ===== OAuth URL Generation ====="
    log.debug "${app.name}: Client ID: ${clientId ? clientId.take(8) + '...' : 'NULL/EMPTY'}"
    log.debug "${app.name}: Client ID length: ${clientId?.length() ?: 0}"
    log.debug "${app.name}: Redirect URI: ${redirectUri}"
    log.debug "${app.name}: State: ${stateValue?.take(20)}..."
    log.debug "${app.name}: Hubitat access token exists: ${state.accessToken ? 'YES' : 'NO'}"

    if (!clientId) {
        log.error "${app.name}: CLIENT ID IS EMPTY - OAuth will fail!"
    }

    def params = [
        'client_id': clientId,
        'redirect_uri': redirectUri,
        'response_type': 'code',
        'scope': 'IdentifyAppliance Monitor Settings Control',
        'state': stateValue
    ]
    
    def queryString = params.collect { k, v -> 
        "${URLEncoder.encode(k, 'UTF-8')}=${URLEncoder.encode(v?.toString() ?: '', 'UTF-8')}" 
    }.join('&')
    
    def url = "${OAUTH_AUTHORIZATION_URL}?${queryString}"
    log.debug "${app.name}: Full OAuth URL length: ${url.length()}"
    log.debug "${app.name}: ===== End OAuth URL Generation ====="
    
    return url
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
 * This must match EXACTLY what's registered in the Home Connect Developer Portal
 */
private String getOAuthRedirectUrl() {
    // Ensure access token exists
    ensureAccessToken()
    return "${getFullApiServerUrl()}/oauth/callback?access_token=${state.accessToken}"
}

/**
 * Handles the OAuth callback from Home Connect
 */
def oAuthCallback() {
    // Always log callback details regardless of log level
    log.info "${app.name}: ===== OAuth Callback Received ====="
    log.info "${app.name}: Full params: ${params}"
    log.info "${app.name}: Code present: ${params.code ? 'YES (' + params.code.take(10) + '...)' : 'NO'}"
    log.info "${app.name}: State present: ${params.state ? 'YES' : 'NO'}"
    log.info "${app.name}: Error: ${params.error ?: 'none'}"
    log.info "${app.name}: Error description: ${params.error_description ?: 'none'}"

    def code = params.code
    def oAuthState = params.state
    def error = params.error
    def errorDesc = params.error_description

    // Check for error response from Home Connect
    if (error) {
        log.error "${app.name}: OAuth error from Home Connect: ${error} - ${errorDesc}"
        return renderOAuthFailure("Home Connect error: ${errorDesc ?: error}")
    }

    if (!code) {
        log.error "${app.name}: No authorization code in OAuth callback"
        return renderOAuthFailure("No authorization code received")
    }

    if (!oAuthState) {
        log.error "${app.name}: No state parameter in OAuth callback"
        return renderOAuthFailure("Missing state parameter")
    }
    
    log.debug "${app.name}: Validating state..."
    if (!validateSecureState(oAuthState)) {
        log.error "${app.name}: Invalid OAuth state in callback"
        return renderOAuthFailure("Invalid state - please try again")
    }
    log.debug "${app.name}: State validated successfully"

    // Clear any existing tokens
    atomicState.oAuthRefreshToken = null
    atomicState.oAuthAuthToken = null
    atomicState.oAuthTokenExpires = null

    // Exchange code for tokens
    log.info "${app.name}: Exchanging authorization code for tokens..."
    def success = acquireOAuthToken(code)

    if (!success || !atomicState.oAuthAuthToken) {
        log.error "${app.name}: Failed to acquire OAuth token"
        return renderOAuthFailure("Failed to get access token")
    }

    log.info "${app.name}: ===== OAuth Authentication Successful ====="
    return renderOAuthSuccess()
}

/**
 * Exchanges authorization code for access token
 */
private boolean acquireOAuthToken(String code) {
    def redirectUri = getOAuthRedirectUrl()
    def clientId = getClientId()
    def clientSecret = getClientSecret()
    
    log.debug "${app.name}: ===== Token Acquisition ====="
    log.debug "${app.name}: Redirect URI for token request: ${redirectUri}"
    log.debug "${app.name}: Client ID: ${clientId ? clientId.take(8) + '...' : 'NULL'}"
    log.debug "${app.name}: Client Secret: ${clientSecret ? 'SET (' + clientSecret.length() + ' chars)' : 'NULL/EMPTY'}"
    log.debug "${app.name}: Code: ${code?.take(10)}..."
    
    return apiRequestAccessToken([
        'grant_type': 'authorization_code',
        'code': code,
        'client_id': clientId,
        'client_secret': clientSecret,
        'redirect_uri': redirectUri
    ])
}

/**
 * Refreshes the OAuth access token
 */
private void refreshOAuthToken() {
    logDebug("Refreshing OAuth token")
    apiRequestAccessToken([
        'grant_type': 'refresh_token',
        'refresh_token': atomicState.oAuthRefreshToken,
        'client_secret': getClientSecret()
    ])
}

/**
 * Makes the OAuth token request
 */
private boolean apiRequestAccessToken(Map body) {
    try {
        log.debug "${app.name}: Making token request to: ${OAUTH_TOKEN_URL}"
        log.debug "${app.name}: Request body keys: ${body.keySet()}"
        
        httpPost(uri: OAUTH_TOKEN_URL, requestContentType: 'application/x-www-form-urlencoded', body: body) { response ->
            log.debug "${app.name}: Token response status: ${response.status}"
            log.debug "${app.name}: Token response success: ${response.success}"
            
            if (response?.data && response.success) {
                atomicState.oAuthRefreshToken = response.data.refresh_token
                atomicState.oAuthAuthToken = response.data.access_token
                atomicState.oAuthTokenExpires = now() + (response.data.expires_in * 1000)
                log.info "${app.name}: OAuth token acquired successfully!"
                log.debug "${app.name}: Token expires in ${response.data.expires_in}s"
                log.debug "${app.name}: Access token: ${response.data.access_token?.take(20)}..."
                return true
            } else {
                log.error "${app.name}: Token response unsuccessful"
                log.error "${app.name}: Response data: ${response.data}"
                return false
            }
        }
        return atomicState.oAuthAuthToken != null
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error "${app.name}: HTTP error acquiring token: ${e.statusCode}"
        log.error "${app.name}: Response: ${e.response?.data}"
        return false
    } catch (Exception e) {
        log.error "${app.name}: Exception acquiring token: ${e.class.name}: ${e.message}"
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
private def renderOAuthFailure(String message = "Unknown error") {
    render contentType: 'text/html', data: """
    <html>
    <head><title>Error</title></head>
    <body style="font-family: sans-serif; padding: 20px;">
        <h2>✗ Connection Failed</h2>
        <p>${message}</p>
        <p>Please check the Hubitat logs for details and try again.</p>
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
