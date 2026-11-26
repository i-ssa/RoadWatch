# RoadWatch AI

Native Android pothole scanning with a bundled YOLO11s ONNX model, optional foreground location, private local drafts, capture review, and account-isolated Supabase sync.

## Run

1. In Android Studio, select **Open** and choose this folder (the one containing `settings.gradle.kts`). Install Android SDK Platform 37 via SDK Manager and use JDK 25 for Gradle. The included wrapper uses Gradle 9.5.0.
2. Copy `roadwatch.properties.example` to `roadwatch.properties` in this folder. Add only the settings for the services you plan to use. Gradle checks this file first, then environment variables, then the legacy `local.properties` entries.
3. Sync Gradle, select the **app** run configuration and a device, and click **Run**. Use an Android 8.0+ device with a rear camera or an emulator with a virtual camera. A Google APIs or Google Play image is needed for the native map on an emulator.

| Settings | Used for |
| --- | --- |
| `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` | Sign-in, cloud report sync and shared hazards. See [Supabase setup](SUPABASE_SETUP.md) for auth and database migrations. |
| `MAPS_API_KEY` | Google basemap. See [Google Maps setup](GOOGLE_MAPS_SETUP.md) for the Android package and certificate restrictions. |
| `GEMINI_API_KEY` | Optional cloud verification of Video Scan candidates. Initial detection runs on the device. |

Debug and release builds compile with blank service settings. Supabase is needed for initial sign-in. An existing account can reopen offline, and map tiles and Gemini verification require their respective services. An Android APK exposes build-time client keys to anyone who inspects it. Use a Supabase publishable or anon key and a restricted Maps key. Use a disposable Gemini key for local testing and leave it blank in any APK you share.

## Project layout

- `app/src/main/java/com/example/roadwatch/ui/` contains the Jetpack Compose screens, navigation, scanning and map UI.
- `app/src/main/java/com/example/roadwatch/data/` contains authentication, report persistence and background sync.
- `app/src/main/assets/` includes the ONNX model, and `app/src/main/res/` contains Android resources.
- `app/src/test/` and `app/src/androidTest/` contain local and device tests.
- `supabase/migrations/` contains the database schema and security policies.

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

Boxes are model suggestions, not verified defects. The app does not estimate pothole depth or severity, count unique defects across frames, or record in the background. Submitted hazard coordinates are shared with signed-in users; drafts, original reports and photos stay private. Hazard alerts, moderation and notifications need separate product/backend decisions. Camera frames without detections are not retained.

To create a release signing key on a new machine, run `python3 scripts/create_signing_key.py` once. It refuses to overwrite existing credentials. If `.signing/` already exists, preserve its key. Back up that directory privately before relying on release upgrades. Then run `./gradlew :app:assembleRelease`; Gradle reads `.signing/release.properties`. Without those credentials, release output is unsigned. Never commit or upload the signing directory as a public artifact.

A Drive APK is a direct distribution, not a Play Store listing. Keep signing credentials private and preserve the signing key for future upgrades. A different certificate cannot update an already-installed build with the same application ID.

## Native Google Maps

Version 1.2.0 replaces the web map with Maps SDK for Android and Maps Compose. See [GOOGLE_MAPS_SETUP.md](GOOGLE_MAPS_SETUP.md) to enable the SDK, restrict the API key to RoadWatch's package/signing certificates, and configure the local build.

No map ID is set. This build does not call Places, Routes, Street View or the Map Tiles API. The app and SDK request English, although Google may retain local-language place names alongside English. Private draft and shared submitted hazard markers, current-location centering, pinch gestures, and zoom buttons use the native map. Map padding keeps the Google logo and attribution clear of the action panel.

With a blank Maps key, the map displays a setup message. Live map and zoom tests require a valid key.

## Workflow reliability

Version 1.2.1 isolates upload errors per submitted report so one failure does not block the rest of the queue. Upload requests have a 60-second timeout; IDs and object paths remain stable after interrupted responses. Acknowledging an upload requires the same report owner and pending state. Drafts remain local until explicitly submitted. See [WORKFLOW_TESTING.md](WORKFLOW_TESTING.md) for physical-phone and offline-to-cloud acceptance checks.

## Location and community reports (1.2.2)

The map centers at street level on a fresh foreground GPS fix when opened. The target requests a new fix and returns to the user's location after browsing. Shared coordinates refresh every 15 seconds while the map is foregrounded. The deployed `20261103_shared_hazards.sql` migration publishes only submitted reports; report/photo ownership stays private. Nearby accurate sightings are grouped on the server, and markers show distinct reporter counts. See SUPABASE_SETUP.md for deployment evidence and the limitations of proximity matching.

Compose BOM 2026.04.01 keeps UI, runtime and UI tests on 1.11.0, matching the existing lifecycle dependency. The previous BOM mixed 1.10 UI tests with the 1.11 runtime and failed during asynchronous map updates.

## Road authority complaints (1.2.4)

In **Detections → Review captures**, select **Report pothole to authority** below the relevant photo. Each complaint uses one capture and can be prepared directly from a local draft. **Submit report** remains the separate RoadWatch sync and community-map action.

RoadWatch uses Android's reverse geocoder to look up the road name and county from the saved GPS fix. It suggests a recipient for recognized Kenyan trunk-road references and a limited set of named roads from agency or county maintenance records. Nearby roads, service lanes, shared responsibilities and stale road names can produce ambiguous matches. Review the suggested recipient or choose one manually. Use the [Kenya Roads Board road conditions and reporting map](https://maps.krb.go.ke/kenya-roads-board12769/maps/110400/2-road-network-conditions-map) to check the affected segment.

- [KeNHA](https://kenha.co.ke/report-an-incident/), [KURA](https://kura.go.ke/report-incident/), [KeRRA](https://kerra.go.ke/kerra-complaint-handling-form/) and [Mombasa County](https://eportal.mombasa.go.ke/#contact-form) open their official forms inside RoadWatch and prefill the fields available there. Review and submit on the form.
- On KeNHA's form, use **Upload Photo** and confirm the selected capture. Email drafts for the three agencies and Nairobi or Mombasa county contacts can attach the photo in a compatible email app. The scanner saves JPEG captures.
- Nairobi County opens its [county contacts](https://nairobi.go.ke/faqs-and-help-desks). Other counties can use the [Senate county website directory](https://www.parliament.go.ke/the-senate/counties) and **Copy report details**. The email draft and copy actions are also useful when a form changes or cannot load.
