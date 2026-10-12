# Status log

## 2026-10-10: repository created (phase 0)

- Scaffold: a copy of the Emby SmartLists app v0.4.1 (commit 79444cb, source `C:\dev\emby.smartlists.android`) with the package, app name, theme and keystore alias renamed to Embidextrous. Features today = SmartLists 0.4.1 (sign-in, list overview, editor, backups, widget and tile).
- Builds (`assembleDebug`) and all 86 carried-over unit tests pass.
- Logo: the "Handshake" Es as an adaptive icon (background `#0F2A1C`, green E + cream mirrored E, one-colour themed version); master files in `assets/logo/`.
- Added: README, SECURITY.md, CLAUDE.md, `docs/plan.md`, `docs/decisions.md`, CI workflow, release workflow (signed APK, provenance attestation, actions pinned to commits, `release` environment), Dependabot, issue templates.
- Not done yet: phase 1 onward (see `docs/plan.md`). No release has been made; the signing key does not exist yet (the owner creates it and adds the `SIGNING_*` secrets).
- Not verified: the renamed app was not launched on an emulator in this step (build and unit tests only).

## 2026-10-10: phase 1 (shell and Watching Eye tab) smoke-tested

- Built: one sign-in for any Emby account; bottom navigation; Watching Eye tab (live push connection with REST fallback, cards, quick add-time with undo); Smart Lists overview tab (administrators only); Settings with the Watching Eye key. Unit tests pass.
- Smoke test on a private emulator (Android 37, debug build) against the local Emby 4.10.1.0 test server with throw-away users, all since deleted and the plugin reset:
  - Administrator: three tabs; key prompt, then "Live"; a user added to the plugin appeared by push without refreshing; +15 changed 1 h 00 to 1 h 15 and showed the Undo snackbar; Smart Lists overview loaded (empty list); Settings shows the key as set.
  - Regular account: only Watching Eye and Settings tabs; key entry, live card and data worked. No crash in logcat.
- Not verified: Undo and the other actions (time-out, resume, reset, stop stream), playback (now playing, countdown while watching), live reconnect after network loss, rotation, dark theme, the SmartLists overview with real lists, signing out, a real phone, and the Smart Lists tab's "not available" state.
- Minor to fix: the Smart Lists tab has its own gear icon and the title "Embidextrous", which duplicates the Settings tab.

## 2026-10-10: phase 3 (alerts, widget, name hiding, settings)

- Built: optional background alerts (foreground service holding the live connection; notification when someone reaches a limit, with +15/+30/+60 buttons that need the phone unlocked, and a generic lock-screen version); the Watching Eye home-screen widget (live while the app or the service runs, otherwise its own 30-minute refresh, cleared on sign-out); "hide names" for the widget; settings switches for both; restart of the alerts at boot; the Smart Lists tab no longer repeats a gear icon. 9 new unit tests (limit-alert rules, widget text).
- Smoke test on a private emulator (Android 37, debug build, test server, throw-away users since deleted, plugin reset):
  - Alerts: the notification permission prompt appears when the switch is turned on; the service runs as a foreground service; with the app in the background, a user going over a 1-minute limit (real playback reports) produced the notification with its three buttons; "+15 min" gave the user 900 s of credit and the notification went away.
  - Widget: placed from the launcher, showed "embi_reg — 13 min left, Live"; with "hide names" on it showed "User 1"; after sign-out it showed "Signed out" and the service was gone.
- Not verified: lock-screen behaviour of the notification (hidden text, buttons needing unlock), reboot restart, the widget's own 30-minute refresh with the app closed, notification permission denied path, Android 13-, a real phone, rotation. The widget picker shows the app icon as its preview (a proper preview is not made yet).

## 2026-10-10: phase 5 (smart-list editor inside the combined app) verified

- `gradlew test assembleDebug` passes (all unit tests). No code change was needed: the editor copied from SmartLists v0.4.1 works as is in the combined app.
- Verified on a private emulator (Android 37, debug build) against the local Emby 4.10.1.0 server and the SmartLists plugin, with a throw-away administrator (deleted afterwards; the plugin lists and backups made were deleted too):
  - Navigation and layout: the bottom bar shows on the three tabs and is hidden on the template picker, the editor and Backups; the Smart Lists overview, snackbars and the "New list" button clear the bar; no double status-bar padding; the keyboard pushes the Name and Description fields up; the sort, field and time pickers (full-screen dialogs) and the preview sheet respect the system bars; rotation to landscape keeps the editor state.
  - Create from the "Next Up" template; change a rule value (library chip); remove a rule; add an OR group with a "Production Year equals 2024" rule (the saved JSON had both expression sets); change the sort to Name descending; add a daily 2:00 AM refresh schedule; set the description; preview (12 items, descending); save, which closes the editor and the overview shows the new list at once (notifyListsChanged).
  - Edit an existing list (back with no change leaves without a prompt; back with changes shows "Discard your changes?" and Keep editing works; the first Back only closes the keyboard).
  - Duplicate (name "... (copy)", schedule and description kept on the server); disable and enable (snackbar, "Disabled" chip); refresh now; refresh all (toolbar icon and the widget/tile receiver by broadcast); delete with Undo (list stays on the server) and delete without Undo (removed from the server after 8 s).
  - Share (system sheet with the list JSON), copy it, then Import from the clipboard (Paste, Import) opens a filled "New list" draft.
  - Backups: opened from Settings; Back up now (2 lists), restore (a list deleted on the server came back and the overview showed it), delete a backup (done by API at cleanup).
  - No crash in logcat from the app (the only ANRs were Google system apps during the cold boot).
- Not tested: import from a file ("Open file..."), "save a copy" of a backup to the phone (system file picker), custom image upload (photo picker) and bumpers, the collection type and the "playlist for every user" switch, per-rule runtime/audio/parent/collection-depth options, the tag-add field, "last refresh failed" marker, Material You colours, placing the widget on the home screen and tapping it (only the receiver was exercised, and the Quick Settings tile), a real phone, dark theme in the editor, tablet or large font sizes.
- Observations (not changed): Back from the Smart Lists or Settings tab goes to the Watching Eye tab (the start destination) instead of leaving the app; the import dialog's draft is not marked as changed, so leaving it without saving gives no discard prompt.

## 2026-10-10: release preparation

- Phase 5 (editor) was verified in the combined app (see the entry above this one); no code changes were needed.
- The release workflow now fails unless the APK is signed with the expected key: the SHA-256 of the release certificate (public information) is pinned in `release.yml`. The first key was replaced before any release existed, because its keystore text had been pasted into a chat; the pinned value belongs to the second key.
- CodeQL: GitHub's default setup failed at its automatic build (Android needs Java 21 and the SDK licenses). `.github/workflows/codeql.yml` builds the app like CI and scans Kotlin and the workflow files. The default setup must be OFF in the repository settings, otherwise GitHub refuses the results of this workflow.
- Still to do before 0.1.0: the owner adds the `SIGNING_*` secrets, the `release` environment (Tag rule `v*`), immutable releases, rulesets, secret scanning and push protection; release notes in `docs/releases/v0.1.0.md`; then "publish it".

## 2026-10-11: first release attempt

- Tag `v0.1.0` was pushed first. Its release run failed at the unit-test step (`./gradlew: Permission denied`, the wrapper had no executable bit and the release workflow, unlike ci.yml, did not chmod it). Nothing was built or published. The tag cannot be moved (tag ruleset), so the first release is `v0.1.1`. Fixed in `dcd8b1e`.
- The steps after the build (pinned-key check, attestation, release creation) had not run in a real release before 0.1.1.

## 2026-10-11: first feedback from the real phone (0.1.1)

- Reported: sign-in works and both tabs load; the accent colour tints everything on the Watching Eye tab, and the users' profile pictures were missing.
- Changed: the Watching Eye tab (and the bottom bar while it is open) now uses neutral greys for the background, cards and tonal buttons, in light and dark; the accent only colours the progress bar, the main button and links. The Smart Lists tab keeps the full accent look. Users' profile pictures are loaded from the server (`/Users/{id}/Images/Primary`, 96 px, kept in memory) with the first letter as the fallback.
- Checked on an emulator (violet accent, light and dark): neutral cards, picture shown for a user who has one, initial for one who has none. Not checked: a real phone, a very large picture, a regular account's access to other users' pictures (it falls back to the initial if refused).

## 2026-10-11: gaps against the old apps closed

Compared the old Watching Eye app with this one and fixed what was missing:
- **Wrong-key protection** (`KeyGuard`): the plugin locks its public API for 5 minutes after 5 wrong keys in 5 minutes. After the plugin refuses the key (HTTP 403 or a refused live connection) the app now makes no key-protected request for 5 minutes (live reconnect waits as long as well), shows a clear banner, and starts afresh as soon as a new key is saved. Before, the 20-second fallback would have tripped the lockout by itself.
- **Poster** of what is being watched on the card (from `/Items/{id}/Images/Primary`, in-memory cache).
- **Battery hint** under the alerts switch, with a button that opens Android's battery settings.
- Not ported on purpose: support for plugins older than 1.9 (now-playing from Emby sessions, the admin status route); README says 1.9 or newer.
- Emulator check (light theme): a wrong key showed the banner and no lockout (about 2 minutes later the plugin still accepted the correct key from this PC); saving the correct key recovered at once; a stream by the limited user showed the poster slot, title, position and client; the battery hint and button appear when alerts are on. Not checked: a real phone, a real poster image, the battery settings screen itself opening, the 5-minute retry actually happening (only the unit test covers the timing).
- 5 new unit tests (key guard).

## 2026-10-11: Gradle 9.8.1 and Android Gradle Plugin 9.4.1

- Merged the two Dependabot upgrades. The new `gradle-wrapper.jar` was checked against the SHA-256 Gradle publishes for 9.8.1 (`3b8a2577...e68e98`, match). The AGP PR had failed CI on its own with Gradle 9.5.1; together with Gradle 9.8.1 it builds.
- Verified locally before merging: `test assembleDebug`, `assembleRelease` (2.14 MB, same size as before), the release APK starts without a crash and shows the HTTPS-only sign-in, and the debug build still signs in, shows the Watching Eye cards live and the Smart Lists tab (emulator). Not verified: the release APK against a server (release builds refuse plain http, so the test server cannot be used), a real phone. These upgrades are not in the 0.1.2 release; they ship with the next one.

## 2026-10-11: phase 6 built (profiles and the Home screen), time-left rounding fixed

- **Profiles:** after an administrator account signs in for the first time on a phone the app asks how it is used (Parent, Administrator, Custom); the answer is stored on the phone and changeable in Settings. A regular account is simply Parent. The tabs follow it: Home, Watching Eye, Smart Lists (only for Administrator or Custom with the switch, and only with an administrator account), Settings. Parent shows no Smart Lists tab and none of the smart-list settings.
- **Home** is the start screen (neutral "calm" look like Watching Eye): a status card ("All good" or "N things need attention" with an action each: set the key, +15 min for someone at their limit; connection problems first; a deliberate time-out is not a problem), Watching now (poster, title, device, time left, +15 min, Stop stream), Kids today (one row per user with time left, a thin bar, this week's total; tapping opens the Watching Eye tab), and Bedtime.
- **Bedtime:** choose who (everyone ticked) and 1 hour or until 6:00 AM (the next 6:00, at 1 AM that is the same morning; the dialog says when it ends), then each user is timed out with one Undo; "End time-outs" (with a confirmation) appears while anyone is timed out.
- **Bug fix reported by the owner:** "time left" now rounds UP to the minute from 10 minutes upward (below 10 minutes it still counts seconds), so +15 min on someone who ran a moment past the limit reads "15 min" and not "14 min" (14 min 59 s). Time used and weekly totals still round down. Applies to the cards, Home and the widget text.
- Emulator check (Android 37, debug build, throw-away users, all since deleted, plugin reset): first-run choice; Home with four tabs; missing-key row with a Settings button; live data; Watching now with poster; a kid past a 1-minute limit showed "1 thing needs attention" and +15 min gave "15 min left"; Bedtime dialog (both presets and their end times), timing both kids out (the stream stopped), End time-outs; switching to Parent removed the Smart Lists tab. 20 new unit tests (attention rules, Bedtime timing including the night the clocks change, profiles, rounding).
- Not verified: a real phone; the Custom profile on a device (only its unit test); the "until 6:00 AM" Bedtime being started (only its dialog text was checked, the 1-hour one was run); Undo of Bedtime; the Home screen with a regular (non-administrator) account; dark theme and large fonts on Home; many limited users (a long list).

## 2026-10-11: Hall Monitor rename and the violet and mint palette

- **Renamed Watching Eye to Hall Monitor** everywhere the user sees it: the tab (its icon is now a timer instead of an eye), Settings, the key prompts, the Home screen texts, the sign-in text, notifications ("Hall Monitor is watching for limits"), the widget, error messages and the README. Internal code names (`watching` package, `WatchingEyeClient`, the plugin's `/WatchingEye/...` routes) are unchanged, so the app works with plugin 1.9 and with 1.10. The plugin (now `feerlessleadr/hall-monitor-emby`, version 1.10.0.0) carries the new name and logo; its regression suite passed 65 of 65 on the test server.
- **The palette from the Hall Monitor logo is now the app's default** (the first accent swatch, "Hall Monitor"): deep violet `#140B2E` surfaces, violet `#B388FF`, mint `#5CF2C0`; hand-tuned light and dark schemes, contrast-checked in unit tests (every text pair at least 4.5:1). The other accent colours and Material You still exist. With the Hall Monitor palette the Home and Hall Monitor tabs use the normal scheme (no extra neutral override); with another accent they keep the neutral "calm" cards.
- **Launcher icon:** same shape (two Es shaking hands), now violet and mint on deep violet (asset PNG and SVG in `assets/logo/` too). The widgets and the notification icon (a progress ring around a play button) follow the palette; the Smart Lists refresh widget background is a pale lavender.
- Emulator check (light and dark, Android 37): sign-in, first-run profile choice, Settings (new swatch first, mint selected-tab indicator), Home, the Hall Monitor tab, Smart Lists, widget (violet, mint title), launcher icon. Not checked: the notification icon on a real notification (only built and shipped as a vector), a real phone, large fonts, other accent colours after this change beyond what the existing tests cover.
- Tests: the default-accent test now expects Hall Monitor; new `PaletteTest` (contrast and the logo colours).

## 2026-10-11: new launcher icon
- The launcher icon, the themed (monochrome) icon and the README image are now the two-circles design described in `docs/decisions.md`. The old handshake Es are gone from the app (the history keeps them).

## 2026-10-11: 0.2.0 released
- Embidextrous 0.2.0 (tag `v0.2.0`, commit `ae27362`) published by `release.yml`: tests, signing-key check, provenance attestation and the immutable release all passed. `Embidextrous-0.2.0.apk`, 2,177,310 bytes, SHA-256 `6ea64f1c954f7e3b82c0ac42aa6d3888bbf1d21dcf871f4c7b7eaec67d86a85c`.
- Hall Monitor plugin 1.10.0.0 (tag `v1.10.0.0`) published the same day: `WatchingEye.dll` 214,528 bytes, SHA-256 `b3b0de8f090abb68bb72915c7caddc6899ae6b11b428bef57eb87a0ad337ec10`.
- Not yet verified: use on a real phone (see the release notes' known limits). Next: phase 7, and phase 9 (plugin manager, spec from the GitHub Repo Plugin Install plugin).
