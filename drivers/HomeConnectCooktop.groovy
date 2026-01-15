/**
 *  Home Connect Cooktop v3 Driver
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
 *  This driver represents a Home Connect cooktop/hob appliance (Thermador, Bosch, etc.).
 *  
 *  IMPORTANT: Cooktops are MONITORING ONLY - they cannot be controlled remotely for safety reasons.
 *  The Home Connect API does not allow turning burners on/off or adjusting power levels remotely.
 *  
 *  Supported Features:
 *  - Zone monitoring (active state, power level) for up to 6 zones
 *  - Kitchen timer (alarm clock) - the only controllable feature
 *  - Child lock status
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
    definition(name: "Home Connect Cooktop v3", namespace: "craigde", author: "Craig Dewar") {

        // Standard capabilities
        capability "Initialize"
        capability "Refresh"
        capability "Switch"
        capability "Sensor"
        capability "Configuration"
        capability "PushableButton"

        // =====================================================================
        // USER-FACING COMMANDS
        // =====================================================================
        
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
        attribute "powerState", "string"
        attribute "friendlyStatus", "string"

        // =====================================================================
        // ATTRIBUTES - Zone Status (up to 6 zones)
        // =====================================================================
        
        attribute "zone1Active", "string"
        attribute "zone1PowerLevel", "number"
        attribute "zone2Active", "string"
        attribute "zone2PowerLevel", "number"
        attribute "zone3Active", "string"
        attribute "zone3PowerLevel", "number"
        attribute "zone4Active", "string"
        attribute "zone4PowerLevel", "number"
        attribute "zone5Active", "string"
        attribute "zone5PowerLevel", "number"
        attribute "zone6Active", "string"
        attribute "zone6PowerLevel", "number"

        // =====================================================================
        // ATTRIBUTES - Derived Zone Info
        // =====================================================================
        
        attribute "activeZones", "number"
        attribute "activeZonesList", "string"

        // =====================================================================
        // ATTRIBUTES - Settings
        // =====================================================================
        
        attribute "childLock", "string"

        // =====================================================================
        // ATTRIBUTES - Timer
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

// =============================================================================
// LIFECYCLE METHODS
// =============================================================================

def installed() {
    log.info "${device.displayName}: Installed"
    initializeState()
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "eventPresentState", value: "Off")
    sendEvent(name: "activeZones", value: 0)
    sendEvent(name: "switch", value: "off")
    
    // Button 1: Timer complete
    // Button 2: Zone activated
    // Button 3: All zones off
    sendEvent(name: "numberOfButtons", value: 3)
    
    // Initialize all zones
    (1..6).each { zone ->
        sendEvent(name: "zone${zone}Active", value: "false")
        sendEvent(name: "zone${zone}PowerLevel", value: 0)
    }
}

def updated() {
    log.info "${device.displayName}: Updated"
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
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
    logInfo("--- Zone Summary ---")
    (1..6).each { zone ->
        def active = device.currentValue("zone${zone}Active")
        def power = device.currentValue("zone${zone}PowerLevel")
        if (active == "true" || power > 0) {
            logInfo("  Zone ${zone}: Active=${active}, Power=${power}")
        }
    }
    
    logInfo("")
    logInfo("--- State Variables ---")
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
    logWarn("Cooktops cannot be turned on remotely for safety reasons")
    logWarn("Please use the physical controls on the cooktop")
}

def off() {
    logWarn("Cooktops cannot be turned off remotely for safety reasons")
    logWarn("Please use the physical controls on the cooktop")
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
    logDebug("Parsing available programs (cooktops have no programs)")
}

def z_parseAvailableOptions(String json) {
    logDebug("Parsing available options")
}

def z_parseActiveProgram(String json) {
    logDebug("Parsing active program (cooktops have no programs)")
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

        case "BSH.Common.Setting.AlarmClock":
            Integer sec = evt.value as Integer
            sendEvent(name: "alarmClockRemaining", value: sec)
            sendEvent(name: "alarmClockRemainingFormatted", value: secondsToTime(sec))
            break

        case "BSH.Common.Event.AlarmClockElapsed":
            def value = extractEnum(evt.value)
            if (value == "Present") {
                logInfo("Alarm clock elapsed - pushing button 1")
                sendEvent(name: "pushed", value: 1, isStateChange: true, descriptionText: "Timer complete")
                sendAlert("AlarmElapsed", "Kitchen timer has finished")
            }
            break

        case "Cooking.Hob.Setting.ChildLock":
            def locked = evt.value.toString().toLowerCase() == "true"
            sendEvent(name: "childLock", value: locked ? "On" : "Off")
            break

        // Zone Active status - handle zones 1-6 dynamically
        case ~/Cooking\.Hob\.Status\.Zone(\d+)\.Active/:
            def matcher = evt.key =~ /Zone(\d+)/
            if (matcher.find()) {
                def zoneNum = matcher.group(1)
                def active = evt.value.toString().toLowerCase() == "true"
                def prevActive = device.currentValue("zone${zoneNum}Active")
                
                sendEvent(name: "zone${zoneNum}Active", value: active.toString())
                
                // Push button when zone becomes active
                if (active && prevActive != "true") {
                    logInfo("Zone ${zoneNum} activated - pushing button 2")
                    sendEvent(name: "pushed", value: 2, isStateChange: true, descriptionText: "Zone ${zoneNum} activated")
                }
                
                updateActiveZonesCount()
                updateJsonState()
            }
            break

        // Zone Power Level - handle zones 1-6 dynamically
        case ~/Cooking\.Hob\.Status\.Zone(\d+)\.PowerLevel/:
            def matcher = evt.key =~ /Zone(\d+)/
            if (matcher.find()) {
                def zoneNum = matcher.group(1)
                Integer powerLevel = evt.value as Integer
                sendEvent(name: "zone${zoneNum}PowerLevel", value: powerLevel)
                updateJsonState()
            }
            break

        // Catch-all for other zone events
        case ~/Cooking\.Hob\.Status\.Zone\d+\..*/:
            def attr = evt.key.split("\\.").last()
            logDebug("Zone status: ${attr} = ${evt.value}")
            break

        case ~/Cooking\.Hob\.Setting\..*/:
            def attr = evt.key.split("\\.").last()
            logDebug("Cooktop setting: ${attr} = ${evt.value}")
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

private void updateActiveZonesCount() {
    Integer activeCount = 0
    def activeList = []
    
    (1..6).each { zone ->
        def active = device.currentValue("zone${zone}Active")
        if (active == "true") {
            activeCount++
            def power = device.currentValue("zone${zone}PowerLevel") ?: 0
            activeList << "Zone${zone}(${power})"
        }
    }
    
    def prevCount = device.currentValue("activeZones") as Integer ?: 0
    
    sendEvent(name: "activeZones", value: activeCount)
    sendEvent(name: "activeZonesList", value: activeList.join(", ") ?: "None")
    sendEvent(name: "switch", value: (activeCount > 0 ? "on" : "off"))
    
    // Push button 3 when all zones turn off
    if (activeCount == 0 && prevCount > 0) {
        logInfo("All zones off - pushing button 3")
        sendEvent(name: "pushed", value: 3, isStateChange: true, descriptionText: "All zones turned off")
    }
    
    updateDerivedState()
}

private void updateDerivedState() {
    try {
        Integer activeCount = device.currentValue("activeZones") as Integer ?: 0
        String childLock = device.currentValue("childLock")
        
        String friendly
        if (childLock == "On") {
            friendly = "Child Lock Active"
        } else if (activeCount == 0) {
            friendly = "All Off"
        } else if (activeCount == 1) {
            friendly = "1 Zone Active"
        } else {
            friendly = "${activeCount} Zones Active"
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
        def zones = [:]
        (1..6).each { zone ->
            def active = device.currentValue("zone${zone}Active")
            def power = device.currentValue("zone${zone}PowerLevel")
            if (active == "true" || power > 0) {
                zones["zone${zone}"] = [
                    active: active == "true",
                    powerLevel: power ?: 0
                ]
            }
        }
        
        def stateMap = [
            operationState: device.currentValue("operationState"),
            powerState: device.currentValue("powerState"),
            friendlyStatus: device.currentValue("friendlyStatus"),
            activeZones: device.currentValue("activeZones"),
            activeZonesList: device.currentValue("activeZonesList"),
            zones: zones,
            childLock: device.currentValue("childLock"),
            alarmClockRemaining: device.currentValue("alarmClockRemaining"),
            alarmClockRemainingFormatted: device.currentValue("alarmClockRemainingFormatted"),
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
