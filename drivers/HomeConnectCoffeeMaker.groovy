/**
 *  Home Connect CoffeeMaker v3 Driver
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
 *  This driver represents a Home Connect coffee maker appliance. It receives real-time events from the
 *  Stream Driver (via the parent app) and provides commands to control the coffee maker.
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
 *  3.0.0  2026-01-14  Initial v3 architecture with parseEvent() pattern
 *  3.0.1  2026-01-14  Enhanced debugging for remote troubleshooting
 */

import groovy.json.JsonSlurper
import groovy.transform.Field

metadata {
    definition(name: "Home Connect CoffeeMaker v3", namespace: "craigde", author: "Craig Dewar") {

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
        
        command "makeCoffee", [
            [name: "Beverage", type: "ENUM", constraints: [
                "Espresso", "EspressoMacchiato", "Coffee", "Cappuccino",
                "LatteMacchiato", "CaffeLatte", "MilkFroth", "WarmMilk",
                "Americano", "EspressoDoppio", "FlatWhite", "Cortado",
                "CaffeGrandeCrema", "RistrettoMacchiato"
            ], description: "Select beverage to make"],
            [name: "Bean Amount", type: "ENUM", constraints: [
                "VeryMild", "Mild", "Normal", "Strong", "VeryStrong", 
                "DoubleShot", "DoubleShotPlus", "DoubleShotPlusPlus", "TripleShot"
            ], description: "Coffee strength (optional)"],
            [name: "Fill Quantity (ml)", type: "NUMBER", description: "Amount in ml (optional)"]
        ]
        
        command "startProgram", [
            [name: "Program", type: "ENUM", constraints: [
                "Espresso", "EspressoMacchiato", "Coffee", "Cappuccino",
                "LatteMacchiato", "CaffeLatte", "MilkFroth", "WarmMilk",
                "Americano", "EspressoDoppio", "FlatWhite", "Cortado",
                "Rinse", "Clean", "Descale"
            ], description: "Leave empty to use last program"]
        ]
        
        command "startProgramByKey", [
            [name: "Program Key*", type: "STRING", description: "e.g., ConsumerProducts.CoffeeMaker.Program.Beverage.Espresso"]
        ]
        
        command "startProgramWithOptions", [
            [name: "Program*", type: "STRING", description: "Program name or key"],
            [name: "Bean Amount", type: "ENUM", constraints: [
                "VeryMild", "Mild", "Normal", "Strong", "VeryStrong", 
                "DoubleShot", "DoubleShotPlus", "DoubleShotPlusPlus", "TripleShot"
            ]],
            [name: "Fill Quantity (ml)", type: "NUMBER"],
            [name: "Coffee Temperature", type: "ENUM", constraints: ["Normal", "High", "VeryHigh"]]
        ]
        
        command "stopProgram"
        
        command "setPower", [
            [name: "State*", type: "ENUM", constraints: ["on", "off", "standby"]]
        ]
        
        command "setProgramOption", [
            [name: "Option Key*", type: "STRING"],
            [name: "Value*", type: "STRING"]
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

        // =====================================================================
        // ATTRIBUTES - Timing
        // =====================================================================
        
        attribute "remainingProgramTime", "number"
        attribute "remainingProgramTimeFormatted", "string"
        attribute "elapsedProgramTime", "number"
        attribute "elapsedProgramTimeFormatted", "string"

        // =====================================================================
        // ATTRIBUTES - Control State
        // =====================================================================
        
        attribute "remoteControlStartAllowed", "string"
        attribute "remoteControlActive", "string"
        attribute "localControlActive", "string"

        // =====================================================================
        // ATTRIBUTES - CoffeeMaker Options
        // =====================================================================
        
        attribute "BeanAmount", "string"
        attribute "FillQuantity", "number"
        attribute "CoffeeTemperature", "string"
        attribute "BeanContainerSelection", "string"
        attribute "FlowRate", "string"
        attribute "Aroma", "string"
        attribute "HotWaterTemperature", "string"

        // =====================================================================
        // ATTRIBUTES - CoffeeMaker Counters
        // =====================================================================
        
        attribute "beverageCounterCoffee", "number"
        attribute "beverageCounterPowderCoffee", "number"
        attribute "beverageCounterHotWater", "number"
        attribute "beverageCounterHotMilk", "number"
        attribute "beverageCounterFrothedMilk", "number"
        attribute "beverageCounterMilkCoffee", "number"
        attribute "beverageCounterCoffeeAndMilk", "number"

        // =====================================================================
        // ATTRIBUTES - CoffeeMaker Events & Alerts
        // =====================================================================
        
        attribute "BeanContainerEmpty", "string"
        attribute "WaterTankEmpty", "string"
        attribute "DripTrayFull", "string"
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
        attribute "lastBeverage", "string"
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
        input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false,
              description: "Enable detailed logging for troubleshooting"
        input name: "traceLogging", type: "bool", title: "Enable trace logging", defaultValue: false,
              description: "Enable verbose trace logging (very detailed)"
        input name: "logRawEvents", type: "bool", title: "Log raw events", defaultValue: false,
              description: "Log complete raw event data before parsing"
        input name: "maxRecentEvents", type: "number", title: "Recent events to keep", defaultValue: 20,
              description: "Number of recent events to store (0 to disable)"
        input name: "defaultBeanAmount", type: "enum", title: "Default Bean Amount",
              options: ["VeryMild", "Mild", "Normal", "Strong", "VeryStrong", "DoubleShot"],
              defaultValue: "Normal"
        input name: "defaultFillQuantity", type: "number", title: "Default Fill Quantity (ml)",
              defaultValue: 120
    }
}

// =============================================================================
// CONSTANTS
// =============================================================================

@Field static final String DRIVER_VERSION = "3.0.1"
@Field static final Integer MAX_DISCOVERED_KEYS = 100

@Field static final Map BEVERAGE_PROGRAMS = [
    "Espresso": "ConsumerProducts.CoffeeMaker.Program.Beverage.Espresso",
    "EspressoMacchiato": "ConsumerProducts.CoffeeMaker.Program.Beverage.EspressoMacchiato",
    "Coffee": "ConsumerProducts.CoffeeMaker.Program.Beverage.Coffee",
    "Cappuccino": "ConsumerProducts.CoffeeMaker.Program.Beverage.Cappuccino",
    "LatteMacchiato": "ConsumerProducts.CoffeeMaker.Program.Beverage.LatteMacchiato",
    "CaffeLatte": "ConsumerProducts.CoffeeMaker.Program.Beverage.CaffeLatte",
    "MilkFroth": "ConsumerProducts.CoffeeMaker.Program.Beverage.MilkFroth",
    "WarmMilk": "ConsumerProducts.CoffeeMaker.Program.Beverage.WarmMilk",
    "Americano": "ConsumerProducts.CoffeeMaker.Program.Beverage.Americano",
    "EspressoDoppio": "ConsumerProducts.CoffeeMaker.Program.Beverage.EspressoDoppio",
    "FlatWhite": "ConsumerProducts.CoffeeMaker.Program.Beverage.FlatWhite",
    "Cortado": "ConsumerProducts.CoffeeMaker.Program.Beverage.Cortado",
    "CaffeGrandeCrema": "ConsumerProducts.CoffeeMaker.Program.Beverage.CaffeGrandeCrema",
    "RistrettoMacchiato": "ConsumerProducts.CoffeeMaker.Program.Beverage.RistrettoMacchiato",
    "Rinse": "ConsumerProducts.CoffeeMaker.Program.CoffeeWorld.Rinse",
    "Clean": "ConsumerProducts.CoffeeMaker.Program.Cleaning.Clean",
    "Descale": "ConsumerProducts.CoffeeMaker.Program.Cleaning.Descale"
]

@Field static final Map BEAN_AMOUNTS = [
    "VeryMild": "ConsumerProducts.CoffeeMaker.EnumType.BeanAmount.VeryMild",
    "Mild": "ConsumerProducts.CoffeeMaker.EnumType.BeanAmount.Mild",
    "Normal": "ConsumerProducts.CoffeeMaker.EnumType.BeanAmount.Normal",
    "Strong": "ConsumerProducts.CoffeeMaker.EnumType.BeanAmount.Strong",
    "VeryStrong": "ConsumerProducts.CoffeeMaker.EnumType.BeanAmount.VeryStrong",
    "DoubleShot": "ConsumerProducts.CoffeeMaker.EnumType.BeanAmount.DoubleShot",
    "DoubleShotPlus": "ConsumerProducts.CoffeeMaker.EnumType.BeanAmount.DoubleShotPlus",
    "DoubleShotPlusPlus": "ConsumerProducts.CoffeeMaker.EnumType.BeanAmount.DoubleShotPlusPlus",
    "TripleShot": "ConsumerProducts.CoffeeMaker.EnumType.BeanAmount.TripleShot"
]

@Field static final Map COFFEE_TEMPS = [
    "Normal": "ConsumerProducts.CoffeeMaker.EnumType.CoffeeTemperature.Normal",
    "High": "ConsumerProducts.CoffeeMaker.EnumType.CoffeeTemperature.High",
    "VeryHigh": "ConsumerProducts.CoffeeMaker.EnumType.CoffeeTemperature.VeryHigh"
]

// =============================================================================
// LIFECYCLE METHODS
// =============================================================================

def installed() {
    log.info "${device.displayName}: Installed"
    initializeState()
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "eventPresentState", value: "Off")
    sendEvent(name: "numberOfButtons", value: 4)
    sendEvent(name: "beverageCounterCoffee", value: 0)
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
    logInfo("  defaultBeanAmount: ${defaultBeanAmount}")
    logInfo("  defaultFillQuantity: ${defaultFillQuantity}")
    
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
    def beverage = getDefaultProgram()
    logInfo("Starting default beverage: ${beverage}")
    startProgram(beverage)
}

def makeCoffee(String beverage = null, String beanAmount = null, BigDecimal fillQuantity = null) {
    def selectedBeverage = beverage ?: getDefaultProgram()
    def programKey = buildProgramKey(selectedBeverage)
    
    def options = []
    
    def strength = beanAmount ?: settings?.defaultBeanAmount ?: "Normal"
    if (BEAN_AMOUNTS.containsKey(strength)) {
        options << [key: "ConsumerProducts.CoffeeMaker.Option.BeanAmount", value: BEAN_AMOUNTS[strength]]
    }
    
    def quantity = fillQuantity?.toInteger() ?: settings?.defaultFillQuantity?.toInteger() ?: 120
    if (quantity > 0) {
        options << [key: "ConsumerProducts.CoffeeMaker.Option.FillQuantity", value: quantity, unit: "ml"]
    }
    
    saveLastProgram(selectedBeverage)
    
    logInfo("Making ${selectedBeverage}: strength=${strength}, quantity=${quantity}ml")
    recordCommand("makeCoffee", [beverage: selectedBeverage, strength: strength, quantity: quantity])
    
    if (options.size() > 0) {
        parent?.startProgram(device, programKey, options)
    } else {
        parent?.startProgram(device, programKey)
    }
}

def startProgram(String program = null) {
    def selectedProgram = program ?: getDefaultProgram()
    def programKey = buildProgramKey(selectedProgram)
    
    saveLastProgram(selectedProgram)
    
    logInfo("Starting program: ${selectedProgram} (${programKey})")
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

def startProgramWithOptions(String program, String beanAmount = null, BigDecimal fillQuantity = null, String coffeeTemp = null) {
    def programKey = buildProgramKey(program)
    
    def options = []
    
    if (beanAmount && BEAN_AMOUNTS.containsKey(beanAmount)) {
        options << [key: "ConsumerProducts.CoffeeMaker.Option.BeanAmount", value: BEAN_AMOUNTS[beanAmount]]
    }
    
    if (fillQuantity && fillQuantity > 0) {
        options << [key: "ConsumerProducts.CoffeeMaker.Option.FillQuantity", value: fillQuantity.toInteger(), unit: "ml"]
    }
    
    if (coffeeTemp && COFFEE_TEMPS.containsKey(coffeeTemp)) {
        options << [key: "ConsumerProducts.CoffeeMaker.Option.CoffeeTemperature", value: COFFEE_TEMPS[coffeeTemp]]
    }
    
    saveLastProgram(program)
    
    logInfo("Starting ${program} with ${options.size()} options")
    recordCommand("startProgramWithOptions", [program: program, options: options])
    
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

def setPower(String powerState) {
    def pState = powerState.toLowerCase()
    logInfo("Setting power: ${pState}")
    recordCommand("setPower", [state: pState])
    
    if (pState == "standby") {
        parent?.setPowerState(device, "Standby")
    } else {
        parent?.setPowerState(device, pState == "on")
    }
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

private String buildProgramKey(String program) {
    if (program?.contains(".")) {
        return program
    }
    
    if (BEVERAGE_PROGRAMS.containsKey(program)) {
        return BEVERAGE_PROGRAMS[program]
    }
    
    if (state.programMap?.containsKey(program)) {
        return state.programMap[program]
    }
    
    return "ConsumerProducts.CoffeeMaker.Program.Beverage.${program}"
}

private String getDefaultProgram() {
    def lastBeverage = device.currentValue("lastBeverage")
    if (lastBeverage) return lastBeverage
    
    def lastProgram = device.currentValue("lastProgram")
    if (lastProgram) return lastProgram
    
    if (state.programNames?.contains("Espresso")) return "Espresso"
    if (state.programNames?.size() > 0) return state.programNames[0]
    
    return "Espresso"
}

private void saveLastProgram(String program) {
    if (program) {
        sendEvent(name: "lastProgram", value: program)
        if (BEVERAGE_PROGRAMS.containsKey(program) || program.contains("Beverage")) {
            sendEvent(name: "lastBeverage", value: program)
        }
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
    
    // Log raw event if enabled
    if (settings?.logRawEvents) {
        log.debug "${device.displayName}: RAW EVENT: ${evt}"
    }
    
    // Record event for debugging
    recordEvent(evt)
    
    logDebug("Event: ${evt.key} = ${evt.value}")
    logTrace("Event details: key=${evt.key}, value=${evt.value}, displayvalue=${evt.displayvalue}, unit=${evt.unit}")

    switch (evt.key) {

        case "BSH.Common.Status.OperationState":
            def opState = extractEnum(evt.value)
            def previousState = device.currentValue("operationState")
            sendEvent(name: "operationState", value: opState)
            
            if ((opState == "Finished" || opState == "Ready") && previousState == "Run") {
                logInfo("Brew complete - pushing button 1")
                sendEvent(name: "pushed", value: 1, isStateChange: true, descriptionText: "Brew complete")
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
            updateJsonState()
            break

        case "BSH.Common.Status.RemoteControlActive":
            sendEvent(name: "remoteControlActive", value: evt.value.toString())
            updateJsonState()
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

        case "BSH.Common.Option.RemainingProgramTime":
            Integer sec = evt.value as Integer
            sendEvent(name: "remainingProgramTime", value: sec)
            sendEvent(name: "remainingProgramTimeFormatted", value: secondsToTime(sec))
            updateJsonState()
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

        case "BSH.Common.Root.ActiveProgram":
            sendEvent(name: "activeProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            updateJsonState()
            break

        case "BSH.Common.Root.SelectedProgram":
            sendEvent(name: "selectedProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            updateJsonState()
            break

        case "ConsumerProducts.CoffeeMaker.Option.BeanAmount":
            sendEvent(name: "BeanAmount", value: extractEnum(evt.value))
            break

        case "ConsumerProducts.CoffeeMaker.Option.FillQuantity":
            sendEvent(name: "FillQuantity", value: evt.value as Integer)
            break

        case "ConsumerProducts.CoffeeMaker.Option.CoffeeTemperature":
            sendEvent(name: "CoffeeTemperature", value: extractEnum(evt.value))
            break

        case "ConsumerProducts.CoffeeMaker.Option.BeanContainerSelection":
            sendEvent(name: "BeanContainerSelection", value: extractEnum(evt.value))
            break

        case "ConsumerProducts.CoffeeMaker.Option.FlowRate":
            sendEvent(name: "FlowRate", value: extractEnum(evt.value))
            break

        case "ConsumerProducts.CoffeeMaker.Option.Aroma":
            sendEvent(name: "Aroma", value: extractEnum(evt.value))
            break

        case "ConsumerProducts.CoffeeMaker.Option.HotWaterTemperature":
            sendEvent(name: "HotWaterTemperature", value: extractEnum(evt.value))
            break

        case "ConsumerProducts.CoffeeMaker.Status.BeverageCounterCoffee":
            sendEvent(name: "beverageCounterCoffee", value: evt.value as Integer)
            break

        case "ConsumerProducts.CoffeeMaker.Status.BeverageCounterPowderCoffee":
            sendEvent(name: "beverageCounterPowderCoffee", value: evt.value as Integer)
            break

        case "ConsumerProducts.CoffeeMaker.Status.BeverageCounterHotWater":
            sendEvent(name: "beverageCounterHotWater", value: evt.value as Integer)
            break

        case "ConsumerProducts.CoffeeMaker.Status.BeverageCounterHotMilk":
            sendEvent(name: "beverageCounterHotMilk", value: evt.value as Integer)
            break

        case "ConsumerProducts.CoffeeMaker.Status.BeverageCounterFrothedMilk":
            sendEvent(name: "beverageCounterFrothedMilk", value: evt.value as Integer)
            break

        case "ConsumerProducts.CoffeeMaker.Status.BeverageCounterMilkCoffee":
            sendEvent(name: "beverageCounterMilkCoffee", value: evt.value as Integer)
            break

        case "ConsumerProducts.CoffeeMaker.Status.BeverageCounterCoffeeAndMilk":
            sendEvent(name: "beverageCounterCoffeeAndMilk", value: evt.value as Integer)
            break

        case "ConsumerProducts.CoffeeMaker.Event.BeanContainerEmpty":
            def value = extractEnum(evt.value)
            sendEvent(name: "BeanContainerEmpty", value: value)
            if (value == "Present") {
                logInfo("Bean container empty - pushing button 2")
                sendEvent(name: "pushed", value: 2, isStateChange: true, descriptionText: "Bean container empty")
                sendAlert("BeanContainerEmpty", "Bean container is empty - please refill")
            }
            updateJsonState()
            break

        case "ConsumerProducts.CoffeeMaker.Event.WaterTankEmpty":
            def value = extractEnum(evt.value)
            sendEvent(name: "WaterTankEmpty", value: value)
            if (value == "Present") {
                logInfo("Water tank empty - pushing button 3")
                sendEvent(name: "pushed", value: 3, isStateChange: true, descriptionText: "Water tank empty")
                sendAlert("WaterTankEmpty", "Water tank is empty - please refill")
            }
            updateJsonState()
            break

        case "ConsumerProducts.CoffeeMaker.Event.DripTrayFull":
            def value = extractEnum(evt.value)
            sendEvent(name: "DripTrayFull", value: value)
            if (value == "Present") {
                logInfo("Drip tray full - pushing button 4")
                sendEvent(name: "pushed", value: 4, isStateChange: true, descriptionText: "Drip tray full")
                sendAlert("DripTrayFull", "Drip tray is full - please empty")
            }
            updateJsonState()
            break

        case ~/ConsumerProducts\.CoffeeMaker\.Option\..*/:
            def attr = evt.key.split("\\.").last()
            sendEvent(name: attr, value: evt.value.toString())
            logDebug("CoffeeMaker option: ${attr} = ${evt.value}")
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
    switch (opState) {
        case "Ready":
        case "Inactive":
            return "Ready"
        case "Run":
            def activeProgram = device.currentValue("activeProgram")
            return activeProgram ? "Brewing ${activeProgram}" : "Brewing"
        case "Pause":
            return "Paused"
        case "Finished":
            return "Ready"
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
            selectedProgram: device.currentValue("selectedProgram"),
            programProgress: device.currentValue("programProgress"),
            remainingProgramTime: device.currentValue("remainingProgramTime"),
            remainingProgramTimeFormatted: device.currentValue("remainingProgramTimeFormatted"),
            beanAmount: device.currentValue("BeanAmount"),
            fillQuantity: device.currentValue("FillQuantity"),
            coffeeTemperature: device.currentValue("CoffeeTemperature"),
            beverageCounterCoffee: device.currentValue("beverageCounterCoffee"),
            remoteControlStartAllowed: device.currentValue("remoteControlStartAllowed"),
            remoteControlActive: device.currentValue("remoteControlActive"),
            beanContainerEmpty: device.currentValue("BeanContainerEmpty") == "Present",
            waterTankEmpty: device.currentValue("WaterTankEmpty") == "Present",
            dripTrayFull: device.currentValue("DripTrayFull") == "Present",
            lastAlert: device.currentValue("lastAlert"),
            lastBeverage: device.currentValue("lastBeverage"),
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
