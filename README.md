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

Boxes are model suggestions, not verified defects. The app does not estimate pothole depth or severity, count unique defects across frames, send reports to a road authority, or record in the background. The map is private to the account, not a public community feed. Public reporting, hazard alerts, moderation and notifications need separate product/backend decisions. Camera frames without detections are not retained.

To create a release signing key on a new machine, run `python3 scripts/create_signing_key.py` once. It refuses to overwrite existing credentials. If `.signing/` already exists, preserve its key. Back up that directory privately before relying on release upgrades. Then run `./gradlew :app:assembleRelease`; Gradle reads `.signing/release.properties`. Without those credentials, release output is unsigned. Never commit or upload the signing directory as a public artifact.

A Drive APK is a direct distribution, not a Play Store listing. Keep signing credentials private and preserve the signing key for future upgrades. A different certificate cannot update an already-installed build with the same application ID.

## Map license

Leaflet 1.9.4 is bundled with its BSD license in `app/src/main/assets/map/LICENSE`. Map data is © OpenStreetMap contributors. Tile loading follows the [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/); no bulk/offline tile downloads are provided. See https://leafletjs.com/ for library documentation.
