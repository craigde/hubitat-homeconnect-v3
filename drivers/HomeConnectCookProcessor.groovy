/**
 *  Home Connect CookProcessor v3 Driver
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
 *  This driver represents a Home Connect cook processor appliance (Bosch Cookit, etc.).
 *  
 *  Supported Features:
 *  - Automatic cooking programs (guided recipes)
 *  - Manual mode (temperature, speed, time control)
 *  - Mixing/stirring speed control
 *  - Heating temperature control
 *  - Timer functions
 *  - Step-by-step program progress
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
    definition(name: "Home Connect CookProcessor v3", namespace: "craigde", author: "Craig Dewar") {

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
            [name: "Program", type: "STRING", description: "Program name or key"]
        ]
        
        command "startProgramByKey", [
            [name: "Program Key*", type: "STRING", description: "Full program key"]
        ]
        
        command "startManualMode", [
            [name: "Temperature", type: "NUMBER", description: "Target temperature in °C (0 for no heat)"],
            [name: "Speed", type: "NUMBER", description: "Mixing speed (0-10)"],
            [name: "Duration Minutes", type: "NUMBER", description: "Duration in minutes"]
        ]
        
        command "stopProgram"
        command "pauseProgram"
        command "resumeProgram"
        
        command "setTemperature", [
            [name: "Temperature*", type: "NUMBER", description: "Target temperature in °C"]
        ]
        
        command "setMixingSpeed", [
            [name: "Speed*", type: "NUMBER", description: "Mixing speed (0-10, 0=off)"]
        ]
        
        command "setDuration", [
            [name: "Minutes*", type: "NUMBER", description: "Duration in minutes"]
        ]
        
        command "nextStep"
        command "previousStep"
        
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
        attribute "currentStep", "number"
        attribute "totalSteps", "number"
        attribute "stepProgress", "string"

        // =====================================================================
        // ATTRIBUTES - Timing
        // =====================================================================
        
        attribute "remainingProgramTime", "number"
        attribute "remainingProgramTimeFormatted", "string"
        attribute "elapsedProgramTime", "number"
        attribute "elapsedProgramTimeFormatted", "string"
        attribute "estimatedEndTimeFormatted", "string"
        attribute "stepRemainingTime", "number"
        attribute "stepRemainingTimeFormatted", "string"

        // =====================================================================
        // ATTRIBUTES - Cooking Parameters
        // =====================================================================
        
        attribute "currentTemperature", "number"
        attribute "targetTemperature", "number"
        attribute "mixingSpeed", "number"
        attribute "mixingSpeedDisplay", "string"
        attribute "heatingActive", "string"
        attribute "motorActive", "string"

        // =====================================================================
        // ATTRIBUTES - Control State
        // =====================================================================
        
        attribute "remoteControlStartAllowed", "string"
        attribute "remoteControlActive", "string"
        attribute "localControlActive", "string"

        // =====================================================================
        // ATTRIBUTES - Safety
        // =====================================================================
        
        attribute "lidLocked", "string"
        attribute "childLock", "string"

        // =====================================================================
        // ATTRIBUTES - Events & Alerts
        // =====================================================================
        
        attribute "lastAlert", "string"
        attribute "lastAlertTime", "string"
        attribute "actionRequired", "string"

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
        input name: "defaultTemperature", type: "number", title: "Default Temperature (°C)",
              defaultValue: 100, range: "0..200"
        input name: "defaultSpeed", type: "number", title: "Default Mixing Speed (0-10)",
              defaultValue: 3, range: "0..10"
        input name: "defaultDuration", type: "number", title: "Default Duration (minutes)",
              defaultValue: 10, range: "1..180"
    }
}

// =============================================================================
// CONSTANTS
// =============================================================================

@Field static final String DRIVER_VERSION = "3.0.1"
@Field static final Integer MAX_DISCOVERED_KEYS = 100

@Field static final Map COOK_PROGRAMS = [
    "ManualCooking": "ConsumerProducts.CookProcessor.Program.Manual.ManualCooking",
    "AutomaticCooking": "ConsumerProducts.CookProcessor.Program.Automatic.AutomaticCooking",
    "KeepWarm": "ConsumerProducts.CookProcessor.Program.Manual.KeepWarm",
    "SlowCook": "ConsumerProducts.CookProcessor.Program.Manual.SlowCook",
    "Steam": "ConsumerProducts.CookProcessor.Program.Manual.Steam",
    "Stir": "ConsumerProducts.CookProcessor.Program.Manual.Stir",
    "Chop": "ConsumerProducts.CookProcessor.Program.Manual.Chop",
    "Puree": "ConsumerProducts.CookProcessor.Program.Manual.Puree",
    "Mix": "ConsumerProducts.CookProcessor.Program.Manual.Mix",
    "Knead": "ConsumerProducts.CookProcessor.Program.Manual.Knead",
    "Whip": "ConsumerProducts.CookProcessor.Program.Manual.Whip",
    "Emulsify": "ConsumerProducts.CookProcessor.Program.Manual.Emulsify"
]

@Field static final Map SPEED_NAMES = [
    0: "Off",
    1: "Stir",
    2: "Very Low",
    3: "Low",
    4: "Low-Medium",
    5: "Medium",
    6: "Medium-High",
    7: "High",
    8: "Very High",
    9: "Turbo",
    10: "Max"
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
    sendEvent(name: "contact", value: "closed")
    
    // Button 1: Program/step complete
    // Button 2: Action required (add ingredients, etc.)
    // Button 3: Temperature reached
    // Button 4: Timer complete
    sendEvent(name: "numberOfButtons", value: 4)
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
    logInfo("--- Cooking Parameters ---")
    logInfo("  Current Temp: ${device.currentValue('currentTemperature')}°C")
    logInfo("  Target Temp: ${device.currentValue('targetTemperature')}°C")
    logInfo("  Mixing Speed: ${device.currentValue('mixingSpeed')} (${device.currentValue('mixingSpeedDisplay')})")
    logInfo("  Heating Active: ${device.currentValue('heatingActive')}")
    logInfo("  Motor Active: ${device.currentValue('motorActive')}")
    
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
    logInfo("  defaultTemperature: ${defaultTemperature}")
    logInfo("  defaultSpeed: ${defaultSpeed}")
    logInfo("  defaultDuration: ${defaultDuration}")
    
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
    def temp = settings?.defaultTemperature ?: 100
    def speed = settings?.defaultSpeed ?: 3
    def duration = settings?.defaultDuration ?: 10
    startManualMode(temp, speed, duration)
}

def startProgram(String program = null) {
    def selectedProgram = program ?: "ManualCooking"
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

def startManualMode(BigDecimal temperature = null, BigDecimal speed = null, BigDecimal durationMinutes = null) {
    def programKey = COOK_PROGRAMS["ManualCooking"]
    
    def temp = temperature?.toInteger() ?: settings?.defaultTemperature ?: 100
    def spd = speed?.toInteger() ?: settings?.defaultSpeed ?: 3
    def dur = durationMinutes?.toInteger() ?: settings?.defaultDuration ?: 10
    Integer durationSec = dur * 60
    
    def options = [
        [key: "ConsumerProducts.CookProcessor.Option.TargetTemperature", value: temp, unit: "°C"],
        [key: "ConsumerProducts.CookProcessor.Option.MotorSpeed", value: spd],
        [key: "BSH.Common.Option.Duration", value: durationSec, unit: "seconds"]
    ]
    
    saveLastProgram("Manual: ${temp}°C, Speed ${spd}, ${dur} min")
    
    logInfo("Starting manual mode: ${temp}°C, speed ${spd}, ${dur} min")
    recordCommand("startManualMode", [temp: temp, speed: spd, duration: dur])
    parent?.startProgram(device, programKey, options)
}

def stopProgram() {
    logInfo("Stopping program")
    recordCommand("stopProgram")
    parent?.stopProgram(device)
}

def pauseProgram() {
    logInfo("Pausing program")
    recordCommand("pauseProgram")
    parent?.sendCommand(device, "BSH.Common.Command.PauseProgram")
}

def resumeProgram() {
    logInfo("Resuming program")
    recordCommand("resumeProgram")
    parent?.sendCommand(device, "BSH.Common.Command.ResumeProgram")
}

def setTemperature(BigDecimal temperature) {
    Integer temp = temperature.toInteger()
    logInfo("Setting temperature: ${temp}°C")
    recordCommand("setTemperature", [temp: temp])
    parent?.setSelectedProgramOption(device, "ConsumerProducts.CookProcessor.Option.TargetTemperature", temp)
}

def setMixingSpeed(BigDecimal speed) {
    Integer spd = speed.toInteger()
    if (spd < 0) spd = 0
    if (spd > 10) spd = 10
    
    logInfo("Setting mixing speed: ${spd} (${SPEED_NAMES[spd] ?: spd})")
    recordCommand("setMixingSpeed", [speed: spd])
    parent?.setSelectedProgramOption(device, "ConsumerProducts.CookProcessor.Option.MotorSpeed", spd)
}

def setDuration(BigDecimal minutes) {
    Integer durationSec = (minutes * 60).toInteger()
    logInfo("Setting duration: ${minutes} minutes")
    recordCommand("setDuration", [minutes: minutes])
    parent?.setSelectedProgramOption(device, "BSH.Common.Option.Duration", durationSec)
}

def nextStep() {
    logInfo("Advancing to next step")
    recordCommand("nextStep")
    parent?.sendCommand(device, "ConsumerProducts.CookProcessor.Command.NextStep")
}

def previousStep() {
    logInfo("Going to previous step")
    recordCommand("previousStep")
    parent?.sendCommand(device, "ConsumerProducts.CookProcessor.Command.PreviousStep")
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
    
    if (COOK_PROGRAMS.containsKey(program)) {
        return COOK_PROGRAMS[program]
    }
    
    if (state.programMap?.containsKey(program)) {
        return state.programMap[program]
    }
    
    return "ConsumerProducts.CookProcessor.Program.Manual.${program}"
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
                logInfo("Program complete - pushing button 1")
                sendEvent(name: "pushed", value: 1, isStateChange: true, descriptionText: "Program complete")
            }
            
            if (opState == "ActionRequired" && previousState != "ActionRequired") {
                logInfo("Action required - pushing button 2")
                sendEvent(name: "pushed", value: 2, isStateChange: true, descriptionText: "Action required")
            }
            
            sendEvent(name: "switch", value: (opState == "Run" ? "on" : "off"))
            
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
            
            def locked = (doorState == "Locked")
            sendEvent(name: "lidLocked", value: locked.toString())
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

        case "BSH.Common.Setting.ChildLock":
            def locked = evt.value.toString().toLowerCase() == "true"
            sendEvent(name: "childLock", value: locked ? "On" : "Off")
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

        case "BSH.Common.Option.ProgramProgress":
            Integer progress = evt.value as Integer
            sendEvent(name: "programProgress", value: progress)
            sendEvent(name: "progressBar", value: "${progress}%")
            updateDerivedState()
            updateJsonState()
            break

        case "BSH.Common.Option.Duration":
            // Duration is set, not a status
            break

        case "BSH.Common.Root.ActiveProgram":
            sendEvent(name: "activeProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            updateJsonState()
            break

        case "BSH.Common.Root.SelectedProgram":
            sendEvent(name: "selectedProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            break

        case "ConsumerProducts.CookProcessor.Status.CurrentTemperature":
            Integer temp = evt.value as Integer
            sendEvent(name: "currentTemperature", value: temp)
            sendEvent(name: "temperature", value: temp)
            
            // Check if target temperature reached
            def targetTemp = device.currentValue("targetTemperature") as Integer
            if (targetTemp && temp >= targetTemp && device.currentValue("heatingActive") == "true") {
                logInfo("Target temperature reached - pushing button 3")
                sendEvent(name: "pushed", value: 3, isStateChange: true, descriptionText: "Temperature ${temp}°C reached")
            }
            updateJsonState()
            break

        case "ConsumerProducts.CookProcessor.Option.TargetTemperature":
            Integer temp = evt.value as Integer
            sendEvent(name: "targetTemperature", value: temp)
            sendEvent(name: "heatingActive", value: (temp > 0).toString())
            updateJsonState()
            break

        case "ConsumerProducts.CookProcessor.Option.MotorSpeed":
            Integer speed = evt.value as Integer
            sendEvent(name: "mixingSpeed", value: speed)
            sendEvent(name: "mixingSpeedDisplay", value: SPEED_NAMES[speed] ?: "Speed ${speed}")
            sendEvent(name: "motorActive", value: (speed > 0).toString())
            updateJsonState()
            break

        case "ConsumerProducts.CookProcessor.Status.CurrentStep":
            Integer step = evt.value as Integer
            sendEvent(name: "currentStep", value: step)
            updateStepProgress()
            updateDerivedState()
            break

        case "ConsumerProducts.CookProcessor.Status.TotalSteps":
            Integer total = evt.value as Integer
            sendEvent(name: "totalSteps", value: total)
            updateStepProgress()
            break

        case "ConsumerProducts.CookProcessor.Option.StepRemainingTime":
            Integer sec = evt.value as Integer
            sendEvent(name: "stepRemainingTime", value: sec)
            sendEvent(name: "stepRemainingTimeFormatted", value: secondsToTime(sec))
            
            if (sec == 0) {
                logInfo("Step timer complete - pushing button 4")
                sendEvent(name: "pushed", value: 4, isStateChange: true, descriptionText: "Step timer complete")
            }
            break

        case "ConsumerProducts.CookProcessor.Event.ActionRequired":
            def value = extractEnum(evt.value)
            sendEvent(name: "actionRequired", value: value)
            if (value == "Present") {
                sendAlert("ActionRequired", "Action required - check appliance")
            }
            break

        case ~/ConsumerProducts\.CookProcessor\.Option\..*/:
        case ~/ConsumerProducts\.CookProcessor\.Status\..*/:
            def attr = evt.key.split("\\.").last()
            logDebug("CookProcessor option/status: ${attr} = ${evt.value}")
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
    sendEvent(name: "currentStep", value: 0)
    sendEvent(name: "totalSteps", value: 0)
    sendEvent(name: "stepProgress", value: "")
    sendEvent(name: "heatingActive", value: "false")
    sendEvent(name: "motorActive", value: "false")
    sendEvent(name: "actionRequired", value: "")
}

private void updateStepProgress() {
    def current = device.currentValue("currentStep") ?: 0
    def total = device.currentValue("totalSteps") ?: 0
    
    if (total > 0) {
        sendEvent(name: "stepProgress", value: "Step ${current} of ${total}")
    } else {
        sendEvent(name: "stepProgress", value: "")
    }
}

private void updateDerivedState() {
    try {
        String opState = device.currentValue("operationState") as String
        Integer progress = device.currentValue("programProgress") as Integer
        String activeProgram = device.currentValue("activeProgram")
        Integer currentTemp = device.currentValue("currentTemperature") as Integer
        Integer targetTemp = device.currentValue("targetTemperature") as Integer
        Integer speed = device.currentValue("mixingSpeed") as Integer
        def stepProgress = device.currentValue("stepProgress")
        
        String friendly = determineFriendlyStatus(opState, progress, activeProgram, currentTemp, targetTemp, speed, stepProgress)
        sendEvent(name: "friendlyStatus", value: friendly)
        
    } catch (Exception e) {
        logWarn("Error updating derived state: ${e.message}")
    }
}

private String determineFriendlyStatus(String opState, Integer progress, String program, Integer currentTemp, Integer targetTemp, Integer speed, String stepProgress) {
    switch (opState) {
        case "Ready":
        case "Inactive":
            return "Ready"
        case "Run":
            def parts = []
            if (program) parts << program
            if (stepProgress) parts << stepProgress
            if (currentTemp && targetTemp && currentTemp < targetTemp) {
                parts << "Heating ${currentTemp}°/${targetTemp}°C"
            } else if (currentTemp) {
                parts << "${currentTemp}°C"
            }
            if (speed && speed > 0) {
                parts << "Speed ${speed}"
            }
            if (progress) parts << "${progress}%"
            return parts.join(" - ") ?: "Running"
        case "Pause":
            return "Paused"
        case "ActionRequired":
            return "Action Required"
        case "Finished":
            return "Complete"
        case "Aborting":
            return "Stopping"
        case "Error":
            return "Error"
        default:
            return opState ?: "Unknown"
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
            lidLocked: device.currentValue("lidLocked") == "true",
            powerState: device.currentValue("powerState"),
            friendlyStatus: device.currentValue("friendlyStatus"),
            activeProgram: device.currentValue("activeProgram"),
            programProgress: device.currentValue("programProgress"),
            currentStep: device.currentValue("currentStep"),
            totalSteps: device.currentValue("totalSteps"),
            currentTemperature: device.currentValue("currentTemperature"),
            targetTemperature: device.currentValue("targetTemperature"),
            mixingSpeed: device.currentValue("mixingSpeed"),
            mixingSpeedDisplay: device.currentValue("mixingSpeedDisplay"),
            heatingActive: device.currentValue("heatingActive") == "true",
            motorActive: device.currentValue("motorActive") == "true",
            remainingProgramTime: device.currentValue("remainingProgramTime"),
            remainingProgramTimeFormatted: device.currentValue("remainingProgramTimeFormatted"),
            estimatedEndTime: device.currentValue("estimatedEndTimeFormatted"),
            remoteControlStartAllowed: device.currentValue("remoteControlStartAllowed"),
            actionRequired: device.currentValue("actionRequired"),
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
