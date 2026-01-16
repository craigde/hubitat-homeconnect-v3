# Home Connect Integration v3 for Hubitat

A Hubitat Elevation integration for Home Connect smart appliances (Bosch, Siemens, Thermador, Gaggenau, Neff).

## Features

- **Real-time updates** via Server-Sent Events (SSE) - no polling
- **Full appliance control** - start/stop programs, adjust settings, power on/off
- **Complete appliance coverage** - 13 drivers covering all Home Connect device types
- **Dashboard ready** - standard capabilities for native Hubitat dashboard tiles
- **Rule Machine compatible** - trigger automations on cycle complete, alerts, etc.
- **Node-RED friendly** - JSON state attribute for easy integration
- **Built-in debugging** - troubleshooting commands for remote support
- **Conservative API usage** - respects Home Connect rate limits (1000 calls/day)

## Supported Appliances

| Appliance | Driver | Status | Key Features |
|-----------|--------|:------:|--------------|
| Dishwasher | ✅ | Complete | Programs, timing, salt/rinse aid alerts |
| Coffee Maker | ✅ | Complete | Beverages, bean/water levels, drink counters |
| Oven | ✅ | Complete | Heating modes, temp (F/C), meat probe, preheat alerts |
| Hood | ✅ | Complete | Fan speed (5 levels + intensive), ambient lighting |
| Cooktop/Hob | ✅ | Complete | Zone monitoring (6 zones), timer alerts |
| Fridge/Freezer | ✅ | Complete | Temp control, multi-door, super modes, dispenser |
| Warming Drawer | ✅ | Complete | Warming levels, programs, push-to-open |
| Washer | ✅ | Complete | Programs, temp, spin speed, i-Dos dosing |
| Dryer | ✅ | Complete | Programs, drying target, lint/condenser alerts |
| Washer/Dryer | ✅ | Complete | Combined wash+dry, mode tracking |
| Cook Processor | ✅ | Complete | Manual mode, temp/speed control, step navigation |
| Cleaning Robot | ✅ | Complete | Battery, dock status, stuck detection, zones |

All drivers include:
- Real-time SSE event updates
- PushableButton capability for Rule Machine triggers
- JSON state attribute for external integrations
- Debugging commands (`dumpState`, `getDiscoveredKeys`, `getRecentEvents`)

## Architecture

The v3 integration uses a three-component architecture:

```
┌─────────────────────────────────────────────────────────────────┐
│                     Home Connect Cloud                          │
└──────────────────────────┬──────────────────────────────────────┘
                           │ SSE Events + REST API
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                 Stream Driver (HC3-StreamDriver)                │
│  • Single SSE connection for all appliances                     │
│  • API library (GET/PUT/DELETE)                                 │
│  • Rate limit management                                        │
│  • Automatic reconnection with backoff                          │
└──────────────────────────┬──────────────────────────────────────┘
                           │ Events + API delegation
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│              Parent App (Home Connect Integration v3)           │
│  • OAuth authentication                                         │
│  • Device discovery                                             │
│  • Event routing to child devices                               │
└───────────┬─────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Appliance Drivers                          │
│  Dishwasher │ CoffeeMaker │ Oven │ Hood │ Cooktop │ ...        │
│  • parseEvent() handles SSE events                              │
│  • Device attributes for dashboards                             │
│  • User commands for control                                    │
└─────────────────────────────────────────────────────────────────┘
```

### Why This Architecture?

1. **Single SSE Connection**: Home Connect allows only one event stream per account. The Stream Driver maintains this connection and routes events to the appropriate appliance.

2. **Shared API Library**: All API calls go through the Stream Driver, centralizing authentication, error handling, and rate limit management.

3. **Simple Child Drivers**: Appliance drivers only need to handle events and expose commands - no complex API or connection logic.

4. **Easy to Extend**: Adding a new appliance type only requires creating a new driver with the appropriate attributes and event handling.

## Installation

### Option 1: Hubitat Package Manager (Recommended)

1. Install [Hubitat Package Manager](https://github.com/dcmeglio/hubitat-packagemanager) if you haven't already
2. Go to **Apps** → **Hubitat Package Manager** → **Install**
3. Search for "Home Connect Integration v3"
4. Install the package
5. Continue to **Step 2: Create Home Connect Application** below

### Option 2: Manual Installation

1. In Hubitat, go to **Drivers Code**
2. Click **+ New Driver** and paste the code for:
   - `Home Connect Stream Driver v3` (required)
   - Appliance drivers for your devices (e.g., `Home Connect Dishwasher v3`)
3. Go to **Apps Code**
4. Click **+ New App** and paste the code for:
   - `Home Connect Integration v3`

### Step 2: Create Home Connect Application

1. Go to **Apps** → **Add User App** → **Home Connect Integration v3**
2. On the first page, note the **Redirect URI** shown - you'll need this next
3. Log in to the [Home Connect Developer Portal](https://developer.home-connect.com)
4. Create a new application with these settings:
   - **Application ID**: `hubitat-homeconnect-integration`
   - **OAuth Flow**: Authorization Code Grant Flow
   - **Redirect URI**: Use the URL from step 2 (looks like `https://cloud.hubitat.com/api/xxx/apps/xxx/oauth/callback`)
5. Note your **Client ID** and **Client Secret**
6. **Wait 30 minutes** - Home Connect requires propagation time before you can authenticate

### Step 3: Configure the Integration

1. Return to the Hubitat app configuration
2. Enter your Client ID and Client Secret
3. Select your region/language
4. Click **Next** and authenticate with Home Connect
5. Select your appliances
6. Click **Done**

## Usage

### Dashboard Tiles

The drivers support standard Hubitat capabilities:

| Capability | Dashboard Tile | Shows |
|------------|----------------|-------|
| Switch | Switch tile | Running/Idle or Power on/off |
| Contact Sensor | Contact tile | Door open/closed |
| Temperature | Temperature tile | Current temperature |
| PushableButton | - | Triggers for automations |

Custom attributes can be displayed using Attribute tiles:
- `friendlyStatus` - "Running (45%)", "Ready", "Done - Door Locked", etc.
- `remainingProgramTimeFormatted` - "01:23"
- `programProgress` - 0-100
- `estimatedEndTimeFormatted` - "3:45 PM"

### Rule Machine

#### Trigger on Cycle Complete

```
Trigger: Button 1 pushed on [Dishwasher]
Action: Send notification "Dishwasher cycle complete!"
```

#### Button Events by Appliance

**Dishwasher:**
| Button | Event |
|--------|-------|
| 1 | Cycle Complete |
| 2 | Salt Low |
| 3 | Rinse Aid Low |

**Washer/Dryer:**
| Button | Event |
|--------|-------|
| 1 | Cycle Complete |
| 2 | Door Unlocked |
| 3 | i-Dos/Lint Filter Alert |

**Oven:**
| Button | Event |
|--------|-------|
| 1 | Program Complete |
| 2 | Preheat Complete |
| 3 | Meat Probe Target Reached |
| 4 | Timer Complete |

**Coffee Maker:**
| Button | Event |
|--------|-------|
| 1 | Beverage Ready |
| 2 | Bean Container Empty |
| 3 | Water Tank Empty |
| 4 | Drip Tray Full |

See individual driver documentation for complete button mappings.

### Node-RED Integration

The `jsonState` attribute provides all device state in a single JSON object:

```json
{
  "operationState": "Run",
  "doorState": "Closed",
  "powerState": "On",
  "friendlyStatus": "Running (45%)",
  "activeProgram": "Eco50",
  "programProgress": 45,
  "remainingProgramTime": 2700,
  "remainingProgramTimeFormatted": "00:45",
  "estimatedEndTime": "3:45 PM",
  "remoteControlStartAllowed": "true",
  "lastUpdate": "2026-01-15T12:30:45.123-0800"
}
```

Use the Maker API to subscribe to attribute changes.

### Common Commands

| Command | Description |
|---------|-------------|
| `on` / `off` | Power on/off or start/stop |
| `startProgram(program)` | Start a program |
| `stopProgram` | Stop running program |
| `pauseProgram` / `resumeProgram` | Pause/resume (if supported) |
| `getAvailablePrograms` | Fetch available programs |
| `refresh` | Refresh all status |

### Debugging Commands

All drivers include these debugging commands:

| Command | Description |
|---------|-------------|
| `dumpState` | Output all attributes and state to logs |
| `getDiscoveredKeys` | Show all event keys received from appliance |
| `clearDiscoveredKeys` | Reset discovered keys list |
| `getRecentEvents(n)` | Show last N events received |

Enable **Debug Logging** in driver preferences for detailed event logging.

## Rate Limits

Home Connect enforces strict rate limits:
- **1000 API calls per day**
- **50 calls per hour per appliance**

This integration is designed to stay well within limits:

| Activity | API Calls |
|----------|-----------|
| SSE connection | 1 per reconnect |
| Status refresh | 2-3 per device |
| Start/stop program | 1 |
| Normal operation | ~100-300/day |

The Stream Driver automatically:
- Reconnects every 5 minutes during idle (normal SSE timeout)
- Uses exponential backoff on connection failures
- Detects rate limiting and schedules auto-recovery
- Shows rate limit status in device attributes

## Troubleshooting

### OAuth Authentication Failed

1. Verify Client ID and Client Secret are correct (no extra spaces)
2. Ensure Redirect URI in Home Connect Developer Portal **exactly** matches what's shown in the app
3. Wait at least 5 minutes after creating the application
4. Check Hubitat logs for specific error messages

### "Rate limited until..."

You've exceeded the daily API limit. The integration will automatically reconnect when the limit expires (typically midnight UTC).

### Events not updating

1. Check Stream Driver status:
   - Go to **Devices** → **HC3-StreamDriver**
   - Check `connectionStatus` attribute
2. If "disconnected", click **Connect**
3. Enable debug logging to see event flow

### Appliance shows offline

The appliance may be:
- Powered off at the wall
- Not connected to WiFi
- In a state that doesn't allow remote control

Check the Home Connect mobile app to verify connectivity.

### Unknown events in logs

If you see "UNHANDLED SIGNIFICANT EVENT" in logs:
1. Run `getDiscoveredKeys` command on the device
2. Share the output when reporting issues
3. This helps improve driver coverage

## Version History

### v3.2.0 (2026-01-15)
- Complete appliance coverage - all 13 Home Connect device types supported
- Enhanced debugging commands for remote troubleshooting
- Improved OAuth error handling and logging

### v3.0.6 (2026-01-11)
- Fixed device creation timing issue on first install

### v3.0.0 (2026-01-07)
- Complete architecture redesign
- Stream Driver for SSE connection
- Conservative rate limit handling
- PushableButton for notifications
- JSON state for Node-RED

## Contributing

Contributions welcome! 

If you encounter issues:
1. Enable debug logging
2. Run `dumpState` and `getDiscoveredKeys` on affected device
3. Share logs when opening an issue

## License

Apache License 2.0

## Credits

- Home Connect API documentation: [api-docs.home-connect.com](https://api-docs.home-connect.com)
- Hubitat community for testing and feedback
