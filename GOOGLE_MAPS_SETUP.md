# Activate native Google Maps for RoadWatch

The app uses Maps SDK for Android through Maps Compose. It does not set a map ID and does not call Places, Routes, Street View, or the Map Tiles API. This preserves the standard Maps SDK pricing category. Billing still has to be enabled in the Google Cloud project. Prices and terms can change; check Google's current pricing before enabling additional services.

## Google Cloud setup

1. Open https://console.cloud.google.com/projectcreate and create a project such as `RoadWatch AI`, or select your existing project.
2. In **Billing**, link an active billing account. This step requires the account owner's payment information and agreement to Google's terms.
3. Open https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com with that project selected. Enable **Maps SDK for Android** only.
4. Open **APIs & Services → Credentials → Create credentials → API key**.
5. Edit the key. Under **Application restrictions**, choose **Android apps**. Add the package and certificate entries below.
6. Under **API restrictions**, choose **Restrict key**, select **Maps SDK for Android**, then save.
7. Add the key to the existing ignored `local.properties` file at this project's root:

   ```properties
   MAPS_API_KEY=your_actual_key
   ```

Do not commit this file. Do not put the key into Kotlin source code. `MAPS_API_KEY` can alternatively be supplied as a build environment variable. Application/API restrictions are necessary because Android APK contents can be inspected.

## Android key restrictions

Package name for both entries: `com.example.roadwatch`

- Signed release APK SHA-1: `0B:A7:B7:74:64:E6:2A:74:6B:74:50:9D:A6:59:13:67:6D:A4:AF:9B`
- This computer's debug APK SHA-1: `30:E2:9B:96:D0:9D:82:47:A0:3B:C7:3E:D3:83:36:CC:AB:5D:17:3B`

Add both entries if using one key for local testing and the signed APK. A separate debug/release key is also supported by supplying the appropriate key at build time. If later publishing via Play App Signing, add the Play app-signing certificate SHA-1 as well. Preserve `.signing/`; changing its signing key prevents upgrades of existing release installs.

## Rebuild and verify

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.roadwatch.MapAndOverlayTest
```

Release builds intentionally fail if `MAPS_API_KEY` is absent. Debug builds without a key display a setup-unavailable map message, keep Scan/Reports accessible, and skip the live native map test. Never treat a successful keyless debug build as verification of Google map tiles.

After adding the key, verify the emulator and signed phone APK: map tiles, English labels, draft/submitted markers, pinch and button zoom, fit-to-captures, returning from the background, and visible Google attribution. Google determines its basemap labels and may retain local-language names alongside English; the app requests English and does not change the phone's language.

If the map is blank, inspect Android Studio Logcat for Google Maps authentication errors. Check the selected Google Cloud project, billing activation, SDK enablement, the key's Android package/SHA-1 restrictions, and network access. Do not print the API key in shared logs.

## Costs

- Standard native map, no map ID: currently unlimited no-charge map loads under the Maps SDK SKU.
- A map ID can trigger Dynamic Maps billing. This app deliberately omits it.
- Extra APIs have separate pricing. Keep unused APIs disabled. Budget alerts notify you; they are not a hard spending cap.

Sources:
- https://developers.google.com/maps/documentation/android-sdk/get-api-key
- https://developers.google.com/maps/billing-and-pricing/sku-details/maps
- https://developers.google.com/maps/billing-and-pricing/pricing
