# Embidextrous: handoff (current state, decisions, lessons)

Written 2026-10-13 at app 0.8.0. `docs/status.md` is the dated log, `docs/decisions.md` has every decision with its reason, `docs/plan.md` the phases; this file is the consolidated picture for someone starting cold. `CLAUDE.md` has the fixed foundations and commands.

## 1. What it is
Kotlin / Jetpack Compose / Material 3 / OkHttp Android app (package `com.feerlessleadr.embidextrous`, debug build id `...embidextrous.debug`) that combines the owner's Emby tools: Hall Monitor (watch-time limits, `C:\dev\watching-eye`), SmartLists, a plugin manager for the GitHub Repo Plugin Install plugin, Tracearr activity, server tools, and the Hall Pass review tab (`C:\dev\hall-pass-emby`). Signed APK on GitHub releases, installed with Obtainium. Signing key is on the owner's other machine; the assistant never sees it.

### Releases (tag = `v<version>`; hashes are GitHub's digest of the APK)
| Version | Commit | APK bytes | SHA-256 | What |
|---|---|---|---|---|
| 0.4.0 | (tag v0.4.0) | 2,406,454 | f88e63799f13276f846495a2b98aff2b149529e2291be9427d28216cabd8a609 | plugin manager, Tracearr, grouped navigation |
| 0.4.1 | | 2,406,454 | 99cda972c239cdcabe36189865f2a6bcecaf8a7c32781c4a3857ebdabdb3defa | Live shows the series poster |
| 0.5.0 | d3c2183 | 2,406,454 | 544ab5a276e62cb307c994dbe20e7d835f9f49ee5d649fec4def0f99495bcaf7 | everything renamed to Hall Monitor (needs plugin 2.0+) |
| 0.5.1 | 87115d1 | 2,406,454 | 5ec13237c4f19fef65e2219dbf5b6b5c5f547da098661e88ba9456f4fd36422d | stable Live order, show poster on Home |
| 0.6.0 | 0dff441 | 2,439,222 | 0f86b0b1e0c122e6b05cdad30d812786203c7e5cd9b5e7db9329146f65de9b00 | first Hall Pass tab |
| 0.7.0 | 140796a | 2,439,222 | 3183e24063e3c21d4ca28c3c949c11dc624370c142ec7cce525a5e58dda949eb | watched totals on the Kids card; Reset means a fresh day (needs Hall Monitor plugin 2.1.0.0) |
| 0.8.0 | c33a234 | 2,455,606 | 4e6b3f360d0efbc98c00364f4b9ca8714e770b0de062de91ab293c9d2d06f8f5 | swipe review, artists, most played first, bulk marking; needs Hall Pass 1.2.0.0 |
Release routine (only on "publish it"): notes `docs/releases/vX.Y.Z.md` committed first; CI and both CodeQL jobs green on that commit; `git tag -a vX.Y.Z`; push; the Release workflow builds and signs; report size and digest. Version name comes from the tag (`-PappVersion`).

## 2. Navigation
Bottom bar: Home, Watching (Hall Monitor, Activity), Library (Smart lists, Libraries, **Hall Pass**, a debug-only Tags placeholder), Server (Live, Tasks, Activity, Devices, Plugins), Settings. Profile (Parent / Administrator / Custom) is per phone and decides the tabs and Home cards. GroupTabs uses a HorizontalPager (so horizontal gestures inside a tab can fight the pager: the swipe review is a full-screen Dialog for that reason).

## 3. Hall Pass tab (Library > Hall Pass)
Files: `data/HallPass.kt` (models, tolerant parser, version gate `HallPass.MIN_PLUGIN = "1.2.0.0"`), `ApiClient.hp*` (calls `/emby/HallPass/...` with the signed-in **administrator's token**, no separate key), `hallpass/HallPassViewModel.kt`, `HallPassScreen.kt`, `HallPassSwipe.kt`. Features: summary card (counts, scan progress, Scan now / Scan all again / Apply...), "What to do next" card (`nextStep`, five steps, says where the dry-run switch is: plugin page Settings > Tags & writing), filters (Needs review, Explicit, Likely clean, Clean, All; reason chips with counts when Needs review), Tracks / Albums / Artists, Order A to Z / Most played, search, track cards (Explicit / Clean / Clear, tap for evidence, plays), album and artist cards (Open, All explicit, All clean with a confirmation), "Decide faster" card (swipe review; All N explicit / All N clean for the current view with a confirmation that spells the view out: `describeView`), apply preview dialog (only offers to write when dry run is off). Swipe review: right = clean, left = explicit, threshold 30% of the width (`swipeOutcome`), Skip moves a track to the end, Undo clears the manual decision; decisions leave the list at once and go to the plugin in the background, a failure restores the track (`decidedInSwipe` filters stale reloads). Settings (word list, online lookups, roll-up, dry run) deliberately stay on the plugin's Emby page: they change what is sent out or written.

## 4. Hall Monitor additions (needs plugin 2.1.0.0, `ApiLevel >= 3`)
`UserStatus` reads `WatchedToday/Week/Month/YearSeconds` and `WatchedTrackingSince` (kept, not shown). The Home "Kids today" card shows watched today and week, then month and year (`WatchText.total`); the Hall Monitor card shows the same line. The footnote about when counting started was removed at the owner's request. With an older plugin the card keeps its old "This week" line. The three-dot item reads "Reset today's time..." with a "Fresh day" dialog on plugin 2.1 (Reset now only zeroes today's allowance and extra time). Background: weekly reset was off for the owner's children, Reset used to zero day, week, month and year together (that made one child's week look wrong and another's zero), and the owner wants totals of time really watched that no reset touches (a permanent ledger in the plugin, `docs`/PROGRESS section 16 of the plugin repo).

## 5. Decisions worth knowing (more in decisions.md)
- No app lock and no FLAG_SECURE (decided twice). HTTPS only, no redirects, token only in `X-Emby-Token`, ids validated through `seg`.
- Hall Pass tab uses the administrator token; minimum plugin 1.2.0.0 (raised from 1.1.0.0 at 0.8.0 because Artists and plays need it; older plugins get a "too old" card, the rest of the app is unaffected).
- Bulk actions always show the count and the view in the confirmation; nothing is written to files by marking (only Apply writes, and only with dry run off).
- Secrets (Tracearr key, plugin switches) are entered only by the owner.

## 6. Testing recipes and lessons
- Unit tests: `./gradlew.bat test` (PowerShell, `JAVA_HOME` = Android Studio's JBR, see CLAUDE.md). Parsers, gate, swipe threshold, view description, next step, watched totals are covered (`HallPassTest`, `HallPassSwipeTest`, `WatchedTotalsTest`).
- Emulator (nothing on this machine has `avdmanager`): make a private AVD by hand: new folder `~/.android/avd/Hp_Test.avd` with `config.ini` copied from `Medium_Phone.avd` (set `AvdId`, display name) plus `Hp_Test.ini` (`path`, `path.rel`, `target=android-37.0`), start `emulator -avd Hp_Test -port 5572 -no-window -no-audio -no-snapshot -no-boot-anim`, always `adb -s emulator-5572`, stop with `adb -s ... emu kill`, delete the folder and ini afterwards. Install `app/build/outputs/apk/debug/app-debug.apk`; launch `com.feerlessleadr.embidextrous.debug/com.feerlessleadr.embidextrous.MainActivity`. Server from the emulator: `http://10.0.2.2:8096`. A throw-away admin: `POST /emby/Users/New`, set password, set `IsAdministrator` in the policy; delete it afterwards; the password lives only in a temp file and is typed with `adb shell input text` without printing it. In the sign-in form press Tab (`keyevent 61`) between fields: tapping fields after Back put the password into the user-name field once. Screenshots are 1080x2400, shown at 900x2000 (multiply coordinates by 1.2).
- Done this way for 0.6.0: tab loads live data, a decision moved the counts, Albums, the confirmation and the dry-run dialog. NOT done for the swipe review, the artist view, the reason chips, "All N" confirmations, the Kids card totals (unit tests and plugin-side live checks only).

## 7. Not verified / ideas
Real phone use of everything above; dark theme of new screens; the swipe gesture's feel; Apply with dry run off from the phone; large libraries (paging is 50); Tag Team (bulk tag editing) and the debug-only Tags placeholder; a "Set up another phone" code; raising the plugin gate message for 1.1.x users is the only compatibility handling.
