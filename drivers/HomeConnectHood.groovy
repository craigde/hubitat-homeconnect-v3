/**
 *  Home Connect Hood v3 Driver
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
 *  This driver represents a Home Connect range hood/ventilation appliance (Bosch, Thermador, etc.).
 *  
 *  Supported Features:
 *  - Fan speed control (Off, Fan1-Fan5, Intensive)
 *  - Functional lighting control with brightness
 *  - Ambient lighting with brightness and color
 *  - Delayed shut-off (run-on timer)
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
    definition(name: "Home Connect Hood v3", namespace: "craigde", author: "Craig Dewar") {

        // Standard capabilities
        capability "Initialize"
        capability "Refresh"
        capability "Switch"
        capability "FanControl"
        capability "SwitchLevel"
        capability "Sensor"
        capability "Configuration"

        // =====================================================================
        // USER-FACING COMMANDS
        // =====================================================================
        
        command "getAvailablePrograms"
        
        command "setFanSpeed", [
            [name: "Speed*", type: "ENUM", constraints: [
                "off", "low", "medium", "high", "auto",
                "Fan1", "Fan2", "Fan3", "Fan4", "Fan5", "FanIntensive"
            ]]
        ]
        
        command "fanOff"
        command "fanLow"
        command "fanMedium"
        command "fanHigh"
        command "fanIntensive"
        command "fanAuto"
        
        command "setFanLevel", [
            [name: "Level*", type: "NUMBER", description: "Fan level 0-5 (0=off)"]
        ]
        
        command "startProgram", [
            [name: "Program", type: "ENUM", constraints: [
                "Automatic", "Venting", "DelayedShutOff"
            ]]
        ]
        
        command "stopProgram"
        
        command "setLight", [
            [name: "State*", type: "ENUM", constraints: ["on", "off"]]
        ]
        
        command "setLightBrightness", [
            [name: "Brightness*", type: "NUMBER", description: "0-100%"]
        ]
        
        command "setAmbientLight", [
            [name: "State*", type: "ENUM", constraints: ["on", "off"]]
        ]
        
        command "setAmbientLightBrightness", [
            [name: "Brightness*", type: "NUMBER", description: "0-100%"]
        ]
        
        command "setAmbientLightColor", [
            [name: "Color*", type: "ENUM", constraints: [
                "CustomColor", "Color1", "Color2", "Color3", "Color4", 
                "Color5", "Color6", "Color7", "Color8", "Color9", "Color10"
            ]]
        ]
        
        command "setDelayedShutOff", [
            [name: "Minutes*", type: "NUMBER", description: "Run-on time (0 to cancel)"]
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

        // =====================================================================
        // ATTRIBUTES - Fan
        // =====================================================================
        
        attribute "fanSpeed", "string"
        attribute "fanLevel", "number"
        attribute "speed", "string"
        attribute "ventingLevel", "string"
        attribute "intensiveLevel", "string"

        // =====================================================================
        // ATTRIBUTES - Lighting
        // =====================================================================
        
        attribute "functionalLightState", "string"
        attribute "functionalLightBrightness", "number"
        attribute "ambientLightState", "string"
        attribute "ambientLightBrightness", "number"
        attribute "ambientLightColor", "string"
        attribute "level", "number"

        // =====================================================================
        // ATTRIBUTES - Programs
        // =====================================================================
        
        attribute "activeProgram", "string"
        attribute "selectedProgram", "string"
        attribute "delayedShutOffRemaining", "number"
        attribute "delayedShutOffRemainingFormatted", "string"

        // =====================================================================
        // ATTRIBUTES - Control State
        // =====================================================================
        
        attribute "remoteControlStartAllowed", "string"
        attribute "remoteControlActive", "string"
        attribute "localControlActive", "string"

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
    }
}

// =============================================================================
// CONSTANTS
// =============================================================================

@Field static final String DRIVER_VERSION = "3.0.1"
@Field static final Integer MAX_DISCOVERED_KEYS = 100

@Field static final Map FAN_SPEEDS = [
    "off": "Cooking.Hood.EnumType.Stage.FanOff",
    "low": "Cooking.Hood.EnumType.Stage.FanStage01",
    "medium": "Cooking.Hood.EnumType.Stage.FanStage03",
    "high": "Cooking.Hood.EnumType.Stage.FanStage05",
    "auto": "Cooking.Hood.EnumType.Stage.FanStage03",
    "Fan1": "Cooking.Hood.EnumType.Stage.FanStage01",
    "Fan2": "Cooking.Hood.EnumType.Stage.FanStage02",
    "Fan3": "Cooking.Hood.EnumType.Stage.FanStage03",
    "Fan4": "Cooking.Hood.EnumType.Stage.FanStage04",
    "Fan5": "Cooking.Hood.EnumType.Stage.FanStage05",
    "FanIntensive": "Cooking.Hood.EnumType.IntensiveStage.IntensiveStage1"
]

@Field static final Map VENTING_LEVELS = [
    "FanOff": ["off", 0],
    "FanStage01": ["low", 1],
    "FanStage02": ["medium-low", 2],
    "FanStage03": ["medium", 3],
    "FanStage04": ["medium-high", 4],
    "FanStage05": ["high", 5]
]

// =============================================================================
// LIFECYCLE METHODS
// =============================================================================

def installed() {
    log.info "${device.displayName}: Installed"
    initializeState()
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "eventPresentState", value: "Off")
    sendEvent(name: "fanLevel", value: 0)
    sendEvent(name: "speed", value: "off")
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
    fanLow()
}

def off() { 
    fanOff()
}

def setLevel(level, duration = null) {
    setLightBrightness(level)
}

def setSpeed(String speed) {
    setFanSpeed(speed)
}

def getAvailablePrograms() {
    logInfo("Fetching available programs")
    recordCommand("getAvailablePrograms")
    parent?.getAvailableProgramList(device)
}

def setFanSpeed(String speed) {
    logInfo("Setting fan speed: ${speed}")
    recordCommand("setFanSpeed", [speed: speed])
    
    if (speed == "off") {
        parent?.setSetting(device, "Cooking.Hood.Setting.VentingLevel", "Cooking.Hood.EnumType.Stage.FanOff")
    } else if (speed == "FanIntensive") {
        parent?.setSetting(device, "Cooking.Hood.Setting.IntensiveLevel", "Cooking.Hood.EnumType.IntensiveStage.IntensiveStage1")
    } else if (FAN_SPEEDS.containsKey(speed)) {
        parent?.setSetting(device, "Cooking.Hood.Setting.VentingLevel", FAN_SPEEDS[speed])
    } else {
        logWarn("Unknown fan speed: ${speed}")
    }
}

def fanOff() {
    setFanSpeed("off")
}

def fanLow() {
    setFanSpeed("Fan1")
}

def fanMedium() {
    setFanSpeed("Fan3")
}

def fanHigh() {
    setFanSpeed("Fan5")
}

def fanIntensive() {
    setFanSpeed("FanIntensive")
}

def fanAuto() {
    setFanSpeed("Fan3")
}

def setFanLevel(BigDecimal level) {
    Integer lvl = level.toInteger()
    if (lvl < 0) lvl = 0
    if (lvl > 5) lvl = 5
    
    def speedName = lvl == 0 ? "off" : "Fan${lvl}"
    setFanSpeed(speedName)
}

def startProgram(String program = "Venting") {
    def programKey
    switch (program) {
        case "Automatic":
            programKey = "Cooking.Hood.Program.Automatic"
            break
        case "Venting":
            programKey = "Cooking.Hood.Program.Venting"
            break
        case "DelayedShutOff":
            programKey = "Cooking.Hood.Program.DelayedShutOff"
            break
        default:
            programKey = "Cooking.Hood.Program.${program}"
    }
    
    logInfo("Starting program: ${program}")
    recordCommand("startProgram", [program: program, key: programKey])
    parent?.startProgram(device, programKey)
}

def stopProgram() {
    logInfo("Stopping program")
    recordCommand("stopProgram")
    parent?.stopProgram(device)
}

def setLight(String state) {
    boolean lightOn = (state.toLowerCase() == "on")
    logInfo("Setting functional light: ${lightOn ? 'ON' : 'OFF'}")
    recordCommand("setLight", [state: state])
    parent?.setSetting(device, "Cooking.Common.Setting.Lighting", lightOn)
}

def setLightBrightness(BigDecimal brightness) {
    Integer level = brightness.toInteger()
    if (level < 0) level = 0
    if (level > 100) level = 100
    
    logInfo("Setting functional light brightness: ${level}%")
    recordCommand("setLightBrightness", [brightness: level])
    parent?.setSetting(device, "Cooking.Common.Setting.LightingBrightness", level)
}

def setAmbientLight(String state) {
    boolean lightOn = (state.toLowerCase() == "on")
    logInfo("Setting ambient light: ${lightOn ? 'ON' : 'OFF'}")
    recordCommand("setAmbientLight", [state: state])
    parent?.setSetting(device, "Cooking.Hood.Setting.AmbientLightEnabled", lightOn)
}

def setAmbientLightBrightness(BigDecimal brightness) {
    Integer level = brightness.toInteger()
    if (level < 0) level = 0
    if (level > 100) level = 100
    
    logInfo("Setting ambient light brightness: ${level}%")
    recordCommand("setAmbientLightBrightness", [brightness: level])
    parent?.setSetting(device, "Cooking.Hood.Setting.AmbientLightBrightness", level)
}

def setAmbientLightColor(String color) {
    logInfo("Setting ambient light color: ${color}")
    recordCommand("setAmbientLightColor", [color: color])
    def colorKey = "Cooking.Hood.EnumType.AmbientLightColor.${color}"
    parent?.setSetting(device, "Cooking.Hood.Setting.AmbientLightColor", colorKey)
}

def setDelayedShutOff(BigDecimal minutes) {
    Integer seconds = (minutes * 60).toInteger()
    logInfo("Setting delayed shut-off: ${minutes} minutes")
    recordCommand("setDelayedShutOff", [minutes: minutes])
    
    if (seconds > 0) {
        def options = [[key: "BSH.Common.Option.Duration", value: seconds, unit: "seconds"]]
        parent?.startProgram(device, "Cooking.Hood.Program.DelayedShutOff", options)
    } else {
        stopProgram()
    }
}

def setPower(String powerState) {
    boolean on = (powerState == "on")
    logInfo("Setting power: ${on ? 'ON' : 'OFF'}")
    recordCommand("setPower", [state: powerState])
    parent?.setPowerState(device, on)
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
    
    def programNames = list.collect { it.name ?: extractEnum(it.key) }
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
            sendEvent(name: "operationState", value: opState)
            updateDerivedState()
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

        case "Cooking.Hood.Setting.VentingLevel":
            def ventLevel = extractEnum(evt.value)
            sendEvent(name: "ventingLevel", value: ventLevel)
            
            if (VENTING_LEVELS.containsKey(ventLevel)) {
                def mapping = VENTING_LEVELS[ventLevel]
                sendEvent(name: "speed", value: mapping[0])
                sendEvent(name: "fanSpeed", value: mapping[0])
                sendEvent(name: "fanLevel", value: mapping[1])
                sendEvent(name: "switch", value: (mapping[1] > 0 ? "on" : "off"))
            }
            updateDerivedState()
            updateJsonState()
            break

        case "Cooking.Hood.Setting.IntensiveLevel":
            def intensiveLevel = extractEnum(evt.value)
            sendEvent(name: "intensiveLevel", value: intensiveLevel)
            if (intensiveLevel != "IntensiveStageOff") {
                sendEvent(name: "fanSpeed", value: "intensive")
                sendEvent(name: "speed", value: "high")
                sendEvent(name: "fanLevel", value: 6)
                sendEvent(name: "switch", value: "on")
            }
            updateDerivedState()
            updateJsonState()
            break

        case "Cooking.Common.Setting.Lighting":
            def lightOn = evt.value.toString().toLowerCase() == "true"
            sendEvent(name: "functionalLightState", value: lightOn ? "On" : "Off")
            updateJsonState()
            break

        case "Cooking.Common.Setting.LightingBrightness":
            Integer brightness = evt.value as Integer
            sendEvent(name: "functionalLightBrightness", value: brightness)
            sendEvent(name: "level", value: brightness)
            break

        case "Cooking.Hood.Setting.AmbientLightEnabled":
            def lightOn = evt.value.toString().toLowerCase() == "true"
            sendEvent(name: "ambientLightState", value: lightOn ? "On" : "Off")
            break

        case "Cooking.Hood.Setting.AmbientLightBrightness":
            Integer brightness = evt.value as Integer
            sendEvent(name: "ambientLightBrightness", value: brightness)
            break

        case "Cooking.Hood.Setting.AmbientLightColor":
            def color = extractEnum(evt.value)
            sendEvent(name: "ambientLightColor", value: color)
            break

        case "BSH.Common.Root.ActiveProgram":
            sendEvent(name: "activeProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            updateJsonState()
            break

        case "BSH.Common.Root.SelectedProgram":
            sendEvent(name: "selectedProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            break

        case "BSH.Common.Option.RemainingProgramTime":
            Integer sec = evt.value as Integer
            sendEvent(name: "delayedShutOffRemaining", value: sec)
            sendEvent(name: "delayedShutOffRemainingFormatted", value: secondsToTime(sec))
            break

        case ~/Cooking\.Hood\.Setting\..*/:
            def attr = evt.key.split("\\.").last()
            sendEvent(name: attr, value: evt.value.toString())
            logDebug("Hood setting: ${attr} = ${evt.value}")
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
        Integer fanLevel = device.currentValue("fanLevel") as Integer
        String lightState = device.currentValue("functionalLightState")
        
        String friendly = "Off"
        if (fanLevel && fanLevel > 0) {
            if (fanLevel >= 6) {
                friendly = "Intensive"
            } else if (fanLevel >= 5) {
                friendly = "High"
            } else if (fanLevel >= 3) {
                friendly = "Medium"
            } else {
                friendly = "Low"
            }
        } else if (lightState == "On") {
            friendly = "Light Only"
        }
        
        sendEvent(name: "friendlyStatus", value: friendly)
        
    } catch (Exception e) {
        logWarn("Error updating derived state: ${e.message}")
    }
}

// =============================================================================
// JSON STATE
// =============================================================================

private void updateJsonState() {
    try {
        def stateMap = [
            operationState: device.currentValue("operationState"),
            powerState: device.currentValue("powerState"),
            friendlyStatus: device.currentValue("friendlyStatus"),
            fanSpeed: device.currentValue("fanSpeed"),
            fanLevel: device.currentValue("fanLevel"),
            ventingLevel: device.currentValue("ventingLevel"),
            intensiveLevel: device.currentValue("intensiveLevel"),
            functionalLightState: device.currentValue("functionalLightState"),
            functionalLightBrightness: device.currentValue("functionalLightBrightness"),
            ambientLightState: device.currentValue("ambientLightState"),
            ambientLightBrightness: device.currentValue("ambientLightBrightness"),
            ambientLightColor: device.currentValue("ambientLightColor"),
            activeProgram: device.currentValue("activeProgram"),
            delayedShutOffRemaining: device.currentValue("delayedShutOffRemaining"),
            remoteControlStartAllowed: device.currentValue("remoteControlStartAllowed"),
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
    long minutes = sec / 60
    long seconds = sec % 60
    return String.format("%02d:%02d", minutes, seconds)
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
