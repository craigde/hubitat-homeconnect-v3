/**
 *  Home Connect WarmingDrawer v3 Driver
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
 *  This driver represents a Home Connect warming drawer appliance (Thermador, Bosch, etc.).
 *  
 *  Supported Features:
 *  - Temperature/warming level control (Low, Medium, High)
 *  - Push-to-open control (if equipped)
 *  - Program control for warming modes
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
    definition(name: "Home Connect WarmingDrawer v3", namespace: "craigde", author: "Craig Dewar") {

        // Standard capabilities
        capability "Initialize"
        capability "Refresh"
        capability "Switch"
        capability "ContactSensor"
        capability "Sensor"
        capability "Configuration"
        capability "PushableButton"

        // =====================================================================
        // USER-FACING COMMANDS
        // =====================================================================
        
        command "getAvailablePrograms"
        command "start"
        
        command "setWarmingLevel", [
            [name: "Level*", type: "ENUM", constraints: ["Low", "Medium", "High"]]
        ]
        
        command "startProgram", [
            [name: "Program", type: "ENUM", constraints: [
                "WarmingDrawer", "Keepwarm", "Slowcook", "Defrost",
                "Preheat", "HotHold", "Proof"
            ], description: "Leave empty for default warming"]
        ]
        
        command "startProgramByKey", [
            [name: "Program Key*", type: "STRING", description: "e.g., Cooking.Oven.Program.HeatingMode.WarmingDrawer"]
        ]
        
        command "startProgramWithOptions", [
            [name: "Program*", type: "STRING", description: "Program name or key"],
            [name: "Warming Level", type: "ENUM", constraints: ["Low", "Medium", "High"]],
            [name: "Target Temperature", type: "NUMBER", description: "Temperature in °F or °C"]
        ]
        
        command "stopProgram"
        
        command "setTargetTemperature", [
            [name: "Temperature*", type: "NUMBER", description: "Target temperature in °F or °C"]
        ]
        
        command "setPower", [
            [name: "State*", type: "ENUM", constraints: ["on", "off"]]
        ]
        
        command "setPushToOpen", [
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
        attribute "doorState", "string"
        attribute "powerState", "string"
        attribute "friendlyStatus", "string"

        // =====================================================================
        // ATTRIBUTES - Program & Temperature
        // =====================================================================
        
        attribute "activeProgram", "string"
        attribute "selectedProgram", "string"
        attribute "warmingLevel", "string"
        attribute "currentTemperature", "number"
        attribute "targetTemperature", "number"
        attribute "temperatureUnit", "string"

        // =====================================================================
        // ATTRIBUTES - Settings
        // =====================================================================
        
        attribute "pushToOpen", "string"
        attribute "childLock", "string"

        // =====================================================================
        // ATTRIBUTES - Control State
        // =====================================================================
        
        attribute "remoteControlStartAllowed", "string"
        attribute "remoteControlActive", "string"
        attribute "localControlActive", "string"

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
        input name: "temperatureUnit", type: "enum", title: "Temperature Unit",
              options: ["F", "C"], defaultValue: "F"
        input name: "defaultWarmingLevel", type: "enum", title: "Default Warming Level",
              options: ["Low", "Medium", "High"], defaultValue: "Medium"
    }
}

// =============================================================================
// CONSTANTS
// =============================================================================

@Field static final String DRIVER_VERSION = "3.0.1"
@Field static final Integer MAX_DISCOVERED_KEYS = 100

@Field static final Map WARMING_PROGRAMS = [
    "WarmingDrawer": "Cooking.Oven.Program.HeatingMode.WarmingDrawer",
    "Keepwarm": "Cooking.Oven.Program.HeatingMode.KeepWarm",
    "Slowcook": "Cooking.Oven.Program.HeatingMode.SlowCook",
    "Defrost": "Cooking.Oven.Program.HeatingMode.Defrost",
    "Preheat": "Cooking.Oven.Program.HeatingMode.PreheatOvenware",
    "HotHold": "Cooking.Oven.Program.HeatingMode.HotHold",
    "Proof": "Cooking.Oven.Program.HeatingMode.Proof"
]

@Field static final Map WARMING_LEVELS = [
    "Low": "Cooking.Oven.EnumType.WarmingLevel.Low",
    "Medium": "Cooking.Oven.EnumType.WarmingLevel.Medium",
    "High": "Cooking.Oven.EnumType.WarmingLevel.High"
]

// =============================================================================
// LIFECYCLE METHODS
// =============================================================================

def installed() {
    log.info "${device.displayName}: Installed"
    initializeState()
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "eventPresentState", value: "Off")
    sendEvent(name: "temperatureUnit", value: settings?.temperatureUnit ?: "F")
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "contact", value: "closed")
    
    // Button 1: Program complete
    // Button 2: Drawer opened
    sendEvent(name: "numberOfButtons", value: 2)
}

def updated() {
    log.info "${device.displayName}: Updated"
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "temperatureUnit", value: settings?.temperatureUnit ?: "F")
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
    logInfo("  temperatureUnit: ${temperatureUnit}")
    logInfo("  defaultWarmingLevel: ${defaultWarmingLevel}")
    
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
    stopProgram()
}

def getAvailablePrograms() {
    logInfo("Fetching available programs")
    recordCommand("getAvailablePrograms")
    parent?.getAvailableProgramList(device)
}

def start() {
    def level = settings?.defaultWarmingLevel ?: "Medium"
    setWarmingLevel(level)
}

def setWarmingLevel(String level) {
    def programKey = WARMING_PROGRAMS["WarmingDrawer"]
    def levelKey = WARMING_LEVELS[level] ?: WARMING_LEVELS["Medium"]
    
    def options = [
        [key: "Cooking.Oven.Option.WarmingLevel", value: levelKey]
    ]
    
    logInfo("Setting warming level: ${level}")
    recordCommand("setWarmingLevel", [level: level])
    parent?.startProgram(device, programKey, options)
}

def startProgram(String program = null) {
    def selectedProgram = program ?: "WarmingDrawer"
    def programKey = buildProgramKey(selectedProgram)
    
    saveLastProgram(selectedProgram)
    
    logInfo("Starting program: ${selectedProgram}")
    recordCommand("startProgram", [program: selectedProgram, key: programKey])
    parent?.startProgram(device, programKey)
}

def startProgramByKey(String programKey) {
    def programName = extractEnum(programKey)
    saveLastProgram(programName)
    
    logInfo("Starting program by key: ${programKey}")
    recordCommand("startProgramByKey", [key: programKey])
    parent?.startProgram(device, programKey)
}

def startProgramWithOptions(String program, String warmingLevel = null, BigDecimal targetTemp = null) {
    def programKey = buildProgramKey(program)
    
    def options = []
    
    if (warmingLevel && WARMING_LEVELS.containsKey(warmingLevel)) {
        options << [key: "Cooking.Oven.Option.WarmingLevel", value: WARMING_LEVELS[warmingLevel]]
    }
    
    if (targetTemp && targetTemp > 0) {
        def tempUnit = settings?.temperatureUnit ?: "F"
        Integer tempC = (tempUnit == "F") ? fahrenheitToCelsius(targetTemp.toInteger()) : targetTemp.toInteger()
        options << [key: "Cooking.Oven.Option.SetpointTemperature", value: tempC, unit: "°C"]
    }
    
    saveLastProgram(program)
    
    logInfo("Starting ${program}" + (warmingLevel ? " at ${warmingLevel}" : "") + (targetTemp ? " @ ${targetTemp}°" : ""))
    recordCommand("startProgramWithOptions", [program: program, level: warmingLevel, temp: targetTemp])
    
    if (options.size() > 0) {
        parent?.startProgram(device, programKey, options)
    } else {
        parent?.startProgram(device, programKey)
    }
}

def stopProgram() {
    logInfo("Stopping program")
    recordCommand("stopProgram")
    parent?.stopProgram(device)
}

def setTargetTemperature(BigDecimal temperature) {
    def tempUnit = settings?.temperatureUnit ?: "F"
    Integer temp = temperature.toInteger()
    Integer tempC = (tempUnit == "F") ? fahrenheitToCelsius(temp) : temp
    
    logInfo("Setting target temperature to ${temp}°${tempUnit}")
    recordCommand("setTargetTemperature", [temp: temp, unit: tempUnit])
    parent?.setSelectedProgramOption(device, "Cooking.Oven.Option.SetpointTemperature", tempC)
}

def setPower(String powerState) {
    boolean on = (powerState == "on")
    logInfo("Setting power: ${on ? 'ON' : 'OFF'}")
    recordCommand("setPower", [state: powerState])
    parent?.setPowerState(device, on)
}

def setPushToOpen(String state) {
    boolean on = (state.toLowerCase() == "on")
    logInfo("Setting Push-to-Open: ${on ? 'ON' : 'OFF'}")
    recordCommand("setPushToOpen", [state: state])
    parent?.setSetting(device, "Cooking.Oven.Setting.PushToOpen", on)
}

private String buildProgramKey(String program) {
    if (program?.contains(".")) {
        return program
    }
    
    if (WARMING_PROGRAMS.containsKey(program)) {
        return WARMING_PROGRAMS[program]
    }
    
    if (state.programMap?.containsKey(program)) {
        return state.programMap[program]
    }
    
    return "Cooking.Oven.Program.HeatingMode.${program}"
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
                logInfo("Warming complete - pushing button 1")
                sendEvent(name: "pushed", value: 1, isStateChange: true, descriptionText: "Warming complete")
            }
            
            sendEvent(name: "switch", value: (opState == "Run" ? "on" : "off"))
            
            updateDerivedState()
            updateJsonState()
            break

        case "BSH.Common.Status.DoorState":
            def doorState = extractEnum(evt.value)
            def previousDoor = device.currentValue("doorState")
            sendEvent(name: "doorState", value: doorState)
            sendEvent(name: "contact", value: (doorState == "Open" ? "open" : "closed"))
            
            if (doorState == "Open" && previousDoor != "Open") {
                logInfo("Drawer opened - pushing button 2")
                sendEvent(name: "pushed", value: 2, isStateChange: true, descriptionText: "Drawer opened")
            }
            
            updateJsonState()
            break

        case "BSH.Common.Status.RemoteControlStartAllowed":
            sendEvent(name: "remoteControlStartAllowed", value: evt.value.toString())
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

        case "Cooking.Oven.Status.CurrentCavityTemperature":
            Integer tempC = evt.value as Integer
            Integer tempDisplay = convertTemperatureForDisplay(tempC)
            sendEvent(name: "currentTemperature", value: tempDisplay)
            updateJsonState()
            break

        case "Cooking.Oven.Option.SetpointTemperature":
            Integer tempC = evt.value as Integer
            Integer tempDisplay = convertTemperatureForDisplay(tempC)
            sendEvent(name: "targetTemperature", value: tempDisplay)
            updateJsonState()
            break

        case "Cooking.Oven.Option.WarmingLevel":
            def level = extractEnum(evt.value)
            sendEvent(name: "warmingLevel", value: level)
            updateDerivedState()
            updateJsonState()
            break

        case "Cooking.Oven.Setting.PushToOpen":
            def on = evt.value.toString().toLowerCase() == "true"
            sendEvent(name: "pushToOpen", value: on ? "On" : "Off")
            break

        case "BSH.Common.Setting.ChildLock":
            def locked = evt.value.toString().toLowerCase() == "true"
            sendEvent(name: "childLock", value: locked ? "On" : "Off")
            break

        case ~/Cooking\.Oven\.Option\..*/:
        case ~/Cooking\.Oven\.Setting\..*/:
            def attr = evt.key.split("\\.").last()
            logDebug("Warming drawer option/setting: ${attr} = ${evt.value}")
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

private void updateDerivedState() {
    try {
        String opState = device.currentValue("operationState") as String
        String warmingLevel = device.currentValue("warmingLevel")
        String doorState = device.currentValue("doorState")
        Integer temp = device.currentValue("currentTemperature") as Integer
        
        String friendly
        
        switch (opState) {
            case "Ready":
            case "Inactive":
                friendly = "Off"
                break
            case "Run":
                if (warmingLevel) {
                    friendly = "Warming (${warmingLevel})"
                } else if (temp) {
                    friendly = "Warming at ${temp}°"
                } else {
                    friendly = "Warming"
                }
                break
            case "Finished":
                friendly = "Complete"
                break
            case "Pause":
                friendly = "Paused"
                break
            case "ActionRequired":
                friendly = "Action Required"
                break
            default:
                friendly = opState ?: "Unknown"
        }
        
        if (doorState == "Open") {
            friendly = "${friendly} (Open)"
        }
        
        sendEvent(name: "friendlyStatus", value: friendly)
        
    } catch (Exception e) {
        logWarn("Error updating derived state: ${e.message}")
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
            doorState: device.currentValue("doorState"),
            powerState: device.currentValue("powerState"),
            friendlyStatus: device.currentValue("friendlyStatus"),
            activeProgram: device.currentValue("activeProgram"),
            warmingLevel: device.currentValue("warmingLevel"),
            currentTemperature: device.currentValue("currentTemperature"),
            targetTemperature: device.currentValue("targetTemperature"),
            temperatureUnit: settings?.temperatureUnit ?: "F",
            pushToOpen: device.currentValue("pushToOpen") == "On",
            childLock: device.currentValue("childLock") == "On",
            remoteControlStartAllowed: device.currentValue("remoteControlStartAllowed"),
            remoteControlActive: device.currentValue("remoteControlActive"),
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

private Integer convertTemperatureForDisplay(Integer tempC) {
    def unit = settings?.temperatureUnit ?: "F"
    if (unit == "F") {
        return celsiusToFahrenheit(tempC)
    }
    return tempC
}

private Integer celsiusToFahrenheit(Integer c) {
    return Math.round((c * 9.0 / 5.0) + 32)
}

private Integer fahrenheitToCelsius(Integer f) {
    return Math.round((f - 32) * 5.0 / 9.0)
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
