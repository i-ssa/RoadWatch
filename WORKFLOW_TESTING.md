# RoadWatch workflow acceptance checks

Keep the APK version, phone model, Android version and test account consistent for each run. Version 1.2.0 is the previous Google Maps baseline; 1.2.1 adds sync resilience fixes. Finish a baseline run before upgrading so results can be compared. Never share passwords, access tokens, API keys, or identifiable road images in a bug report.

## Real phone: first scan and location

1. Sign in online. Deny the camera once: the app should explain the permission and remain usable. Allow it and open Scan.
2. With location disabled, stop a short scan: a draft must exist, and captures must not invent coordinates.
3. Enable precise location outdoors. Wait for a fix and scan a known test scene. Review saved image boxes, confidence, capture time and coordinates; compare the map marker with the actual test location. Location fixes older than 30 seconds or less accurate than 100 m must not be attached.
4. Repeat with approximate location or GPS disabled: absence of accurate coordinates is expected, not a crash.
5. Start another scan, then put the app in the background. Scanning should end and save a draft. Reopening should not leave the camera recording or upload the draft.
6. Rotate the phone and try portrait/landscape. Stop while analysis is running. Review must include the final completed capture and remain responsive.
7. Record time to first analysis, displayed inference latency, crashes, heat and battery change over a timed scan. Emulator success does not establish these measurements on a phone.

The detector saves frames with possible potholes, not one unique pothole per frame. A scan with no saved captures can be a valid empty draft. Use known positive and negative test scenes to assess accuracy; do not infer model quality from the emulator's virtual room.

## Detector calibration in 1.2.3

The confidence cutoff rose from 0.25 to 0.46 for the bundled YOLO11s checkpoint. The cutoff was selected by box F1 on the 375 cleaned-v3 validation images, where F1 rose from 68.42% to 70.59%. The app runs one inference pass per frame with overlap suppression at IoU 0.45.

On the separate 369-image cleaned-v3 test split, the bundled ONNX model on desktop CPU produced 603 matched boxes, 174 unmatched predictions, and 270 missed annotations at the new cutoff. The previous cutoff produced 652 matched boxes, 300 unmatched predictions, and 221 missed annotations. A match requires at least 0.50 IoU with one previously unmatched annotation. Precision rose from 68.49% to 77.61%, recall fell from 74.68% to 69.07%, and F1 rose from 71.45% to 73.09%.

Every image in these splits has a labeled pothole. For the phone comparison, use the same scenes on 1.2.2 and 1.2.3 and include pothole-free roads, especially shadows, patches, and puddles. For each build, track captures that require manual rejection and known potholes the app misses.

## Offline draft, approval, reconnect

1. Sign in online, then enable airplane mode and confirm Wi-Fi is also off.
2. Scan, stop and review. The report must remain **Draft**, with images accessible locally.
3. Force-stop RoadWatch and reopen it. The account and draft must still be available offline.
4. Remove an incorrect capture. Only that capture should disappear. Reconnect before submitting: the draft must still not upload.
5. Open Review captures and explicitly Submit report. The report becomes **waiting to sync** until the upload is acknowledged.
6. Reconnect. Verify **Synced**, one Supabase `road_reports` row for that report UUID, and each expected private image under the same owner/report UUID. Check location, boxes and capture timestamps survived.
7. Tap Retry sync repeatedly and reopen the app: no additional report row or duplicate image object should be created for the same IDs.

## Interrupted uploads and account isolation

1. Submit a report with several captures. Interrupt network access during upload. The report must remain pending with its local images intact. Reconnect and verify exactly one completed remote report and the expected images.
2. Sign out while an upload is in progress, then sign in to a different test account. The previous account's reports must not appear or upload using the new account. An HTTP request already accepted by the server may finish before sign-out takes effect.
3. Sign back into the first account. Pending reports should resume, while unsubmitted drafts stay local.
4. Check Supabase with each test account's normal permissions: account B must not read account A's report rows or private images. Local automated account checks do not prove server RLS is deployed correctly.

## Automated checks

`SyncResilienceTest` runs against real Android SQLite/files and a simulated remote receiver. It covers offline failures, lost acknowledgements, stable retry IDs, session changes, request timeouts, missing images, failure isolation and owner-scoped acknowledgements. It does not contact Supabase.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.roadwatch.SyncResilienceTest,com.example.roadwatch.OfflineReportsTest,com.example.roadwatch.LocationAndEditingTest,com.example.roadwatch.ReportReviewTest,com.example.roadwatch.CameraScanTest
```

For an explicitly authorized live cloud test, follow `LiveAccountScanTest` instructions in SUPABASE_SETUP.md. That test creates cloud data in the account signed into the selected test device. Do not run it against a partner's active scan without coordinating the report/account first.

## Reporting a failure

Record the APK version, phone/Android model, exact steps, connectivity and permissions, report UUID, expected result, actual result and approximate time. Include a screenshot of the error and whether the draft/images still exist after reopening. Redact personal details and credentials. Cloud verification must distinguish a missing report row from a missing image or an account-policy denial.

## Verified community-map release (1.2.2, 3 November 2026)

The shared-hazard migration is deployed to the RoadWatch Supabase project. The live transactional SQL test in `supabase/tests/live_shared_hazards.sql` verified visibility across two authenticated identities, private report/photo metadata isolation, matching nearby observations, distinct reporter counts, idempotent retries, write restrictions and anonymous-read denial. All synthetic records were rolled back. A separate real REST request without a user token returned HTTP 401. Local concurrent PostgreSQL transactions resulted in one marker and two reporters. Existing submitted reports had no usable coordinates and produced no markers.

Android API 36 emulator regression run: 39 discovered cases, 35 passed, 4 intentionally skipped, zero failures. The skipped cases require a keyless build or explicit live-account opt-in. Map coverage includes fresh simulated GPS centering at zoom 17, browsing away and requesting a changed fix with the target button, native markers, pinch zoom, +/- controls and map navigation. Camera, offline storage, report review, account isolation, interrupted uploads and inference checks also passed. This is simulated GPS on an emulator, not a claim of outdoor phone GPS validation.

Debug/release builds, JVM tests and release lint passed after aligning Compose UI/test/runtime to 1.11.0 via BOM 2026.04.01. The signed release certificate matches 1.2.1. The final APK is `outputs/RoadWatch-1.2.2.apk` at the project root; its checksum is in `outputs/roadwatch-builds.json`.
