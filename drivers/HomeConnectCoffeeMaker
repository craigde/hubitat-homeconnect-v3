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
 *  3.0.0  2026-01-14  Initial v3 architecture with parseEvent() pattern
 */

import groovy.json.JsonSlurper
import groovy.transform.Field

metadata {
    definition(name: "Home Connect CoffeeMaker v3", namespace: "craigde", author: "Craig Dewar") {

        // Standard capabilities
        capability "Initialize"
        capability "Refresh"
        capability "Switch"
        capability "ContactSensor"      // Door state (open/closed) - for American models with doors
        capability "Sensor"
        capability "Configuration"
        capability "PushableButton"     // For brew complete notifications

        // =====================================================================
        // USER-FACING COMMANDS
        // =====================================================================
        
        command "getAvailablePrograms"
        
        command "start"
        
        command "makeCoffee", [
            [name: "Beverage", type: "ENUM", constraints: [
                // Common beverages
                "Espresso", "EspressoMacchiato", "Coffee", "Cappuccino",
                "LatteMacchiato", "CaffeLatte", "MilkFroth", "WarmMilk",
                "Americano", "EspressoDoppio", "FlatWhite", "Cortado",
                "-- Specialty Drinks --",
                "CaffeGrandeCrema", "RistrettoMacchiato",
                "-- Or use startProgramByKey --"
            ], description: "Select beverage to make"],
            [name: "Bean Amount", type: "ENUM", constraints: [
                "VeryMild", "Mild", "Normal", "Strong", "VeryStrong", 
                "DoubleShot", "DoubleShotPlus", "DoubleShotPlusPlus", "TripleShot"
            ], description: "Coffee strength (optional)"],
            [name: "Fill Quantity (ml)", type: "NUMBER", description: "Amount in ml (optional)"]
        ]
        
        command "startProgram", [
            [name: "Program", type: "ENUM", constraints: [
                // Common beverages
                "Espresso", "EspressoMacchiato", "Coffee", "Cappuccino",
                "LatteMacchiato", "CaffeLatte", "MilkFroth", "WarmMilk",
                "Americano", "EspressoDoppio", "FlatWhite", "Cortado",
                // Cleaning programs
                "Rinse", "Clean", "Descale",
                "-- Or use startProgramByKey --"
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
        // ATTRIBUTES - Status
        // =====================================================================
        
        attribute "operationState", "string"        // Ready, Run, Finished, etc.
        attribute "doorState", "string"             // Open, Closed (American models)
        attribute "powerState", "string"            // On, Off, Standby
        attribute "friendlyStatus", "string"        // Human-readable status

        // =====================================================================
        // ATTRIBUTES - Program & Progress
        // =====================================================================
        
        attribute "activeProgram", "string"         // Currently running program
        attribute "selectedProgram", "string"       // Selected but not started
        attribute "programProgress", "number"       // 0-100 percent
        attribute "progressBar", "string"           // "45%"

        // =====================================================================
        // ATTRIBUTES - Timing
        // =====================================================================
        
        attribute "remainingProgramTime", "number"          // Seconds remaining
        attribute "remainingProgramTimeFormatted", "string" // "00:45"
        attribute "elapsedProgramTime", "number"            // Seconds elapsed
        attribute "elapsedProgramTimeFormatted", "string"   // "00:30"

        // =====================================================================
        // ATTRIBUTES - Control State
        // =====================================================================
        
        attribute "remoteControlStartAllowed", "string"
        attribute "remoteControlActive", "string"
        attribute "localControlActive", "string"

        // =====================================================================
        // ATTRIBUTES - CoffeeMaker Options (Current Settings)
        // =====================================================================
        
        attribute "BeanAmount", "string"            // VeryMild, Mild, Normal, Strong, VeryStrong, DoubleShot, etc.
        attribute "FillQuantity", "number"          // Amount in ml
        attribute "CoffeeTemperature", "string"     // Normal, High, VeryHigh
        attribute "BeanContainerSelection", "string" // Which bean container to use
        attribute "FlowRate", "string"              // Normal, Intense, IntensePlus (for some models)
        attribute "Aroma", "string"                 // Aroma intensity setting
        attribute "HotWaterTemperature", "string"   // For hot water dispense

        // =====================================================================
        // ATTRIBUTES - CoffeeMaker Counters
        // =====================================================================
        
        attribute "beverageCounterCoffee", "number"         // Total coffee cups made
        attribute "beverageCounterPowderCoffee", "number"   // Powder coffee count
        attribute "beverageCounterHotWater", "number"       // Hot water dispenses
        attribute "beverageCounterHotMilk", "number"        // Hot milk dispenses
        attribute "beverageCounterFrothedMilk", "number"    // Frothed milk dispenses
        attribute "beverageCounterMilkCoffee", "number"     // Milk coffee drinks
        attribute "beverageCounterCoffeeAndMilk", "number"  // Combined counter

        // =====================================================================
        // ATTRIBUTES - CoffeeMaker Events & Alerts
        // =====================================================================
        
        attribute "BeanContainerEmpty", "string"    // Present, Off, Confirmed
        attribute "WaterTankEmpty", "string"        // Present, Off, Confirmed
        attribute "DripTrayFull", "string"          // Present, Off, Confirmed
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
        attribute "lastProgram", "string"           // Last program used (for defaults)
        attribute "lastBeverage", "string"          // Last beverage made
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
        input name: "defaultBeanAmount", type: "enum", title: "Default Bean Amount",
              options: ["VeryMild", "Mild", "Normal", "Strong", "VeryStrong", "DoubleShot"],
              defaultValue: "Normal", description: "Default strength for beverages"
        input name: "defaultFillQuantity", type: "number", title: "Default Fill Quantity (ml)",
              defaultValue: 120, description: "Default amount for beverages"
    }
}

/* ===========================================================================================================
   CONSTANTS
   =========================================================================================================== */

@Field static final String DRIVER_VERSION = "3.0.0"

// Program key mappings for coffee maker
@Field static final Map BEVERAGE_PROGRAMS = [
    // Standard beverages
    "Espresso": "ConsumerProducts.CoffeeMaker.Program.Beverage.Espresso",
    "EspressoMacchiato": "ConsumerProducts.CoffeeMaker.Program.Beverage.EspressoMacchiato",
    "Coffee": "ConsumerProducts.CoffeeMaker.Program.Beverage.Coffee",
    "Cappuccino": "ConsumerProducts.CoffeeMaker.Program.Beverage.Cappuccino",
    "LatteMacchiato": "ConsumerProducts.CoffeeMaker.Program.Beverage.LatteMacchiato",
    "CaffeLatte": "ConsumerProducts.CoffeeMaker.Program.Beverage.CaffeLatte",
    "MilkFroth": "ConsumerProducts.CoffeeMaker.Program.Beverage.MilkFroth",
    "WarmMilk": "ConsumerProducts.CoffeeMaker.Program.Beverage.WarmMilk",
    // Additional beverages
    "Americano": "ConsumerProducts.CoffeeMaker.Program.Beverage.Americano",
    "EspressoDoppio": "ConsumerProducts.CoffeeMaker.Program.Beverage.EspressoDoppio",
    "FlatWhite": "ConsumerProducts.CoffeeMaker.Program.Beverage.FlatWhite",
    "Cortado": "ConsumerProducts.CoffeeMaker.Program.Beverage.Cortado",
    "CaffeGrandeCrema": "ConsumerProducts.CoffeeMaker.Program.Beverage.CaffeGrandeCrema",
    "RistrettoMacchiato": "ConsumerProducts.CoffeeMaker.Program.Beverage.RistrettoMacchiato",
    // Cleaning programs
    "Rinse": "ConsumerProducts.CoffeeMaker.Program.CoffeeWorld.Rinse",
    "Clean": "ConsumerProducts.CoffeeMaker.Program.Cleaning.Clean",
    "Descale": "ConsumerProducts.CoffeeMaker.Program.Cleaning.Descale"
]

// Bean amount mappings
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

// Coffee temperature mappings
@Field static final Map COFFEE_TEMPS = [
    "Normal": "ConsumerProducts.CoffeeMaker.EnumType.CoffeeTemperature.Normal",
    "High": "ConsumerProducts.CoffeeMaker.EnumType.CoffeeTemperature.High",
    "VeryHigh": "ConsumerProducts.CoffeeMaker.EnumType.CoffeeTemperature.VeryHigh"
]

/* ===========================================================================================================
   LIFECYCLE METHODS
   =========================================================================================================== */

def installed() {
    log.info "${device.displayName}: Installed"
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "eventPresentState", value: "Off")
    
    // Configure pushable button for notifications
    // Button 1: Brew Complete
    // Button 2: Bean Container Empty
    // Button 3: Water Tank Empty
    // Button 4: Drip Tray Full
    sendEvent(name: "numberOfButtons", value: 4)
    
    // Initialize counters
    sendEvent(name: "beverageCounterCoffee", value: 0)
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
 * Fetches available programs from the coffee maker
 * Results are returned via z_parseAvailablePrograms callback
 */
def getAvailablePrograms() {
    logInfo("Fetching available programs")
    parent?.getAvailableProgramList(device)
}

/**
 * Starts the last used beverage (or Espresso if none)
 * Convenience command for simple automation
 */
def start() {
    def beverage = getDefaultProgram()
    logInfo("Starting default beverage: ${beverage}")
    startProgram(beverage)
}

/**
 * Makes a coffee beverage with optional strength and quantity
 * This is the main command for brewing coffee
 */
def makeCoffee(String beverage = null, String beanAmount = null, BigDecimal fillQuantity = null) {
    def selectedBeverage = beverage ?: getDefaultProgram()
    
    // Skip if it's a separator line
    if (selectedBeverage?.startsWith("--")) {
        logWarn("Please select a valid beverage")
        return
    }
    
    def programKey = buildProgramKey(selectedBeverage)
    
    // Build options list
    def options = []
    
    // Bean amount
    def strength = beanAmount ?: settings?.defaultBeanAmount ?: "Normal"
    if (BEAN_AMOUNTS.containsKey(strength)) {
        options << [key: "ConsumerProducts.CoffeeMaker.Option.BeanAmount", value: BEAN_AMOUNTS[strength]]
    }
    
    // Fill quantity
    def quantity = fillQuantity?.toInteger() ?: settings?.defaultFillQuantity?.toInteger() ?: 120
    if (quantity > 0) {
        options << [key: "ConsumerProducts.CoffeeMaker.Option.FillQuantity", value: quantity, unit: "ml"]
    }
    
    // Remember this beverage for next time
    saveLastProgram(selectedBeverage)
    
    logInfo("Making ${selectedBeverage}: strength=${strength}, quantity=${quantity}ml")
    
    if (options.size() > 0) {
        parent?.startProgram(device, programKey, options)
    } else {
        parent?.startProgram(device, programKey)
    }
}

/**
 * Starts a program from the dropdown list
 * If no program specified, uses last program or default
 */
def startProgram(String program = null) {
    def selectedProgram = program ?: getDefaultProgram()
    
    // Skip if it's a separator line
    if (selectedProgram?.startsWith("--")) {
        logWarn("Please select a valid program")
        return
    }
    
    def programKey = buildProgramKey(selectedProgram)
    
    // Remember this program for next time
    saveLastProgram(selectedProgram)
    
    logInfo("Starting program: ${selectedProgram} (${programKey})")
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
    parent?.startProgram(device, programKey)
}

/**
 * Starts a program with all options specified
 */
def startProgramWithOptions(String program, String beanAmount = null, BigDecimal fillQuantity = null, String coffeeTemp = null) {
    def programKey = buildProgramKey(program)
    
    // Build options list
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
    
    if (options.size() > 0) {
        parent?.startProgram(device, programKey, options)
    } else {
        parent?.startProgram(device, programKey)
    }
}

/**
 * Stops the currently running program
 */
def stopProgram() {
    logInfo("Stopping program")
    parent?.stopProgram(device)
}

/**
 * Sets the coffee maker power state
 */
def setPower(String powerState) {
    def state = powerState.toLowerCase()
    logInfo("Setting power: ${state}")
    
    if (state == "standby") {
        parent?.setPowerState(device, "Standby")
    } else {
        boolean on = (state == "on")
        parent?.setPowerState(device, on)
    }
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
    if (program?.contains(".")) {
        return program
    }
    
    // Check our static mappings first
    if (BEVERAGE_PROGRAMS.containsKey(program)) {
        return BEVERAGE_PROGRAMS[program]
    }
    
    // Check if we have a mapping from getAvailablePrograms
    if (state.programMap?.containsKey(program)) {
        return state.programMap[program]
    }
    
    // Build standard CoffeeMaker beverage program key
    return "ConsumerProducts.CoffeeMaker.Program.Beverage.${program}"
}

/**
 * Gets the default program to use when none specified
 * Returns: last used beverage, or "Espresso", or first available program
 */
private String getDefaultProgram() {
    // First choice: last used beverage
    def lastBeverage = device.currentValue("lastBeverage")
    if (lastBeverage) {
        logDebug("Using last beverage: ${lastBeverage}")
        return lastBeverage
    }
    
    // Second choice: last used program
    def lastProgram = device.currentValue("lastProgram")
    if (lastProgram) {
        logDebug("Using last program: ${lastProgram}")
        return lastProgram
    }
    
    // Third choice: "Espresso" if available
    if (state.programNames?.contains("Espresso")) {
        logDebug("Using default: Espresso")
        return "Espresso"
    }
    
    // Fourth choice: first available program
    if (state.programNames?.size() > 0) {
        def first = state.programNames[0]
        logDebug("Using first available: ${first}")
        return first
    }
    
    // Fallback: generic "Espresso" and hope for the best
    logDebug("No programs known, falling back to Espresso")
    return "Espresso"
}

/**
 * Saves the program/beverage as the last used for future defaults
 */
private void saveLastProgram(String program) {
    if (program && !program.startsWith("--")) {
        sendEvent(name: "lastProgram", value: program)
        // If it's a beverage, also save as lastBeverage
        if (BEVERAGE_PROGRAMS.containsKey(program) || program.contains("Beverage")) {
            sendEvent(name: "lastBeverage", value: program)
        }
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
            haId: device.deviceNetworkId.replace("HC3-", "").replace("HC3SIM-", ""),
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
            
            // Push button 1 when brew completes (transition to Finished or Ready)
            if ((opState == "Finished" || opState == "Ready") && previousState == "Run") {
                logInfo("Brew complete - pushing button 1")
                sendEvent(name: "pushed", value: 1, isStateChange: true, descriptionText: "Brew complete")
            }
            
            // Reset timers when program ends
            if (opState in ["Ready", "Inactive", "Finished"]) {
                resetProgramState()
            }
            updateDerivedState()
            updateJsonState()
            break

        // ===== Door State (American models) =====
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

        // ===== Programs =====
        case "BSH.Common.Root.ActiveProgram":
            sendEvent(name: "activeProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            updateJsonState()
            break

        case "BSH.Common.Root.SelectedProgram":
            sendEvent(name: "selectedProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            updateJsonState()
            break

        // ===== CoffeeMaker-Specific Options =====
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

        // ===== Beverage Counters =====
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

        // ===== CoffeeMaker Events/Alerts =====
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

        // ===== Generic CoffeeMaker Options (catch-all) =====
        case ~/ConsumerProducts\.CoffeeMaker\.Option\..*/:
            def attr = evt.key.split("\\.").last()
            sendEvent(name: attr, value: evt.value.toString())
            break

        // ===== Unhandled =====
        default:
            logDebug("Unhandled event: ${evt.key}")
    }
}

/* ===========================================================================================================
   DERIVED STATE CALCULATIONS
   =========================================================================================================== */

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
}

/**
 * Updates derived attributes based on current state
 */
private void updateDerivedState() {
    try {
        String opState = device.currentValue("operationState") as String
        
        // Generate friendly status message
        String friendly = determineFriendlyStatus(opState)
        if (friendly) {
            sendEvent(name: "friendlyStatus", value: friendly)
        }

    } catch (Exception e) {
        logWarn("Error updating derived state: ${e.message}")
    }
}

/**
 * Generates a human-readable status message
 */
private String determineFriendlyStatus(String opState) {
    switch (opState) {
        case "Ready":
        case "Inactive":
            return "Ready"
        case "Run":
            def activeProgram = device.currentValue("activeProgram")
            if (activeProgram) {
                return "Brewing ${activeProgram}"
            }
            return "Brewing"
        case "Pause":
            return "Paused"
        case "Finished":
            return "Ready"  // Coffee makers typically go back to Ready after brewing
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
            
            // Timing
            remainingProgramTime: device.currentValue("remainingProgramTime"),
            remainingProgramTimeFormatted: device.currentValue("remainingProgramTimeFormatted"),
            
            // Options
            beanAmount: device.currentValue("BeanAmount"),
            fillQuantity: device.currentValue("FillQuantity"),
            coffeeTemperature: device.currentValue("CoffeeTemperature"),
            
            // Counters
            beverageCounterCoffee: device.currentValue("beverageCounterCoffee"),
            
            // Control
            remoteControlStartAllowed: device.currentValue("remoteControlStartAllowed"),
            remoteControlActive: device.currentValue("remoteControlActive"),
            
            // Alerts
            beanContainerEmpty: device.currentValue("BeanContainerEmpty") == "Present",
            waterTankEmpty: device.currentValue("WaterTankEmpty") == "Present",
            dripTrayFull: device.currentValue("DripTrayFull") == "Present",
            lastAlert: device.currentValue("lastAlert"),
            
            // Meta
            lastBeverage: device.currentValue("lastBeverage"),
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
 * e.g., "ConsumerProducts.CoffeeMaker.EnumType.BeanAmount.Strong" → "Strong"
 */
private String extractEnum(String full) {
    if (!full) return null
    return full.substring(full.lastIndexOf(".") + 1)
}

/**
 * Converts seconds to MM:SS format (coffee brewing is typically short)
 */
private String secondsToTime(Integer sec) {
    if (sec == null || sec <= 0) return "00:00"
    long minutes = sec / 60
    long seconds = sec % 60
    return String.format("%02d:%02d", minutes, seconds)
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
