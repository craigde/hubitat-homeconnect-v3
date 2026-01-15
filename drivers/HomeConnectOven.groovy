/**
 *  Home Connect Oven v3 Driver
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
 *  This driver represents a Home Connect oven appliance (Thermador, Bosch, Siemens, Gaggenau, Neff).
 *  
 *  Supported Features:
 *  - Heating modes (Convection, Top/Bottom Heat, Grill, etc.)
 *  - Temperature control with setpoint and current temperature
 *  - Meat probe temperature monitoring
 *  - Program timers (duration, delayed start)
 *  - Preheat notifications
 *  - Door state monitoring
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
 *  3.0.0  2026-01-14  Initial v3 architecture with parseEvent() pattern
 *  3.0.1  2026-01-14  Enhanced debugging for remote troubleshooting
 */

import groovy.json.JsonSlurper
import groovy.transform.Field

metadata {
    definition(name: "Home Connect Oven v3", namespace: "craigde", author: "Craig Dewar") {

        // Standard capabilities
        capability "Initialize"
        capability "Refresh"
        capability "Switch"
        capability "ContactSensor"
        capability "TemperatureMeasurement"
        capability "Sensor"
        capability "Configuration"
        capability "PushableButton"

        // =====================================================================
        // USER-FACING COMMANDS
        // =====================================================================
        
        command "getAvailablePrograms"
        command "start"
        
        command "startProgram", [
            [name: "Heating Mode", type: "ENUM", constraints: [
                "HotAir", "TopBottomHeating", "CircularAir", "GrillCircularAir",
                "Defrost", "BottomHeating", "TopHeating", "SlowCook",
                "Grill", "GrillLargeArea", "GrillSmallArea",
                "PizzaSetting", "Desiccation", "IntensiveHeat", "KeepWarm",
                "Pyrolysis", "SabbathProgramme",
                "ConvectionBake", "ConvectionRoast", "ConvectionBroil",
                "TruConvection", "Broil", "Bake", "Roast", "Proof"
            ], description: "Select heating mode"]
        ]
        
        command "setOvenTemperature", [
            [name: "Temperature*", type: "NUMBER", description: "Temperature in °F or °C"],
            [name: "Unit", type: "ENUM", constraints: ["F", "C"], description: "Temperature unit (default: F)"]
        ]
        
        command "startProgramWithTemp", [
            [name: "Heating Mode*", type: "ENUM", constraints: [
                "HotAir", "TopBottomHeating", "Grill", "CircularAir",
                "ConvectionBake", "ConvectionRoast", "Broil", "Bake"
            ]],
            [name: "Temperature*", type: "NUMBER"],
            [name: "Duration (minutes)", type: "NUMBER", description: "Optional cooking time"]
        ]
        
        command "startProgramByKey", [
            [name: "Program Key*", type: "STRING", description: "e.g., Cooking.Oven.Program.HeatingMode.HotAir"]
        ]
        
        command "startTimedProgram", [
            [name: "Heating Mode*", type: "STRING"],
            [name: "Temperature*", type: "NUMBER"],
            [name: "Duration (minutes)*", type: "NUMBER"],
            [name: "Delay Start (minutes)", type: "NUMBER", description: "Optional delay"]
        ]
        
        command "stopProgram"
        
        command "setPower", [
            [name: "State*", type: "ENUM", constraints: ["on", "off"]]
        ]
        
        command "setTargetTemperature", [
            [name: "Temperature*", type: "NUMBER"],
            [name: "Unit", type: "ENUM", constraints: ["F", "C"]]
        ]
        
        command "setDuration", [
            [name: "Minutes*", type: "NUMBER"]
        ]
        
        command "setProgramOption", [
            [name: "Option Key*", type: "STRING"],
            [name: "Value*", type: "STRING"]
        ]
        
        command "setAlarmClock", [
            [name: "Minutes*", type: "NUMBER", description: "Kitchen timer in minutes"]
        ]
        
        command "cancelAlarmClock"

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
        // ATTRIBUTES - Program & Progress
        // =====================================================================
        
        attribute "activeProgram", "string"
        attribute "selectedProgram", "string"
        attribute "programProgress", "number"
        attribute "progressBar", "string"

        // =====================================================================
        // ATTRIBUTES - Temperature
        // =====================================================================
        
        attribute "currentTemperature", "number"
        attribute "targetTemperature", "number"
        attribute "temperatureUnit", "string"
        attribute "preheating", "string"
        attribute "preheatFinished", "string"

        // =====================================================================
        // ATTRIBUTES - Meat Probe
        // =====================================================================
        
        attribute "meatProbeTemperature", "number"
        attribute "meatProbeTargetTemperature", "number"
        attribute "meatProbeConnected", "string"

        // =====================================================================
        // ATTRIBUTES - Timing
        // =====================================================================
        
        attribute "remainingProgramTime", "number"
        attribute "remainingProgramTimeFormatted", "string"
        attribute "elapsedProgramTime", "number"
        attribute "elapsedProgramTimeFormatted", "string"
        attribute "duration", "number"
        attribute "durationFormatted", "string"
        attribute "startInRelative", "string"
        attribute "estimatedEndTimeFormatted", "string"

        // =====================================================================
        // ATTRIBUTES - Alarm/Timer
        // =====================================================================
        
        attribute "alarmClockRemaining", "number"
        attribute "alarmClockRemainingFormatted", "string"

        // =====================================================================
        // ATTRIBUTES - Control State
        // =====================================================================
        
        attribute "remoteControlStartAllowed", "string"
        attribute "remoteControlActive", "string"
        attribute "localControlActive", "string"

        // =====================================================================
        // ATTRIBUTES - Oven Options
        // =====================================================================
        
        attribute "fastPreHeat", "string"
        attribute "setpointTemperature", "number"

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
        // ATTRIBUTES - Lists & Meta
        // =====================================================================
        
        attribute "availableProgramsList", "string"
        attribute "availableOptionsList", "string"
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
        input name: "defaultTemperature", type: "number", title: "Default Temperature",
              defaultValue: 350
    }
}

// =============================================================================
// CONSTANTS
// =============================================================================

@Field static final String DRIVER_VERSION = "3.0.1"
@Field static final Integer MAX_DISCOVERED_KEYS = 100

@Field static final Map HEATING_MODES = [
    "HotAir": "Cooking.Oven.Program.HeatingMode.HotAir",
    "TopBottomHeating": "Cooking.Oven.Program.HeatingMode.TopBottomHeating",
    "CircularAir": "Cooking.Oven.Program.HeatingMode.CircularAir",
    "GrillCircularAir": "Cooking.Oven.Program.HeatingMode.GrillCircularAir",
    "BottomHeating": "Cooking.Oven.Program.HeatingMode.BottomHeating",
    "TopHeating": "Cooking.Oven.Program.HeatingMode.TopHeating",
    "SlowCook": "Cooking.Oven.Program.HeatingMode.SlowCook",
    "IntensiveHeat": "Cooking.Oven.Program.HeatingMode.IntensiveHeat",
    "KeepWarm": "Cooking.Oven.Program.HeatingMode.KeepWarm",
    "Defrost": "Cooking.Oven.Program.HeatingMode.Defrost",
    "Desiccation": "Cooking.Oven.Program.HeatingMode.Desiccation",
    "PizzaSetting": "Cooking.Oven.Program.HeatingMode.PizzaSetting",
    "Grill": "Cooking.Oven.Program.HeatingMode.Grill",
    "GrillLargeArea": "Cooking.Oven.Program.HeatingMode.GrillLargeArea",
    "GrillSmallArea": "Cooking.Oven.Program.HeatingMode.GrillSmallArea",
    "Pyrolysis": "Cooking.Oven.Program.HeatingMode.Pyrolysis",
    "SabbathProgramme": "Cooking.Oven.Program.HeatingMode.SabbathProgramme",
    "ConvectionBake": "Cooking.Oven.Program.HeatingMode.ConvectionBake",
    "ConvectionRoast": "Cooking.Oven.Program.HeatingMode.ConvectionRoast",
    "ConvectionBroil": "Cooking.Oven.Program.HeatingMode.ConvectionBroil",
    "TruConvection": "Cooking.Oven.Program.HeatingMode.TruConvection",
    "Broil": "Cooking.Oven.Program.HeatingMode.Broil",
    "Bake": "Cooking.Oven.Program.HeatingMode.Bake",
    "Roast": "Cooking.Oven.Program.HeatingMode.Roast",
    "Proof": "Cooking.Oven.Program.HeatingMode.Proof"
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
    sendEvent(name: "numberOfButtons", value: 4)
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
    logInfo("  defaultTemperature: ${defaultTemperature}")
    
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
    setPower("on") 
}

def off() { 
    setPower("off") 
}

def getAvailablePrograms() {
    logInfo("Fetching available programs")
    recordCommand("getAvailablePrograms")
    parent?.getAvailableProgramList(device)
}

def start() {
    def program = getDefaultProgram()
    def temp = settings?.defaultTemperature ?: 350
    logInfo("Starting default program: ${program} at ${temp}°")
    startProgramWithTemp(program, temp, null)
}

def startProgram(String heatingMode = null) {
    def selectedMode = heatingMode ?: getDefaultProgram()
    def programKey = buildProgramKey(selectedMode)
    
    saveLastProgram(selectedMode)
    
    logInfo("Starting program: ${selectedMode} (${programKey})")
    recordCommand("startProgram", [mode: selectedMode, key: programKey])
    parent?.startProgram(device, programKey)
}

def setOvenTemperature(BigDecimal temperature, String unit = null) {
    def tempUnit = unit ?: settings?.temperatureUnit ?: "F"
    Integer temp = temperature.toInteger()
    Integer tempC = (tempUnit == "F") ? fahrenheitToCelsius(temp) : temp
    
    logInfo("Setting temperature to ${temp}°${tempUnit} (${tempC}°C)")
    recordCommand("setOvenTemperature", [temp: temp, unit: tempUnit, tempC: tempC])
    parent?.setSelectedProgramOption(device, "Cooking.Oven.Option.SetpointTemperature", tempC)
}

def startProgramWithTemp(String heatingMode, BigDecimal temperature, BigDecimal durationMinutes = null) {
    def programKey = buildProgramKey(heatingMode)
    def tempUnit = settings?.temperatureUnit ?: "F"
    Integer temp = temperature.toInteger()
    Integer tempC = (tempUnit == "F") ? fahrenheitToCelsius(temp) : temp
    
    def options = [
        [key: "Cooking.Oven.Option.SetpointTemperature", value: tempC, unit: "°C"]
    ]
    
    if (durationMinutes && durationMinutes > 0) {
        Integer durationSec = (durationMinutes * 60).toInteger()
        options << [key: "BSH.Common.Option.Duration", value: durationSec, unit: "seconds"]
    }
    
    saveLastProgram(heatingMode)
    
    logInfo("Starting ${heatingMode} at ${temp}°${tempUnit}" + (durationMinutes ? " for ${durationMinutes} min" : ""))
    recordCommand("startProgramWithTemp", [mode: heatingMode, temp: temp, duration: durationMinutes])
    parent?.startProgram(device, programKey, options)
}

def startProgramByKey(String programKey) {
    def programName = extractEnum(programKey)
    saveLastProgram(programName)
    
    logInfo("Starting program by key: ${programKey}")
    recordCommand("startProgramByKey", [key: programKey])
    parent?.startProgram(device, programKey)
}

def startTimedProgram(String heatingMode, BigDecimal temperature, BigDecimal durationMinutes, BigDecimal delayMinutes = null) {
    def programKey = buildProgramKey(heatingMode)
    def tempUnit = settings?.temperatureUnit ?: "F"
    Integer temp = temperature.toInteger()
    Integer tempC = (tempUnit == "F") ? fahrenheitToCelsius(temp) : temp
    Integer durationSec = (durationMinutes * 60).toInteger()
    
    def options = [
        [key: "Cooking.Oven.Option.SetpointTemperature", value: tempC, unit: "°C"],
        [key: "BSH.Common.Option.Duration", value: durationSec, unit: "seconds"]
    ]
    
    if (delayMinutes && delayMinutes > 0) {
        Integer delaySec = (delayMinutes * 60).toInteger()
        options << [key: "BSH.Common.Option.StartInRelative", value: delaySec, unit: "seconds"]
    }
    
    saveLastProgram(heatingMode)
    
    logInfo("Starting ${heatingMode} at ${temp}°${tempUnit} for ${durationMinutes} min" + 
            (delayMinutes ? " (delayed ${delayMinutes} min)" : ""))
    recordCommand("startTimedProgram", [mode: heatingMode, temp: temp, duration: durationMinutes, delay: delayMinutes])
    parent?.startProgram(device, programKey, options)
}

def stopProgram() {
    logInfo("Stopping program")
    recordCommand("stopProgram")
    parent?.stopProgram(device)
}

def setPower(String powerState) {
    boolean on = (powerState == "on")
    logInfo("Setting power: ${on ? 'ON' : 'OFF'}")
    recordCommand("setPower", [state: powerState])
    parent?.setPowerState(device, on)
}

def setTargetTemperature(BigDecimal temperature, String unit = null) {
    setOvenTemperature(temperature, unit)
}

def setDuration(BigDecimal minutes) {
    Integer durationSec = (minutes * 60).toInteger()
    logInfo("Setting duration: ${minutes} minutes")
    recordCommand("setDuration", [minutes: minutes])
    parent?.setSelectedProgramOption(device, "BSH.Common.Option.Duration", durationSec)
}

def setProgramOption(String optionKey, String value) {
    logInfo("Setting option ${optionKey} = ${value}")
    recordCommand("setProgramOption", [key: optionKey, value: value])
    
    def typedValue = value
    if (value.isInteger()) {
        typedValue = value.toInteger()
    } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
        typedValue = value.toBoolean()
    }
    
    parent?.setSelectedProgramOption(device, optionKey, typedValue)
}

def setAlarmClock(BigDecimal minutes) {
    Integer seconds = (minutes * 60).toInteger()
    logInfo("Setting alarm clock: ${minutes} minutes")
    recordCommand("setAlarmClock", [minutes: minutes])
    parent?.setSetting(device, "BSH.Common.Setting.AlarmClock", seconds)
}

def cancelAlarmClock() {
    logInfo("Canceling alarm clock")
    recordCommand("cancelAlarmClock")
    parent?.setSetting(device, "BSH.Common.Setting.AlarmClock", 0)
}

private String buildProgramKey(String program) {
    if (program?.contains(".")) {
        return program
    }
    
    if (HEATING_MODES.containsKey(program)) {
        return HEATING_MODES[program]
    }
    
    if (state.programMap?.containsKey(program)) {
        return state.programMap[program]
    }
    
    return "Cooking.Oven.Program.HeatingMode.${program}"
}

private String getDefaultProgram() {
    def lastProgram = device.currentValue("lastProgram")
    if (lastProgram) return lastProgram
    
    if (state.programNames?.contains("ConvectionBake")) return "ConvectionBake"
    if (state.programNames?.contains("HotAir")) return "HotAir"
    if (state.programNames?.contains("Bake")) return "Bake"
    if (state.programNames?.size() > 0) return state.programNames[0]
    
    return "HotAir"
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
    def list = new JsonSlurper().parseText(json)
    def names = list.collect { it.name ?: extractEnum(it.key) }
    sendEvent(name: "availableOptionsList", value: names.join(", "))
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
                logInfo("Cooking complete - pushing button 2")
                sendEvent(name: "pushed", value: 2, isStateChange: true, descriptionText: "Cooking complete")
            }
            
            if (opState in ["Ready", "Inactive", "Finished"]) {
                resetProgramState()
            }
            updateDerivedState()
            updateJsonState()
            break

        case "BSH.Common.Status.DoorState":
            def doorState = extractEnum(evt.value)
            sendEvent(name: "doorState", value: doorState)
            sendEvent(name: "contact", value: (doorState == "Open" ? "open" : "closed"))
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
            sendEvent(name: "switch", value: (power == "On" ? "on" : "off"))
            updateJsonState()
            break

        case "Cooking.Oven.Status.CurrentCavityTemperature":
            Integer tempC = evt.value as Integer
            Integer tempDisplay = convertTemperatureForDisplay(tempC)
            sendEvent(name: "currentTemperature", value: tempDisplay)
            sendEvent(name: "temperature", value: tempDisplay)
            checkPreheatStatus()
            updateJsonState()
            break

        case "Cooking.Oven.Option.SetpointTemperature":
            Integer tempC = evt.value as Integer
            Integer tempDisplay = convertTemperatureForDisplay(tempC)
            sendEvent(name: "targetTemperature", value: tempDisplay)
            sendEvent(name: "setpointTemperature", value: tempDisplay)
            checkPreheatStatus()
            updateJsonState()
            break

        case "Cooking.Oven.Status.MeatProbeTemperature":
            Integer tempC = evt.value as Integer
            Integer tempDisplay = convertTemperatureForDisplay(tempC)
            sendEvent(name: "meatProbeTemperature", value: tempDisplay)
            sendEvent(name: "meatProbeConnected", value: "true")
            checkMeatProbeTarget()
            break

        case "Cooking.Oven.Option.MeatProbeTemperature":
            Integer tempC = evt.value as Integer
            Integer tempDisplay = convertTemperatureForDisplay(tempC)
            sendEvent(name: "meatProbeTargetTemperature", value: tempDisplay)
            break

        case "BSH.Common.Option.RemainingProgramTime":
            Integer sec = evt.value as Integer
            sendEvent(name: "remainingProgramTime", value: sec)
            sendEvent(name: "remainingProgramTimeFormatted", value: secondsToTime(sec))
            updateEstimatedEndTime(sec)
            updateJsonState()
            break

        case "BSH.Common.Option.ElapsedProgramTime":
            Integer sec = evt.value as Integer
            sendEvent(name: "elapsedProgramTime", value: sec)
            sendEvent(name: "elapsedProgramTimeFormatted", value: secondsToTime(sec))
            break

        case "BSH.Common.Option.Duration":
            Integer sec = evt.value as Integer
            sendEvent(name: "duration", value: sec)
            sendEvent(name: "durationFormatted", value: secondsToTime(sec))
            break

        case "BSH.Common.Option.StartInRelative":
            Integer sec = evt.value as Integer
            sendEvent(name: "startInRelative", value: secondsToTime(sec))
            break

        case "BSH.Common.Option.ProgramProgress":
            Integer progress = evt.value as Integer
            sendEvent(name: "programProgress", value: progress)
            sendEvent(name: "progressBar", value: "${progress}%")
            updateJsonState()
            break

        case "BSH.Common.Setting.AlarmClock":
            Integer sec = evt.value as Integer
            sendEvent(name: "alarmClockRemaining", value: sec)
            sendEvent(name: "alarmClockRemainingFormatted", value: secondsToTime(sec))
            break

        case "BSH.Common.Event.AlarmClockElapsed":
            def value = extractEnum(evt.value)
            if (value == "Present") {
                logInfo("Alarm clock elapsed - pushing button 4")
                sendEvent(name: "pushed", value: 4, isStateChange: true, descriptionText: "Timer complete")
                sendAlert("AlarmElapsed", "Kitchen timer has finished")
            }
            break

        case "BSH.Common.Root.ActiveProgram":
            sendEvent(name: "activeProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            updateJsonState()
            break

        case "BSH.Common.Root.SelectedProgram":
            sendEvent(name: "selectedProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            break

        case "Cooking.Oven.Event.PreheatFinished":
            def value = extractEnum(evt.value)
            sendEvent(name: "preheatFinished", value: (value == "Present") ? "true" : "false")
            if (value == "Present") {
                logInfo("Preheat complete - pushing button 1")
                sendEvent(name: "pushed", value: 1, isStateChange: true, descriptionText: "Preheat complete")
                sendEvent(name: "preheating", value: "false")
                sendAlert("PreheatComplete", "Oven has reached target temperature")
            }
            break

        case "Cooking.Oven.Option.FastPreHeat":
            sendEvent(name: "fastPreHeat", value: evt.value.toString())
            break

        case ~/Cooking\.Oven\.Option\..*/:
            def attr = evt.key.split("\\.").last()
            sendEvent(name: attr, value: evt.value.toString())
            logDebug("Oven option: ${attr} = ${evt.value}")
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

private void resetProgramState() {
    logDebug("Resetting program state")
    sendEvent(name: "remainingProgramTime", value: 0)
    sendEvent(name: "remainingProgramTimeFormatted", value: "00:00")
    sendEvent(name: "elapsedProgramTime", value: 0)
    sendEvent(name: "elapsedProgramTimeFormatted", value: "00:00")
    sendEvent(name: "programProgress", value: 0)
    sendEvent(name: "progressBar", value: "0%")
    sendEvent(name: "estimatedEndTimeFormatted", value: "")
    sendEvent(name: "preheating", value: "false")
    sendEvent(name: "preheatFinished", value: "false")
}

private void updateDerivedState() {
    try {
        String opState = device.currentValue("operationState") as String
        String friendly = determineFriendlyStatus(opState)
        if (friendly) {
            sendEvent(name: "friendlyStatus", value: friendly)
        }
    } catch (Exception e) {
        logWarn("Error updating derived state: ${e.message}")
    }
}

private String determineFriendlyStatus(String opState) {
    def preheating = device.currentValue("preheating")
    def activeProgram = device.currentValue("activeProgram")
    def currentTemp = device.currentValue("currentTemperature")
    def targetTemp = device.currentValue("targetTemperature")
    
    switch (opState) {
        case "Ready":
        case "Inactive":
            return "Ready"
        case "DelayedStart":
            return "Waiting to start"
        case "Run":
            if (preheating == "true") {
                return "Preheating to ${targetTemp}°"
            }
            if (activeProgram) {
                return "${activeProgram} at ${currentTemp}°"
            }
            return "Cooking at ${currentTemp}°"
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

private void checkPreheatStatus() {
    def opState = device.currentValue("operationState")
    def currentTemp = device.currentValue("currentTemperature") as Integer
    def targetTemp = device.currentValue("targetTemperature") as Integer
    def preheatFinished = device.currentValue("preheatFinished")
    
    if (opState == "Run" && targetTemp && currentTemp) {
        if (currentTemp < (targetTemp - 10) && preheatFinished != "true") {
            sendEvent(name: "preheating", value: "true")
        } else if (currentTemp >= (targetTemp - 5)) {
            sendEvent(name: "preheating", value: "false")
        }
    }
}

private void checkMeatProbeTarget() {
    def currentTemp = device.currentValue("meatProbeTemperature") as Integer
    def targetTemp = device.currentValue("meatProbeTargetTemperature") as Integer
    
    if (currentTemp && targetTemp && currentTemp >= targetTemp) {
        logInfo("Meat probe target reached - pushing button 3")
        sendEvent(name: "pushed", value: 3, isStateChange: true, descriptionText: "Meat probe target reached")
        sendAlert("MeatProbeReady", "Meat has reached target temperature of ${targetTemp}°")
    }
}

private void updateEstimatedEndTime(Integer remainingSec) {
    if (remainingSec && remainingSec > 0) {
        Long endMillis = now() + (remainingSec * 1000L)
        TimeZone tz = location?.timeZone ?: TimeZone.getDefault()
        String endFormatted = new Date(endMillis).format("h:mm a", tz)
        sendEvent(name: "estimatedEndTimeFormatted", value: endFormatted)
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
            programProgress: device.currentValue("programProgress"),
            currentTemperature: device.currentValue("currentTemperature"),
            targetTemperature: device.currentValue("targetTemperature"),
            temperatureUnit: settings?.temperatureUnit ?: "F",
            preheating: device.currentValue("preheating"),
            meatProbeTemperature: device.currentValue("meatProbeTemperature"),
            meatProbeTargetTemperature: device.currentValue("meatProbeTargetTemperature"),
            remainingProgramTime: device.currentValue("remainingProgramTime"),
            remainingProgramTimeFormatted: device.currentValue("remainingProgramTimeFormatted"),
            estimatedEndTime: device.currentValue("estimatedEndTimeFormatted"),
            remoteControlStartAllowed: device.currentValue("remoteControlStartAllowed"),
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
    return String.format("%02d:%02d", hours, minutes)
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
