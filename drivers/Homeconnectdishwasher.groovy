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
 *  ===========================================================================================================
 *
 *  Version History:
 *  ----------------
 *  3.0.0  - Initial v3 architecture with parseEvent() pattern
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
        
        command "startProgram", [
            [name: "Program*", type: "ENUM", constraints: [
                // Common programs (various brands/regions)
                "Auto", "Auto1", "Auto2", "Auto3",
                "Normal", "Heavy", "Delicate", "Express",
                "Eco50", "Quick45", "Quick65",
                "Intensiv70", "Intensiv45",
                "Rinse", "PreRinse",
                "NightWash", "Glas40", "GlassCare",
                "MachineCare", "Machine Care",
                "-- Or use startProgramByKey --"
            ]]
        ]
        
        command "startProgramByKey", [
            [name: "Program Key*", type: "STRING", description: "e.g., Dishcare.Dishwasher.Program.Eco50"]
        ]
        
        command "startProgramDelayed", [
            [name: "Delay (minutes)*", type: "NUMBER"],
            [name: "Program*", type: "ENUM", constraints: [
                // Common programs (various brands/regions)
                "Auto", "Auto1", "Auto2", "Auto3",
                "Normal", "Heavy", "Delicate", "Express",
                "Eco50", "Quick45", "Quick65",
                "Intensiv70", "Intensiv45",
                "Rinse", "PreRinse",
                "NightWash", "Glas40", "GlassCare",
                "MachineCare", "Machine Care"
            ]]
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
        // ATTRIBUTES - Lists & Meta
        // =====================================================================
        
        attribute "availableProgramsList", "string"
        attribute "availableOptionsList", "string"
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
    }
}

/* ===========================================================================================================
   CONSTANTS
   =========================================================================================================== */

@Field static final String DRIVER_VERSION = "3.0.0"

/* ===========================================================================================================
   LIFECYCLE METHODS
   =========================================================================================================== */

def installed() {
    log.info "${device.displayName}: Installed"
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
}

/**
 * Initializes the device by fetching current status from Home Connect
 */
def initialize() {
    logInfo("Initializing")
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
    parent?.getAvailableProgramList(device)
}

/**
 * Starts a program from the dropdown list
 * Automatically converts short names to full Home Connect keys
 */
def startProgram(String program) {
    def programKey = buildProgramKey(program)
    logInfo("Starting program: ${programKey}")
    parent?.startProgram(device, programKey)
}

/**
 * Starts a program using the full Home Connect key
 */
def startProgramByKey(String programKey) {
    logInfo("Starting program by key: ${programKey}")
    parent?.startProgram(device, programKey)
}

/**
 * Starts a program after a delay
 */
def startProgramDelayed(BigDecimal delayMinutes, String program) {
    def programKey = buildProgramKey(program)
    Integer minutes = delayMinutes?.toInteger() ?: 0
    Integer delaySeconds = minutes * 60
    
    logInfo("Starting program '${programKey}' with ${minutes} minute delay")
    
    def options = [[key: "BSH.Common.Option.StartInRelative", value: delaySeconds, unit: "seconds"]]
    parent?.startProgram(device, programKey, options)
}

/**
 * Stops the currently running program
 */
def stopProgram() {
    logInfo("Stopping program")
    parent?.stopProgram(device)
}

/**
 * Sets the dishwasher power state
 */
def setPower(String powerState) {
    boolean on = (powerState == "on")
    logInfo("Setting power: ${on ? 'ON' : 'OFF'}")
    parent?.setPowerState(device, on)
}

/**
 * Sets an option on the selected program
 * Automatically converts string values to appropriate types
 */
def setProgramOption(String optionKey, String value) {
    logInfo("Setting option ${optionKey} = ${value}")
    
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

/* ===========================================================================================================
   INTERNAL COMMANDS (z_ prefix)
   Called by parent app to pass data to the driver
   =========================================================================================================== */

/**
 * Parses status data from Home Connect API
 */
def z_parseStatus(String json) {
    logDebug("Parsing status")
    def list = new JsonSlurper().parseText(json)
    parseItemList(list)
}

/**
 * Parses settings data from Home Connect API
 */
def z_parseSettings(String json) {
    logDebug("Parsing settings")
    def list = new JsonSlurper().parseText(json)
    parseItemList(list)
}

/**
 * Parses available programs list
 */
def z_parseAvailablePrograms(String json) {
    logDebug("Parsing available programs")
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
    def list = new JsonSlurper().parseText(json)
    def names = list.collect { it.name ?: extractEnum(it.key) }
    sendEvent(name: "availableOptionsList", value: names.join(", "))
}

/**
 * Parses active program data including options and timing
 */
def z_parseActiveProgram(String json) {
    logDebug("Parsing active program")
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
    
    logDebug("Event: ${evt.key} = ${evt.value}")

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
            logDebug("Unhandled event: ${evt.key}")
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

private void logDebug(String msg) {
    if (debugLogging) {
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
