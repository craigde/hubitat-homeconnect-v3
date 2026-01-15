/**
 *  Home Connect CleaningRobot v3 Driver
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
 *  This driver represents a Home Connect cleaning robot / robotic vacuum appliance.
 *  
 *  Supported Features:
 *  - Cleaning programs (auto, spot, silent, turbo)
 *  - Start/stop/pause cleaning
 *  - Return to dock / charging station
 *  - Battery level monitoring
 *  - Cleaning map/zone selection (if supported)
 *  - Dustbin full alerts
 *  - Stuck/error detection
 *  
 *  Debugging:
 *  ----------
 *  Enable "Debug Logging" in preferences to capture detailed event information.
 *  Use "dumpState" command to output all current attribute values.
 *  Use "getDiscoveredKeys" to see all event keys received from your appliance.
 *  
 *  ===========================================================================================================
 *
 *  Version History:
 *  ----------------
 *  3.0.0  2026-01-15  Initial v3 architecture with parseEvent() pattern
 *  3.0.1  2026-01-15  Enhanced debugging for remote troubleshooting
 */

import groovy.json.JsonSlurper
import groovy.transform.Field

metadata {
    definition(name: "Home Connect CleaningRobot v3", namespace: "craigde", author: "Craig Dewar") {

        // Standard capabilities
        capability "Initialize"
        capability "Refresh"
        capability "Switch"
        capability "Battery"
        capability "Sensor"
        capability "Configuration"
        capability "PushableButton"

        // =====================================================================
        // USER-FACING COMMANDS
        // =====================================================================
        
        command "getAvailablePrograms"
        command "start"
        
        command "startProgram", [
            [name: "Program", type: "ENUM", constraints: [
                "Auto", "Silent", "Turbo", "SpotCleaning", "QuickCleaning",
                "EdgeCleaning", "DeepCleaning", "MoppingOnly"
            ], description: "Select cleaning program"]
        ]
        
        command "startProgramByKey", [
            [name: "Program Key*", type: "STRING", description: "Full program key"]
        ]
        
        command "startCleaningZone", [
            [name: "Zone*", type: "STRING", description: "Zone name or ID to clean"]
        ]
        
        command "stopProgram"
        command "pauseProgram"
        command "resumeProgram"
        
        command "returnToDock"
        command "goToCharger"
        command "locate"
        
        command "setCleaningMode", [
            [name: "Mode*", type: "ENUM", constraints: ["Auto", "Silent", "Turbo"]]
        ]
        
        command "setPower", [
            [name: "State*", type: "ENUM", constraints: ["on", "off"]]
        ]

        // =====================================================================
        // DEBUGGING COMMANDS
        // =====================================================================
        
        command "dumpState"
        command "getDiscoveredKeys"
        command "clearDiscoveredKeys"
        command "getRecentEvents", [[name: "count", type: "NUMBER", description: "Number of recent events (default 10)"]]

        // =====================================================================
        // ATTRIBUTES - Status
        // =====================================================================
        
        attribute "operationState", "string"
        attribute "powerState", "string"
        attribute "friendlyStatus", "string"
        attribute "robotState", "string"

        // =====================================================================
        // ATTRIBUTES - Program & Progress
        // =====================================================================
        
        attribute "activeProgram", "string"
        attribute "selectedProgram", "string"
        attribute "cleaningMode", "string"
        attribute "currentZone", "string"

        // =====================================================================
        // ATTRIBUTES - Timing
        // =====================================================================
        
        attribute "cleaningTime", "number"
        attribute "cleaningTimeFormatted", "string"
        attribute "lastCleaningTime", "number"
        attribute "lastCleaningTimeFormatted", "string"

        // =====================================================================
        // ATTRIBUTES - Battery & Charging
        // =====================================================================
        
        attribute "batteryLevel", "number"
        attribute "chargingState", "string"
        attribute "docked", "string"
        attribute "estimatedRuntime", "number"
        attribute "estimatedRuntimeFormatted", "string"

        // =====================================================================
        // ATTRIBUTES - Cleaning Stats
        // =====================================================================
        
        attribute "cleanedArea", "number"
        attribute "totalCleanedArea", "number"
        attribute "cleaningCycles", "number"

        // =====================================================================
        // ATTRIBUTES - Maintenance
        // =====================================================================
        
        attribute "dustbinFull", "string"
        attribute "brushWear", "string"
        attribute "filterWear", "string"
        attribute "sidebrushWear", "string"

        // =====================================================================
        // ATTRIBUTES - Control State
        // =====================================================================
        
        attribute "remoteControlStartAllowed", "string"
        attribute "remoteControlActive", "string"
        attribute "localControlActive", "string"

        // =====================================================================
        // ATTRIBUTES - Errors
        // =====================================================================
        
        attribute "stuck", "string"
        attribute "errorState", "string"
        attribute "errorMessage", "string"

        // =====================================================================
        // ATTRIBUTES - Events & Alerts
        // =====================================================================
        
        attribute "lastAlert", "string"
        attribute "lastAlertTime", "string"

        // =====================================================================
        // ATTRIBUTES - Integration Support
        // =====================================================================
        
        attribute "jsonState", "string"
        attribute "lastCommandStatus", "string"

        // =====================================================================
        // ATTRIBUTES - Debugging
        // =====================================================================
        
        attribute "lastUnhandledEvent", "string"
        attribute "lastUnhandledEventTime", "string"
        attribute "lastCommandSent", "string"
        attribute "lastCommandTime", "string"
        attribute "discoveredKeysCount", "number"

        // =====================================================================
        // ATTRIBUTES - Meta
        // =====================================================================
        
        attribute "availableProgramsList", "string"
        attribute "lastProgram", "string"
        attribute "driverVersion", "string"
        attribute "eventStreamStatus", "string"
        attribute "eventPresentState", "string"

        // =====================================================================
        // INTERNAL COMMANDS (z_ prefix)
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
        input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false
        input name: "logRawEvents", type: "bool", title: "Log raw events", defaultValue: false
        input name: "maxRecentEvents", type: "number", title: "Recent events to keep", defaultValue: 20
        input name: "defaultProgram", type: "enum", title: "Default Cleaning Program",
              options: ["Auto", "Silent", "Turbo", "QuickCleaning"],
              defaultValue: "Auto"
        input name: "lowBatteryThreshold", type: "number", title: "Low Battery Alert (%)",
              defaultValue: 20, range: "5..50"
    }
}

// =============================================================================
// CONSTANTS
// =============================================================================

@Field static final String DRIVER_VERSION = "3.0.1"
@Field static final Integer MAX_DISCOVERED_KEYS = 100

@Field static final Map CLEANING_PROGRAMS = [
    "Auto": "ConsumerProducts.CleaningRobot.Program.Cleaning.CleanAll",
    "Silent": "ConsumerProducts.CleaningRobot.Program.Cleaning.CleanAll.Silent",
    "Turbo": "ConsumerProducts.CleaningRobot.Program.Cleaning.CleanAll.Turbo",
    "SpotCleaning": "ConsumerProducts.CleaningRobot.Program.Cleaning.SpotCleaning",
    "QuickCleaning": "ConsumerProducts.CleaningRobot.Program.Cleaning.QuickCleaning",
    "EdgeCleaning": "ConsumerProducts.CleaningRobot.Program.Cleaning.EdgeCleaning",
    "DeepCleaning": "ConsumerProducts.CleaningRobot.Program.Cleaning.DeepCleaning",
    "MoppingOnly": "ConsumerProducts.CleaningRobot.Program.Cleaning.MoppingOnly"
]

@Field static final Map CLEANING_MODES = [
    "Auto": "ConsumerProducts.CleaningRobot.EnumType.CleaningMode.Auto",
    "Silent": "ConsumerProducts.CleaningRobot.EnumType.CleaningMode.Silent",
    "Turbo": "ConsumerProducts.CleaningRobot.EnumType.CleaningMode.Turbo"
]

@Field static final Map ROBOT_STATES = [
    "Idle": "Idle",
    "Cleaning": "Cleaning",
    "Charging": "Charging",
    "Docking": "Returning to Dock",
    "Stuck": "Stuck",
    "Error": "Error",
    "Paused": "Paused"
]

// =============================================================================
// LIFECYCLE METHODS
// =============================================================================

def installed() {
    log.info "${device.displayName}: Installed"
    initializeState()
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "eventPresentState", value: "Off")
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "battery", value: 0)
    sendEvent(name: "docked", value: "unknown")
    
    // Button 1: Cleaning complete
    // Button 2: Returned to dock
    // Button 3: Dustbin full
    // Button 4: Robot stuck/needs help
    // Button 5: Low battery
    sendEvent(name: "numberOfButtons", value: 5)
}

def updated() {
    log.info "${device.displayName}: Updated"
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    if (state.discoveredKeys == null) initializeState()
}

private void initializeState() {
    if (state.discoveredKeys == null) state.discoveredKeys = [:]
    if (state.recentEvents == null) state.recentEvents = []
    if (state.programMap == null) state.programMap = [:]
    if (state.programNames == null) state.programNames = []
}

def initialize() {
    logInfo("Initializing")
    initializeState()
    parent?.initializeStatus(device)
    runIn(5, "getAvailablePrograms")
}

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

// =============================================================================
// DEBUGGING COMMANDS
// =============================================================================

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
    logInfo("--- Robot Status ---")
    logInfo("  Robot State: ${device.currentValue('robotState')}")
    logInfo("  Battery: ${device.currentValue('battery')}%")
    logInfo("  Charging: ${device.currentValue('chargingState')}")
    logInfo("  Docked: ${device.currentValue('docked')}")
    logInfo("  Stuck: ${device.currentValue('stuck')}")
    
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
    logInfo("  defaultProgram: ${defaultProgram}")
    logInfo("  lowBatteryThreshold: ${lowBatteryThreshold}")
    
    logInfo("=== END STATE DUMP ===")
}

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
    }
    
    logInfo("=== END DISCOVERED KEYS ===")
    sendEvent(name: "discoveredKeysCount", value: state.discoveredKeys?.size() ?: 0)
}

def clearDiscoveredKeys() {
    logInfo("Clearing discovered keys")
    state.discoveredKeys = [:]
    sendEvent(name: "discoveredKeysCount", value: 0)
}

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

private void recordEvent(Map evt) {
    if (!evt?.key) return
    
    def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
    
    if (state.discoveredKeys == null) state.discoveredKeys = [:]
    
    if (state.discoveredKeys.size() < MAX_DISCOVERED_KEYS) {
        if (!state.discoveredKeys.containsKey(evt.key)) {
            state.discoveredKeys[evt.key] = [
                firstSeen: timestamp,
                lastSeen: timestamp,
                lastValue: evt.value?.toString()?.take(100),
                count: 1
            ]
        } else {
            state.discoveredKeys[evt.key].lastSeen = timestamp
            state.discoveredKeys[evt.key].lastValue = evt.value?.toString()?.take(100)
            state.discoveredKeys[evt.key].count = (state.discoveredKeys[evt.key].count ?: 0) + 1
        }
    }
    
    def maxEvents = (settings?.maxRecentEvents ?: 20) as Integer
    if (maxEvents > 0) {
        if (state.recentEvents == null) state.recentEvents = []
        
        state.recentEvents.add(0, [
            time: timestamp,
            key: evt.key,
            value: evt.value?.toString()?.take(100),
            displayvalue: evt.displayvalue?.take(50)
        ])
        
        if (state.recentEvents.size() > maxEvents) {
            state.recentEvents = state.recentEvents.take(maxEvents)
        }
    }
}

private void recordCommand(String command, Map params = [:]) {
    def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
    def cmdInfo = params ? "${command}: ${params}" : command
    
    sendEvent(name: "lastCommandSent", value: cmdInfo.take(200))
    sendEvent(name: "lastCommandTime", value: timestamp)
    
    logDebug("Command sent: ${cmdInfo}")
}

// =============================================================================
// USER-FACING COMMANDS
// =============================================================================

def on() { 
    start()
}

def off() { 
    returnToDock()
}

def getAvailablePrograms() {
    logInfo("Fetching available programs")
    recordCommand("getAvailablePrograms")
    parent?.getAvailableProgramList(device)
}

def start() {
    def program = settings?.defaultProgram ?: "Auto"
    startProgram(program)
}

def startProgram(String program = null) {
    def selectedProgram = program ?: settings?.defaultProgram ?: "Auto"
    def programKey = buildProgramKey(selectedProgram)
    
    saveLastProgram(selectedProgram)
    
    logInfo("Starting cleaning program: ${selectedProgram}")
    recordCommand("startProgram", [program: selectedProgram])
    parent?.startProgram(device, programKey)
}

def startProgramByKey(String programKey) {
    def programName = extractEnum(programKey)
    saveLastProgram(programName)
    
    logInfo("Starting program by key: ${programKey}")
    recordCommand("startProgramByKey", [key: programKey])
    parent?.startProgram(device, programKey)
}

def startCleaningZone(String zone) {
    def programKey = CLEANING_PROGRAMS["Auto"]
    
    def options = [
        [key: "ConsumerProducts.CleaningRobot.Option.CleaningZone", value: zone]
    ]
    
    logInfo("Starting zone cleaning: ${zone}")
    recordCommand("startCleaningZone", [zone: zone])
    parent?.startProgram(device, programKey, options)
}

def stopProgram() {
    logInfo("Stopping cleaning")
    recordCommand("stopProgram")
    parent?.stopProgram(device)
}

def pauseProgram() {
    logInfo("Pausing cleaning")
    recordCommand("pauseProgram")
    parent?.sendCommand(device, "BSH.Common.Command.PauseProgram")
}

def resumeProgram() {
    logInfo("Resuming cleaning")
    recordCommand("resumeProgram")
    parent?.sendCommand(device, "BSH.Common.Command.ResumeProgram")
}

def returnToDock() {
    logInfo("Returning to dock")
    recordCommand("returnToDock")
    parent?.sendCommand(device, "ConsumerProducts.CleaningRobot.Command.GoHome")
}

def goToCharger() {
    returnToDock()
}

def locate() {
    logInfo("Locating robot")
    recordCommand("locate")
    parent?.sendCommand(device, "ConsumerProducts.CleaningRobot.Command.Locate")
}

def setCleaningMode(String mode) {
    logInfo("Setting cleaning mode: ${mode}")
    recordCommand("setCleaningMode", [mode: mode])
    
    if (CLEANING_MODES.containsKey(mode)) {
        parent?.setSelectedProgramOption(device, "ConsumerProducts.CleaningRobot.Option.CleaningMode", CLEANING_MODES[mode])
    } else {
        logWarn("Unknown cleaning mode: ${mode}")
    }
}

def setPower(String powerState) {
    boolean on = (powerState == "on")
    logInfo("Setting power: ${on ? 'ON' : 'OFF'}")
    recordCommand("setPower", [state: powerState])
    parent?.setPowerState(device, on)
}

private String buildProgramKey(String program) {
    if (program?.contains(".")) {
        return program
    }
    
    if (CLEANING_PROGRAMS.containsKey(program)) {
        return CLEANING_PROGRAMS[program]
    }
    
    if (state.programMap?.containsKey(program)) {
        return state.programMap[program]
    }
    
    return "ConsumerProducts.CleaningRobot.Program.Cleaning.${program}"
}

private void saveLastProgram(String program) {
    if (program) {
        sendEvent(name: "lastProgram", value: program)
    }
}

// =============================================================================
// INTERNAL COMMANDS (z_ prefix)
// =============================================================================

def z_parseStatus(String json) {
    logDebug("Parsing status")
    logTrace("Status JSON: ${json}")
    def list = new JsonSlurper().parseText(json)
    parseItemList(list)
}

def z_parseSettings(String json) {
    logDebug("Parsing settings")
    logTrace("Settings JSON: ${json}")
    def list = new JsonSlurper().parseText(json)
    parseItemList(list)
}

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
    }
    
    state.programMap = programMap
    state.programNames = programNames
    
    logInfo("Found ${programNames.size()} available programs: ${programNames.join(', ')}")
    sendEvent(name: "availableProgramsList", value: programNames.join(", "))
}

def z_parseAvailableOptions(String json) {
    logDebug("Parsing available options")
    logTrace("Options JSON: ${json}")
}

def z_parseActiveProgram(String json) {
    logDebug("Parsing active program")
    logTrace("Active program JSON: ${json}")
    def obj = new JsonSlurper().parseText(json)

    def name = obj?.name ?: obj?.data?.name ?: extractEnum(obj?.key ?: obj?.data?.key)
    if (name) {
        sendEvent(name: "activeProgram", value: name)
    }

    def options = obj?.options ?: obj?.data?.options
    if (options instanceof List) {
        parseItemList(options)
    }
    
    updateDerivedState()
}

def z_updateEventStreamStatus(String status) {
    logDebug("Event stream status: ${status}")
    sendEvent(name: "eventStreamStatus", value: status)
}

def z_updateEventPresentState(String eventState) {
    sendEvent(name: "eventPresentState", value: eventState)
}

def z_deviceLog(String level, String msg) {
    switch (level) {
        case "debug": logDebug(msg); break
        case "info": logInfo(msg); break
        case "warn": logWarn(msg); break
        case "error": logError(msg); break
        default: log.info "${device.displayName}: ${msg}"
    }
}

// =============================================================================
// EVENT PARSING
// =============================================================================

private void parseItemList(List items) {
    items?.each { item ->
        parseEvent([
            haId: device.deviceNetworkId.replace("HC3-", "").replace("HC3SIM-", ""),
            key: item.key,
            value: item.value,
            displayvalue: item.displayvalue ?: item.value?.toString(),
            unit: item.unit
        ])
    }
}

def parseEvent(Map evt) {
    if (!evt?.key) return
    
    if (settings?.logRawEvents) {
        log.debug "${device.displayName}: RAW EVENT: ${evt}"
    }
    
    recordEvent(evt)
    
    logDebug("Event: ${evt.key} = ${evt.value}")
    logTrace("Event details: key=${evt.key}, value=${evt.value}, displayvalue=${evt.displayvalue}, unit=${evt.unit}")

    switch (evt.key) {

        case "BSH.Common.Status.OperationState":
            def opState = extractEnum(evt.value)
            def previousState = device.currentValue("operationState")
            sendEvent(name: "operationState", value: opState)
            
            if (opState == "Finished" && previousState == "Run") {
                logInfo("Cleaning complete - pushing button 1")
                sendEvent(name: "pushed", value: 1, isStateChange: true, descriptionText: "Cleaning complete")
            }
            
            sendEvent(name: "switch", value: (opState == "Run" ? "on" : "off"))
            updateRobotState(opState)
            updateDerivedState()
            updateJsonState()
            break

        case "BSH.Common.Status.RemoteControlStartAllowed":
            sendEvent(name: "remoteControlStartAllowed", value: evt.value.toString())
            updateJsonState()
            break

        case "BSH.Common.Status.RemoteControlActive":
            sendEvent(name: "remoteControlActive", value: evt.value.toString())
            break

        case "BSH.Common.Status.LocalControlActive":
            sendEvent(name: "localControlActive", value: evt.value.toString())
            break

        case "BSH.Common.Setting.PowerState":
            def power = extractEnum(evt.value)
            sendEvent(name: "powerState", value: power)
            updateJsonState()
            break

        case "BSH.Common.Root.ActiveProgram":
            sendEvent(name: "activeProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            updateJsonState()
            break

        case "BSH.Common.Root.SelectedProgram":
            sendEvent(name: "selectedProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            break

        case "ConsumerProducts.CleaningRobot.Status.BatteryLevel":
            Integer level = evt.value as Integer
            def prevLevel = device.currentValue("battery") as Integer
            sendEvent(name: "battery", value: level)
            sendEvent(name: "batteryLevel", value: level)
            
            def threshold = settings?.lowBatteryThreshold ?: 20
            if (level <= threshold && (prevLevel == null || prevLevel > threshold)) {
                logInfo("Low battery (${level}%) - pushing button 5")
                sendEvent(name: "pushed", value: 5, isStateChange: true, descriptionText: "Low battery: ${level}%")
                sendAlert("LowBattery", "Battery level is ${level}%")
            }
            updateJsonState()
            break

        case "ConsumerProducts.CleaningRobot.Status.ChargingState":
            def state = extractEnum(evt.value)
            sendEvent(name: "chargingState", value: state)
            updateJsonState()
            break

        case "ConsumerProducts.CleaningRobot.Status.DockState":
        case "ConsumerProducts.CleaningRobot.Status.Docked":
            def docked = evt.value.toString().toLowerCase() == "true" || extractEnum(evt.value) == "Docked"
            def wasDocked = device.currentValue("docked") == "true"
            sendEvent(name: "docked", value: docked.toString())
            
            if (docked && !wasDocked) {
                logInfo("Returned to dock - pushing button 2")
                sendEvent(name: "pushed", value: 2, isStateChange: true, descriptionText: "Returned to dock")
            }
            updateRobotState(device.currentValue("operationState"))
            updateJsonState()
            break

        case "ConsumerProducts.CleaningRobot.Status.RobotState":
            def state = extractEnum(evt.value)
            sendEvent(name: "robotState", value: ROBOT_STATES[state] ?: state)
            updateDerivedState()
            break

        case "ConsumerProducts.CleaningRobot.Status.CleaningTime":
            Integer sec = evt.value as Integer
            sendEvent(name: "cleaningTime", value: sec)
            sendEvent(name: "cleaningTimeFormatted", value: secondsToTime(sec))
            break

        case "ConsumerProducts.CleaningRobot.Status.CleanedArea":
            Integer area = evt.value as Integer
            sendEvent(name: "cleanedArea", value: area)
            break

        case "ConsumerProducts.CleaningRobot.Option.CleaningMode":
            def mode = extractEnum(evt.value)
            sendEvent(name: "cleaningMode", value: mode)
            updateJsonState()
            break

        case "ConsumerProducts.CleaningRobot.Option.CleaningZone":
            sendEvent(name: "currentZone", value: evt.value?.toString())
            break

        case "ConsumerProducts.CleaningRobot.Event.DustBoxFull":
        case "ConsumerProducts.CleaningRobot.Status.DustbinFull":
            def full = evt.value.toString().toLowerCase() == "true" || extractEnum(evt.value) == "Present"
            sendEvent(name: "dustbinFull", value: full.toString())
            if (full) {
                logInfo("Dustbin full - pushing button 3")
                sendEvent(name: "pushed", value: 3, isStateChange: true, descriptionText: "Dustbin full")
                sendAlert("DustbinFull", "Dustbin needs emptying")
            }
            break

        case "ConsumerProducts.CleaningRobot.Event.Stuck":
        case "ConsumerProducts.CleaningRobot.Status.Stuck":
            def stuck = evt.value.toString().toLowerCase() == "true" || extractEnum(evt.value) == "Present"
            def wasStuck = device.currentValue("stuck") == "true"
            sendEvent(name: "stuck", value: stuck.toString())
            if (stuck && !wasStuck) {
                logInfo("Robot stuck - pushing button 4")
                sendEvent(name: "pushed", value: 4, isStateChange: true, descriptionText: "Robot stuck - needs help")
                sendAlert("Stuck", "Robot is stuck and needs assistance")
            }
            updateRobotState(device.currentValue("operationState"))
            break

        case "ConsumerProducts.CleaningRobot.Status.Error":
            def error = extractEnum(evt.value)
            sendEvent(name: "errorState", value: error)
            if (error && error != "None" && error != "NoError") {
                sendEvent(name: "errorMessage", value: evt.displayvalue ?: error)
                sendAlert("Error", "Robot error: ${evt.displayvalue ?: error}")
            }
            break

        case "ConsumerProducts.CleaningRobot.Status.MainBrushWear":
            sendEvent(name: "brushWear", value: "${evt.value}%")
            break

        case "ConsumerProducts.CleaningRobot.Status.SideBrushWear":
            sendEvent(name: "sidebrushWear", value: "${evt.value}%")
            break

        case "ConsumerProducts.CleaningRobot.Status.FilterWear":
            sendEvent(name: "filterWear", value: "${evt.value}%")
            break

        case ~/ConsumerProducts\.CleaningRobot\.Option\..*/:
        case ~/ConsumerProducts\.CleaningRobot\.Status\..*/:
            def attr = evt.key.split("\\.").last()
            logDebug("CleaningRobot option/status: ${attr} = ${evt.value}")
            break

        default:
            logDebug("Unhandled event: ${evt.key} = ${evt.value}")
            
            def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss")
            sendEvent(name: "lastUnhandledEvent", value: "${evt.key}=${evt.value}".take(200))
            sendEvent(name: "lastUnhandledEventTime", value: timestamp)
            
            if (evt.key?.contains("Event.") || evt.key?.contains("Status.")) {
                logInfo("UNHANDLED SIGNIFICANT EVENT: ${evt.key} = ${evt.value} - Please report this")
            }
    }
}

// =============================================================================
// DERIVED STATE
// =============================================================================

private void updateRobotState(String opState) {
    def docked = device.currentValue("docked") == "true"
    def stuck = device.currentValue("stuck") == "true"
    def charging = device.currentValue("chargingState")
    
    String robotState
    if (stuck) {
        robotState = "Stuck"
    } else if (opState == "Run") {
        robotState = "Cleaning"
    } else if (charging == "Charging") {
        robotState = "Charging"
    } else if (docked) {
        robotState = "Docked"
    } else if (opState == "Pause") {
        robotState = "Paused"
    } else {
        robotState = "Idle"
    }
    
    sendEvent(name: "robotState", value: robotState)
}

private void updateDerivedState() {
    try {
        String opState = device.currentValue("operationState") as String
        String robotState = device.currentValue("robotState")
        Integer battery = device.currentValue("battery") as Integer
        String activeProgram = device.currentValue("activeProgram")
        def stuck = device.currentValue("stuck") == "true"
        def docked = device.currentValue("docked") == "true"
        
        String friendly = determineFriendlyStatus(opState, robotState, battery, activeProgram, stuck, docked)
        sendEvent(name: "friendlyStatus", value: friendly)
        
    } catch (Exception e) {
        logWarn("Error updating derived state: ${e.message}")
    }
}

private String determineFriendlyStatus(String opState, String robotState, Integer battery, String program, boolean stuck, boolean docked) {
    if (stuck) {
        return "Stuck - Needs Help"
    }
    
    switch (opState) {
        case "Ready":
        case "Inactive":
            if (docked) {
                return battery ? "Docked (${battery}%)" : "Docked"
            }
            return "Ready"
        case "Run":
            def status = program ? "Cleaning: ${program}" : "Cleaning"
            return battery ? "${status} (${battery}%)" : status
        case "Pause":
            return "Paused"
        case "Finished":
            return "Cleaning Complete"
        case "ActionRequired":
            return "Action Required"
        case "Error":
            return "Error"
        default:
            return robotState ?: opState ?: "Unknown"
    }
}

// =============================================================================
// ALERT HANDLING
// =============================================================================

private void sendAlert(String alertType, String message) {
    def timestamp = new Date().format("yyyy-MM-dd HH:mm:ss", location?.timeZone ?: TimeZone.getDefault())
    sendEvent(name: "lastAlert", value: "${alertType}: ${message}")
    sendEvent(name: "lastAlertTime", value: timestamp)
    logInfo("Alert: ${alertType} - ${message}")
}

// =============================================================================
// JSON STATE
// =============================================================================

private void updateJsonState() {
    try {
        def stateMap = [
            operationState: device.currentValue("operationState"),
            robotState: device.currentValue("robotState"),
            powerState: device.currentValue("powerState"),
            friendlyStatus: device.currentValue("friendlyStatus"),
            activeProgram: device.currentValue("activeProgram"),
            cleaningMode: device.currentValue("cleaningMode"),
            battery: device.currentValue("battery"),
            chargingState: device.currentValue("chargingState"),
            docked: device.currentValue("docked") == "true",
            stuck: device.currentValue("stuck") == "true",
            dustbinFull: device.currentValue("dustbinFull") == "true",
            cleaningTime: device.currentValue("cleaningTime"),
            cleaningTimeFormatted: device.currentValue("cleaningTimeFormatted"),
            cleanedArea: device.currentValue("cleanedArea"),
            remoteControlStartAllowed: device.currentValue("remoteControlStartAllowed"),
            errorState: device.currentValue("errorState"),
            lastAlert: device.currentValue("lastAlert"),
            lastUpdate: new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        ]
        
        def json = new groovy.json.JsonOutput().toJson(stateMap)
        sendEvent(name: "jsonState", value: json)
        
    } catch (Exception e) {
        logWarn("Error updating JSON state: ${e.message}")
    }
}

// =============================================================================
// UTILITY METHODS
// =============================================================================

private String extractEnum(String full) {
    if (!full) return null
    return full.substring(full.lastIndexOf(".") + 1)
}

private String secondsToTime(Integer sec) {
    if (sec == null || sec <= 0) return "00:00"
    long hours = sec / 3600
    long minutes = (sec % 3600) / 60
    if (hours > 0) {
        return String.format("%d:%02d", hours, minutes)
    }
    return String.format("%02d:%02d", minutes, sec % 60)
}

// =============================================================================
// LOGGING METHODS
// =============================================================================

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
