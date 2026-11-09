# RoadWatch AI

Native Android pothole scanning with a bundled YOLO11s ONNX model, optional foreground location, private local drafts, capture review, and account-isolated Supabase sync.

## Run

Use Android Studio with the installed SDK matching `compileSdk`, and configure the public Supabase URL/key in `local.properties` as described in [SUPABASE_SETUP.md](SUPABASE_SETUP.md). Apply the supplied SQL migration once. No admin key belongs in the app.

Connect an Android 8.0+ device with a rear camera, or use an emulator. An emulator's virtual camera shows a simulated scene; a real phone uses its rear camera. Enable USB debugging only for development, select the device in Android Studio, then Run. Installing an APK directly does not require USB debugging.

## Scan and review

1. Sign in once online. The saved account can reopen offline.
2. Set up the mounted phone while parked; start a scan and allow camera access.
3. Optionally enable location. Only a fix received during the scan, at most 30 seconds old and with reported accuracy within 100 m, is attached to a capture. Approximate permission may not provide that accuracy. No fix means no coordinates, including indoors or with GPS disabled.
4. Tap Stop & review to save a draft. Leaving the app also stops scanning. Interrupted scans recover as drafts.
5. In Detections, review orange boxes, confidence and location. Remove incorrect captures, then submit the remaining report. Submitted reports cannot be edited during sync.
6. Reports retry automatically when connected. Synced reports can be removed locally to free space; this does not delete the cloud copy. Draft deletion removes the local draft and its images.
7. The map shows only this account's saved and cloud locations. Refresh loads the latest 500 cloud reports. Cloud data is cached privately per account. Basemap tiles require internet; capture review remains available offline.

## Linux emulator startup

On the development machine, Emulator 37.1.11's headless auto mode selected SwiftShader and crashed with SIGSEGV inside its GLES renderer. Host rendering uses the AMD driver and has completed a cold boot successfully. KVM was available; AVD data did not need to be wiped.

The original local AVD configuration now uses `hw.gpu.mode=host`; its previous configuration was backed up beside `config.ini`. That device’s 6 GB data partition had under 500 MB free and could not stage an app update, even after Android cache cleanup. A separate `RoadWatch_API_36` device was created with 12 GB storage, host graphics, four virtual CPUs, and a 720 × 1600 display at 280 dpi to reduce overhead while preserving the original logical screen dimensions. The original device and its data were retained. Prefer running one emulator at a time on this development machine. To explicitly select the working renderer from the CLI:

```sh
bash scripts/run_emulator.sh RoadWatch_API_36
# For automated tests without a separate window:
bash scripts/run_emulator.sh RoadWatch_API_36 -no-window -no-audio -no-snapshot
```

The helper defaults to host graphics; `ROADWATCH_EMULATOR_GPU` can override it on other machines. If the AVD is already open, use that instance or close it normally before launching another one. No system security settings are changed.

## Map and scanner layout

Version 1.1 uses full-screen map and camera views with floating, labelled controls and a compact action panel. The map opens outside the dashboard header and tabs. Zoom, refresh, fit-to-captures, scan, reports and back controls remain available. Scanner details are in the information dialog; location and available torch controls float beside the camera. Portrait and landscape use different panel widths, and system-bar icon contrast follows the screen background.

Version 1.1.1 also loads the map only after its native WebView has been measured, explicitly fills the viewport, and adds a regression check against zero-height maps.

## Build and checks

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

The default APK contains ARM64, ARMv7, x86 and x86_64 libraries. Native libraries are compressed to keep the universal download smaller. The optional `-ProadwatchAbi=arm64-v8a` property builds a smaller ARM64-only APK; omit it for broad support. ONNX and CameraX still require supported hardware/software. Devices below Android 8 are unsupported.

`LiveAccountScanTest` is opt-in and creates real cloud data. See SUPABASE_SETUP.md for the explicit instrumentation arguments. Regular tests do not establish physical-phone heat, battery, latency, GPS acquisition, or road detection accuracy.

## Boundaries

Boxes are model suggestions, not verified defects. The app does not estimate pothole depth or severity, count unique defects across frames, send reports to a road authority, or record in the background. Submitted hazard coordinates are shared with signed-in users; drafts, original reports and photos stay private. Hazard alerts, moderation and notifications need separate product/backend decisions. Camera frames without detections are not retained.

To create a release signing key on a new machine, run `python3 scripts/create_signing_key.py` once. It refuses to overwrite existing credentials. If `.signing/` already exists, preserve its key. Back up that directory privately before relying on release upgrades. Then run `./gradlew :app:assembleRelease`; Gradle reads `.signing/release.properties`. Without those credentials, release output is unsigned. Never commit or upload the signing directory as a public artifact.

A Drive APK is a direct distribution, not a Play Store listing. Keep signing credentials private and preserve the signing key for future upgrades. A different certificate cannot update an already-installed build with the same application ID.

## Native Google Maps

Version 1.2.0 replaces the web map with Maps SDK for Android and Maps Compose. See [GOOGLE_MAPS_SETUP.md](GOOGLE_MAPS_SETUP.md) to enable the SDK, restrict the API key to RoadWatch's package/signing certificates, and configure the local build.

No map ID is set. This build does not call Places, Routes, Street View or the Map Tiles API. The app and SDK request English, although Google may retain local-language place names alongside English. Private draft and shared submitted hazard markers, current-location centering, pinch gestures, and zoom buttons use the native map. Map padding keeps the Google logo and attribution clear of the action panel.

Debug builds can compile without a key and display a map-unavailable state while preserving Scan/Reports access. A missing key blocks release builds. Live map/zoom tests require a valid key; a keyless build cannot validate Google authentication or tile rendering.

## Workflow reliability

Version 1.2.1 isolates upload errors per submitted report so one failure does not block the rest of the queue. Upload requests have a 60-second timeout; IDs and object paths remain stable after interrupted responses. Acknowledging an upload requires the same report owner and pending state. Drafts remain local until explicitly submitted. See [WORKFLOW_TESTING.md](WORKFLOW_TESTING.md) for physical-phone and offline-to-cloud acceptance checks.

## Location and community reports (1.2.2)

The map centers at street level on a fresh foreground GPS fix when opened. The target requests a new fix and returns to the user's location after browsing. Shared coordinates refresh every 15 seconds while the map is foregrounded. The deployed `20261103_shared_hazards.sql` migration publishes only submitted reports; report/photo ownership stays private. Nearby accurate sightings are grouped on the server, and markers show distinct reporter counts. See SUPABASE_SETUP.md for deployment evidence and the limitations of proximity matching.

Compose BOM 2026.04.01 keeps UI, runtime and UI tests on 1.11.0, matching the existing lifecycle dependency. The previous BOM mixed 1.10 UI tests with the 1.11 runtime and failed during asynchronous map updates.
