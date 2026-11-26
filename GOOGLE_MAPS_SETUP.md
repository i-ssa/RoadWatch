# Activate native Google Maps for RoadWatch

The app uses Maps SDK for Android through Maps Compose. It does not set a map ID and does not call Places, Routes, Street View, or the Map Tiles API. This preserves the standard Maps SDK pricing category. Billing still has to be enabled in the Google Cloud project. Prices and terms can change; check Google's current pricing before enabling additional services.

## Google Cloud setup

1. Open https://console.cloud.google.com/projectcreate and create a project such as `RoadWatch AI`, or select your existing project.
2. In **Billing**, link an active billing account. This step requires the account owner's payment information and agreement to Google's terms.
3. Open https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com with that project selected. Enable **Maps SDK for Android** only.
4. Open **APIs & Services → Credentials → Create credentials → API key**.
5. Edit the key. Under **Application restrictions**, choose **Android apps**. Add the package and certificate entries below.
6. Under **API restrictions**, choose **Restrict key**, select **Maps SDK for Android**, then save.
7. Add the key to the ignored `roadwatch.properties` file. Start with `roadwatch.properties.example`:

   ```properties
   MAPS_API_KEY=your_actual_key
   ```

Do not commit this file. Do not put the key into Kotlin source code. `MAPS_API_KEY` can alternatively be supplied as a build environment variable. Application/API restrictions are necessary because Android APK contents can be inspected.

## Android key restrictions

Package name for both entries: `com.example.roadwatch`

Run `./gradlew :app:signingReport` and copy the SHA-1 for each build variant you will run. Debug certificates are specific to the machine, and a signed release uses its own certificate. If later publishing via Play App Signing, also add the Play app-signing certificate SHA-1.

## Rebuild and verify

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.roadwatch.MapAndOverlayTest
```

With a blank Maps key, the map displays a setup message. The live native map test requires a valid key.

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
