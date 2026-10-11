# Embidextrous (Android)

One Android app for the owner's Emby administration: smart lists (the SmartLists plugin) and watch-time limits (the Watching Eye plugin), with more to come (a plugin manager for GitHub downloads, bulk tag editing, explicit-tag editing for music). Kotlin, Jetpack Compose, Material 3, OkHttp, kotlinx.serialization. Package `com.feerlessleadr.embidextrous`, repo `feerlessleadr/embidextrous` (public, MIT).

Origin: this code began as a copy of `C:\dev\emby.smartlists.android` (v0.4.1, commit 79444cb) and is being merged with `C:\dev\watching-eye-android-app` (Java/XML, being ported to Kotlin/Compose). Those repos stay untouched until this app replaces them. Their notes: `C:\dev\emby.smartlists.android\HANDOFF.md`, `C:\dev\watching-eye\PROGRESS.md`.

## Read first
- `docs/plan.md` (roadmap), `docs/decisions.md` (every decision with the reason: add to it whenever a decision is made, however small), `docs/status.md` (what is built and verified).

## Foundations (do not relitigate without new evidence)
- **HTTPS only in release builds**, system CAs only, no cleartext (the debug source set allows cleartext to the emulator host `10.0.2.2` only), **no redirects followed** (`followRedirects(false)`).
- **Token only in the `X-Emby-Token` header**, encrypted with an Android Keystore AES-GCM key; the password is never stored; sign-out ends the server session and deletes the key.
- **Every server-supplied id is validated before it goes into a URL** (`ApiClient.seg`); inbound sizes and depth are bounded (`data/Safety.kt`).
- **No app lock and no FLAG_SECURE** (decided twice: the phone's own screen lock is relied on). Do not add one.
- Notifications that carry names show a generic version on the lock screen; their action buttons need an unlocked phone.
- One sign-in. An administrator account sees every tab; a regular account sees only Watching Eye (the SmartLists plugin needs an administrator).
- Lists are raw JSON edited through drafts, so unknown plugin fields survive; rule and sort knowledge comes from the plugin's `/catalog`.
- Distribution: signed APK on GitHub releases, installed with Obtainium. The signing key is NOT in the repo and a debug-signed release must never be published (Gradle refuses unless `-PallowDebugSigning`).

## Commands (Windows, PowerShell)
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
./gradlew.bat test
./gradlew.bat assembleDebug
```
Versions are in `gradle/libs.versions.toml` (AGP 9.x needs Gradle 9.4+; the wrapper is 9.5.1; compileSdk 37). `local.properties` (git-ignored) holds `sdk.dir`.

## Testing on the emulator
- Use a private AVD with its own name and port, started headless (`-no-window -no-audio -no-snapshot`), and stop only that one by name. Other projects share this machine's emulators and adb: never kill all emulator or adb processes. Run adb calls with a timeout.
- The test Emby server is `C:\Users\kgiglio\Downloads\embyserver-win-x64-4.10.1.0` (port 8096, media `C:\claude`), reachable from the emulator at `http://10.0.2.2:8096`. Its admin API key is in `%USERPROFILE%\.emby-spike-key` (never print it). Create throw-away users for tests and delete them afterwards. Never touch the real users `kevin` and `test1`.
- Screenshots are 1080x2400, shown at 900x2000: multiply coordinates by 1.2. Check each screenshot is really this app before tapping.

## Conventions
- Commit attribution line: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Commits are authored as `feerlessleadr <github@thecgkg.com>` (never the work email). Commit with `git commit -F <file>`, never a quoted multi-line argument in PowerShell.
- Push and tag only as the owner has instructed (standing permission to push; releases need "publish it").
- Never print, store or commit keys, tokens, passwords or keystores. GitHub secrets are added by the owner.
- Keep README, `docs/` current; note anything a later session needs in `docs/status.md`.
