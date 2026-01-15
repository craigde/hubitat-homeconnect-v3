/**
 *  Home Connect WasherDryer v3 Driver
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
 *  This driver represents a Home Connect combination washer-dryer appliance (Bosch, Siemens, etc.).
 *  
 *  Supported Features:
 *  - Wash programs (Cotton, Synthetics, Delicates, etc.)
 *  - Dry programs and wash+dry combined programs
 *  - Temperature and spin speed selection
 *  - Drying target selection
 *  - Program progress and remaining time
 *  - Door lock status
 *  - i-Dos automatic detergent dosing (if equipped)
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
    definition(name: "Home Connect WasherDryer v3", namespace: "craigde", author: "Craig Dewar") {

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
        
        command "startWashProgram", [
            [name: "Program", type: "ENUM", constraints: [
                "Cotton", "EasyCare", "Synthetics", "Delicates", "Wool",
                "QuickWash", "Mix", "DarkWash", "Shirts", "SportFitness",
                "Spin", "Rinse", "DrumClean"
            ], description: "Select wash program"]
        ]
        
        command "startDryProgram", [
            [name: "Program", type: "ENUM", constraints: [
                "Cotton", "Synthetics", "Delicates", "TimedDry", "Refresh"
            ], description: "Select dry program"]
        ]
        
        command "startWashAndDry", [
            [name: "Wash Program*", type: "ENUM", constraints: [
                "Cotton", "EasyCare", "Synthetics", "Delicates", "Mix"
            ]],
            [name: "Drying Target", type: "ENUM", constraints: [
                "IronDry", "CupboardDry", "CupboardDryPlus", "ExtraDry"
            ], description: "Dryness level after washing"]
        ]
        
        command "startProgramByKey", [
            [name: "Program Key*", type: "STRING", description: "Full program key"]
        ]
        
        command "startProgramWithOptions", [
            [name: "Program*", type: "STRING", description: "Program name or key"],
            [name: "Temperature", type: "ENUM", constraints: [
                "Cold", "20", "30", "40", "50", "60", "90"
            ]],
            [name: "Spin Speed", type: "ENUM", constraints: [
                "Off", "400", "600", "800", "1000", "1200", "1400"
            ]],
            [name: "Drying Target", type: "ENUM", constraints: [
                "None", "IronDry", "CupboardDry", "CupboardDryPlus", "ExtraDry"
            ]]
        ]
        
        command "startDelayed", [
            [name: "Program*", type: "STRING"],
            [name: "Delay Minutes*", type: "NUMBER", description: "Start in X minutes"]
        ]
        
        command "stopProgram"
        command "pauseProgram"
        command "resumeProgram"
        
        command "setTemperature", [
            [name: "Temperature*", type: "ENUM", constraints: [
                "Cold", "20", "30", "40", "50", "60", "90"
            ]]
        ]
        
        command "setSpinSpeed", [
            [name: "Speed*", type: "ENUM", constraints: [
                "Off", "400", "600", "800", "1000", "1200", "1400"
            ]]
        ]
        
        command "setDryingTarget", [
            [name: "Target*", type: "ENUM", constraints: [
                "IronDry", "CupboardDry", "CupboardDryPlus", "ExtraDry"
            ]]
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
        attribute "doorState", "string"
        attribute "powerState", "string"
        attribute "friendlyStatus", "string"
        attribute "currentMode", "string"

        // =====================================================================
        // ATTRIBUTES - Program & Progress
        // =====================================================================
        
        attribute "activeProgram", "string"
        attribute "selectedProgram", "string"
        attribute "programProgress", "number"
        attribute "progressBar", "string"
        attribute "programPhase", "string"

        // =====================================================================
        // ATTRIBUTES - Timing
        // =====================================================================
        
        attribute "remainingProgramTime", "number"
        attribute "remainingProgramTimeFormatted", "string"
        attribute "elapsedProgramTime", "number"
        attribute "elapsedProgramTimeFormatted", "string"
        attribute "estimatedEndTimeFormatted", "string"
        attribute "startInRelative", "number"
        attribute "startInRelativeFormatted", "string"

        // =====================================================================
        // ATTRIBUTES - Washer Settings
        // =====================================================================
        
        attribute "temperature", "string"
        attribute "spinSpeed", "string"

        // =====================================================================
        // ATTRIBUTES - Dryer Settings
        // =====================================================================
        
        attribute "dryingTarget", "string"

        // =====================================================================
        // ATTRIBUTES - i-Dos (Automatic Dosing)
        // =====================================================================
        
        attribute "iDos1Level", "string"
        attribute "iDos2Level", "string"
        attribute "iDos1Active", "string"
        attribute "iDos2Active", "string"

        // =====================================================================
        // ATTRIBUTES - Control State
        // =====================================================================
        
        attribute "remoteControlStartAllowed", "string"
        attribute "remoteControlActive", "string"
        attribute "localControlActive", "string"
        attribute "doorLocked", "string"

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
        input name: "defaultWashProgram", type: "enum", title: "Default Wash Program",
              options: ["Cotton", "EasyCare", "Synthetics", "Delicates", "QuickWash"],
              defaultValue: "Cotton"
        input name: "defaultTemperature", type: "enum", title: "Default Temperature",
              options: ["Cold", "20", "30", "40", "60", "90"], defaultValue: "40"
        input name: "defaultSpinSpeed", type: "enum", title: "Default Spin Speed",
              options: ["Off", "400", "800", "1000", "1200", "1400"], defaultValue: "1200"
        input name: "defaultDryingTarget", type: "enum", title: "Default Drying Target",
              options: ["None", "IronDry", "CupboardDry", "CupboardDryPlus", "ExtraDry"],
              defaultValue: "None"
    }
}

// =============================================================================
// CONSTANTS
// =============================================================================

@Field static final String DRIVER_VERSION = "3.0.1"
@Field static final Integer MAX_DISCOVERED_KEYS = 100

@Field static final Map WASH_PROGRAMS = [
    "Cotton": "LaundryCare.WasherDryer.Program.Cotton",
    "EasyCare": "LaundryCare.WasherDryer.Program.EasyCare",
    "Synthetics": "LaundryCare.WasherDryer.Program.Synthetic",
    "Delicates": "LaundryCare.WasherDryer.Program.Delicates",
    "Wool": "LaundryCare.WasherDryer.Program.Wool",
    "QuickWash": "LaundryCare.WasherDryer.Program.QuickWash",
    "Mix": "LaundryCare.WasherDryer.Program.Mix",
    "DarkWash": "LaundryCare.WasherDryer.Program.DarkWash",
    "Shirts": "LaundryCare.WasherDryer.Program.Shirts",
    "SportFitness": "LaundryCare.WasherDryer.Program.SportFitness",
    "Spin": "LaundryCare.WasherDryer.Program.Spin",
    "Rinse": "LaundryCare.WasherDryer.Program.Rinse",
    "DrumClean": "LaundryCare.WasherDryer.Program.DrumClean"
]

@Field static final Map DRY_PROGRAMS = [
    "Cotton": "LaundryCare.WasherDryer.Program.DryCotton",
    "Synthetics": "LaundryCare.WasherDryer.Program.DrySynthetic",
    "Delicates": "LaundryCare.WasherDryer.Program.DryDelicates",
    "TimedDry": "LaundryCare.WasherDryer.Program.TimeDrying",
    "Refresh": "LaundryCare.WasherDryer.Program.Refresh"
]

@Field static final Map TEMPERATURES = [
    "Cold": "LaundryCare.WasherDryer.EnumType.Temperature.Cold",
    "20": "LaundryCare.WasherDryer.EnumType.Temperature.GC20",
    "30": "LaundryCare.WasherDryer.EnumType.Temperature.GC30",
    "40": "LaundryCare.WasherDryer.EnumType.Temperature.GC40",
    "50": "LaundryCare.WasherDryer.EnumType.Temperature.GC50",
    "60": "LaundryCare.WasherDryer.EnumType.Temperature.GC60",
    "90": "LaundryCare.WasherDryer.EnumType.Temperature.GC90"
]

@Field static final Map SPIN_SPEEDS = [
    "Off": "LaundryCare.WasherDryer.EnumType.SpinSpeed.Off",
    "400": "LaundryCare.WasherDryer.EnumType.SpinSpeed.RPM400",
    "600": "LaundryCare.WasherDryer.EnumType.SpinSpeed.RPM600",
    "800": "LaundryCare.WasherDryer.EnumType.SpinSpeed.RPM800",
    "1000": "LaundryCare.WasherDryer.EnumType.SpinSpeed.RPM1000",
    "1200": "LaundryCare.WasherDryer.EnumType.SpinSpeed.RPM1200",
    "1400": "LaundryCare.WasherDryer.EnumType.SpinSpeed.RPM1400"
]

@Field static final Map DRYING_TARGETS = [
    "IronDry": "LaundryCare.WasherDryer.EnumType.DryingTarget.IronDry",
    "CupboardDry": "LaundryCare.WasherDryer.EnumType.DryingTarget.CupboardDry",
    "CupboardDryPlus": "LaundryCare.WasherDryer.EnumType.DryingTarget.CupboardDryPlus",
    "ExtraDry": "LaundryCare.WasherDryer.EnumType.DryingTarget.ExtraDry"
]

@Field static final Map PROGRAM_PHASES = [
    "Washing": "Washing",
    "Rinsing": "Rinsing",
    "Spinning": "Spinning",
    "Drying": "Drying",
    "Cooling": "Cooling",
    "Prewash": "Pre-wash",
    "MainWash": "Main Wash",
    "FinalRinse": "Final Rinse",
    "Finished": "Finished",
    "AntiCrease": "Anti-Crease"
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
    sendEvent(name: "currentMode", value: "Unknown")
    
    // Button 1: Cycle complete
    // Button 2: Door unlocked (can be opened)
    // Button 3: i-Dos low alert
    // Button 4: Wash phase complete (for wash+dry)
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
    logInfo("  defaultWashProgram: ${defaultWashProgram}")
    logInfo("  defaultTemperature: ${defaultTemperature}")
    logInfo("  defaultSpinSpeed: ${defaultSpinSpeed}")
    logInfo("  defaultDryingTarget: ${defaultDryingTarget}")
    
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
    def program = settings?.defaultWashProgram ?: "Cotton"
    def temp = settings?.defaultTemperature ?: "40"
    def spin = settings?.defaultSpinSpeed ?: "1200"
    def dryTarget = settings?.defaultDryingTarget ?: "None"
    startProgramWithOptions(program, temp, spin, dryTarget)
}

def startWashProgram(String program = null) {
    def selectedProgram = program ?: settings?.defaultWashProgram ?: "Cotton"
    def programKey = buildWashProgramKey(selectedProgram)
    
    saveLastProgram(selectedProgram)
    sendEvent(name: "currentMode", value: "Wash")
    
    logInfo("Starting wash program: ${selectedProgram}")
    recordCommand("startWashProgram", [program: selectedProgram])
    parent?.startProgram(device, programKey)
}

def startDryProgram(String program = null) {
    def selectedProgram = program ?: "Cotton"
    def programKey = buildDryProgramKey(selectedProgram)
    
    saveLastProgram("Dry: ${selectedProgram}")
    sendEvent(name: "currentMode", value: "Dry")
    
    logInfo("Starting dry program: ${selectedProgram}")
    recordCommand("startDryProgram", [program: selectedProgram])
    parent?.startProgram(device, programKey)
}

def startWashAndDry(String washProgram, String dryingTarget = null) {
    def programKey = buildWashProgramKey(washProgram)
    def target = dryingTarget ?: settings?.defaultDryingTarget ?: "CupboardDry"
    
    def options = []
    
    if (target && target != "None" && DRYING_TARGETS.containsKey(target)) {
        options << [key: "LaundryCare.WasherDryer.Option.DryingTarget", value: DRYING_TARGETS[target]]
    }
    
    saveLastProgram("${washProgram} + Dry")
    sendEvent(name: "currentMode", value: "Wash+Dry")
    
    logInfo("Starting ${washProgram} with drying target: ${target}")
    recordCommand("startWashAndDry", [program: washProgram, dryTarget: target])
    
    if (options.size() > 0) {
        parent?.startProgram(device, programKey, options)
    } else {
        parent?.startProgram(device, programKey)
    }
}

def startProgramByKey(String programKey) {
    def programName = extractEnum(programKey)
    saveLastProgram(programName)
    
    def mode = programKey.contains("Dry") ? "Dry" : "Wash"
    sendEvent(name: "currentMode", value: mode)
    
    logInfo("Starting program by key: ${programKey}")
    recordCommand("startProgramByKey", [key: programKey])
    parent?.startProgram(device, programKey)
}

def startProgramWithOptions(String program, String temperature = null, String spinSpeed = null, String dryingTarget = null) {
    def programKey = buildWashProgramKey(program)
    
    def options = []
    
    def temp = temperature ?: settings?.defaultTemperature ?: "40"
    if (TEMPERATURES.containsKey(temp)) {
        options << [key: "LaundryCare.WasherDryer.Option.Temperature", value: TEMPERATURES[temp]]
    }
    
    def spin = spinSpeed ?: settings?.defaultSpinSpeed ?: "1200"
    if (SPIN_SPEEDS.containsKey(spin)) {
        options << [key: "LaundryCare.WasherDryer.Option.SpinSpeed", value: SPIN_SPEEDS[spin]]
    }
    
    def dryTarget = dryingTarget ?: settings?.defaultDryingTarget ?: "None"
    if (dryTarget && dryTarget != "None" && DRYING_TARGETS.containsKey(dryTarget)) {
        options << [key: "LaundryCare.WasherDryer.Option.DryingTarget", value: DRYING_TARGETS[dryTarget]]
        sendEvent(name: "currentMode", value: "Wash+Dry")
        saveLastProgram("${program} + Dry")
    } else {
        sendEvent(name: "currentMode", value: "Wash")
        saveLastProgram(program)
    }
    
    logInfo("Starting ${program}: temp=${temp}°C, spin=${spin} RPM" + (dryTarget != "None" ? ", dry=${dryTarget}" : ""))
    recordCommand("startProgramWithOptions", [program: program, temp: temp, spin: spin, dryTarget: dryTarget])
    
    if (options.size() > 0) {
        parent?.startProgram(device, programKey, options)
    } else {
        parent?.startProgram(device, programKey)
    }
}

def startDelayed(String program, BigDecimal delayMinutes) {
    def programKey = buildWashProgramKey(program)
    Integer delaySec = (delayMinutes * 60).toInteger()
    
    def options = [
        [key: "BSH.Common.Option.StartInRelative", value: delaySec, unit: "seconds"]
    ]
    
    saveLastProgram(program)
    
    logInfo("Starting ${program} in ${delayMinutes} minutes")
    recordCommand("startDelayed", [program: program, delay: delayMinutes])
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

def setTemperature(String temperature) {
    logInfo("Setting temperature: ${temperature}°C")
    recordCommand("setTemperature", [temp: temperature])
    
    if (TEMPERATURES.containsKey(temperature)) {
        parent?.setSelectedProgramOption(device, "LaundryCare.WasherDryer.Option.Temperature", TEMPERATURES[temperature])
    } else {
        logWarn("Unknown temperature: ${temperature}")
    }
}

def setSpinSpeed(String speed) {
    logInfo("Setting spin speed: ${speed} RPM")
    recordCommand("setSpinSpeed", [speed: speed])
    
    if (SPIN_SPEEDS.containsKey(speed)) {
        parent?.setSelectedProgramOption(device, "LaundryCare.WasherDryer.Option.SpinSpeed", SPIN_SPEEDS[speed])
    } else {
        logWarn("Unknown spin speed: ${speed}")
    }
}

def setDryingTarget(String target) {
    logInfo("Setting drying target: ${target}")
    recordCommand("setDryingTarget", [target: target])
    
    if (DRYING_TARGETS.containsKey(target)) {
        parent?.setSelectedProgramOption(device, "LaundryCare.WasherDryer.Option.DryingTarget", DRYING_TARGETS[target])
    } else {
        logWarn("Unknown drying target: ${target}")
    }
}

def setPower(String powerState) {
    boolean on = (powerState == "on")
    logInfo("Setting power: ${on ? 'ON' : 'OFF'}")
    recordCommand("setPower", [state: powerState])
    parent?.setPowerState(device, on)
}

private String buildWashProgramKey(String program) {
    if (program?.contains(".")) {
        return program
    }
    
    if (WASH_PROGRAMS.containsKey(program)) {
        return WASH_PROGRAMS[program]
    }
    
    if (state.programMap?.containsKey(program)) {
        return state.programMap[program]
    }
    
    return "LaundryCare.WasherDryer.Program.${program}"
}

private String buildDryProgramKey(String program) {
    if (program?.contains(".")) {
        return program
    }
    
    if (DRY_PROGRAMS.containsKey(program)) {
        return DRY_PROGRAMS[program]
    }
    
    return "LaundryCare.WasherDryer.Program.Dry${program}"
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
        
        // Determine current mode from program name
        def key = obj?.key ?: obj?.data?.key ?: ""
        if (key.contains("Dry")) {
            sendEvent(name: "currentMode", value: "Dry")
        } else {
            // Check if drying is enabled
            def dryTarget = device.currentValue("dryingTarget")
            if (dryTarget && dryTarget != "null" && dryTarget != "None") {
                sendEvent(name: "currentMode", value: "Wash+Dry")
            } else {
                sendEvent(name: "currentMode", value: "Wash")
            }
        }
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
                logInfo("Cycle complete - pushing button 1")
                sendEvent(name: "pushed", value: 1, isStateChange: true, descriptionText: "Cycle complete")
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
            def previousDoor = device.currentValue("doorState")
            sendEvent(name: "doorState", value: doorState)
            sendEvent(name: "contact", value: (doorState == "Open" ? "open" : "closed"))
            
            def locked = (doorState == "Locked")
            def wasLocked = device.currentValue("doorLocked") == "true"
            sendEvent(name: "doorLocked", value: locked.toString())
            
            if (!locked && wasLocked) {
                logInfo("Door unlocked - pushing button 2")
                sendEvent(name: "pushed", value: 2, isStateChange: true, descriptionText: "Door unlocked - laundry ready")
            }
            
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

        case "BSH.Common.Option.StartInRelative":
            Integer sec = evt.value as Integer
            sendEvent(name: "startInRelative", value: sec)
            sendEvent(name: "startInRelativeFormatted", value: secondsToTime(sec))
            break

        case "BSH.Common.Root.ActiveProgram":
            sendEvent(name: "activeProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            updateJsonState()
            break

        case "BSH.Common.Root.SelectedProgram":
            sendEvent(name: "selectedProgram", value: evt.displayvalue ?: extractEnum(evt.value))
            break

        case "LaundryCare.WasherDryer.Option.Temperature":
            def temp = extractTemperature(evt.value)
            sendEvent(name: "temperature", value: temp)
            updateJsonState()
            break

        case "LaundryCare.WasherDryer.Option.SpinSpeed":
            def spin = extractSpinSpeed(evt.value)
            sendEvent(name: "spinSpeed", value: spin)
            updateJsonState()
            break

        case "LaundryCare.WasherDryer.Option.DryingTarget":
            def target = extractEnum(evt.value)
            sendEvent(name: "dryingTarget", value: friendlyDryingTarget(target))
            
            // Update mode if drying is enabled
            if (target && target != "None") {
                def currentMode = device.currentValue("currentMode")
                if (currentMode == "Wash") {
                    sendEvent(name: "currentMode", value: "Wash+Dry")
                }
            }
            updateJsonState()
            break

        case "LaundryCare.Common.Status.ProgramPhase":
            def phase = extractEnum(evt.value)
            def previousPhase = device.currentValue("programPhase")
            def friendlyPhase = PROGRAM_PHASES[phase] ?: phase
            sendEvent(name: "programPhase", value: friendlyPhase)
            
            // Check if wash phase finished and drying is starting
            def currentMode = device.currentValue("currentMode")
            if (currentMode == "Wash+Dry" && phase == "Drying" && previousPhase != "Drying") {
                logInfo("Wash phase complete, starting drying - pushing button 4")
                sendEvent(name: "pushed", value: 4, isStateChange: true, descriptionText: "Wash complete, drying started")
            }
            
            updateDerivedState()
            break

        case "LaundryCare.WasherDryer.Option.IDos1DosingLevel":
            sendEvent(name: "iDos1Level", value: extractEnum(evt.value))
            break

        case "LaundryCare.WasherDryer.Option.IDos2DosingLevel":
            sendEvent(name: "iDos2Level", value: extractEnum(evt.value))
            break

        case "LaundryCare.WasherDryer.Option.IDos1Active":
            sendEvent(name: "iDos1Active", value: evt.value.toString())
            break

        case "LaundryCare.WasherDryer.Option.IDos2Active":
            sendEvent(name: "iDos2Active", value: evt.value.toString())
            break

        case "LaundryCare.WasherDryer.Event.IDos1FillLevelPoor":
        case "LaundryCare.WasherDryer.Event.IDos2FillLevelPoor":
            def value = extractEnum(evt.value)
            def dosNumber = evt.key.contains("IDos1") ? "1" : "2"
            if (value == "Present") {
                logInfo("i-Dos ${dosNumber} level low - pushing button 3")
                sendEvent(name: "pushed", value: 3, isStateChange: true, descriptionText: "i-Dos ${dosNumber} needs refill")
                sendAlert("iDosLow", "i-Dos ${dosNumber} detergent level is low")
            }
            break

        case ~/LaundryCare\.WasherDryer\.Option\..*/:
            def attr = evt.key.split("\\.").last()
            logDebug("WasherDryer option: ${attr} = ${evt.value}")
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
    sendEvent(name: "programPhase", value: "")
    sendEvent(name: "currentMode", value: "Ready")
}

private void updateDerivedState() {
    try {
        String opState = device.currentValue("operationState") as String
        String programPhase = device.currentValue("programPhase")
        Integer progress = device.currentValue("programProgress") as Integer
        String activeProgram = device.currentValue("activeProgram")
        String currentMode = device.currentValue("currentMode")
        
        String friendly = determineFriendlyStatus(opState, programPhase, progress, activeProgram, currentMode)
        sendEvent(name: "friendlyStatus", value: friendly)
        
    } catch (Exception e) {
        logWarn("Error updating derived state: ${e.message}")
    }
}

private String determineFriendlyStatus(String opState, String phase, Integer progress, String program, String mode) {
    switch (opState) {
        case "Ready":
        case "Inactive":
            return "Ready"
        case "DelayedStart":
            def delay = device.currentValue("startInRelativeFormatted")
            return delay ? "Starting in ${delay}" : "Delayed Start"
        case "Run":
            def modePrefix = ""
            if (mode == "Wash+Dry") {
                modePrefix = phase == "Drying" ? "Drying: " : "Washing: "
            }
            
            if (phase && phase != "null") {
                return "${modePrefix}${phase}" + (progress ? " (${progress}%)" : "")
            }
            return program ? "${modePrefix}${program}" + (progress ? " (${progress}%)" : "") : "${modePrefix}Running"
        case "Pause":
            return "Paused"
        case "Finished":
            def doorLocked = device.currentValue("doorLocked") == "true"
            return doorLocked ? "Done - Door Locked" : "Done - Ready to Unload"
        case "ActionRequired":
            return "Action Required"
        case "Aborting":
            return "Stopping"
        case "Error":
            return "Error"
        default:
            return opState ?: "Unknown"
    }
}

private String friendlyDryingTarget(String target) {
    switch (target) {
        case "IronDry": return "Iron Dry"
        case "CupboardDry": return "Cupboard Dry"
        case "CupboardDryPlus": return "Cupboard Dry+"
        case "ExtraDry": return "Extra Dry"
        default: return target
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
            doorLocked: device.currentValue("doorLocked") == "true",
            powerState: device.currentValue("powerState"),
            friendlyStatus: device.currentValue("friendlyStatus"),
            currentMode: device.currentValue("currentMode"),
            activeProgram: device.currentValue("activeProgram"),
            programProgress: device.currentValue("programProgress"),
            programPhase: device.currentValue("programPhase"),
            temperature: device.currentValue("temperature"),
            spinSpeed: device.currentValue("spinSpeed"),
            dryingTarget: device.currentValue("dryingTarget"),
            remainingProgramTime: device.currentValue("remainingProgramTime"),
            remainingProgramTimeFormatted: device.currentValue("remainingProgramTimeFormatted"),
            estimatedEndTime: device.currentValue("estimatedEndTimeFormatted"),
            remoteControlStartAllowed: device.currentValue("remoteControlStartAllowed"),
            iDos1Level: device.currentValue("iDos1Level"),
            iDos2Level: device.currentValue("iDos2Level"),
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

private String extractTemperature(String value) {
    if (!value) return null
    def enumVal = extractEnum(value)
    if (enumVal == "Cold") return "Cold"
    if (enumVal?.startsWith("GC")) {
        return enumVal.replace("GC", "") + "°C"
    }
    return enumVal
}

private String extractSpinSpeed(String value) {
    if (!value) return null
    def enumVal = extractEnum(value)
    if (enumVal == "Off") return "No Spin"
    if (enumVal?.startsWith("RPM")) {
        return enumVal.replace("RPM", "") + " RPM"
    }
    return enumVal
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
