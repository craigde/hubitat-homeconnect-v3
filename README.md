# Home Connect Integration v3 for Hubitat

A Hubitat Elevation integration for Home Connect smart appliances (Bosch, Siemens, Thermador, Gaggenau, Neff).

## Features

- **Real-time updates** via Server-Sent Events (SSE) - no polling
- **Full appliance control** - start/stop programs, adjust settings, power on/off
- **Dashboard ready** - standard capabilities for native Hubitat dashboard tiles
- **Rule Machine compatible** - trigger automations on cycle complete, alerts, etc.
- **Node-RED friendly** - JSON state attribute for easy integration
- **Conservative API usage** - respects Home Connect rate limits (1000 calls/day)

## Supported Appliances

| Appliance | Driver | Programs | Timing | Door State |
|-----------|--------|:--------:|:------:|:----------:|
| Dishwasher | ✅ | ✅ | ✅ | ✅ |
| Washer | 🚧 | 🚧 | 🚧 | 🚧 |
| Dryer | 🚧 | 🚧 | 🚧 | 🚧 |
| Washer/Dryer | 🚧 | 🚧 | 🚧 | 🚧 |
| Oven | 🚧 | 🚧 | 🚧 | 🚧 |
| Coffee Maker | 🚧 | 🚧 | 🚧 | - |
| Fridge/Freezer | 🚧 | - | - | 🚧 |
| Hood | 🚧 | 🚧 | - | - |
| Hob/Cooktop | 🚧 | - | - | - |

✅ = Available | 🚧 = Coming Soon | - = Not supported by appliance

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
└───────────┬─────────────────────────────────┬───────────────────┘
            │                                 │
            ▼                                 ▼
┌───────────────────────┐         ┌───────────────────────┐
│   Dishwasher Driver   │         │    Other Drivers...   │
│  • parseEvent()       │         │                       │
│  • Device attributes  │         │                       │
│  • User commands      │         │                       │
└───────────────────────┘         └───────────────────────┘
```

### Why This Architecture?

1. **Single SSE Connection**: Home Connect allows only one event stream per account. The Stream Driver maintains this connection and routes events to the appropriate appliance.

2. **Shared API Library**: All API calls go through the Stream Driver, centralizing authentication, error handling, and rate limit management.

3. **Simple Child Drivers**: Appliance drivers only need to handle events and expose commands - no complex API or connection logic.

4. **Easy to Extend**: Adding a new appliance type only requires creating a new driver with the appropriate attributes and event handling.

## Installation

### Prerequisites

1. A Home Connect account with registered appliances
2. Home Connect Developer account ([developer.home-connect.com](https://developer.home-connect.com))

### Step 1: Install Drivers and App in Hubitat

1. In Hubitat, go to **Drivers Code**
2. Click **+ New Driver** and paste the code for:
   - `Home Connect Stream Driver v3`
   - `Home Connect Dishwasher v3` (and other appliance drivers you need)
3. Go to **Apps Code**
4. Click **+ New App** and paste the code for:
   - `Home Connect Integration v3`
5. Go to **Apps** → **Add User App** → **Home Connect Integration v3**
6. On the first page, note the **Redirect URI** shown - you'll need this for Step 2

### Step 2: Create Home Connect Application

1. Log in to the [Home Connect Developer Portal](https://developer.home-connect.com)
2. Create a new application with these settings:
   - **Application ID**: `hubitat-homeconnect-integration`
   - **OAuth Flow**: Authorization Code Grant Flow
   - **Redirect URI**: Use the URL from Step 1.6 (it will look like `https://cloud.hubitat.com/api/xxx/apps/xxx/oauth/callback`)
3. Note your **Client ID** and **Client Secret**
4. **Wait 30 minutes** - Home Connect requires propagation time before you can authenticate

### Step 3: Configure the Integration

1. Return to the Hubitat app configuration (or go to **Apps** → **Home Connect Integration v3**)
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
| Switch | Switch tile | Power on/off |
| Contact Sensor | Contact tile | Door open/closed |
| PushableButton | - | Triggers for automations |

Custom attributes can be displayed using Attribute tiles:
- `friendlyStatus` - "Running", "Ready", "Complete", etc.
- `remainingProgramTimeFormatted` - "01:23"
- `programProgress` - 0-100
- `progressBar` - "45%"
- `estimatedEndTimeFormatted` - "3:45 PM"

### Rule Machine

#### Trigger on Cycle Complete

```
Trigger: Button 1 pushed on [Dishwasher]
Action: Send notification "Dishwasher cycle complete!"
```

#### Trigger on Alerts

| Button | Alert |
|--------|-------|
| 1 | Cycle Complete |
| 2 | Salt Low |
| 3 | Rinse Aid Low |
| 4 | Error |

#### Check Status in Rules

Use custom attributes as conditions:
- `operationState` = "Run" (dishwasher is running)
- `doorState` = "Open" (door is open)
- `remoteControlStartAllowed` = "true" (can start remotely)

### Node-RED Integration

The `jsonState` attribute provides all device state in a single JSON object:

```json
{
  "operationState": "Run",
  "doorState": "Closed",
  "powerState": "On",
  "friendlyStatus": "Running",
  "activeProgram": "Eco50",
  "programProgress": 45,
  "cyclePhase": "Wash",
  "remainingProgramTime": 2700,
  "remainingProgramTimeFormatted": "00:45",
  "estimatedEndTime": "3:45 PM",
  "remoteControlStartAllowed": "true",
  "saltLow": false,
  "rinseAidLow": false,
  "lastUpdate": "2026-01-06T12:30:45.123-0800"
}
```

Use the Maker API to subscribe to attribute changes:
1. Enable Maker API in Hubitat
2. Add your dishwasher device
3. Subscribe to the `jsonState` attribute
4. Parse with a JSON node in Node-RED

### Commands

#### Dishwasher Commands

| Command | Description |
|---------|-------------|
| `on` / `off` | Power on/off |
| `startProgram(program)` | Start a program from dropdown |
| `startProgramByKey(key)` | Start using full program key |
| `startProgramDelayed(minutes, program)` | Delayed start |
| `stopProgram` | Stop running program |
| `getAvailablePrograms` | Fetch program list from appliance |
| `refresh` | Refresh all status |

#### Program Keys

Common dishwasher programs:
- `Dishcare.Dishwasher.Program.Auto1`
- `Dishcare.Dishwasher.Program.Auto2`
- `Dishcare.Dishwasher.Program.Eco50`
- `Dishcare.Dishwasher.Program.Quick45`
- `Dishcare.Dishwasher.Program.Intensiv70`
- `Dishcare.Dishwasher.Program.PreRinse`

Use `getAvailablePrograms` to see what your specific appliance supports.

## Rate Limits

Home Connect enforces strict rate limits:
- **1000 API calls per day**
- **50 calls per hour per appliance**

This integration is designed to stay well within limits:

| Activity | API Calls |
|----------|-----------|
| SSE reconnection | 1 per reconnect |
| Status refresh | 2-3 per device |
| Start/stop program | 1 |
| Normal operation | ~300/day |

The Stream Driver automatically:
- Reconnects every 5 minutes during idle (normal SSE timeout)
- Uses exponential backoff on connection failures
- Detects rate limiting and schedules auto-recovery
- Shows rate limit status and expiry time

## Troubleshooting

### "Rate limited until..."

You've exceeded the daily API limit. The integration will automatically reconnect when the limit expires. This typically resets at midnight UTC.

**Prevention**: Avoid frequent manual refreshes or reinstalls.

### "No OAuth token available"

Re-authenticate:
1. Go to the app settings
2. Click the authentication link
3. Log in to Home Connect again

### Events not updating

1. Check Stream Driver status:
   - Go to **Devices** → **HC3-StreamDriver**
   - Check `connectionStatus` attribute
2. If "disconnected" or "failed", click **Connect**
3. Enable debug logging to see event flow

### Appliance shows offline

The appliance may be:
- Powered off
- Not connected to WiFi
- In a state that doesn't allow remote control

Check the Home Connect mobile app to verify connectivity.

## Version History

### v3.0.0
- Complete architecture redesign
- Stream Driver for SSE connection
- Conservative rate limit handling
- PushableButton for notifications
- JSON state for Node-RED
- Improved error handling and logging

## Contributing

Contributions welcome! To add support for a new appliance:

1. Create a new driver based on the Dishwasher template
2. Update attributes for the appliance type
3. Implement `parseEvent()` for relevant Home Connect keys
4. Add the driver mapping in the Parent App

## License

Apache License 2.0

## Credits

- Original concept inspired by various Hubitat community integrations
- Home Connect API documentation: [api-docs.home-connect.com](https://api-docs.home-connect.com)
