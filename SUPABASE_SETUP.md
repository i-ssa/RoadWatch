# RoadWatch authentication

Add the project URL and **publishable** key (or legacy public anon key) to the ignored `local.properties` file alongside `sdk.dir`:

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLIC_CLIENT_KEY
```

Never use a secret or service-role key in the Android app. These public client settings are compiled into the APK. The build also accepts environment variables with the same names.

In Supabase Authentication:

1. Enable the Email provider and keep email confirmation enabled.
2. Add `roadwatch://auth` to URL Configuration → Redirect URLs.
3. Keep confirmation and recovery email templates using the Supabase confirmation link (`{{ .ConfirmationURL }}`). Configure production SMTP before public use.
4. Set a password policy of at least 8 characters, matching the local form minimum.

The app uses the Supabase Kotlin Auth SDK for signup (with `full_name` metadata), email/password login, automatic session persistence/refresh, and logout. Signup with confirmation enabled stays on the auth screen until email verification. Confirmation links open the app. Recovery links open the password update form; saving signs out so the user can log in with the new password. Backup is disabled to avoid backing up local auth sessions.

No custom public database tables or database permissions are required for authentication. Future road reports should have their own tables and row-level security policies.

## Device verification

After adding settings, rebuild and run the app. Create a test account, confirm its email, sign in, restart the app to check restoration, and sign out. Request a reset email, open it on the Android device, set a new password, and confirm that only the new password works. Check incorrect credentials, expired links, offline errors, and duplicate-tap prevention. This requires a configured Supabase project and real email delivery; compilation alone does not verify those paths.

Reference: https://supabase.com/docs/reference/kotlin/initializing

## Offline accounts, scans, and report sync

The app restores the SDK's persisted session with auto-refresh disabled during startup, so loading the dashboard does not wait for internet or an expired token refresh. Passwords are never stored by the app. The background worker refreshes the token before uploading; a network failure leaves the saved account and local reports intact. Explicit sign-out clears the local session even offline. A revoked/invalid session may require signing in again. Reports remain on the device under their original account and are never uploaded by another account.

Run `supabase/migrations/20261101_offline_reports.sql` in the Supabase SQL editor. This creates `road_reports`, a private `roadwatch-captures` bucket, and owner-only row/storage policies. The Android app uses only the public key plus the signed-in user's token. Do not put administrative credentials in the APK.

Camera scanning uses the bundled cleaned-v3 YOLO11s ONNX model without a network. Start a scan while parked; allow camera access. The app analyzes at most one frame every two seconds and saves road images with possible potholes and normalized boxes to private files/SQLite. Stop the scan to save a draft, including scans with zero detections. Interrupted scans are recovered as drafts on the next launch. Open Detections, review the captures, and tap Submit report to queue an upload. Drafts never upload automatically. Upgrading from the original offline-scans release returns unsynced pending reports to drafts; already synced reports remain synced. Location is optional. A fresh foreground fix, when available, is stored with latitude, longitude, accuracy and capture time inside each capture JSON object. Missing coordinates stay null. Severity is not estimated. No additional SQL columns are needed for coordinates because captures is JSONB. Leaving the app stops and saves the current scan as a draft; background recording is not implemented.

WorkManager retries uploads with a connected-network constraint and exponential backoff. Android controls exact scheduling, so sync is eventual rather than instantaneous. Stable UUID paths and database upserts make retries safe if the connection drops after a server write. Local data is retained after sync for offline review; there is no automatic retention cleanup yet. Detections are model suggestions and may contain missed/duplicate potholes across frames.

### Model provenance and rebuilding

The model comes from `i-ssa/SemProject_MLEngine`, using the checkpoint SHA-256 documented in its `docs/MODEL.md`. `app/src/main/assets/pothole-model.json` records the export's checksum and shape contract. To reproduce, install PyTorch, Ultralytics, ONNX, and ONNX Runtime in a Python environment, then run:

```bash
python scripts/export_android_model.py /path/to/verified/best.pt
```

### Acceptance checks

1. Configure Supabase, apply the migration, sign in online, then force-stop and reopen in airplane mode: the dashboard should open under the same account.
2. With airplane mode on, complete a camera scan and review its saved captures. Force-stop/reopen: the report remains a draft and does not upload after reconnecting until explicitly submitted.
3. Review the draft and tap Submit report. Restore internet and wait for WorkManager (or tap Retry sync); verify the row and private images belong to the same account and the report becomes Synced.
4. Drop connectivity during upload and retry: one report row and stable image paths should remain.
5. Sign out offline, sign in to a different account: the previous account's reports must remain hidden and must not upload. Sign back in to resume them.
6. Test camera denial, unavailable camera, interrupted scan, expired access token, invalid refresh token, and database/storage policy errors on a real device. Emulator/unit tests do not establish phone performance or real Supabase delivery.

### Automated verification

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest` with an emulator or device attached. Device tests cover restoring an expired session without a server connection, SQLite persistence and account isolation, interrupted-scan recovery, active scans surviving screen recreation, bundled ONNX inference, camera capture, explicit submission, upgrade migration, and dashboard navigation.

`LiveAccountScanTest` is skipped by default because it uses the account already signed in on the device. For an explicit acceptance check, disable the emulator's Wi-Fi/mobile data and run `recordOfflineScan` with instrumentation argument `liveAccount=true`. Re-enable connectivity, then run `syncRecordedScan` with `liveAccount=true` and `reportId` set to the UUID printed by the first phase. It verifies the actual report row and every uploaded private image. This creates a real emulator test report in that account; its model suggestions are not verified road defects.

On 2 November 2026, the debug build, two JVM tests, nine regular device tests, and both live acceptance phases passed. A force-stopped app reopened directly to the saved account's dashboard with emulator Wi-Fi and mobile data disabled. The offline emulator scan uploaded one report and one capture after reconnecting. These results predate the draft approval flow; rerun acceptance checks for that flow.

## Hazard map and local cleanup

Version 1.2.2 combines private local drafts/pending captures with the latest 500 shared submitted hazard locations. Apply both migrations below; the original offline-report migration alone does not enable sharing. Refresh and Google basemap tiles require internet; coordinate caches are account-specific and stored in private files. Raw reports and photos remain owner-only.

Only drafts can be edited. Removing a synced report locally frees its images on this device and does not delete the cloud copy. Cloud deletion is not implemented. During sign-out, upload work is cancelled and later requests check the account generation. A request already accepted by the server may still complete. Token refresh uses a bounded lock; the lock no longer spans image uploads.

## Workflow regression checks (1.2.1)

`SyncResilienceTest` uses isolated Android databases, local test images and a fake cloud receiver; it makes no Supabase requests. It exercises lost upload responses, process reopening, unchanged UUID/object paths, cancellation/account changes, per-request timeouts and per-report error isolation. See WORKFLOW_TESTING.md for device steps and commands.

The opt-in `syncRecordedScan` acceptance check can resume a pending report or re-verify a synced report using the same report ID. It checks one remote row, the exact capture ID set, metadata, owner-scoped object paths and SHA-256 equality of uploaded images. It still requires explicit `liveAccount=true` and writes only when the selected report is submitted/scheduled. Do not claim live cloud acceptance from the fake-remote suite.

## Community hazard map (1.2.2)

Apply `supabase/migrations/20261103_shared_hazards.sql` after the original report migration. It publishes coordinates from submitted reports, including existing uploaded reports, to signed-in users. Raw reports, images, drafts and observation owners remain private. Only the server trigger can write shared hazards. The new APK requires this migration; until applied, shared refresh fails and local pending/draft locations remain available.

The map requests foreground location on entry, zooms to a fresh fix at street level, and the target button requests another fix. Denied permission or unavailable GPS leaves browsing usable and shows retry guidance. Opening the map does not upload the user's browsing location.

While open, cloud coordinates refresh every 15 seconds; uploads still depend on WorkManager and connectivity. The feed contains the latest 500 hazards. This is polling, not realtime streaming.

Each capture has an idempotent observation key. A submitted capture within 10 metres of an existing hazard is attached to it only when both have reported GPS accuracy of at most 20 metres. Other valid locations remain separate. A server transaction lock serializes matching/creation to prevent simultaneous duplicate clusters. The public count measures distinct reporting accounts, not frames or retries. GPS proximity is only a grouping heuristic: nearby distinct potholes may merge and inaccurate sightings may stay duplicated. A capture may contain multiple detected potholes, but only has one phone coordinate; this release maps a possible-hazard location, not individually geolocated defects. Spatial indexes, smaller lock partitions, moderation, repair status and image-based confirmation are future work.

Deployed on 3 November 2026 to RoadWatch (`pbyfwdwndnopgddymsee`) using the authenticated Supabase SQL editor. The deployed SQL exactly matches `20261103_shared_hazards.sql`. Live transactional checks in `supabase/tests/live_shared_hazards.sql` passed under two authenticated user identities: shared marker visibility, private report/photo metadata isolation, distinct-user counts, upload retries, direct-write denial and anonymous-read denial. All synthetic users, reports, storage metadata and markers were rolled back. A real anonymous REST request returned HTTP 401. The three pre-existing uploaded reports produced zero geolocated observations; no coordinates were invented for them. Concurrent local PostgreSQL transactions also produced one marker with two reporters.

The live checks exercise deployed database roles/RLS; they do not claim two physical phones were tested. `LiveCommunityMapTest` is an opt-in read-only Android REST/cache check (`liveAccount=true`) requiring a signed-in device.
