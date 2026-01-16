# Home Connect Integration v3 for Hubitat

A complete rewrite of the Home Connect integration for Hubitat Elevation, supporting Bosch, Siemens, Thermador, Gaggenau, and Neff smart appliances.

## What's New in v3?

This isn't just an update—it's a ground-up redesign with significant improvements:

### Architecture & Reliability

**Three-Component Architecture:**
- **Stream Driver**: Handles the Server-Sent Events (SSE) connection to Home Connect's event stream. This persistent connection receives real-time updates about your appliances' status, ensuring immediate notification of cycle completions, errors, and state changes without polling.
- **Parent App**: Manages OAuth authentication and device discovery. This is your control center for authorizing with Home Connect, discovering new appliances, and managing which devices are added to your Hubitat hub.
- **Child Drivers**: Dedicated drivers for each appliance type. Each child driver translates Home Connect's API data into Hubitat attributes and commands, providing appliance-specific capabilities and controls.

**New OAuth Implementation:**
- Stateless validation eliminates race conditions and timing issues people experienced previously
- Improved error handling with clear, actionable error messages
- More reliable authentication experience that "just works"

### Enhanced Features

- **Button notification system** for Rule Machine triggers (cycle complete, maintenance alerts, errors)
- **Delayed program start** support for cost optimization (start during off-peak electricity hours)
- **Node-RED compatibility** through JSON state attributes
- **Built-in debugging** commands for remote troubleshooting
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

## Installation

### Option 1: New Installation (No Existing Home Connect Setup)

If you've never used Home Connect with Hubitat before, follow these steps:

#### Step 1: Create Home Connect Developer Account

1. Go to [Home Connect Developer Portal](https://developer.home-connect.com)
2. Sign up for a developer account (free)
3. Create a new application with these settings:
   - **Application ID**: Choose a unique name (e.g., `hubitat-yourname`)
   - **OAuth Flow**: Authorization Code Grant Flow
   - **Redirect URI**: Leave blank for now (we'll add this in Step 4)
4. Note your **Client ID** and **Client Secret** - you'll need these next

#### Step 2: Install the Integration

**Option A: Via Hubitat Package Manager (Recommended)**

1. Open Hubitat Package Manager
2. Search for "Home Connect Integration v3"
3. Click Install
4. HPM will install all components and automatically launch the app configuration screen

**Option B: Manual Installation**

1. Go to **Drivers Code** and create a new driver
2. Copy the contents from [HomeConnectStreamDriver.groovy](https://raw.githubusercontent.com/craigde/hubitat-homeconnect-v3/main/drivers/HomeConnectStreamDriver.groovy)
3. Paste into the driver code editor and click **Save**
4. Create additional drivers for your appliance types (e.g., HomeConnectDishwasher.groovy)
5. Go to **Apps Code** and create a new app
6. Copy the contents from [HomeConnectIntegration.groovy](https://raw.githubusercontent.com/craigde/hubitat-homeconnect-v3/main/apps/HomeConnectIntegration.groovy)
7. Paste into the app code editor and click **Save**
8. Click the **OAuth** button and enable OAuth for the app
9. Go to **Apps** and click **Add User App**
10. Select "Home Connect Integration v3" from the list

#### Step 3: Initial App Configuration

1. In the app configuration screen:
   - Enter your **Client ID** from Step 1
   - Enter your **Client Secret** from Step 1
   - Select your **Region**
   - Set **Log Level** (recommend "debug" for initial setup)
2. Click **Next**
3. The app will display your unique **Redirect URI** (includes your app-id)
4. Copy this URI - you'll need it for the next step
5. Do NOT click "Authorize" yet - we need to add this URI to Home Connect first

#### Step 4: Configure Home Connect with Your URI

1. Return to your [Home Connect Developer Portal](https://developer.home-connect.com)
2. Open the application you created in Step 1
3. In the **Redirect URI** field, paste the full URI shown in the Hubitat app
4. Save your Home Connect application settings
5. **Wait 30 minutes** - Home Connect requires propagation time

#### Step 5: Authorize & Discover Devices

1. Return to the Hubitat app configuration
2. Click "**Connect to Home Connect**"
3. Log in with your Home Connect account credentials
4. Grant permissions to the integration
5. After successful authorization, click **Next**
6. Your appliances will appear - select the ones you want to add
7. Click **Done** to create the devices

---

### Option 2: Side-by-Side Installation (Test Alongside Existing Integration)

If you're currently using the original Home Connect driver and want to test v3 before fully committing:

#### Step 1: Install v3 Integration

**Option A: Via Hubitat Package Manager (Recommended)**

1. Open Hubitat Package Manager
2. Search for "Home Connect Integration v3"
3. Click Install
4. HPM will install all components and automatically launch the app configuration screen

**Option B: Manual Installation**

Follow the manual installation steps from Option 1, Step 2B above.

#### Step 2: Initial Configuration to Get URI

1. In the app configuration screen:
   - Enter the **same Client ID and Client Secret** from your existing integration
   - Select your **Region** (same as your existing integration)
   - Set **Log Level** (recommend "debug" for initial setup)
2. Click **Next**
3. The app will display your v3-specific **Redirect URI**
4. Copy the Redirect URI (it will have a different app-id than your original integration)
5. Do NOT click "Authorize" yet

#### Step 3: Add Second Redirect URI to Home Connect

1. Go to your [Home Connect Developer Portal](https://developer.home-connect.com)
2. Open your existing application
3. Add the new v3 Redirect URI (don't replace the existing one - you'll now have two URIs listed)
4. Save the application settings
5. **Wait 15-30 minutes** for propagation

#### Step 4: Authorize & Create Test Devices

1. Return to the v3 app in Hubitat
2. Click "**Connect to Home Connect**"
3. Log in and grant permissions
4. After successful authorization, click **Next** and select your appliances
5. Click **Done**

**Note:** v3 devices will have a `HC3-` prefix (e.g., `HC3-Dishwasher`) to distinguish them from your original devices.

#### Step 5: Testing & Migration

- Both integrations will work simultaneously
- Test v3 features with the new devices
- Update your rules/dashboards to use v3 devices when ready
- Once satisfied, you can remove the original integration and its devices

---

## Usage

### Dashboard Tiles

The drivers support standard Hubitat capabilities:

| Capability | Dashboard Tile | Shows |
|------------|----------------|-------|
| Switch | Switch tile | Running/Idle or Power on/off |
| Contact Sensor | Contact tile | Door open/closed |
| Temperature | Temperature tile | Current temperature |
| PushableButton | - | Triggers for automations |

Custom attributes for Attribute tiles:
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
  "lastUpdate": "2026-01-15T12:30:45.123-0800"
}
```

### Debugging Commands

All drivers include these debugging commands:

| Command | Description |
|---------|-------------|
| `dumpState` | Output all attributes and state to logs |
| `getDiscoveredKeys` | Show all event keys received from appliance |
| `clearDiscoveredKeys` | Reset discovered keys list |
| `getRecentEvents(n)` | Show last N events received |

Enable **Debug Logging** in driver preferences for detailed event logging.

---

## Troubleshooting

### OAuth Authentication Failed

- **"missing or invalid request parameters"**: Double-check your Redirect URI in the Home Connect Developer Portal matches exactly what the app shows
- Verify Client ID and Client Secret are correct (no extra spaces)
- Wait at least 30 minutes after creating/modifying the application
- Check Hubitat logs for specific error messages

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

---

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

---

## Installation Links

- **Hubitat Package Manager**: [packageManifest.json](https://raw.githubusercontent.com/craigde/hubitat-homeconnect-v3/main/packageManifest.json)
- **GitHub Repository**: [craigde/hubitat-homeconnect-v3](https://github.com/craigde/hubitat-homeconnect-v3)

## Credits

A huge thank you to **Rfg81** for creating the original Home Connect integration. This project wouldn't exist without that foundational work. Version 3 builds on those concepts while addressing limitations and adding modern features requested by the community.

## License

Apache License 2.0
