# Home Connect Integration v3 for Hubitat

Complete integration for Home Connect smart appliances (Bosch, Siemens, Gaggenau, Neff, and Thermador) with Hubitat Elevation.

## Overview

Home Connect Integration v3 is a complete rewrite of the Home Connect integration for Hubitat, featuring improved architecture, enhanced reliability, and additional features for automation.

### Current Support
- **Dishwasher** - Fully implemented with all programs, options, and notifications
- **Additional appliances coming soon** - Ovens, coffee makers, refrigerators, washers/dryers

### Key Features

- **Real-time status updates** via Server-Sent Events (SSE)
- **Cycle completion notifications** with automation-friendly button events
- **Maintenance alerts** for consumables (salt, rinse aid, etc.)
- **Delayed start support** for cost optimization (off-peak electricity hours)
- **Node-RED compatible** JSON state attributes
- **Robust OAuth implementation** with improved error handling
- **Comprehensive logging** for easy troubleshooting

## Architecture

The integration uses a three-component architecture:

1. **Stream Driver** - Manages the persistent SSE connection to Home Connect's event stream for real-time updates
2. **Parent App** - Handles OAuth authentication, device discovery, and device management
3. **Child Drivers** - Appliance-specific drivers (currently Dishwasher) that translate Home Connect API data into Hubitat attributes and commands

## Requirements

- Hubitat Elevation hub (firmware 2.3.0 or later)
- Home Connect compatible appliance(s)
- Home Connect Developer account (free)
- Internet connection for cloud API access

## Installation

Choose one of the following installation methods based on your needs:

### New Installation

If you've never used Home Connect with Hubitat before, follow these steps:

#### Step 1: Create Home Connect Developer Account
1. Go to [Home Connect Developer Portal](https://developer.home-connect.com/)
2. Sign up for a developer account (free)
3. Create a new application with these settings:
   - **Application ID**: Choose a unique name (e.g., `hubitat-yourname`)
   - **Redirect URI**: Leave blank for now (we'll add this in Step 4)
4. Note your **Client ID** and **Client Secret** - you'll need these next

#### Step 2: Install the Integration

**Option A: Via Hubitat Package Manager (Recommended)**
1. Open **Hubitat Package Manager**
2. Search for "Home Connect Integration v3"
3. Click **Install**
4. HPM will install all three components and automatically launch the app configuration screen

**Option B: Manual Installation**
1. Go to **Drivers Code** and create a new driver
   - Copy the contents from: [HomeConnectStreamDriver.groovy](https://raw.githubusercontent.com/craigde/hubitat-homeconnect-v3/main/drivers/HomeConnectStreamDriver.groovy)
   - Paste into the driver code editor
   - Click **Save**
2. Create another new driver
   - Copy the contents from: [HomeConnectDishwasher.groovy](https://raw.githubusercontent.com/craigde/hubitat-homeconnect-v3/main/drivers/HomeConnectDishwasher.groovy)
   - Paste into the driver code editor
   - Click **Save**
3. Go to **Apps Code** and create a new app
   - Copy the contents from: [HomeConnectIntegration.groovy](https://raw.githubusercontent.com/craigde/hubitat-homeconnect-v3/main/apps/HomeConnectIntegration.groovy)
   - Paste into the app code editor
   - Click **Save**
4. Click the **OAuth** button and enable OAuth for the app
5. Go to **Apps** and click **Add User App**
6. Select "Home Connect Integration v3" from the list

#### Step 3: Initial App Configuration
1. In the app configuration screen:
   - Enter your **Client ID** from Step 1
   - Enter your **Client Secret** from Step 1
   - Select your **Region** (US or EU)
   - Set **Debug Level** (recommend "Info" for initial setup)
   - Click **Next**
2. The app will now display your unique Redirect URI (this includes your app-id)
3. **Copy the Redirect URI** - you'll need it for the next step
4. **Do NOT click "Authorize" yet** - we need to add this URI to Home Connect first

#### Step 4: Configure Home Connect with Your URI
1. Return to your [Home Connect Developer Portal](https://developer.home-connect.com/)
2. Open the application you created in Step 1
3. In the **Redirect URI** field, paste the full URI shown in the Hubitat app
4. **Save** your Home Connect application settings

#### Step 5: Authorize & Discover Devices
1. Return to the Hubitat app configuration
2. Click **"Authorize with Home Connect"**
3. Log in with your Home Connect account credentials
4. Grant permissions to the integration
5. After successful authorization, return to the app and click **"Discover Devices"**
6. Your appliances will appear - select the ones you want to add
7. Click **Done** to create the devices

### Side-by-Side Installation

If you're currently using an older Home Connect integration and want to test v3 alongside it:

#### Step 1: Install v3 Integration

**Option A: Via Hubitat Package Manager (Recommended)**
1. Open **Hubitat Package Manager**
2. Search for "Home Connect Integration v3"
3. Click **Install**
4. HPM will install all three components and automatically launch the app configuration screen

**Option B: Manual Installation**
1. Go to **Drivers Code** and create a new driver
   - Copy the contents from: [HomeConnectStreamDriver.groovy](https://raw.githubusercontent.com/craigde/hubitat-homeconnect-v3/main/drivers/HomeConnectStreamDriver.groovy)
   - Paste into the driver code editor
   - Click **Save**
2. Create another new driver
   - Copy the contents from: [HomeConnectDishwasher.groovy](https://raw.githubusercontent.com/craigde/hubitat-homeconnect-v3/main/drivers/HomeConnectDishwasher.groovy)
   - Paste into the driver code editor
   - Click **Save**
3. Go to **Apps Code** and create a new app
   - Copy the contents from: [HomeConnectIntegration.groovy](https://raw.githubusercontent.com/craigde/hubitat-homeconnect-v3/main/apps/HomeConnectIntegration.groovy)
   - Paste into the app code editor
   - Click **Save**
4. Click the **OAuth** button and enable OAuth for the app
5. Go to **Apps** and click **Add User App**
6. Select "Home Connect Integration v3" from the list

#### Step 2: Initial Configuration to Get URI
1. In the app configuration screen:
   - Enter the **same Client ID and Client Secret** from your existing integration
   - Select your **Region** (same as your existing integration)
   - Set **Debug Level** (recommend "Info" for initial setup)
   - Click **Next**
2. The app will display your v3-specific Redirect URI
3. **Copy the Redirect URI** (it will have a different app-id than your original integration)
4. **Do NOT click "Authorize" yet** - we need to add this URI to Home Connect first

#### Step 3: Add Second Redirect URI to Home Connect
1. Go to your [Home Connect Developer Portal](https://developer.home-connect.com/)
2. Open your existing application
3. Check the **"Add additional redirect URIs"** checkbox
4. **Add** the new v3 Redirect URI from Step 2 (don't replace the existing one - you'll now have two URIs listed)
5. **Save** the application settings

#### Step 4: Authorize & Create Test Devices
1. Return to the v3 app in Hubitat
2. Click **"Authorize with Home Connect"**
3. Log in and grant permissions
4. After successful authorization, click **"Discover Devices"** and select your appliances
5. Click **Done**
6. **Note**: v3 devices will have a `HC3-` prefix (e.g., `HC3-Dishwasher`) to distinguish them from your original devices

#### Step 5: Testing & Migration
- Both integrations will work simultaneously
- Test v3 features with the new devices
- Update your rules/dashboards to use v3 devices when ready
- Once satisfied, you can remove the original integration and its devices

## Device Capabilities

### Dishwasher

**Attributes:**
- `operationState` - Current operation state (Ready, Run, Finished, etc.)
- `doorState` - Door open/closed status
- `remainingTime` - Time remaining in current program (minutes)
- `programProgress` - Progress percentage (0-100)
- `activeProgram` - Currently selected/running program
- `selectedProgram` - User-selected program (if different from active)
- `remoteControlActive` - Whether remote control is enabled
- `remoteControlStartAllowed` - Whether remote start is permitted
- `localControlActive` - Whether local control is active
- `stateJson` - Complete state in JSON format (for Node-RED)
- Plus maintenance alerts for salt, rinse aid, etc.

**Commands:**
- `startProgram(programKey)` - Start a specific program
- `stopProgram()` - Stop the current program
- `pauseProgram()` - Pause the current program
- `resumeProgram()` - Resume a paused program
- `selectProgram(programKey)` - Select a program without starting
- `getPrograms()` - Refresh available programs list
- `refresh()` - Manual state refresh

**Button Events:**
- Button 1: Cycle complete
- Button 2: Salt low
- Button 3: Rinse aid low
- Button 4: Error occurred

## Automation Examples

### Rule Machine - Cycle Complete Notification
```
Trigger: Button 1 pushed on Kitchen Dishwasher
Actions:
  - Send notification "Dishwasher cycle complete!"
  - Turn on under-cabinet lights for 5 minutes
  - Announce on Google Home
```

### Rule Machine - Maintenance Alert
```
Trigger: Button 2 pushed on Kitchen Dishwasher
Actions:
  - Send notification "Dishwasher salt is low - add to shopping list"
  - Log event
```

### Node-RED Integration

The `stateJson` attribute contains the complete appliance state in JSON format, making it easy to parse in Node-RED:
```json
{
  "operationState": "Run",
  "doorState": "Closed",
  "remainingTime": 45,
  "programProgress": 60,
  "activeProgram": "Dishcare.Dishwasher.Program.Normal"
}
```

## Troubleshooting

### OAuth Authorization Fails

**Symptom:** "Missing or invalid request parameters" error during authorization

**Solution:**
1. Verify your Redirect URI in the Home Connect Developer Portal exactly matches the URI shown in the Hubitat app
2. Make sure you saved the Home Connect application after adding the URI
3. Enable debug logging in the app settings and check the logs for specific error details

### Devices Not Discovered

**Symptom:** "Discover Devices" returns no appliances

**Solution:**
1. Ensure your appliances are powered on and connected to your Home Connect account
2. Verify you successfully completed the OAuth authorization
3. Check the Stream Driver logs for connection status
4. Try clicking "Discover Devices" again after a few seconds

### Real-time Updates Not Working

**Symptom:** Device status doesn't update automatically

**Solution:**
1. Check the Stream Driver device logs for SSE connection errors
2. Verify your internet connection is stable
3. The SSE connection may take 30-60 seconds to establish after authorization
4. Try clicking "Refresh" on the device to manually update status

### HTTP 431 Errors

**Symptom:** Authorization fails with HTTP 431 (Request Header Fields Too Large)

**Solution:** This should not occur with v3's stateless implementation. If you encounter this:
1. Remove and reinstall the app
2. Clear your browser cache
3. Report the issue on the community forum with debug logs

## Debug Logging

Enable debug logging for detailed troubleshooting information:

1. Open the Home Connect Integration v3 app
2. Set **Debug Level** to "Debug" or "Trace"
3. Save the settings
4. Reproduce the issue
5. Check **Logs** for detailed information

**Remember to set Debug Level back to "Info" or "Warning" after troubleshooting to reduce log volume.**

## Frequently Asked Questions

### Can I use this with multiple Home Connect accounts?

No, the integration currently supports a single Home Connect account per Hubitat hub. However, that account can have multiple appliances.

### Do I need a Home Connect Plus subscription?

No, the basic (free) Home Connect account works fine. Home Connect Plus features are not required.

### Can I control my appliance when I'm away from home?

Yes, as long as:
1. Your Hubitat hub has internet connectivity
2. Your appliance has "Remote Control" enabled (usually set on the appliance itself)
3. The appliance indicates "Remote Start Allowed" is active

### Will this work with my older appliance?

If your appliance works with the Home Connect mobile app, it should work with this integration. Check the Home Connect website for appliance compatibility.

### Can I run this alongside the original Home Connect driver?

Yes! Follow the Side-by-Side Installation instructions above to test v3 without removing your existing integration.

## Known Issues

- Initial device discovery may take 30-60 seconds after authorization
- Some program names may appear technical rather than friendly (e.g., "Dishcare.Dishwasher.Program.Eco" instead of "Eco") - this varies by appliance model
- Delayed start time setting is not yet implemented (coming in future release)

## Support

For issues, questions, or feature requests:

- **Community Forum:** [Hubitat Community Thread](https://community.hubitat.com/t/home-connect-integration-v3)
- **GitHub Issues:** [GitHub Repository](https://github.com/craigde/hubitat-homeconnect-v3/issues)

When reporting issues, please include:
- Hubitat firmware version
- Integration version
- Appliance type and model
- Debug logs showing the issue

## Credits

This integration builds on the foundation laid by the original Home Connect integration author. Version 3 represents a complete rewrite with enhanced features and improved reliability based on community feedback.

## License

MIT License

## Changelog

### v3.0.9 (2026-01-09)
- Fixed device creation on first install (foundDevices timing issue)

### v3.0.5
- Resolved OAuth authentication issues
- Implemented stateless validation to prevent HTTP 431 errors
- Separated display URIs from OAuth flow URIs
- Enhanced error handling and logging

### v3.0.0
- Complete rewrite with new architecture
- Added button-based notification system
- Implemented SSE stream driver
- Added comprehensive JSDoc documentation
- Initial release
