/**
 *  Home Connect Dishwasher v3 Driver
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
 *  DRIVER OVERVIEW
 *  ===========================================================================================================
 *  
 *  This driver represents a Home Connect dishwasher appliance. It receives real-time events from the
 *  Stream Driver (via the parent app) and provides commands to control the dishwasher.
 *  
 *  Event Flow:
 *  -----------
 *  Stream Driver → parent.handleApplianceEvent() → parseEvent() → sendEvent() → attribute update
 *  
 *  Command Flow:
 *  -------------
 *  User command → parent.startProgram() → Stream Driver → Home Connect API
 *  
 *  Internal Commands (z_ prefix):
 *  ------------------------------
 *  Commands prefixed with z_ are called by the parent app to pass data to the driver.
 *  They are not intended for user interaction and are grouped at the bottom of the command list.
 *  
 *  Debugging:
 *  ----------
 *  Enable "Debug Logging" in preferences to capture detailed event information.
 *  Use "dumpState" command to output all current attribute values.
 *  Use "getDiscoveredKeys" to see all event keys received from your appliance.
 *  Check "lastUnhandledEvent" attribute for events not yet supported by this driver.
 *  
 *  ===========================================================================================================
 *
 *  Version History:
 *  ----------------
 *  3.0.0  2026-01-07  Initial v3 architecture with parseEvent() pattern
 *  3.0.1  2026-01-08  Added PushableButton capability for cycle complete notifications
 *                     Added jsonState attribute for Node-RED integration
 *                     Added lastAlert/lastAlertTime for alert tracking
 *                     Added lastCommandStatus for command feedback
 *  3.0.2  2026-01-08  Added lastProgram tracking and default program support
 *                     Made program parameter optional in startProgram/startProgramDelayed
 *                     Added simple start() command for automation
 *                     Updated program dropdown with friendly names (Heavy, Normal, etc.)
 *  3.0.3  2026-01-09  Fixed start command definition for Hubitat compatibility
 *  3.0.4  2026-01-14  Enhanced debugging for remote troubleshooting
 *                     Added dumpState, getDiscoveredKeys commands
 *                     Added lastUnhandledEvent, recentEvents tracking
 *                     Added raw event logging option
 */

import groovy.json.JsonSlurper
import groovy.transform.Field

metadata {
    definition(name: "Home Connect Dishwasher v3", namespace: "craigde", author: "Craig Dewar") {

        // Standard capabilities
        capability "Initialize"
        capability "Refresh"
        capability "Switch"
        capability "ContactSensor"      // Door state (open/closed)
        capability "Sensor"
        capability "Configuration"
        capability "PushableButton"     // For cycle complete notifications

        // =====================================================================
        // USER-FACING COMMANDS
        // =====================================================================
        
        command "getAvailablePrograms"
        
        command "start"
        
        command "startProgram", [
            [name: "Program", type: "ENUM", constraints: [
                // Common friendly names (US/international)
                "Heavy", "Auto", "Normal", "Delicate", "Express", 
                "Rinse", "Machine Care",
                // European technical names
                "Auto1", "Auto2", "Auto3",
                "Eco50", "Quick45", "Quick65",
                "Intensiv70", "Intensiv45",
                "NightWash", "Glas40", "GlassCare",
                "MachineCare", "PreRinse",
                "-- Or use startProgramByKey --"
            ], description: "Leave empty to use last program"]
        ]
        
        command "startProgramByKey", [
            [name: "Program Key*", type: "STRING", description: "e.g., Dishcare.Dishwasher.Program.Eco50"]
        ]
        
        command "startProgramDelayed", [
            [name: "Delay (minutes)*", type: "NUMBER"],
            [name: "Program", type: "ENUM", constraints: [
                // Common friendly names (US/international)
                "Heavy", "Auto", "Normal", "Delicate", "Express", 
                "Rinse", "Machine Care",
                // European technical names
                "Auto1", "Auto2", "Auto3",
                "Eco50", "Quick45", "Quick65",
                "Intensiv70", "Intensiv45",
                "NightWash", "Glas40", "GlassCare",
                "MachineCare", "PreRinse"
            ], description: "Leave empty to use last program"]
        ]
        
        command "stopProgram"
        
        command "setPower", [
            [name: "State*", type: "ENUM", constraints: ["on", "off"]]
        ]
        
        command "setProgramOption", [
            [name: "Option Key*", type: "STRING"],
            [name: "Value*", type: "STRING"]
        ]

        // =====================================================================
        // DEBUGGING COMMANDS
        // =====================================================================
        
        command "dumpState", [[name: "description", description: "Logs all current attribute values to help with troubleshooting"]]
        command "getDiscoveredKeys", [[name: "description", description: "Shows all event keys received from your appliance"]]
        command "clearDiscoveredKeys", [[name: "description", description: "Clears the list of discovered event keys"]]
        command "getRecentEvents", [[name: "count", type: "NUMBER", description: "Number of recent events to display (default 10)"]]

        // =====================================================================
        // ATTRIBUTES - Status
        // =====================================================================
        
        attribute "operationState", "string"        // Ready, Run, Finished, etc.
        attribute "doorState", "string"             // Open, Closed
        attribute "powerState", "string"            // On, Off, Standby
        attribute "friendlyStatus", "string"        // Human-readable status

        // =====================================================================
        // ATTRIBUTES - Program & Progress
        // =====================================================================
        
        attribute "activeProgram", "string"         // Currently running program
        attribute "selectedProgram", "string"       // Selected but not started
        attribute "programProgress", "number"       // 0-100 percent
        attribute "progressBar", "string"           // "45%"
        attribute "cyclePhase", "string"            // Prewash, Wash, Rinse, Dry, Complete

        // =====================================================================
        // ATTRIBUTES - Timing
        // =====================================================================
        
        attribute "remainingProgramTime", "number"          // Seconds remaining
        attribute "remainingProgramTimeFormatted", "string" // "01:23"
        attribute "elapsedProgramTime", "number"            // Seconds elapsed
        attribute "elapsedProgramTimeFormatted", "string"   // "00:45"
        attribute "estimatedEndTimeFormatted", "string"     // "3:45 PM"
        attribute "startInRelative", "string"               // Delayed start time

        // =====================================================================
        // ATTRIBUTES - Control State
        // =====================================================================
        
        attribute "remoteControlStartAllowed", "string"
        attribute "remoteControlActive", "string"
        attribute "localControlActive", "string"

        // =====================================================================
        // ATTRIBUTES - Dishwasher Options
        // =====================================================================
        
        attribute "IntensivZone", "string"
        attribute "BrillianceDry", "string"
        attribute "VarioSpeedPlus", "string"
        attribute "SilenceOnDemand", "string"
        attribute "HalfLoad", "string"
        attribute "ExtraDry", "string"
        attribute "HygienePlus", "string"

        // =====================================================================
        // ATTRIBUTES - Events & Alerts
        // =====================================================================
        
        attribute "RinseAidNearlyEmpty", "string"
        attribute "SaltNearlyEmpty", "string"
        attribute "lastAlert", "string"             // Most recent alert message
        attribute "lastAlertTime", "string"         // When the alert occurred

        // =====================================================================
        // ATTRIBUTES - Integration Support
        // =====================================================================
        
        attribute "jsonState", "string"             // JSON blob of all state for Node-RED
        attribute "lastCommandStatus", "string"     // Success/failure of last command

        // =====================================================================
        // ATTRIBUTES - Debugging
        // =====================================================================
        
        attribute "lastUnhandledEvent", "string"        // Most recent unhandled event key=value
        attribute "lastUnhandledEventTime", "string"    // When it occurred
        attribute "lastCommandSent", "string"           // Last command sent to API
        attribute "lastCommandTime", "string"           // When command was sent
        attribute "discoveredKeysCount", "number"       // Count of unique event keys seen

        // =====================================================================
        // ATTRIBUTES - Lists & Meta
        // =====================================================================
        
        attribute "availableProgramsList", "string"
        attribute "availableOptionsList", "string"
        attribute "lastProgram", "string"           // Last program used (for defaults)
        attribute "driverVersion", "string"
        attribute "eventStreamStatus", "string"
        attribute "eventPresentState", "string"

        // =====================================================================
        // INTERNAL COMMANDS (z_ prefix)
        // Called by parent app - not for direct user interaction
        // =====================================================================
        
        command "z_parseStatus", [[name: "json", type: "STRING"]]
        command "z_parseSettings", [[name: "json", type: "STRING"]]
        command "z_parseAvailablePrograms", [[name: "json", type: "STRING"]]
        command "z_parseAvailableOptions", [[name: "json", type: "STRING"]]
        command "z_parseActiveProgram", [[name: "json", type: "STRING"]]
        command "z_updateEventStreamStatus", [[name: "status", type: "STRING"]]
        command "z_updateEventPresentState", [[name: "state", type: "STRING"]]
        command "z_deviceLog", [[name: "level", type: "STRING"], [name: "msg", type: "STRING"]]
    }

    preferences {
        input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false,
              description: "Enable detailed logging for troubleshooting"
        input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false,
              description: "Enable verbose trace logging (very detailed, may impact performance)"
        input name: "logRawEvents", type: "bool", title: "Log raw events", defaultValue: false,
              description: "Log complete raw event data before parsing (useful for reporting issues)"
        input name: "maxRecentEvents", type: "number", title: "Recent events to keep", defaultValue: 20,
              description: "Number of recent events to store for troubleshooting (0 to disable)"
    }
}

/* ===========================================================================================================
   CONSTANTS
   =========================================================================================================== */

@Field static final String DRIVER_VERSION = "3.0.4"
@Field static final Integer MAX_DISCOVERED_KEYS = 100

/* ===========================================================================================================
   LIFECYCLE METHODS
   =========================================================================================================== */

def installed() {
    log.info "${device.displayName}: Installed"
    initializeState()
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "eventPresentState", value: "Off")
    
    // Configure pushable button for notifications
    // Button 1: Cycle Complete
    // Button 2: Salt Low Alert
    // Button 3: Rinse Aid Low Alert
    // Button 4: Error Alert
    sendEvent(name: "numberOfButtons", value: 4)
}

def updated() {
    log.info "${device.displayName}: Updated"
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    
    // Initialize state if needed
    if (state.discoveredKeys == null) {
        initializeState()
    }
}

/**
 * Initializes state variables for debugging
 */
private void initializeState() {
    if (state.discoveredKeys == null) state.discoveredKeys = [:]
    if (state.recentEvents == null) state.recentEvents = []
    if (state.programMap == null) state.programMap = [:]
    if (state.programNames == null) state.programNames = []
}

/**
 * Initializes the device by fetching current status from Home Connect
 */
def initialize() {
    logInfo("Initializing")
    initializeState()
    parent?.initializeStatus(device)
    runIn(5, "getAvailablePrograms")
}

/**
 * Refreshes all device data from Home Connect
 */
def refresh() {
    logInfo("Refreshing")
    parent?.initializeStatus(device)
    getAvailablePrograms()
}

def configure() {
    logInfo("Configuring")
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    initializeState()
}

/* ===========================================================================================================
   DEBUGGING COMMANDS
   =========================================================================================================== */

/**
 * Dumps all current attribute values to the log
 * Useful for troubleshooting and reporting issues
 */
def dumpState() {
    logInfo("=== DEVICE STATE DUMP ===")
    logInfo("Driver Version: ${DRIVER_VERSION}")
    logInfo("Device Network ID: ${device.deviceNetworkId}")
    logInfo("")
    
    logInfo("--- Current Attributes ---")
    device.currentStates.each { attr ->
        logInfo("  ${attr.name}: ${attr.value}")
    }
    
    logInfo("")
    logInfo("--- State Variables ---")
    logInfo("  programMap keys: ${state.programMap?.keySet()?.join(', ') ?: 'none'}")
    logInfo("  programNames: ${state.programNames?.join(', ') ?: 'none'}")
    logInfo("  discoveredKeys count: ${state.discoveredKeys?.size() ?: 0}")
    logInfo("  recentEvents count: ${state.recentEvents?.size() ?: 0}")
    
    logInfo("")
    logInfo("--- Settings ---")
    logInfo("  debugLogging: ${debugLogging}")
    logInfo("  traceLogging: ${traceLogging}")
    logInfo("  logRawEvents: ${logRawEvents}")
    logInfo("  maxRecentEvents: ${maxRecentEvents}")
    
    logInfo("=== END STATE DUMP ===")
}

/**
 * Shows all event keys that have been received from the appliance
 * Helps identify what keys a specific appliance model sends
 */
def getDiscoveredKeys() {
    logInfo("=== DISCOVERED EVENT KEYS ===")
    logInfo("Total unique keys: ${state.discoveredKeys?.size() ?: 0}")
    logInfo("")
    
    state.discoveredKeys?.sort()?.each { key, info ->
        logInfo("  ${key}")
        logInfo("    Last value: ${info.lastValue}")
        logInfo("    Count: ${info.count}")
        logInfo("    First seen: ${info.firstSeen}")
        logInfo("    Last seen: ${info.lastSeen}")
        logInfo("")
    }
    
    logInfo("=== END DISCOVERED KEYS ===")
    
    // Also update the count attribute
    sendEvent(name: "discoveredKeysCount", value: state.discoveredKeys?.size() ?: 0)
}

/**
 * Clears the discovered keys list
 */
def clearDiscoveredKeys() {
    logInfo("Clearing discovered keys")
    state.discoveredKeys = [:]
    sendEvent(name: "discoveredKeysCount", value: 0)
}

/**
 * Shows recent events for troubleshooting
 */
def getRecentEvents(BigDecimal count = 10) {
    Integer num = count?.toInteger() ?: 10
    logInfo("=== RECENT EVENTS (last ${num}) ===")
    
    def events = state.recentEvents ?: []
    def toShow = events.take(num)
    
    toShow.eachWithIndex { evt, idx ->
        logInfo("${idx + 1}. [${evt.time}] ${evt.key} = ${evt.value}")
    }
    
    if (events.size() > num) {
        logInfo("... and ${events.size() - num} more events stored")
    }
    
    logInfo("=== END RECENT EVENTS ===")
}

/**
 * Records an event for debugging purposes
 */
private void recordEvent(Map evt) {
    if (!evt?.key) return
    
    def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
    
    // Track discovered keys
    if (state.discoveredKeys == null) state.discoveredKeys = [:]
    
    if (state.discoveredKeys.size() < MAX_DISCOVERED_KEYS) {
        if (!state.discoveredKeys.containsKey(evt.key)) {
            state.discoveredKeys[evt.key] = [
                firstSeen: timestamp,
                lastSeen: timestamp,
                lastValue: evt.value?.toString()?.take(100),  // Truncate long values
                count: 1
            ]
        } else {
            state.discoveredKeys[evt.key].lastSeen = timestamp
            state.discoveredKeys[evt.key].lastValue = evt.value?.toString()?.take(100)
            state.discoveredKeys[evt.key].count = (state.discoveredKeys[evt.key].count ?: 0) + 1
        }
    }
    
    // Track recent events
    def maxEvents = (settings?.maxRecentEvents ?: 20) as Integer
    if (maxEvents > 0) {
        if (state.recentEvents == null) state.recentEvents = []
        
        state.recentEvents.add(0, [
            time: timestamp,
            key: evt.key,
            value: evt.value?.toString()?.take(100),
            displayvalue: evt.displayvalue?.take(50)
        ])
        
        // Trim to max size
        if (state.recentEvents.size() > maxEvents) {
            state.recentEvents = state.recentEvents.take(maxEvents)
        }
    }
}

/**
 * Records a command being sent for debugging
 */
private void recordCommand(String command, Map params = [:]) {
    def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
    def cmdInfo = params ? "${command}: ${params}" : command
    
    sendEvent(name: "lastCommandSent", value: cmdInfo.take(200))
    sendEvent(name: "lastCommandTime", value: timestamp)
    
    logDebug("Command sent: ${cmdInfo}")
}

/* ===========================================================================================================
   USER-FACING COMMANDS
   =========================================================================================================== */

/**
 * Switch capability - turns power on
 */
def on() { 
    setPower("on") 
}

/**
 * Switch capability - turns power off
 */
def off() { 
    setPower("off") 
}

/**
 * Fetches available programs from the dishwasher
 * Results are returned via z_parseAvailablePrograms callback
 */
def getAvailablePrograms() {
    logInfo("Fetching available programs")
    recordCommand("getAvailablePrograms")
    parent?.getAvailableProgramList(device)
}

/**
 * Starts the last used program (or Normal/Eco50 if none)
 * Convenience command for simple automation
 */
def start() {
    def program = getDefaultProgram()
    logInfo("Starting default program: ${program}")
    startProgram(program)
}

/**
 * Starts a program from the dropdown list
 * If no program specified, uses last program or default
 */
def startProgram(String program = null) {
    def selectedProgram = program ?: getDefaultProgram()
    def programKey = buildProgramKey(selectedProgram)
    
    // Remember this program for next time
    saveLastProgram(selectedProgram)
    
    logInfo("Starting program: ${selectedProgram} (${programKey})")
    recordCommand("startProgram", [program: selectedProgram, key: programKey])
    parent?.startProgram(device, programKey)
}

/**
 * Starts a program using the full Home Connect key
 */
def startProgramByKey(String programKey) {
    // Extract program name for saving
    def programName = extractEnum(programKey)
    saveLastProgram(programName)
    
    logInfo("Starting program by key: ${programKey}")
    recordCommand("startProgramByKey", [key: programKey])
    parent?.startProgram(device, programKey)
}

/**
 * Starts a program after a delay
 * If no program specified, uses last program or default
 */
def startProgramDelayed(BigDecimal delayMinutes, String program = null) {
    def selectedProgram = program ?: getDefaultProgram()
    def programKey = buildProgramKey(selectedProgram)
    Integer minutes = delayMinutes?.toInteger() ?: 0
    Integer delaySeconds = minutes * 60
    
    // Remember this program for next time
    saveLastProgram(selectedProgram)
    
    logInfo("Starting program '${selectedProgram}' with ${minutes} minute delay")
    recordCommand("startProgramDelayed", [program: selectedProgram, key: programKey, delayMinutes: minutes])
    
    def options = [[key: "BSH.Common.Option.StartInRelative", value: delaySeconds, unit: "seconds"]]
    parent?.startProgram(device, programKey, options)
}

/**
 * Stops the currently running program
 */
def stopProgram() {
    logInfo("Stopping program")
    recordCommand("stopProgram")
    parent?.stopProgram(device)
}

/**
 * Sets the dishwasher power state
 */
def setPower(String powerState) {
    boolean on = (powerState == "on")
    logInfo("Setting power: ${on ? 'ON' : 'OFF'}")
    recordCommand("setPower", [state: powerState])
    parent?.setPowerState(device, on)
}

/**
 * Sets an option on the selected program
 * Automatically converts string values to appropriate types
 */
def setProgramOption(String optionKey, String value) {
    logInfo("Setting option ${optionKey} = ${value}")
    recordCommand("setProgramOption", [key: optionKey, value: value])
    
    // Convert to appropriate type
    def typedValue = value
    if (value.isInteger()) {
        typedValue = value.toInteger()
    } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
        typedValue = value.toBoolean()
    }
    
    parent?.setSelectedProgramOption(device, optionKey, typedValue)
}

/**
 * Builds a full program key from a short name
 */
private String buildProgramKey(String program) {
    // If already a full key, return as-is
    if (program.contains(".")) {
        return program
    }
    
    // Check if we have a mapping from getAvailablePrograms
    if (state.programMap?.containsKey(program)) {
        return state.programMap[program]
    }
    
    // Build standard Dishwasher program key
    return "Dishcare.Dishwasher.Program.${program}"
}

/**
 * Gets the default program to use when none specified
 * Returns: last used program, or "Normal", or first available program
 */
private String getDefaultProgram() {
    // First choice: last used program
    def lastProgram = device.currentValue("lastProgram")
    if (lastProgram) {
        logDebug("Using last program: ${lastProgram}")
        return lastProgram
    }
    
    // Second choice: "Normal" if available
    if (state.programNames?.contains("Normal")) {
        logDebug("Using default: Normal")
        return "Normal"
    }
    
    // Third choice: first available program
    if (state.programNames?.size() > 0) {
        def first = state.programNames[0]
        logDebug("Using first available: ${first}")
        return first
    }
    
    // Fallback: generic "Normal" and hope for the best
    logDebug("No programs known, falling back to Normal")
    return "Normal"
}

/**
 * Saves the program as the last used for future defaults
 */
private void saveLastProgram(String program) {
    if (program && program != "-- Or use startProgramByKey --") {
        sendEvent(name: "lastProgram", value: program)
        logDebug("Saved last program: ${program}")
    }
}

/* ===========================================================================================================
   INTERNAL COMMANDS (z_ prefix)
   Called by parent app to pass data to the driver
   =========================================================================================================== */

/**
 * Parses status data from Home Connect API
 */
def z_parseStatus(String json) {
    logDebug("Parsing status")
    logTrace("Status JSON: ${json}")
    def list = new JsonSlurper().parseText(json)
    parseItemList(list)
}

/**
 * Parses settings data from Home Connect API
 */
def z_parseSettings(String json) {
    logDebug("Parsing settings")
    logTrace("Settings JSON: ${json}")
    def list = new JsonSlurper().parseText(json)
    parseItemList(list)
}

/**
 * Parses available programs list
 */
def z_parseAvailablePrograms(String json) {
    logDebug("Parsing available programs")
    logTrace("Programs JSON: ${json}")
    def list = new JsonSlurper().parseText(json)
    
    def programMap = [:]
    def programNames = []
    
    list.each { prog ->
        def key = prog.key
        def name = prog.name ?: extractEnum(key)
        programMap[name] = key
        programNames << name
        logDebug("Program: ${name} -> ${key}")
    }
    
    state.programMap = programMap
    state.programNames = programNames
    
    logInfo("Found ${programNames.size()} available programs: ${programNames.join(', ')}")
    logInfo("Use 'startProgramByKey' with these keys if dropdown doesn't match your appliance")
    
    // Log the full mapping at debug level
    programMap.each { name, key ->
        logDebug("  ${name}: ${key}")
    }
    
    sendEvent(name: "availableProgramsList", value: programNames.join(", "))
}

/**
 * Parses available options for a program
 */
def z_parseAvailableOptions(String json) {
    logDebug("Parsing available options")
    logTrace("Options JSON: ${json}")
    def list = new JsonSlurper().parseText(json)
    def names = list.collect { it.name ?: extractEnum(it.key) }
    sendEvent(name: "availableOptionsList", value: names.join(", "))
}

/**
 * Parses active program data including options and timing
 */
def z_parseActiveProgram(String json) {
    logDebug("Parsing active program")
    logTrace("Active program JSON: ${json}")
    def obj = new JsonSlurper().parseText(json)

    // Extract program name
    def name = obj?.name ?: obj?.data?.name ?: extractEnum(obj?.key ?: obj?.data?.key)
    if (name) {
        sendEvent(name: "activeProgram", value: name)
    }

    // Parse options
    def options = obj?.options ?: obj?.data?.options
    if (options instanceof List) {
        parseItemList(options)
    }
    
    updateDerivedState()
}

/**
 * Updates event stream status (CONNECTED/DISCONNECTED)
 */
def z_updateEventStreamStatus(String status) {
    logDebug("Event stream status: ${status}")
    sendEvent(name: "eventStreamStatus", value: status)
}

/**
 * Updates event present state (legacy compatibility)
 */
def z_updateEventPresentState(String eventState) {
    sendEvent(name: "eventPresentState", value: eventState)
}

/**
 * Internal logging command - can be called from parent if needed
 */
def z_deviceLog(String level, String msg) {
    switch (level) {
        case "debug": logDebug(msg); break
        case "info": logInfo(msg); break
        case "warn": logWarn(msg); break
        case "error": logError(msg); break
        default: log.info "${device.displayName}: ${msg}"
    }
}

/* ===========================================================================================================
   EVENT PARSING
   =========================================================================================================== */

/**
 * Converts a list of status/settings items to parseEvent calls
 */
private void parseItemList(List items) {
    items?.each { item ->
        parseEvent([
            haId: device.deviceNetworkId.replace("HC3-", ""),
            key: item.key,
            value: item.value,
            displayvalue: item.displayvalue ?: item.value?.toString(),
            unit: item.unit
        ])
    }
}

/**
 * Main event handler - processes incoming events from Home Connect
 * Called by parent app when SSE events are received
 *
 * @param evt Map containing: haId, key, value, displayvalue, unit, eventType
 */
def parseEvent(Map evt) {
    if (!evt?.key) return
    
    // Log raw event if enabled
    if (settings?.logRawEvents) {
        log.debug "${device.displayName}: RAW EVENT: ${evt}"
    }
    
    // Record event for debugging
    recordEvent(evt)
    
    logDebug("Event: ${evt.key} = ${evt.value}")
    logTrace("Event details: key=${evt.key}, value=${evt.value}, displayvalue=${evt.displayvalue}, unit=${evt.unit}")

    switch (evt.key) {

        // ===== Operation State =====
        case "BSH.Common.Status.OperationState":
            def opState = extractEnum(evt.value)
            def previousState = device.currentValue("operationState")
            sendEvent(name: "operationState", value: opState)
            
            // Push button 1 when cycle completes (transition to Finished)
            if (opState == "Finished" && previousState == "Run") {
                logInfo("Cycle complete - pushing button 1")
                sendEvent(name: "pushed", value: 1, isStateChange: true, descriptionText: "Cycle complete")
            }
            
            // Push button 4 on error
            if (opState == "Error") {
                logWarn("Error state detected - pushing button 4")
                sendEvent(name: "pushed", value: 4, isStateChange: true, descriptionText: "Error detected")
                sendAlert("Error", "Dishwasher reported an error")
            }
            
            // Reset timers when program ends
            if (opState in ["Ready", "Inactive", "Finished"]) {
                resetProgramState()
            }
            updateDerivedState()
            updateJsonState()
            break

        // ===== Door State =====
        case "BSH.Common.Status.DoorState":
            def doorState = extractEnum(evt.value)
            sendEvent(name: "doorState", value: doorState)
            sendEvent(name: "contact", value: (doorState == "Open" ? "open" : "closed"))
            updateJsonState()
            break

        // ===== Remote/Local Control =====
        case "BSH.Common.Status.RemoteControlStartAllowed":
            sendEvent(name: "remoteControlStartAllowed", value: evt.value.toString())
            updateJsonState()
            break

        case "BSH.Common.Status.RemoteControlActive":
            sendEvent(name: "remoteControlActive", value: evt.value.toString())
            updateJsonState()
            break

        case "BSH.Common.Status.LocalControlActive":
            sendEvent(name: "localControlActive", value: evt.value.toString())
            break

        // ===== Power State =====
        case "BSH.Common.Setting.PowerState":
            def power = extractEnum(evt.value)
            sendEvent(name: "powerState", value: power)
            sendEvent(name: "switch", value: (power == "On" ? "on" : "off"))
            updateJsonState()
            break

        // ===== Timing =====
        case "BSH.Common.Option.RemainingProgramTime":
            Integer sec = evt.value as Integer
            if (shouldUpdateTiming(sec)) {
                sendEvent(name: "remainingProgramTime", value: sec)
                sendEvent(name: "remainingProgramTimeFormatted", value: secondsToTime(sec))
                updateDerivedState()
                updateJsonState()
            }
            break

        case "BSH.Common.Option.ElapsedProgramTime":
            Integer sec = evt.value as Integer
            sendEvent(name: "elapsedProgramTime", value: sec)
            sendEvent(name: "elapsedProgramTimeFormatted", value: secondsToTime(sec))
            updateJsonState()
            break

        case "BSH.Common.Option.ProgramProgress":
            Integer progress = evt.value as Integer
            sendEvent(name: "programProgress", value: progress)
            sendEvent(name: "progressBar", value: "${progress}%")
            updateDerivedState()
            updateJsonState()
            break

        case "BSH.Common.Option.StartInRelative":
            Integer sec = evt.value as Integer
            sendEvent(name: "startInRelative", value: secondsToTime(sec))
            break

        // ===== Programs =====
        case "BSH.Common.Root.ActiveProgram":
            sendEvent(name: "activeProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            updateJsonState()
            break

        case "BSH.Common.Root.SelectedProgram":
            sendEvent(name: "selectedProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            updateJsonState()
            break

        // ===== Dishwasher-Specific Options =====
        // Handles all Dishcare.Dishwasher.Option.* keys dynamically
        case ~/Dishcare\.Dishwasher\.Option\..*/:
            def attr = evt.key.split("\\.").last()
            sendEvent(name: attr, value: evt.value.toString())
            logDebug("Dishwasher option: ${attr} = ${evt.value}")
            break

        // ===== Dishwasher Events =====
        case "Dishcare.Dishwasher.Event.RinseAidNearlyEmpty":
            def value = extractEnum(evt.value)
            sendEvent(name: "RinseAidNearlyEmpty", value: value)
            if (value == "Present") {
                logInfo("Rinse aid low - pushing button 3")
                sendEvent(name: "pushed", value: 3, isStateChange: true, descriptionText: "Rinse aid low")
                sendAlert("RinseAidLow", "Rinse aid is nearly empty")
            }
            updateJsonState()
            break

        case "Dishcare.Dishwasher.Event.SaltNearlyEmpty":
            def value = extractEnum(evt.value)
            sendEvent(name: "SaltNearlyEmpty", value: value)
            if (value == "Present") {
                logInfo("Salt low - pushing button 2")
                sendEvent(name: "pushed", value: 2, isStateChange: true, descriptionText: "Salt low")
                sendAlert("SaltLow", "Salt is nearly empty")
            }
            updateJsonState()
            break

        // ===== Unhandled =====
        default:
            logDebug("Unhandled event: ${evt.key} = ${evt.value}")
            
            // Store unhandled event for troubleshooting
            def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
            sendEvent(name: "lastUnhandledEvent", value: "${evt.key}=${evt.value}".take(200))
            sendEvent(name: "lastUnhandledEventTime", value: timestamp)
            
            // Log at info level if it looks like a significant event
            if (evt.key?.contains("Event.") || evt.key?.contains("Status.")) {
                logInfo("UNHANDLED SIGNIFICANT EVENT: ${evt.key} = ${evt.value} - Please report this to the developer")
            }
    }
}

/* ===========================================================================================================
   TIMING HELPERS
   =========================================================================================================== */

/**
 * Determines if a timing update should be applied
 * Filters spurious zero values that sometimes occur during active runs
 */
private boolean shouldUpdateTiming(Integer newValue) {
    if (newValue == 0) {
        def opState = device.currentValue("operationState")
        if (opState == "Run") {
            def currentRemaining = device.currentValue("remainingProgramTime") as Integer
            if (currentRemaining && currentRemaining > 60) {
                logDebug("Ignoring spurious zero remainingProgramTime during Run")
                return false
            }
        }
    }
    return true
}

/**
 * Resets all program-related state when a cycle ends
 */
private void resetProgramState() {
    logDebug("Resetting program state")
    sendEvent(name: "remainingProgramTime", value: 0)
    sendEvent(name: "remainingProgramTimeFormatted", value: "00:00")
    sendEvent(name: "elapsedProgramTime", value: 0)
    sendEvent(name: "elapsedProgramTimeFormatted", value: "00:00")
    sendEvent(name: "programProgress", value: 0)
    sendEvent(name: "progressBar", value: "0%")
    sendEvent(name: "estimatedEndTimeFormatted", value: "")
    sendEvent(name: "startInRelative", value: "")
}

/* ===========================================================================================================
   DERIVED STATE CALCULATIONS
   =========================================================================================================== */

/**
 * Updates derived attributes based on current state
 * - estimatedEndTimeFormatted: When the cycle should complete
 * - cyclePhase: Current phase of the wash cycle
 * - friendlyStatus: Human-readable status message
 */
private void updateDerivedState() {
    try {
        String opState = device.currentValue("operationState") as String
        Integer remainingSec = device.currentValue("remainingProgramTime") as Integer
        Integer progress = device.currentValue("programProgress") as Integer

        // Calculate estimated end time
        if (remainingSec != null && remainingSec > 0 && opState == "Run") {
            Long endMillis = now() + (remainingSec * 1000L)
            TimeZone tz = location?.timeZone ?: TimeZone.getDefault()
            String endFormatted = new Date(endMillis).format("h:mm a", tz)
            sendEvent(name: "estimatedEndTimeFormatted", value: endFormatted)
        } else if (opState != "Run") {
            sendEvent(name: "estimatedEndTimeFormatted", value: "")
        }

        // Determine cycle phase based on progress
        String phase = determineCyclePhase(opState, progress)
        if (phase) {
            sendEvent(name: "cyclePhase", value: phase)
        }

        // Generate friendly status message
        String friendly = determineFriendlyStatus(opState, phase, remainingSec)
        if (friendly) {
            sendEvent(name: "friendlyStatus", value: friendly)
        }

    } catch (Exception e) {
        logWarn("Error updating derived state: ${e.message}")
    }
}

/**
 * Determines the current cycle phase based on operation state and progress
 */
private String determineCyclePhase(String opState, Integer progress) {
    if (opState == "Finished") return "Complete"
    if (progress == null) return null
    
    // Approximate phases based on typical dishwasher cycle
    if (progress < 5) return "Prewash"
    if (progress < 40) return "Wash"
    if (progress < 70) return "Rinse"
    if (progress < 100) return "Dry"
    return "Complete"
}

/**
 * Generates a human-readable status message
 */
private String determineFriendlyStatus(String opState, String phase, Integer remainingSec) {
    switch (opState) {
        case "Ready":
        case "Inactive":
            return "Ready"
        case "DelayedStart":
            return "Waiting to start"
        case "Run":
            if (phase == "Dry") return "Drying"
            if (phase == "Rinse") return "Rinsing"
            if (remainingSec != null && remainingSec <= 600) return "Finishing soon"
            return "Running"
        case "Pause":
            return "Paused"
        case "Finished":
            return "Complete"
        case "ActionRequired":
            return "Action required"
        case "Aborting":
            return "Stopping"
        case "Error":
            return "Error"
        default:
            return opState ?: "Unknown"
    }
}

/* ===========================================================================================================
   ALERT HANDLING
   =========================================================================================================== */

/**
 * Sends an alert event and updates the lastAlert attributes
 */
private void sendAlert(String alertType, String message) {
    def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss", location?.timeZone ?: TimeZone.getDefault())
    sendEvent(name: "lastAlert", value: "${alertType}: ${message}")
    sendEvent(name: "lastAlertTime", value: timestamp)
    logInfo("Alert: ${alertType} - ${message}")
}

/* ===========================================================================================================
   JSON STATE FOR NODE-RED
   =========================================================================================================== */

/**
 * Updates the jsonState attribute with current device state
 * This provides a single attribute containing all state for easy Node-RED integration
 */
private void updateJsonState() {
    try {
        def stateMap = [
            // Status
            operationState: device.currentValue("operationState"),
            doorState: device.currentValue("doorState"),
            powerState: device.currentValue("powerState"),
            friendlyStatus: device.currentValue("friendlyStatus"),
            
            // Program
            activeProgram: device.currentValue("activeProgram"),
            selectedProgram: device.currentValue("selectedProgram"),
            programProgress: device.currentValue("programProgress"),
            cyclePhase: device.currentValue("cyclePhase"),
            
            // Timing
            remainingProgramTime: device.currentValue("remainingProgramTime"),
            remainingProgramTimeFormatted: device.currentValue("remainingProgramTimeFormatted"),
            elapsedProgramTime: device.currentValue("elapsedProgramTime"),
            elapsedProgramTimeFormatted: device.currentValue("elapsedProgramTimeFormatted"),
            estimatedEndTime: device.currentValue("estimatedEndTimeFormatted"),
            
            // Control
            remoteControlStartAllowed: device.currentValue("remoteControlStartAllowed"),
            remoteControlActive: device.currentValue("remoteControlActive"),
            
            // Alerts
            saltLow: device.currentValue("SaltNearlyEmpty") == "Present",
            rinseAidLow: device.currentValue("RinseAidNearlyEmpty") == "Present",
            lastAlert: device.currentValue("lastAlert"),
            
            // Meta
            lastUpdate: new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        ]
        
        def json = new groovy.json.JsonOutput().toJson(stateMap)
        sendEvent(name: "jsonState", value: json)
        
    } catch (Exception e) {
        logWarn("Error updating JSON state: ${e.message}")
    }
}

/* ===========================================================================================================
   UTILITY METHODS
   =========================================================================================================== */

/**
 * Extracts the last segment from a dotted enum value
 * e.g., "BSH.Common.EnumType.PowerState.On" → "On"
 */
private String extractEnum(String full) {
    if (!full) return null
    return full.substring(full.lastIndexOf(".") + 1)
}

/**
 * Converts seconds to HH:MM format
 */
private String secondsToTime(Integer sec) {
    if (sec == null || sec <= 0) return "00:00"
    long hours = sec / 3600
    long minutes = (sec % 3600) / 60
    return String.format("%02d:%02d", hours, minutes)
}

/* ===========================================================================================================
   LOGGING METHODS
   =========================================================================================================== */

private void logTrace(String msg) {
    if (settings?.traceLogging) {
        log.trace "${device.displayName}: ${msg}"
    }
}

private void logDebug(String msg) {
    if (settings?.debugLogging || settings?.traceLogging) {
        log.debug "${device.displayName}: ${msg}"
    }
}

private void logInfo(String msg) {
    log.info "${device.displayName}: ${msg}"
}

private void logWarn(String msg) {
    log.warn "${device.displayName}: ${msg}"
}

private void logError(String msg) {
    log.error "${device.displayName}: ${msg}"
}
