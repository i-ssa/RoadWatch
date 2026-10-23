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
