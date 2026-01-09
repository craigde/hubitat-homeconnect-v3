/**
 *  Home Connect Stream Driver v3
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
 *  ARCHITECTURE OVERVIEW
 *  ===========================================================================================================
 *  
 *  This driver serves as the central hub for Home Connect communication:
 *  
 *  1. SSE CONNECTION: Maintains a single Server-Sent Events connection to Home Connect for all appliances.
 *     The connection receives real-time updates (status changes, program progress, events) and routes
 *     them to the appropriate child device drivers via the parent app.
 *  
 *  2. API LIBRARY: Provides HTTP methods (GET/PUT/DELETE) for all Home Connect API calls.
 *     Child drivers don't make API calls directly - they go through the parent app, which
 *     delegates to this driver.
 *  
 *  3. RATE LIMIT MANAGEMENT: Home Connect enforces strict rate limits (1000 calls/day).
 *     This driver tracks rate limiting, implements conservative reconnect timing, and
 *     automatically schedules reconnection when limits expire.
 *  
 *  Event Flow:
 *  -----------
 *  Home Connect SSE → parse() → processEventPayload() → parent.handleApplianceEvent() → child.parseEvent()
 *  
 *  API Call Flow:
 *  --------------
 *  Child Driver → parent.startProgram() → streamDriver.setActiveProgram() → Home Connect API
 *  
 *  ===========================================================================================================
 *
 *  Version History:
 *  ----------------
 *  3.0.0  2026-01-07  Initial v3 architecture with Stream Driver pattern
 *  3.0.1  2026-01-08  Added conservative reconnect logic (5 min delay for normal disconnects)
 *                     Added rate limit detection and auto-recovery scheduling
 *                     Added exponential backoff for failed connections
 *  3.0.2  2026-01-08  Changed to z_setApiUrl for flexible API URL configuration
 *                     Supports both production and simulator APIs via parent app
 *  3.0.3  2026-01-08  Added lastEventReceived timestamp for stream health monitoring
 *                     Added rateLimitRemaining/rateLimitLimit attributes from API headers
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

// Buffer for accumulating SSE message fragments across multiple parse() calls
@Field static String messageBuffer = ""

metadata {
    definition(name: "Home Connect Stream Driver v3", namespace: "craigde", author: "Craig Dewar") {
        capability "Initialize"
        capability "Refresh"

        // User-facing commands
        command "connect"
        command "disconnect"
        command "clearRateLimit"

        // Internal command (z_ prefix convention)
        command "z_deviceLog", [[name: "level", type: "STRING"], [name: "msg", type: "STRING"]]
        command "z_setApiUrl", [[name: "url", type: "STRING"]]

        // Attributes
        attribute "connectionStatus", "string"   // connected, disconnected, connecting, rate limited, error
        attribute "lastEventTime", "string"      // Timestamp of last received event (legacy - formatted)
        attribute "lastEventReceived", "string"  // ISO timestamp of last SSE event for health monitoring
        attribute "rateLimitRemaining", "number" // Remaining API calls (from response headers)
        attribute "rateLimitLimit", "number"     // Total API call limit (from response headers)
        attribute "apiUrl", "string"             // Current API URL being used
        attribute "driverVersion", "string"
    }

    preferences {
        input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false,
              description: "Enable detailed logging for troubleshooting. Disable for normal operation."
    }
}

/* ===========================================================================================================
   CONSTANTS
   =========================================================================================================== */

@Field static final String DEFAULT_API_URL = "https://api.home-connect.com"
@Field static final String ENDPOINT_APPLIANCES = "/api/homeappliances"
@Field static final String DRIVER_VERSION = "3.0.3"

// Reconnect timing constants
@Field static final Integer NORMAL_RECONNECT_DELAY = 300      // 5 minutes after normal disconnect
@Field static final Integer MAX_RECONNECT_ATTEMPTS = 10       // Give up after this many failed attempts
@Field static final Integer RATE_LIMIT_BUFFER = 300           // 5 minute buffer after rate limit expires

/**
 * Gets the API URL - can be overridden by parent app via z_setApiUrl
 */
private String getApiUrl() {
    return state.apiUrl ?: DEFAULT_API_URL
}

/* ===========================================================================================================
   LIFECYCLE METHODS
   =========================================================================================================== */

def installed() {
    log.info "Home Connect Stream Driver v3 installed"
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    sendEvent(name: "connectionStatus", value: "disconnected")
}

def updated() {
    log.info "Home Connect Stream Driver v3 updated"
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
}

/**
 * Called when device is initialized or hub restarts
 * Automatically attempts to connect to Home Connect
 */
def initialize() {
    logDebug("Initializing - attempting to connect")
    connect()
}

/**
 * Refreshes the connection by disconnecting and reconnecting
 */
def refresh() {
    logInfo("Refreshing connection")
    disconnect()
    pauseExecution(1000)
    connect()
}

/* ===========================================================================================================
   SSE CONNECTION MANAGEMENT
   =========================================================================================================== */

/**
 * Establishes SSE connection to Home Connect event stream
 * Handles rate limiting, token validation, and connection setup
 */
def connect() {
    // Check if we're rate limited
    if (state.rateLimitedUntil && now() < state.rateLimitedUntil) {
        def rateLimitTime = state.rateLimitedUntilFormatted ?: formatDateTime(state.rateLimitedUntil)
        logWarn("Cannot connect - rate limited until ${rateLimitTime}")
        sendEvent(name: "connectionStatus", value: "rate limited until ${rateLimitTime}")
        return
    }
    
    // Clear expired rate limit state
    if (state.rateLimitedUntil && now() >= state.rateLimitedUntil) {
        state.rateLimitedUntil = null
        state.rateLimitedUntilFormatted = null
        state.reconnectAttempts = 0
        logInfo("Rate limit expired - cleared")
    }
    
    logDebug("Connecting to Home Connect event stream")
    
    def token = parent?.getOAuthToken()
    if (!token) {
        logError("No OAuth token available - cannot connect")
        sendEvent(name: "connectionStatus", value: "error - no token")
        return
    }
    
    def language = parent?.getLanguage() ?: "en-US"
    
    try {
        interfaces.eventStream.connect(
            "${getApiUrl()}${ENDPOINT_APPLIANCES}/events",
            [
                rawData: true,
                ignoreSSLIssues: true,
                headers: [
                    'Authorization': "Bearer ${token}",
                    'Accept': 'text/event-stream',
                    'Accept-Language': language
                ]
            ]
        )
        sendEvent(name: "connectionStatus", value: "connecting")
        
    } catch (Exception e) {
        logError("Failed to connect: ${e.message}")
        sendEvent(name: "connectionStatus", value: "error")
    }
}

/**
 * Closes the SSE connection
 */
def disconnect() {
    logInfo("Disconnecting from Home Connect event stream")
    try {
        interfaces.eventStream.close()
    } catch (Exception e) {
        logWarn("Error during disconnect: ${e.message}")
    }
    sendEvent(name: "connectionStatus", value: "disconnected")
}

/**
 * Clears rate limit state to allow manual reconnection
 * Use after rate limit has expired if auto-reconnect hasn't triggered
 */
def clearRateLimit() {
    logInfo("Clearing rate limit state manually")
    state.rateLimitedUntil = null
    state.rateLimitedUntilFormatted = null
    state.reconnectAttempts = 0
    sendEvent(name: "connectionStatus", value: "disconnected")
}

/* ===========================================================================================================
   SSE EVENT HANDLING
   =========================================================================================================== */

/**
 * Called by Hubitat when SSE connection status changes
 * Handles reconnection logic with conservative timing to avoid rate limits
 *
 * Reconnect Strategy:
 * - Normal disconnect (connection was successful): Wait 5 minutes, then reconnect
 * - Failed connection (never connected): Exponential backoff (60s, 120s, 240s, max 300s)
 * - Rate limited: Schedule reconnect for when limit expires + 5 min buffer
 */
def eventStreamStatus(String status) {
    logDebug("Event stream status: ${status}")
    
    if (status.contains("START")) {
        // Connection successful
        sendEvent(name: "connectionStatus", value: "connected")
        messageBuffer = ""
        
        state.connectionSucceeded = true
        state.reconnectAttempts = 0
        state.lastConnectTime = now()
        
        // Refresh device status if we were disconnected for more than 5 minutes
        def previousDisconnectTime = state.lastDisconnectTime ?: 0
        def disconnectedDuration = now() - previousDisconnectTime
        
        if (previousDisconnectTime > 0 && disconnectedDuration > 300000) {
            logInfo("Was disconnected for ${(disconnectedDuration/1000).toInteger()}s - refreshing device status")
            runIn(2, "notifyParentReconnected")
        }
        
    } else if (status.contains("STOP") || status.contains("ERROR")) {
        // Connection lost
        sendEvent(name: "connectionStatus", value: "disconnected")
        state.lastDisconnectTime = now()
        
        // Don't reconnect if rate limited
        if (state.rateLimitedUntil && now() < state.rateLimitedUntil) {
            logWarn("Rate limited - not reconnecting")
            return
        }
        
        // Determine reconnect strategy based on whether we successfully connected
        if (state.connectionSucceeded) {
            // Normal disconnect after successful connection - wait 5 minutes
            // This is the typical idle timeout from Home Connect
            state.connectionSucceeded = false
            logDebug("Normal disconnect - scheduling reconnect in ${NORMAL_RECONNECT_DELAY}s")
            runIn(NORMAL_RECONNECT_DELAY, "connect")
        } else {
            // Connection failed without ever succeeding - use exponential backoff
            def attempts = (state.reconnectAttempts ?: 0) + 1
            state.reconnectAttempts = attempts
            
            if (attempts > MAX_RECONNECT_ATTEMPTS) {
                logError("Max reconnect attempts (${MAX_RECONNECT_ATTEMPTS}) reached - giving up. Click 'Connect' to retry manually.")
                sendEvent(name: "connectionStatus", value: "failed - manual reconnect required")
                return
            }
            
            // Exponential backoff: 60s, 120s, 240s, max 300s
            def delay = Math.min(300, 60 * Math.pow(2, attempts - 1) as Integer)
            logWarn("Connection failed - scheduling reconnect in ${delay}s (attempt ${attempts}/${MAX_RECONNECT_ATTEMPTS})")
            runIn(delay, "connect")
        }
    }
}

/**
 * Called by parent app after reconnection to refresh all device status
 */
def notifyParentReconnected() {
    logInfo("Notifying parent to refresh device status")
    parent?.refreshAllDeviceStatus()
}

/**
 * Main entry point for SSE data
 * Called by Hubitat each time data arrives on the event stream
 * 
 * SSE Format:
 * -----------
 * event: STATUS
 * data: {"haId":"...", "items":[...]}
 * 
 * Data may arrive in fragments, so we buffer until we have complete messages
 */
def parse(String text) {
    if (!text) return
    
    // Ignore data if rate limited (prevents processing error responses)
    if (state.rateLimitedUntil && now() < state.rateLimitedUntil) {
        return
    }
    
    logDebug("Raw SSE data: ${text?.take(200)}${text?.length() > 200 ? '...' : ''}")
    
    // Update lastEventReceived timestamp on any incoming data
    updateLastEventReceived()
    
    // Check for rate limit error in the stream
    if (text.contains('"key": "429"') || text.contains('"key":"429"') || text.contains("rate limit")) {
        handleRateLimitError(text)
        return
    }
    
    // Buffer incoming data (SSE messages may span multiple parse() calls)
    messageBuffer += text
    
    // Process complete messages (SSE messages are separated by double newlines)
    while (messageBuffer.contains("\n\n")) {
        def idx = messageBuffer.indexOf("\n\n")
        def message = messageBuffer.substring(0, idx)
        messageBuffer = messageBuffer.substring(idx + 2)
        processSSEMessage(message)
    }
    
    // Also handle single data: lines for implementations that send them individually
    if (text.startsWith("data:")) {
        def payload = text.substring(5).trim()
        if (payload && payload.startsWith("{")) {
            processEventPayload(payload)
        }
    }
}

/**
 * Updates the lastEventReceived timestamp
 * Called only when actual SSE data arrives to track stream health
 * NOT called on connection attempts or API calls
 */
private void updateLastEventReceived() {
    def now = new Date()
    // ISO 8601 format for programmatic use
    sendEvent(name: "lastEventReceived", value: now.format("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
    // Human-readable format (legacy attribute)
    sendEvent(name: "lastEventTime", value: now.format("yyyy-MM-dd HH:mm:ss"))
}

/**
 * Handles rate limit (429) errors from the SSE stream
 * Extracts the retry time and schedules automatic reconnection
 */
private void handleRateLimitError(String text) {
    logError("Rate limit detected in SSE stream - stopping reconnects")
    
    // Extract seconds from error message (e.g., "...remaining period of 86400 seconds")
    def matcher = text =~ /(\d+) seconds/
    def backoffSeconds = matcher ? matcher[0][1].toInteger() : 86400  // Default 24 hours if not found
    
    def rateLimitUntil = now() + (backoffSeconds * 1000)
    state.rateLimitedUntil = rateLimitUntil
    state.reconnectAttempts = 0
    
    def rateLimitTime = formatDateTime(rateLimitUntil)
    state.rateLimitedUntilFormatted = rateLimitTime
    
    sendEvent(name: "connectionStatus", value: "rate limited until ${rateLimitTime}")
    sendEvent(name: "rateLimitRemaining", value: 0)
    logError("Rate limited until ${rateLimitTime}")
    
    // Schedule automatic reconnect when rate limit expires (plus buffer)
    def reconnectDelay = backoffSeconds + RATE_LIMIT_BUFFER
    logInfo("Scheduling automatic reconnect in ${reconnectDelay} seconds (${formatDateTime(now() + reconnectDelay * 1000)})")
    runIn(reconnectDelay, "connect")
}

/**
 * Processes a complete SSE message
 * Extracts event type and data payload, then routes to processEventPayload
 */
private void processSSEMessage(String message) {
    logDebug("Processing SSE message: ${message?.take(100)}${message?.length() > 100 ? '...' : ''}")
    
    String eventType = null
    String dataPayload = null
    
    message.split("\n").each { line ->
        if (line.startsWith("event:")) {
            eventType = line.substring(6).trim()
        } else if (line.startsWith("data:")) {
            dataPayload = line.substring(5).trim()
        }
    }
    
    if (eventType && dataPayload) {
        logDebug("Event type: ${eventType}")
        processEventPayload(dataPayload, eventType)
    } else if (dataPayload) {
        processEventPayload(dataPayload)
    }
}

/**
 * Processes the JSON payload from an SSE event
 * Routes events to the appropriate child device via the parent app
 *
 * Event Types:
 * - KEEP-ALIVE: Connection heartbeat (ignored)
 * - CONNECTED/DISCONNECTED: Appliance online status
 * - STATUS/EVENT/NOTIFY: Appliance state changes (routed to child)
 */
private void processEventPayload(String payload, String eventType = null) {
    if (!payload || !payload.startsWith("{")) return
    
    try {
        def json = new JsonSlurper().parseText(payload)
        
        String haId = json.haId
        if (!haId) {
            logWarn("Event payload missing haId - ignoring")
            return
        }
        
        // Handle keep-alive (heartbeat)
        if (eventType == "KEEP-ALIVE") {
            logDebug("Keep-alive received for appliance ${haId}")
            return
        }
        
        // Handle appliance connection status
        if (eventType == "DISCONNECTED" || eventType == "CONNECTED") {
            logInfo("Appliance ${haId} is now ${eventType}")
            parent?.handleApplianceConnectionEvent(haId, eventType)
            return
        }
        
        // Process status/event items and route to child device
        def items = json.items
        if (items instanceof List) {
            items.each { item ->
                def evt = [
                    haId: haId,
                    key: item.key,
                    value: item.value,
                    displayvalue: item.displayvalue ?: item.value?.toString(),
                    unit: item.unit,
                    eventType: eventType
                ]
                
                logDebug("Routing event to child: ${item.key} = ${item.value}")
                parent?.handleApplianceEvent(evt)
            }
        }
        
    } catch (Exception e) {
        logError("Error parsing event payload: ${e.message}")
    }
}

/* ===========================================================================================================
   HOME CONNECT API - CORE HTTP METHODS
   =========================================================================================================== */

/**
 * Performs an HTTP GET request to the Home Connect API
 * Includes automatic token refresh on 401 errors
 *
 * @param path API endpoint path (e.g., "/api/homeappliances/{haId}/status")
 * @param closure Callback to receive the response data
 */
def apiGet(String path, Closure closure) {
    def token = parent?.getOAuthToken()
    def language = parent?.getLanguage() ?: "en-US"
    
    if (!token) {
        logError("No OAuth token for API GET")
        return
    }
    
    logDebug("API GET: ${path}")
    
    try {
        httpGet(
            uri: getApiUrl() + path,
            contentType: "application/json",
            headers: [
                'Authorization': "Bearer ${token}",
                'Accept-Language': language,
                'Accept': "application/vnd.bsh.sdk.v1+json"
            ]
        ) { response ->
            logDebug("API GET response status: ${response.status}")
            
            // Extract rate limit headers
            extractRateLimitHeaders(response)
            
            if (response.data) {
                closure(response.data)
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        handleHttpError("GET", path, e, closure)
    } catch (Exception e) {
        logError("API GET error: ${e.message} - path: ${path}")
    }
}

/**
 * Performs an HTTP PUT request to the Home Connect API
 * Used for setting values (power state, programs, options)
 *
 * @param path API endpoint path
 * @param data Map of data to send in request body
 * @param closure Callback to receive the response data
 */
def apiPut(String path, Map data, Closure closure) {
    def token = parent?.getOAuthToken()
    def language = parent?.getLanguage() ?: "en-US"
    
    if (!token) {
        logError("No OAuth token for API PUT")
        return
    }
    
    // Check rate limit before making request
    if (state.rateLimitedUntil && now() < state.rateLimitedUntil) {
        logWarn("API PUT blocked - rate limited")
        return
    }
    
    String body = new JsonOutput().toJson(data)
    logDebug("API PUT: ${path}")
    
    try {
        httpPut(
            uri: getApiUrl() + path,
            contentType: "application/json",
            requestContentType: "application/json",
            body: body,
            headers: [
                'Authorization': "Bearer ${token}",
                'Accept-Language': language,
                'Accept': "application/vnd.bsh.sdk.v1+json"
            ]
        ) { response ->
            logDebug("API PUT response status: ${response.status}")
            
            // Extract rate limit headers
            extractRateLimitHeaders(response)
            
            if (response.data) {
                closure(response.data)
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        handleHttpError("PUT", path, e, closure)
    } catch (Exception e) {
        logError("API PUT error: ${e.message} - path: ${path}")
    }
}

/**
 * Performs an HTTP DELETE request to the Home Connect API
 * Used for stopping programs
 *
 * @param path API endpoint path
 * @param closure Callback to receive the response data
 */
def apiDelete(String path, Closure closure) {
    def token = parent?.getOAuthToken()
    def language = parent?.getLanguage() ?: "en-US"
    
    if (!token) {
        logError("No OAuth token for API DELETE")
        return
    }
    
    // Check rate limit before making request
    if (state.rateLimitedUntil && now() < state.rateLimitedUntil) {
        logWarn("API DELETE blocked - rate limited")
        return
    }
    
    logDebug("API DELETE: ${path}")
    
    try {
        httpDelete(
            uri: getApiUrl() + path,
            contentType: "application/json",
            requestContentType: "application/json",
            headers: [
                'Authorization': "Bearer ${token}",
                'Accept-Language': language,
                'Accept': "application/vnd.bsh.sdk.v1+json"
            ]
        ) { response ->
            logDebug("API DELETE response status: ${response.status}")
            
            // Extract rate limit headers
            extractRateLimitHeaders(response)
            
            if (response.data) {
                closure(response.data)
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        handleHttpError("DELETE", path, e, closure)
    } catch (Exception e) {
        logError("API DELETE error: ${e.message} - path: ${path}")
    }
}

/**
 * Extracts rate limit information from API response headers
 * Updates rateLimitRemaining and rateLimitLimit attributes
 *
 * Home Connect returns these headers on API responses:
 * - X-RateLimit-Limit: Total allowed calls (typically 1000)
 * - X-RateLimit-Remaining: Calls remaining in the current period
 */
private void extractRateLimitHeaders(response) {
    try {
        def headers = response.getHeaders()
        
        // Try different header name formats (Home Connect uses X-RateLimit-*)
        def remaining = headers?.find { it.name?.equalsIgnoreCase('X-RateLimit-Remaining') }?.value
        def limit = headers?.find { it.name?.equalsIgnoreCase('X-RateLimit-Limit') }?.value
        
        if (remaining != null) {
            def remainingInt = remaining.toString().toInteger()
            sendEvent(name: "rateLimitRemaining", value: remainingInt)
            logDebug("Rate limit remaining: ${remainingInt}")
            
            // Warn if getting low
            if (remainingInt < 100) {
                logWarn("Rate limit warning: only ${remainingInt} API calls remaining")
            }
        }
        
        if (limit != null) {
            def limitInt = limit.toString().toInteger()
            sendEvent(name: "rateLimitLimit", value: limitInt)
            logDebug("Rate limit total: ${limitInt}")
        }
    } catch (Exception e) {
        logDebug("Could not extract rate limit headers: ${e.message}")
    }
}

/**
 * Handles HTTP errors from API calls
 * Implements retry logic for 401 (token expired) errors
 */
private void handleHttpError(String method, String path, groovyx.net.http.HttpResponseException e, Closure closure) {
    def statusCode = e.getStatusCode()
    def responseData = e.getResponse()?.getData()
    
    // Try to extract rate limit headers even from error responses
    try {
        extractRateLimitHeaders(e.getResponse())
    } catch (Exception ex) {
        // Ignore - not all error responses have headers accessible
    }
    
    switch (statusCode) {
        case 401:
            logWarn("API ${method} 401 Unauthorized - refreshing token")
            if (parent?.refreshOAuthTokenAndRetry()) {
                logInfo("Retrying API ${method} after token refresh")
                // Retry based on method type
                switch (method) {
                    case "GET": apiGetRetry(path, closure); break
                    case "PUT": logWarn("PUT retry not implemented - please retry manually"); break
                    case "DELETE": apiDeleteRetry(path, closure); break
                }
            }
            break
            
        case 404:
            if (path.contains('programs/active')) {
                // No active program - expected when appliance is idle
                throw new Exception("No active program")
            } else {
                logWarn("API ${method} 404 Not Found: ${path}")
            }
            break
            
        case 409:
            logWarn("API ${method} 409 Conflict - command cannot be executed in current state")
            break
            
        case 429:
            logError("API ${method} 429 Rate Limited")
            sendEvent(name: "rateLimitRemaining", value: 0)
            state.rateLimitedUntil = now() + 60000  // Back off for 1 minute
            break
            
        case 503:
            logWarn("API ${method} 503 Service Unavailable - appliance may be offline")
            break
            
        default:
            logError("API ${method} error ${statusCode}: ${responseData} - path: ${path}")
    }
}

/**
 * Retry helper for GET requests after token refresh
 */
private void apiGetRetry(String path, Closure closure) {
    def token = parent?.getOAuthToken()
    def language = parent?.getLanguage() ?: "en-US"
    
    if (!token) {
        logError("No OAuth token for API GET retry")
        return
    }
    
    try {
        httpGet(
            uri: getApiUrl() + path,
            contentType: "application/json",
            headers: [
                'Authorization': "Bearer ${token}",
                'Accept-Language': language,
                'Accept': "application/vnd.bsh.sdk.v1+json"
            ]
        ) { response ->
            logDebug("API GET retry response: ${response.status}")
            extractRateLimitHeaders(response)
            if (response.data) {
                closure(response.data)
            }
        }
    } catch (Exception e) {
        logError("API GET retry failed: ${e.message}")
    }
}

/**
 * Retry helper for DELETE requests after token refresh
 */
private void apiDeleteRetry(String path, Closure closure) {
    def token = parent?.getOAuthToken()
    def language = parent?.getLanguage() ?: "en-US"
    
    if (!token) {
        logError("No OAuth token for API DELETE retry")
        return
    }
    
    try {
        httpDelete(
            uri: getApiUrl() + path,
            contentType: "application/json",
            requestContentType: "application/json",
            headers: [
                'Authorization': "Bearer ${token}",
                'Accept-Language': language,
                'Accept': "application/vnd.bsh.sdk.v1+json"
            ]
        ) { response ->
            logDebug("API DELETE retry response: ${response.status}")
            extractRateLimitHeaders(response)
            if (response.data) {
                closure(response.data)
            }
        }
    } catch (Exception e) {
        logError("API DELETE retry failed: ${e.message}")
    }
}

/* ===========================================================================================================
   HOME CONNECT API - APPLIANCE METHODS
   =========================================================================================================== */

/**
 * Retrieves list of all Home Connect appliances registered to the user
 */
def getHomeAppliances(Closure closure) {
    logDebug("Retrieving all Home Appliances")
    apiGet("${ENDPOINT_APPLIANCES}") { response ->
        closure(response.data?.homeappliances ?: [])
    }
}

/**
 * Retrieves details for a specific appliance
 */
def getHomeAppliance(String haId, Closure closure) {
    logDebug("Retrieving appliance ${haId}")
    apiGet("${ENDPOINT_APPLIANCES}/${haId}") { response ->
        closure(response.data)
    }
}

/* ===========================================================================================================
   HOME CONNECT API - PROGRAM METHODS
   =========================================================================================================== */

/**
 * Gets list of available programs for an appliance
 */
def getAvailablePrograms(String haId, Closure closure) {
    logDebug("Retrieving available programs for ${haId}")
    apiGet("${ENDPOINT_APPLIANCES}/${haId}/programs/available") { response ->
        closure(response.data?.programs ?: [])
    }
}

/**
 * Gets details for a specific available program (including options)
 */
def getAvailableProgram(String haId, String programKey, Closure closure) {
    logDebug("Retrieving program ${programKey} for ${haId}")
    apiGet("${ENDPOINT_APPLIANCES}/${haId}/programs/available/${programKey}") { response ->
        closure(response.data)
    }
}

/**
 * Gets the currently active (running) program
 */
def getActiveProgram(String haId, Closure closure) {
    logDebug("Retrieving active program for ${haId}")
    apiGet("${ENDPOINT_APPLIANCES}/${haId}/programs/active") { response ->
        closure(response.data)
    }
}

/**
 * Starts a program on the appliance
 * 
 * @param haId Appliance ID
 * @param programKey Program key (e.g., "Dishcare.Dishwasher.Program.Eco50")
 * @param options Optional program options
 */
def setActiveProgram(String haId, String programKey, def options = "", Closure closure) {
    def data = [key: programKey]
    if (options != "") {
        data.put("options", options)
    }
    logInfo("Starting program ${programKey} on ${haId}")
    apiPut("${ENDPOINT_APPLIANCES}/${haId}/programs/active", [data: data]) { response ->
        closure(response.data)
    }
}

/**
 * Stops the currently running program
 */
def stopActiveProgram(String haId, Closure closure) {
    logInfo("Stopping program on ${haId}")
    apiDelete("${ENDPOINT_APPLIANCES}/${haId}/programs/active") { response ->
        closure(response.data)
    }
}

/**
 * Gets the currently selected (but not started) program
 */
def getSelectedProgram(String haId, Closure closure) {
    logDebug("Retrieving selected program for ${haId}")
    apiGet("${ENDPOINT_APPLIANCES}/${haId}/programs/selected") { response ->
        closure(response.data)
    }
}

/**
 * Sets the selected program (without starting it)
 */
def setSelectedProgram(String haId, String programKey, def options = "", Closure closure) {
    def data = [key: programKey]
    if (options != "") {
        data.put("options", options)
    }
    logDebug("Setting selected program ${programKey} on ${haId}")
    apiPut("${ENDPOINT_APPLIANCES}/${haId}/programs/selected", [data: data]) { response ->
        closure(response.data)
    }
}

/**
 * Sets an option on the selected program
 */
def setSelectedProgramOption(String haId, String optionKey, def optionValue, Closure closure) {
    def data = [key: optionKey, value: optionValue]
    logDebug("Setting program option ${optionKey}=${optionValue} on ${haId}")
    apiPut("${ENDPOINT_APPLIANCES}/${haId}/programs/selected/options/${optionKey}", [data: data]) { response ->
        closure(response.data)
    }
}

/* ===========================================================================================================
   HOME CONNECT API - STATUS & SETTINGS METHODS
   =========================================================================================================== */

/**
 * Gets current status of an appliance (operation state, door state, etc.)
 */
def getStatus(String haId, Closure closure) {
    logDebug("Retrieving status for ${haId}")
    apiGet("${ENDPOINT_APPLIANCES}/${haId}/status") { response ->
        closure(response.data?.status ?: [])
    }
}

/**
 * Gets current settings of an appliance (power state, etc.)
 */
def getSettings(String haId, Closure closure) {
    logDebug("Retrieving settings for ${haId}")
    apiGet("${ENDPOINT_APPLIANCES}/${haId}/settings") { response ->
        closure(response.data?.settings ?: [])
    }
}

/**
 * Sets a setting on an appliance (e.g., power state)
 */
def setSetting(String haId, String settingKey, def value, Closure closure) {
    logInfo("Setting ${settingKey}=${value} on ${haId}")
    apiPut("${ENDPOINT_APPLIANCES}/${haId}/settings/${settingKey}", [data: [key: settingKey, value: value]]) { response ->
        closure(response.data)
    }
}

/* ===========================================================================================================
   UTILITY METHODS
   =========================================================================================================== */

/**
 * Converts a map to a URL query string
 */
def toQueryString(Map m) {
    return m.collect { k, v -> "${k}=${new URI(null, null, v.toString(), null)}" }.sort().join("&")
}

/**
 * Converts seconds to HH:MM format
 */
def convertSecondsToTime(Integer sec) {
    if (!sec || sec <= 0) return "00:00"
    long hours = java.util.concurrent.TimeUnit.SECONDS.toHours(sec)
    long minutes = java.util.concurrent.TimeUnit.SECONDS.toMinutes(sec) % 60
    return String.format("%02d:%02d", hours, minutes)
}

/**
 * Extracts the last segment from a dotted enum value
 * e.g., "BSH.Common.EnumType.PowerState.On" → "On"
 */
def extractEnumValue(String full) {
    if (!full) return null
    return full.substring(full.lastIndexOf(".") + 1)
}

/**
 * Formats a timestamp as human-readable date/time
 */
private String formatDateTime(Long timestamp) {
    def date = new Date(timestamp)
    return date.format("yyyy-MM-dd h:mm a", location?.timeZone ?: TimeZone.getDefault())
}

/**
 * Returns map of supported Home Connect languages/regions
 */
def getSupportedLanguages() {
    return [
        "Bulgarian": ["Bulgaria": "bg-BG"],
        "Chinese (Simplified)": ["China": "zh-CN", "Hong Kong": "zh-HK", "Taiwan": "zh-TW"],
        "Czech": ["Czech Republic": "cs-CZ"],
        "Danish": ["Denmark": "da-DK"],
        "Dutch": ["Belgium": "nl-BE", "Netherlands": "nl-NL"],
        "English": ["Australia": "en-AU", "Canada": "en-CA", "India": "en-IN", "New Zealand": "en-NZ", 
                    "Singapore": "en-SG", "South Africa": "en-ZA", "United Kingdom": "en-GB", "United States": "en-US"],
        "Finnish": ["Finland": "fi-FI"],
        "French": ["Belgium": "fr-BE", "Canada": "fr-CA", "France": "fr-FR", "Luxembourg": "fr-LU", "Switzerland": "fr-CH"],
        "German": ["Austria": "de-AT", "Germany": "de-DE", "Luxembourg": "de-LU", "Switzerland": "de-CH"],
        "Greek": ["Greece": "el-GR"],
        "Hungarian": ["Hungary": "hu-HU"],
        "Italian": ["Italy": "it-IT", "Switzerland": "it-CH"],
        "Norwegian": ["Norway": "nb-NO"],
        "Polish": ["Poland": "pl-PL"],
        "Portuguese": ["Portugal": "pt-PT"],
        "Romanian": ["Romania": "ro-RO"],
        "Russian": ["Russian Federation": "ru-RU"],
        "Serbian": ["Serbia": "sr-SR"],
        "Slovak": ["Slovakia": "sk-SK"],
        "Slovenian": ["Slovenia": "sl-SI"],
        "Spanish": ["Chile": "es-CL", "Peru": "es-PE", "Spain": "es-ES"],
        "Swedish": ["Sweden": "sv-SE"],
        "Turkish": ["Turkey": "tr-TR"],
        "Ukrainian": ["Ukraine": "uk-UA"]
    ]
}

/**
 * Flattens the language map for use in preference dropdowns
 */
def toFlattenedLanguageMap(Map m) {
    return m.collectEntries { k, v ->
        def flattened = [:]
        if (v instanceof Map) {
            v.each { k1, v1 ->
                flattened << ["${v1}": "${k} - ${k1} (${v1})"]
            }
        } else {
            flattened << ["${k}": v]
        }
        return flattened
    }
}

/* ===========================================================================================================
   LOGGING METHODS
   =========================================================================================================== */

/**
 * Internal command for logging (z_ prefix convention)
 * Can be called from other components if needed
 */
def z_deviceLog(String level, String msg) {
    switch (level) {
        case "debug": logDebug(msg); break
        case "info": logInfo(msg); break
        case "warn": logWarn(msg); break
        case "error": logError(msg); break
        default: log.info "Home Connect Stream: ${msg}"
    }
}

/**
 * Sets the API URL to use for Home Connect calls
 * Called by parent app - allows different apps to use different endpoints (production vs simulator)
 */
def z_setApiUrl(String url) {
    state.apiUrl = url
    sendEvent(name: "apiUrl", value: url)
    logInfo("API URL set to: ${url}")
}

private void logDebug(String msg) {
    if (debugLogging) {
        log.debug "Home Connect Stream: ${msg}"
    }
}

private void logInfo(String msg) {
    log.info "Home Connect Stream: ${msg}"
}

private void logWarn(String msg) {
    log.warn "Home Connect Stream: ${msg}"
}

private void logError(String msg) {
    log.error "Home Connect Stream: ${msg}"
}
