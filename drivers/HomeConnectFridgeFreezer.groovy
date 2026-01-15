/**
 *  Home Connect FridgeFreezer v3 Driver
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
 *  This driver represents a Home Connect refrigerator/freezer appliance (Bosch, Thermador, etc.).
 *  
 *  Supported Features:
 *  - Temperature monitoring and setpoint control for fridge and freezer compartments
 *  - Door state monitoring (fridge door, freezer door, flex zone door)
 *  - Super modes (SuperCooling, SuperFreezing)
 *  - Eco mode, Sabbath mode, Vacation mode
 *  - Ice dispenser control (if equipped)
 *  - Door open alarms
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
    definition(name: "Home Connect FridgeFreezer v3", namespace: "craigde", author: "Craig Dewar") {

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
        
        command "setFridgeTemperature", [
            [name: "Temperature*", type: "NUMBER", description: "Temperature in °F or °C"]
        ]
        
        command "setFreezerTemperature", [
            [name: "Temperature*", type: "NUMBER", description: "Temperature in °F or °C"]
        ]
        
        command "setSuperCooling", [
            [name: "State*", type: "ENUM", constraints: ["on", "off"]]
        ]
        
        command "setSuperFreezing", [
            [name: "State*", type: "ENUM", constraints: ["on", "off"]]
        ]
        
        command "setEcoMode", [
            [name: "State*", type: "ENUM", constraints: ["on", "off"]]
        ]
        
        command "setSabbathMode", [
            [name: "State*", type: "ENUM", constraints: ["on", "off"]]
        ]
        
        command "setVacationMode", [
            [name: "State*", type: "ENUM", constraints: ["on", "off"]]
        ]
        
        command "setIceDispenser", [
            [name: "State*", type: "ENUM", constraints: ["on", "off"]]
        ]
        
        command "setDispenserMode", [
            [name: "Mode*", type: "ENUM", constraints: ["Off", "Water", "CrushedIce", "CubedIce"]]
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
        // ATTRIBUTES - Fridge Compartment
        // =====================================================================
        
        attribute "fridgeTemperature", "number"
        attribute "fridgeTargetTemperature", "number"
        attribute "fridgeDoorState", "string"

        // =====================================================================
        // ATTRIBUTES - Freezer Compartment
        // =====================================================================
        
        attribute "freezerTemperature", "number"
        attribute "freezerTargetTemperature", "number"
        attribute "freezerDoorState", "string"

        // =====================================================================
        // ATTRIBUTES - Additional Compartments
        // =====================================================================
        
        attribute "flexZoneTemperature", "number"
        attribute "flexZoneTargetTemperature", "number"
        attribute "flexZoneDoorState", "string"
        attribute "bottleCoolerTemperature", "number"
        attribute "chillZoneTemperature", "number"

        // =====================================================================
        // ATTRIBUTES - Modes
        // =====================================================================
        
        attribute "superCooling", "string"
        attribute "superFreezing", "string"
        attribute "ecoMode", "string"
        attribute "sabbathMode", "string"
        attribute "vacationMode", "string"
        attribute "freshMode", "string"

        // =====================================================================
        // ATTRIBUTES - Dispenser
        // =====================================================================
        
        attribute "dispenserEnabled", "string"
        attribute "dispenserMode", "string"
        attribute "waterFilterStatus", "string"

        // =====================================================================
        // ATTRIBUTES - Door Status
        // =====================================================================
        
        attribute "doorState", "string"
        attribute "anyDoorOpen", "string"

        // =====================================================================
        // ATTRIBUTES - Alarms
        // =====================================================================
        
        attribute "doorAlarm", "string"
        attribute "temperatureAlarm", "string"
        attribute "lastAlert", "string"
        attribute "lastAlertTime", "string"

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
        attribute "temperatureUnit", "string"

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
        input name: "primaryCompartment", type: "enum", title: "Primary Compartment for Temperature",
              options: ["Fridge", "Freezer"], defaultValue: "Fridge",
              description: "Which compartment temperature to show as main 'temperature' attribute"
    }
}

// =============================================================================
// CONSTANTS
// =============================================================================

@Field static final String DRIVER_VERSION = "3.0.1"
@Field static final Integer MAX_DISCOVERED_KEYS = 100

// =============================================================================
// LIFECYCLE METHODS
// =============================================================================

def installed() {
    log.info "${device.displayName}: Installed"
    initializeState()
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "eventPresentState", value: "Off")
    sendEvent(name: "temperatureUnit", value: settings?.temperatureUnit ?: "F")
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "anyDoorOpen", value: "false")
    
    // Button 1: Door left open alarm
    // Button 2: Temperature alarm
    // Button 3: Super mode completed
    // Button 4: Filter needs replacement
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
}

def initialize() {
    logInfo("Initializing")
    initializeState()
    parent?.initializeStatus(device)
}

def refresh() {
    logInfo("Refreshing")
    parent?.initializeStatus(device)
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
    logInfo("--- Compartment Temperatures ---")
    logInfo("  Fridge: ${device.currentValue('fridgeTemperature')}° (target: ${device.currentValue('fridgeTargetTemperature')}°)")
    logInfo("  Freezer: ${device.currentValue('freezerTemperature')}° (target: ${device.currentValue('freezerTargetTemperature')}°)")
    
    logInfo("")
    logInfo("--- Door States ---")
    logInfo("  Fridge Door: ${device.currentValue('fridgeDoorState')}")
    logInfo("  Freezer Door: ${device.currentValue('freezerDoorState')}")
    logInfo("  Any Door Open: ${device.currentValue('anyDoorOpen')}")
    
    logInfo("")
    logInfo("--- Modes ---")
    logInfo("  SuperCooling: ${device.currentValue('superCooling')}")
    logInfo("  SuperFreezing: ${device.currentValue('superFreezing')}")
    logInfo("  EcoMode: ${device.currentValue('ecoMode')}")
    logInfo("  SabbathMode: ${device.currentValue('sabbathMode')}")
    logInfo("  VacationMode: ${device.currentValue('vacationMode')}")
    
    logInfo("")
    logInfo("--- State Variables ---")
    logInfo("  discoveredKeys count: ${state.discoveredKeys?.size() ?: 0}")
    logInfo("  recentEvents count: ${state.recentEvents?.size() ?: 0}")
    
    logInfo("")
    logInfo("--- Settings ---")
    logInfo("  debugLogging: ${debugLogging}")
    logInfo("  traceLogging: ${traceLogging}")
    logInfo("  temperatureUnit: ${temperatureUnit}")
    logInfo("  primaryCompartment: ${primaryCompartment}")
    
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

def setFridgeTemperature(BigDecimal temperature) {
    def tempUnit = settings?.temperatureUnit ?: "F"
    Integer temp = temperature.toInteger()
    Integer tempC = (tempUnit == "F") ? fahrenheitToCelsius(temp) : temp
    
    logInfo("Setting fridge temperature to ${temp}°${tempUnit}")
    recordCommand("setFridgeTemperature", [temp: temp, unit: tempUnit])
    parent?.setSetting(device, "Refrigeration.FridgeFreezer.Setting.SetpointTemperatureRefrigerator", tempC)
}

def setFreezerTemperature(BigDecimal temperature) {
    def tempUnit = settings?.temperatureUnit ?: "F"
    Integer temp = temperature.toInteger()
    Integer tempC = (tempUnit == "F") ? fahrenheitToCelsius(temp) : temp
    
    logInfo("Setting freezer temperature to ${temp}°${tempUnit}")
    recordCommand("setFreezerTemperature", [temp: temp, unit: tempUnit])
    parent?.setSetting(device, "Refrigeration.FridgeFreezer.Setting.SetpointTemperatureFreezer", tempC)
}

def setSuperCooling(String state) {
    boolean on = (state.toLowerCase() == "on")
    logInfo("Setting SuperCooling: ${on ? 'ON' : 'OFF'}")
    recordCommand("setSuperCooling", [state: state])
    parent?.setSetting(device, "Refrigeration.FridgeFreezer.Setting.SuperModeRefrigerator", on)
}

def setSuperFreezing(String state) {
    boolean on = (state.toLowerCase() == "on")
    logInfo("Setting SuperFreezing: ${on ? 'ON' : 'OFF'}")
    recordCommand("setSuperFreezing", [state: state])
    parent?.setSetting(device, "Refrigeration.FridgeFreezer.Setting.SuperModeFreezer", on)
}

def setEcoMode(String state) {
    boolean on = (state.toLowerCase() == "on")
    logInfo("Setting EcoMode: ${on ? 'ON' : 'OFF'}")
    recordCommand("setEcoMode", [state: state])
    parent?.setSetting(device, "Refrigeration.FridgeFreezer.Setting.EcoMode", on)
}

def setSabbathMode(String state) {
    boolean on = (state.toLowerCase() == "on")
    logInfo("Setting SabbathMode: ${on ? 'ON' : 'OFF'}")
    recordCommand("setSabbathMode", [state: state])
    parent?.setSetting(device, "Refrigeration.FridgeFreezer.Setting.SabbathMode", on)
}

def setVacationMode(String state) {
    boolean on = (state.toLowerCase() == "on")
    logInfo("Setting VacationMode: ${on ? 'ON' : 'OFF'}")
    recordCommand("setVacationMode", [state: state])
    parent?.setSetting(device, "Refrigeration.FridgeFreezer.Setting.VacationMode", on)
}

def setIceDispenser(String state) {
    boolean on = (state.toLowerCase() == "on")
    logInfo("Setting IceDispenser: ${on ? 'ON' : 'OFF'}")
    recordCommand("setIceDispenser", [state: state])
    parent?.setSetting(device, "Refrigeration.FridgeFreezer.Setting.IceDispenserEnabled", on)
}

def setDispenserMode(String mode) {
    logInfo("Setting Dispenser Mode: ${mode}")
    recordCommand("setDispenserMode", [mode: mode])
    def modeKey = "Refrigeration.FridgeFreezer.EnumType.DispenserMode.${mode}"
    parent?.setSetting(device, "Refrigeration.FridgeFreezer.Setting.DispenserMode", modeKey)
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
    logDebug("Parsing available programs (fridges typically have no programs)")
}

def z_parseAvailableOptions(String json) {
    logDebug("Parsing available options")
}

def z_parseActiveProgram(String json) {
    logDebug("Parsing active program")
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

        case "BSH.Common.Status.DoorState":
            def doorState = extractEnum(evt.value)
            sendEvent(name: "doorState", value: doorState)
            sendEvent(name: "contact", value: (doorState == "Open" ? "open" : "closed"))
            updateDoorStatus()
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

        // Fridge temperature
        case "Refrigeration.FridgeFreezer.Status.TemperatureRefrigerator":
            Integer tempC = evt.value as Integer
            Integer tempDisplay = convertTemperatureForDisplay(tempC)
            sendEvent(name: "fridgeTemperature", value: tempDisplay)
            updatePrimaryTemperature()
            updateJsonState()
            break

        case "Refrigeration.FridgeFreezer.Setting.SetpointTemperatureRefrigerator":
            Integer tempC = evt.value as Integer
            Integer tempDisplay = convertTemperatureForDisplay(tempC)
            sendEvent(name: "fridgeTargetTemperature", value: tempDisplay)
            updateJsonState()
            break

        // Freezer temperature
        case "Refrigeration.FridgeFreezer.Status.TemperatureFreezer":
            Integer tempC = evt.value as Integer
            Integer tempDisplay = convertTemperatureForDisplay(tempC)
            sendEvent(name: "freezerTemperature", value: tempDisplay)
            updatePrimaryTemperature()
            updateJsonState()
            break

        case "Refrigeration.FridgeFreezer.Setting.SetpointTemperatureFreezer":
            Integer tempC = evt.value as Integer
            Integer tempDisplay = convertTemperatureForDisplay(tempC)
            sendEvent(name: "freezerTargetTemperature", value: tempDisplay)
            updateJsonState()
            break

        // Door states
        case "Refrigeration.Common.Status.Door.Refrigerator":
            def doorState = extractEnum(evt.value)
            sendEvent(name: "fridgeDoorState", value: doorState)
            updateDoorStatus()
            updateJsonState()
            break

        case "Refrigeration.Common.Status.Door.Freezer":
            def doorState = extractEnum(evt.value)
            sendEvent(name: "freezerDoorState", value: doorState)
            updateDoorStatus()
            updateJsonState()
            break

        case "Refrigeration.Common.Status.Door.FlexZone":
            def doorState = extractEnum(evt.value)
            sendEvent(name: "flexZoneDoorState", value: doorState)
            updateDoorStatus()
            break

        // Super modes
        case "Refrigeration.FridgeFreezer.Setting.SuperModeRefrigerator":
            def on = evt.value.toString().toLowerCase() == "true"
            def prevState = device.currentValue("superCooling")
            sendEvent(name: "superCooling", value: on ? "On" : "Off")
            if (!on && prevState == "On") {
                logInfo("SuperCooling completed - pushing button 3")
                sendEvent(name: "pushed", value: 3, isStateChange: true, descriptionText: "SuperCooling completed")
            }
            updateJsonState()
            break

        case "Refrigeration.FridgeFreezer.Setting.SuperModeFreezer":
            def on = evt.value.toString().toLowerCase() == "true"
            def prevState = device.currentValue("superFreezing")
            sendEvent(name: "superFreezing", value: on ? "On" : "Off")
            if (!on && prevState == "On") {
                logInfo("SuperFreezing completed - pushing button 3")
                sendEvent(name: "pushed", value: 3, isStateChange: true, descriptionText: "SuperFreezing completed")
            }
            updateJsonState()
            break

        // Other modes
        case "Refrigeration.FridgeFreezer.Setting.EcoMode":
            def on = evt.value.toString().toLowerCase() == "true"
            sendEvent(name: "ecoMode", value: on ? "On" : "Off")
            break

        case "Refrigeration.FridgeFreezer.Setting.SabbathMode":
            def on = evt.value.toString().toLowerCase() == "true"
            sendEvent(name: "sabbathMode", value: on ? "On" : "Off")
            break

        case "Refrigeration.FridgeFreezer.Setting.VacationMode":
            def on = evt.value.toString().toLowerCase() == "true"
            sendEvent(name: "vacationMode", value: on ? "On" : "Off")
            break

        case "Refrigeration.FridgeFreezer.Setting.FreshMode":
            def on = evt.value.toString().toLowerCase() == "true"
            sendEvent(name: "freshMode", value: on ? "On" : "Off")
            break

        // Dispenser
        case "Refrigeration.FridgeFreezer.Setting.IceDispenserEnabled":
            def on = evt.value.toString().toLowerCase() == "true"
            sendEvent(name: "dispenserEnabled", value: on ? "On" : "Off")
            break

        case "Refrigeration.FridgeFreezer.Setting.DispenserMode":
            def mode = extractEnum(evt.value)
            sendEvent(name: "dispenserMode", value: mode)
            break

        // Water filter
        case "Refrigeration.FridgeFreezer.Status.WaterFilterStatus":
            def status = extractEnum(evt.value)
            sendEvent(name: "waterFilterStatus", value: status)
            if (status == "Replace") {
                logInfo("Water filter needs replacement - pushing button 4")
                sendEvent(name: "pushed", value: 4, isStateChange: true, descriptionText: "Water filter needs replacement")
                sendAlert("WaterFilter", "Water filter needs replacement")
            }
            break

        // Door alarm
        case "Refrigeration.FridgeFreezer.Event.DoorAlarmRefrigerator":
        case "Refrigeration.FridgeFreezer.Event.DoorAlarmFreezer":
            def value = extractEnum(evt.value)
            def compartment = evt.key.contains("Refrigerator") ? "Fridge" : "Freezer"
            sendEvent(name: "doorAlarm", value: value)
            if (value == "Present") {
                logInfo("${compartment} door alarm - pushing button 1")
                sendEvent(name: "pushed", value: 1, isStateChange: true, descriptionText: "${compartment} door left open")
                sendAlert("DoorAlarm", "${compartment} door has been left open")
            }
            break

        // Temperature alarm
        case "Refrigeration.FridgeFreezer.Event.TemperatureAlarmRefrigerator":
        case "Refrigeration.FridgeFreezer.Event.TemperatureAlarmFreezer":
            def value = extractEnum(evt.value)
            def compartment = evt.key.contains("Refrigerator") ? "Fridge" : "Freezer"
            sendEvent(name: "temperatureAlarm", value: value)
            if (value == "Present") {
                logInfo("${compartment} temperature alarm - pushing button 2")
                sendEvent(name: "pushed", value: 2, isStateChange: true, descriptionText: "${compartment} temperature warning")
                sendAlert("TemperatureAlarm", "${compartment} temperature is out of range")
            }
            break

        // Catch-all for other refrigeration settings/status
        case ~/Refrigeration\.FridgeFreezer\.Setting\..*/:
        case ~/Refrigeration\.FridgeFreezer\.Status\..*/:
            def attr = evt.key.split("\\.").last()
            logDebug("Fridge setting/status: ${attr} = ${evt.value}")
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

private void updatePrimaryTemperature() {
    def primary = settings?.primaryCompartment ?: "Fridge"
    def temp
    
    if (primary == "Fridge") {
        temp = device.currentValue("fridgeTemperature")
    } else {
        temp = device.currentValue("freezerTemperature")
    }
    
    if (temp != null) {
        sendEvent(name: "temperature", value: temp)
    }
}

private void updateDoorStatus() {
    def fridgeDoor = device.currentValue("fridgeDoorState")
    def freezerDoor = device.currentValue("freezerDoorState")
    def flexDoor = device.currentValue("flexZoneDoorState")
    def mainDoor = device.currentValue("doorState")
    
    def anyOpen = (fridgeDoor == "Open" || freezerDoor == "Open" || 
                   flexDoor == "Open" || mainDoor == "Open")
    
    sendEvent(name: "anyDoorOpen", value: anyOpen.toString())
    sendEvent(name: "contact", value: anyOpen ? "open" : "closed")
}

private void updateDerivedState() {
    try {
        def fridgeTemp = device.currentValue("fridgeTemperature")
        def freezerTemp = device.currentValue("freezerTemperature")
        def anyDoorOpen = device.currentValue("anyDoorOpen") == "true"
        def superCooling = device.currentValue("superCooling") == "On"
        def superFreezing = device.currentValue("superFreezing") == "On"
        def sabbathMode = device.currentValue("sabbathMode") == "On"
        def vacationMode = device.currentValue("vacationMode") == "On"
        
        String friendly
        if (sabbathMode) {
            friendly = "Sabbath Mode"
        } else if (vacationMode) {
            friendly = "Vacation Mode"
        } else if (anyDoorOpen) {
            friendly = "Door Open"
        } else if (superCooling && superFreezing) {
            friendly = "Super Cooling & Freezing"
        } else if (superCooling) {
            friendly = "Super Cooling"
        } else if (superFreezing) {
            friendly = "Super Freezing"
        } else {
            def unit = settings?.temperatureUnit ?: "F"
            friendly = "Fridge ${fridgeTemp ?: '--'}° / Freezer ${freezerTemp ?: '--'}°"
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
            powerState: device.currentValue("powerState"),
            friendlyStatus: device.currentValue("friendlyStatus"),
            fridge: [
                temperature: device.currentValue("fridgeTemperature"),
                targetTemperature: device.currentValue("fridgeTargetTemperature"),
                doorState: device.currentValue("fridgeDoorState")
            ],
            freezer: [
                temperature: device.currentValue("freezerTemperature"),
                targetTemperature: device.currentValue("freezerTargetTemperature"),
                doorState: device.currentValue("freezerDoorState")
            ],
            temperatureUnit: settings?.temperatureUnit ?: "F",
            anyDoorOpen: device.currentValue("anyDoorOpen") == "true",
            superCooling: device.currentValue("superCooling") == "On",
            superFreezing: device.currentValue("superFreezing") == "On",
            ecoMode: device.currentValue("ecoMode") == "On",
            sabbathMode: device.currentValue("sabbathMode") == "On",
            vacationMode: device.currentValue("vacationMode") == "On",
            dispenserEnabled: device.currentValue("dispenserEnabled") == "On",
            dispenserMode: device.currentValue("dispenserMode"),
            waterFilterStatus: device.currentValue("waterFilterStatus"),
            doorAlarm: device.currentValue("doorAlarm"),
            temperatureAlarm: device.currentValue("temperatureAlarm"),
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
