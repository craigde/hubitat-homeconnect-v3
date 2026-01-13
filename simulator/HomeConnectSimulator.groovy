/**
 *  Home Connect Simulator v3 (Parent App)
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
 *  SIMULATOR APP
 *  ===========================================================================================================
 *  
 *  This app connects to the Home Connect SIMULATOR API for testing and development.
 *  It uses virtual appliances, not real devices.
 *  
 *  Key differences from production:
 *  - Uses simulator.home-connect.com instead of api.home-connect.com
 *  - Uses shared Client ID (no registration required)
 *  - No Client Secret required
 *  - Simplified OAuth flow (no login confirmation)
 *  - Device DNI prefix: HC3SIM- (allows running alongside production)
 *  
 *  DELETE THIS APP BEFORE PUBLIC RELEASE - it's only for development/testing.
 *  
 *  ===========================================================================================================
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

definition(
    name: 'Home Connect Simulator v3',
    namespace: 'craigde',
    author: 'Craig Dewar',
    description: 'DEVELOPMENT ONLY - Connects to Home Connect Simulator for testing virtual appliances',
    category: 'My Apps',
    iconUrl: '',
    iconX2Url: '',
    iconX3Url: ''
)

/* ===========================================================================================================
   CONSTANTS
   =========================================================================================================== */

@Field static final List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field static final String DEFAULT_LOG_LEVEL = "debug"  // More verbose for testing
@Field static final String STREAM_DRIVER_DNI = "HC3SIM-StreamDriver"
@Field static final String DEVICE_PREFIX = "HC3SIM-"
@Field static final String APP_VERSION = "3.0.0-simulator"

// Simulator API endpoints
@Field static final String API_BASE_URL = "https://simulator.home-connect.com"
@Field static final String OAUTH_AUTHORIZATION_URL = 'https://simulator.home-connect.com/security/oauth/authorize'
@Field static final String OAUTH_TOKEN_URL = 'https://simulator.home-connect.com/security/oauth/token'

// Shared simulator Client ID (from Home Connect developer portal)
@Field static final String SIMULATOR_CLIENT_ID = '39679EA438A751FB65C87E1E958B018F1656719DDD9D3F025D93FFBB8F4A001C'

/* ===========================================================================================================
   LIFECYCLE METHODS
   =========================================================================================================== */

def installed() {
    logInfo("Installing Home Connect Simulator v3")
    createStreamDriver()
}

def uninstalled() {
    logInfo("Uninstalling Home Connect Simulator v3")
    deleteChildDevicesByDevices(getChildDevices())
}

def updated() {
    logInfo("Updating Home Connect Simulator v3")
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
 * Introduction page - simplified for simulator
 */
def pageIntro() {
    logDebug("Showing Introduction Page")

    return dynamicPage(
        name: 'pageIntro',
        title: 'Home Connect Simulator v3',
        nextPage: 'pageAuthentication',
        install: false,
        uninstall: true
    ) {
        section('⚠️ Development/Testing Only') {
            paragraph """\
<b>This app connects to the Home Connect SIMULATOR, not real appliances.</b>

Use this to test drivers with virtual appliances before connecting to your real devices.

<b>Before you begin:</b>
1. Go to <a href="https://developer.home-connect.com/simulator" target="_blank">developer.home-connect.com/simulator</a>
2. Start the simulator and select which virtual appliances to enable
3. Come back here and click Next to authenticate

No Client ID or Secret needed - the simulator uses a shared test account.
"""
        }
        section('Logging') {
            input name: 'logLevel', title: 'Log Level', type: 'enum', options: LOG_LEVELS, 
                  defaultValue: DEFAULT_LOG_LEVEL, required: false,
                  description: "Debug recommended for simulator testing"
        }
    }
}

/**
 * Authentication page - handles OAuth flow with simulator
 */
def pageAuthentication() {
    logDebug("Showing Authentication Page")

    // Create Hubitat access token if not exists
    if (!atomicState.accessToken) {
        atomicState.accessToken = createAccessToken()
    }

    return dynamicPage(
        name: 'pageAuthentication',
        title: 'Simulator Authentication',
        nextPage: 'pageDevices',
        install: false,
        uninstall: false,
        refreshInterval: 0
    ) {
        section() {
            if (atomicState.oAuthAuthToken) {
                showHideNextButton(true)
                paragraph '<b>✓ Connected to Simulator!</b> Press Next to select virtual appliances.'
                href url: generateOAuthUrl(), style: 'external', required: false,
                     title: "Re-authenticate", description: "Tap to reconnect if needed"
            } else {
                showHideNextButton(false)
                paragraph 'Click the button below to connect to the Home Connect Simulator.'
                href url: generateOAuthUrl(), style: 'external', required: false,
                     title: "Connect to Simulator", description: "Tap to authenticate"
            }
        }
    }
}

/**
 * Device selection page - lists simulator appliances
 */
def pageDevices() {
    logDebug("Showing Devices Page")
    
    def homeConnectDevices = fetchHomeConnectDevices()
    
    def deviceList = [:]
    state.foundDevices = []

    homeConnectDevices.each { appliance ->
        deviceList << ["${appliance.haId}": "${appliance.name} (${appliance.type}) [SIM]"]
        state.foundDevices << [haId: appliance.haId, name: appliance.name, type: appliance.type]
    }

    return dynamicPage(
        name: 'pageDevices',
        title: 'Select Simulator Appliances',
        install: true,
        uninstall: true
    ) {
        section('🔧 Simulator Mode') {
            paragraph "These are <b>virtual appliances</b> from the Home Connect Simulator. They behave like real appliances but don't control any physical devices."
        }
        section() {
            if (deviceList.isEmpty()) {
                paragraph """\
<b>No simulator appliances found.</b>

Make sure you have:
1. Started the simulator at <a href="https://developer.home-connect.com/simulator" target="_blank">developer.home-connect.com/simulator</a>
2. Enabled at least one virtual appliance
3. Authenticated successfully on the previous page
"""
            } else {
                paragraph "Found ${deviceList.size()} simulator appliance(s):"
                input name: 'devices', title: 'Virtual Appliances', type: 'enum', required: true,
                      multiple: true, options: deviceList
            }
        }
    }
}

/**
 * Fetches list of appliances from Home Connect Simulator API
 */
private List fetchHomeConnectDevices() {
    def homeConnectDevices = []
    
    try {
        def token = getOAuthToken()
        
        logDebug("Fetching appliances from simulator: ${API_BASE_URL}")
        
        httpGet(
            uri: "${API_BASE_URL}/api/homeappliances",
            contentType: "application/json",
            headers: [
                'Authorization': "Bearer ${token}",
                'Accept': "application/vnd.bsh.sdk.v1+json"
            ]
        ) { response ->
            if (response.data?.data?.homeappliances) {
                homeConnectDevices = response.data.data.homeappliances
                logDebug("Found ${homeConnectDevices.size()} simulator appliance(s)")
            }
        }
    } catch (Exception e) {
        logError("Failed to fetch simulator appliances: ${e.message}")
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
        logDebug("Simulator Stream Driver already exists")
        return
    }
    
    logInfo("Creating Simulator Stream Driver")
    try {
        def driver = addChildDevice('craigde', 'Home Connect Stream Driver v3', STREAM_DRIVER_DNI)
        // Set the simulator API URL
        driver.z_setApiUrl(API_BASE_URL)
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
    return "${DEVICE_PREFIX}${haId}"
}

/**
 * Synchronizes child devices with selected appliances
 */
def synchronizeDevices() {
    logDebug("Synchronizing simulator devices")
    
    // Ensure stream driver exists and has correct API URL
    createStreamDriver()
    getStreamDriver()?.z_setApiUrl(API_BASE_URL)
    
    def childDevices = getChildDevices()
    def childrenMap = childDevices.collectEntries { [(it.deviceNetworkId): it] }
    
    // Don't touch the stream driver
    childrenMap.remove(STREAM_DRIVER_DNI)

    def newDevices = []
    
    // Create devices for newly selected appliances
    for (homeConnectDeviceId in settings.devices) {
        def hubitatDeviceId = homeConnectIdToDeviceNetworkId(homeConnectDeviceId)

        if (childrenMap.containsKey(hubitatDeviceId)) {
            childrenMap.remove(hubitatDeviceId)
            continue
        }

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
    
    // Initialize new devices after a short delay
    if (newDevices) {
        runIn(5, "initializeNewDevices", [data: [deviceIds: newDevices.collect { it.deviceNetworkId }]])
    }
}

/**
 * Initializes newly created devices
 */
def initializeNewDevices(Map data) {
    logInfo("Initializing ${data.deviceIds.size()} new simulator device(s)")
    
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
        logWarn("No driver available for appliance type: ${type} - skipping")
        return null
    }
    
    try {
        logInfo("Creating ${driverName} device (simulator)")
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
            logInfo("Removing simulator device: ${d.displayName}")
            deleteChildDevice(d.deviceNetworkId)
        }
    }
}

/* ===========================================================================================================
   EVENT ROUTING
   =========================================================================================================== */

/**
 * Called by Stream Driver when an appliance event is received
 */
def handleApplianceEvent(Map evt) {
    if (!evt?.haId || !evt?.key) {
        logWarn("handleApplianceEvent: missing haId or key")
        return
    }
    
    logDebug("Routing event: ${evt.key} = ${evt.value} for ${evt.haId}")

    String childDni = "${DEVICE_PREFIX}${evt.haId}"
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
    logDebug("Simulator appliance ${haId} connection status: ${status}")
    
    String childDni = "${DEVICE_PREFIX}${haId}"
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
 * Called by Stream Driver after reconnecting
 */
def refreshAllDeviceStatus() {
    logInfo("Refreshing status for all simulator devices after reconnect")
    
    getChildDevices().each { child ->
        if (child.deviceNetworkId != STREAM_DRIVER_DNI) {
            try {
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
    return device.deviceNetworkId?.replaceFirst(/^HC3SIM-/, "")
}

/**
 * Initializes status for a device
 */
def initializeStatus(device, boolean checkActiveProgram = true) {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    
    if (!streamDriver) {
        logError("Stream Driver not available")
        return
    }
    
    logDebug("Initializing status for simulator device ${haId}")

    streamDriver.getStatus(haId) { status ->
        device.z_parseStatus(JsonOutput.toJson(status))
    }

    streamDriver.getSettings(haId) { settings ->
        device.z_parseSettings(JsonOutput.toJson(settings))
    }

    if (checkActiveProgram) {
        try {
            streamDriver.getActiveProgram(haId) { activeProgram ->
                device.z_parseActiveProgram(JsonOutput.toJson(activeProgram))
            }
        } catch (Exception e) {
            // No active program
        }
    }
}

/**
 * Starts a program on an appliance
 */
def startProgram(device, String programKey, def options = "") {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    
    logInfo("Starting program ${programKey} on simulator device ${haId}")
    
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
    
    logInfo("Stopping program on simulator device ${haId}")
    
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
    
    logInfo("Setting power ${state ? 'ON' : 'OFF'} on simulator device ${haId}")
    
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
 */
def getAvailableProgramList(device) {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    
    logDebug("Fetching available programs for simulator device ${haId}")
    
    try {
        streamDriver?.getAvailablePrograms(haId) { programs ->
            if (programs) {
                try {
                    device.z_parseAvailablePrograms(JsonOutput.toJson(programs))
                } catch (Exception e) {
                    logDebug("Device doesn't support z_parseAvailablePrograms: ${e.message}")
                }
            }
        }
    } catch (Exception e) {
        logDebug("Cannot fetch programs for ${haId}: ${e.message}")
    }
}

/**
 * Gets available options for a specific program
 */
def getAvailableProgramOptionsList(device, String programKey) {
    def haId = getHaIdFromDevice(device)
    def streamDriver = getStreamDriver()
    
    logDebug("Fetching options for program ${programKey} on simulator device ${haId}")
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
    
    logDebug("Setting selected program ${programKey} on simulator device ${haId}")
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
    
    logDebug("Setting option ${optionKey}=${optionValue} on simulator device ${haId}")
    streamDriver?.setSelectedProgramOption(haId, optionKey, optionValue) { response ->
        logDebug("setSelectedProgramOption response: ${response}")
    }
}

/* ===========================================================================================================
   OAUTH TOKEN MANAGEMENT
   =========================================================================================================== */

/**
 * Gets a valid OAuth token
 */
def getOAuthToken() {
    if (now() >= (atomicState.oAuthTokenExpires ?: 0) - 60_000) {
        refreshOAuthToken()
    }
    return atomicState.oAuthAuthToken
}

/**
 * Called by Stream Driver when a 401 error occurs
 */
def refreshOAuthTokenAndRetry() {
    logInfo("Forcing OAuth token refresh due to 401 error")
    refreshOAuthToken()
    return atomicState.oAuthAuthToken != null
}

/**
 * Gets the language (simulator doesn't need this but keeping for API compatibility)
 */
def getLanguage() {
    return "en-US"
}

/* ===========================================================================================================
   OAUTH AUTHENTICATION FLOW
   =========================================================================================================== */

mappings {
    path("/oauth/callback") { action: [GET: "oAuthCallback"] }
}

/**
 * Generates the OAuth authorization URL for simulator
 */
private String generateOAuthUrl() {
    def timestamp = now().toString()
    def stateValue = generateSecureState(timestamp)

    def queryParams = [
        'client_id': SIMULATOR_CLIENT_ID,
        'redirect_uri': getOAuthRedirectUrl(),
        'response_type': 'code',
        'scope': 'IdentifyAppliance Monitor Settings Control',
        'state': stateValue
    ]
    
    def queryString = queryParams.collect { k, v -> "${k}=${URLEncoder.encode(v.toString(), 'UTF-8')}" }.join("&")
    
    return "${OAUTH_AUTHORIZATION_URL}?${queryString}"
}

/**
 * Generates a secure state value for OAuth CSRF protection
 */
private String generateSecureState(String timestamp) {
    def message = "${timestamp}:${SIMULATOR_CLIENT_ID}"
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

        def stateAge = now() - timestamp.toLong()
        if (stateAge < 0 || stateAge > 600000) {
            logError("OAuth state expired: ${stateAge}ms old")
            return false
        }

        def message = "${timestamp}:${SIMULATOR_CLIENT_ID}"
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
 */
private String getOAuthRedirectUrl() {
    return "${getFullApiServerUrl()}/oauth/callback?access_token=${atomicState.accessToken}"
}

/**
 * Handles the OAuth callback from Home Connect Simulator
 */
def oAuthCallback() {
    logDebug("Received OAuth callback from simulator")

    def code = params.code
    def oAuthState = params.state

    if (!code) {
        logError("No authorization code in OAuth callback")
        return renderOAuthFailure()
    }

    if (!oAuthState || !validateSecureState(oAuthState)) {
        logError("Invalid OAuth state in callback")
        return renderOAuthFailure()
    }

    atomicState.oAuthRefreshToken = null
    atomicState.oAuthAuthToken = null
    atomicState.oAuthTokenExpires = null

    acquireOAuthToken(code)

    if (!atomicState.oAuthAuthToken) {
        logError("Failed to acquire OAuth token from simulator")
        return renderOAuthFailure()
    }

    logInfo("Simulator OAuth authentication successful")
    return renderOAuthSuccess()
}

/**
 * Exchanges authorization code for access token
 */
private void acquireOAuthToken(String code) {
    logDebug("Acquiring OAuth token from simulator")
    apiRequestAccessToken([
        'grant_type': 'authorization_code',
        'code': code,
        'client_id': SIMULATOR_CLIENT_ID,
        'redirect_uri': getOAuthRedirectUrl()
    ])
}

/**
 * Refreshes the OAuth access token
 */
private void refreshOAuthToken() {
    logDebug("Refreshing OAuth token from simulator")
    apiRequestAccessToken([
        'grant_type': 'refresh_token',
        'refresh_token': atomicState.oAuthRefreshToken
    ])
}

/**
 * Makes the OAuth token request
 */
private void apiRequestAccessToken(Map body) {
    try {
        httpPost(uri: OAUTH_TOKEN_URL, requestContentType: 'application/x-www-form-urlencoded', body: body) { response ->
            if (response?.data && response.success) {
                atomicState.oAuthRefreshToken = response.data.refresh_token
                atomicState.oAuthAuthToken = response.data.access_token
                atomicState.oAuthTokenExpires = now() + (response.data.expires_in * 1000)
                logDebug("Simulator OAuth token acquired, expires in ${response.data.expires_in}s")
            } else {
                logError("Failed to acquire OAuth token from simulator")
            }
        }
    } catch (Exception e) {
        logError("Failed to acquire OAuth token: ${e.message}")
    }
}

/**
 * Renders success page after OAuth
 */
private def renderOAuthSuccess() {
    render contentType: 'text/html', data: '''
    <html>
    <head><title>Simulator Connected</title></head>
    <body style="font-family: sans-serif; padding: 20px;">
        <h2>✓ Connected to Simulator!</h2>
        <p>Your Home Connect Simulator account is now linked.</p>
        <p>You can close this window and continue setup.</p>
    </body>
    </html>
    '''
}

/**
 * Renders failure page after OAuth error
 */
private def renderOAuthFailure() {
    render contentType: 'text/html', data: '''
    <html>
    <head><title>Error</title></head>
    <body style="font-family: sans-serif; padding: 20px;">
        <h2>✗ Connection Failed</h2>
        <p>Unable to connect to Home Connect Simulator.</p>
        <p>Please check the Hubitat logs for details and try again.</p>
    </body>
    </html>
    '''
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
