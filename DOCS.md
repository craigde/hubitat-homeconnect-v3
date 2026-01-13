Home Connect Integration v3 for Hubitat
Complete integration for Home Connect smart appliances (Bosch, Siemens, Gaggenau, Neff, and Thermador) with Hubitat Elevation.
Overview
Home Connect Integration v3 is a complete rewrite of the Home Connect integration for Hubitat, featuring improved architecture, enhanced reliability, and additional features for automation.
Current Support

Dishwasher - Fully implemented with all programs, options, and notifications
Additional appliances coming soon - Ovens, coffee makers, refrigerators, washers/dryers

Key Features

Real-time status updates via Server-Sent Events (SSE)
Cycle completion notifications with automation-friendly button events
Maintenance alerts for consumables (salt, rinse aid, etc.)
Delayed start support for cost optimization (off-peak electricity hours)
Node-RED compatible JSON state attributes
Robust OAuth implementation with improved error handling
Comprehensive logging for easy troubleshooting

Architecture
The integration uses a three-component architecture:

Stream Driver - Manages the persistent SSE connection to Home Connect's event stream for real-time updates
Parent App - Handles OAuth authentication, device discovery, and device management
Child Drivers - Appliance-specific drivers (currently Dishwasher) that translate Home Connect API data into Hubitat attributes and commands

Requirements

Hubitat Elevation hub (firmware 2.3.0 or later)
Home Connect compatible appliance(s)
Home Connect Developer account (free)
Internet connection for cloud API access

Installation
Choose one of the following installation methods based on your needs:
New Installation
If you've never used Home Connect with Hubitat before, follow the New Installation Guide.
Side-by-Side Installation
If you're currently using an older Home Connect integration and want to test v3 alongside it, follow the Side-by-Side Installation Guide.
Device Capabilities
Dishwasher
Attributes:

operationState - Current operation state (Ready, Run, Finished, etc.)
doorState - Door open/closed status
remainingTime - Time remaining in current program (minutes)
programProgress - Progress percentage (0-100)
activeProgram - Currently selected/running program
selectedProgram - User-selected program (if different from active)
remoteControlActive - Whether remote control is enabled
remoteControlStartAllowed - Whether remote start is permitted
localControlActive - Whether local control is active
stateJson - Complete state in JSON format (for Node-RED)
Plus maintenance alerts for salt, rinse aid, etc.

Commands:

startProgram(programKey) - Start a specific program
stopProgram() - Stop the current program
pauseProgram() - Pause the current program
resumeProgram() - Resume a paused program
selectProgram(programKey) - Select a program without starting
getPrograms() - Refresh available programs list
refresh() - Manual state refresh

Button Events:

Button 1: Cycle complete
Button 2: Salt low
Button 3: Rinse aid low
Button 4: Error occurred

Automation Examples
Rule Machine - Cycle Complete Notification
Trigger: Button 1 pushed on Kitchen Dishwasher
Actions:
  - Send notification "Dishwasher cycle complete!"
  - Turn on under-cabinet lights for 5 minutes
  - Announce on Google Home
Rule Machine - Maintenance Alert
Trigger: Button 2 pushed on Kitchen Dishwasher
Actions:
  - Send notification "Dishwasher salt is low - add to shopping list"
  - Log event
Node-RED Integration
The stateJson attribute contains the complete appliance state in JSON format, making it easy to parse in Node-RED:
json{
  "operationState": "Run",
  "doorState": "Closed",
  "remainingTime": 45,
  "programProgress": 60,
  "activeProgram": "Dishcare.Dishwasher.Program.Normal"
}
Troubleshooting
OAuth Authorization Fails
Symptom: "Missing or invalid request parameters" error during authorization
Solution:

Verify your Redirect URI in the Home Connect Developer Portal exactly matches the URI shown in the Hubitat app
Make sure you saved the Home Connect application after adding the URI
Enable debug logging in the app settings and check the logs for specific error details

Devices Not Discovered
Symptom: "Discover Devices" returns no appliances
Solution:

Ensure your appliances are powered on and connected to your Home Connect account
Verify you successfully completed the OAuth authorization
Check the Stream Driver logs for connection status
Try clicking "Discover Devices" again after a few seconds

Real-time Updates Not Working
Symptom: Device status doesn't update automatically
Solution:

Check the Stream Driver device logs for SSE connection errors
Verify your internet connection is stable
The SSE connection may take 30-60 seconds to establish after authorization
Try clicking "Refresh" on the device to manually update status

HTTP 431 Errors
Symptom: Authorization fails with HTTP 431 (Request Header Fields Too Large)
Solution: This should not occur with v3's stateless implementation. If you encounter this:

Remove and reinstall the app
Clear your browser cache
Report the issue on the community forum with debug logs

Debug Logging
Enable debug logging for detailed troubleshooting information:

Open the Home Connect Integration v3 app
Set Debug Level to "Debug" or "Trace"
Save the settings
Reproduce the issue
Check Logs for detailed information

Remember to set Debug Level back to "Info" or "Warning" after troubleshooting to reduce log volume.
Frequently Asked Questions
Can I use this with multiple Home Connect accounts?
No, the integration currently supports a single Home Connect account per Hubitat hub. However, that account can have multiple appliances.
Do I need a Home Connect Plus subscription?
No, the basic (free) Home Connect account works fine. Home Connect Plus features are not required.
Can I control my appliance when I'm away from home?
Yes, as long as:

Your Hubitat hub has internet connectivity
Your appliance has "Remote Control" enabled (usually set on the appliance itself)
The appliance indicates "Remote Start Allowed" is active

Will this work with my older appliance?
If your appliance works with the Home Connect mobile app, it should work with this integration. Check the Home Connect website for appliance compatibility.
Can I run this alongside the original Home Connect driver?
Yes! Follow the Side-by-Side Installation Guide to test v3 without removing your existing integration.
Known Issues

Initial device discovery may take 30-60 seconds after authorization
Some program names may appear technical rather than friendly (e.g., "Dishcare.Dishwasher.Program.Eco" instead of "Eco") - this varies by appliance model
Delayed start time setting is not yet implemented (coming in future release)

Support
For issues, questions, or feature requests:

Community Forum: Hubitat Community Thread
GitHub Issues: GitHub Repository
Documentation: Full Documentation

When reporting issues, please include:

Hubitat firmware version
Integration version
Appliance type and model
Debug logs showing the issue

Credits
This integration builds on the foundation laid by the original Home Connect integration author. Version 3 represents a complete rewrite with enhanced features and improved reliability based on community feedback.
License
[Your chosen license - MIT, Apache 2.0, etc.]
Changelog
v3.0.9 (2026-01-09)

Fixed device creation on first install (foundDevices timing issue)

v3.0.5

Resolved OAuth authentication issues
Implemented stateless validation to prevent HTTP 431 errors
Separated display URIs from OAuth flow URIs
Enhanced error handling and logging

v3.0.0

Complete rewrite with new architecture
Added button-based notification system
Implemented SSE stream driver
Added comprehensive JSDoc documentation
Initial release
Claude is AI and can make mistakes. Please double-check responses.
