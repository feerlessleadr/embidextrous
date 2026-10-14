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

## 2026-10-12: phase 7 (administrator Home cards, Customise Home, widget headline)
- New Home cards for administrator profiles (an administrator account with the Administrator profile, or Custom with Smart Lists on): **Smart lists** (how many, switched on, refreshing now, which failed their last refresh, last refresh time, Refresh all, Open lists), **Server** (Emby version and name, restart pending, update available, connected and playing and transcoding sessions, failed scheduled tasks) and **Plugins** (count, Hall Monitor and SmartLists versions with a warning when Hall Monitor is older than 1.9 or missing, Emby catalog updates, full list on request). Read only except Refresh all. They use Emby's own `System/Info`, `Plugins`, `ScheduledTasks`, `Sessions` and `Packages/Updates`; one failed call blanks only its own line. The server cards look again when Home comes to the front (at most every 30 seconds) and on pull to refresh.
- **Customise Home** (the sliders icon on Home): switch each card on or off and move it up or down; the status line stays. Saved on the phone; Reset restores the default. A card a newer app adds appears at the end.
- **Widget headline**: the Hall Monitor widget now starts with "All good" (and how many are watching) or "<name> at the limit", in amber when something needs attention, using the same rule as the Home status card and honouring "Hide names".
- Tests: 19 new unit tests (layout, parsers, plugin version check, lists snapshot, widget headline, profile rule); 155 in total, all passing.
- Emulator check (Android 37, debug build, local Emby 4.10.1.0, throw-away administrator and limited user, deleted afterwards, plugin reset): first-run choice, Home with the three new cards in the default order, hiding a card, moving a card up, the Hall Monitor key saved and Kids today, Bedtime and the live status appearing.
- Not checked: the widget on a device (only its headline text is unit tested), Refresh all (the test server's lists were left alone because it is shared), a server with failed tasks, a pending restart or plugin updates (only the parsers are tested; the shape of `Packages/Updates` is unverified because the test server had none), the Parent profile hiding the cards on a device (unit tested), a real phone.
- Limits: a plugin installed from GitHub is not checked yet (phase 9 adds that); the "connected" count is Emby sessions active in the last 10 minutes.

## 2026-10-12: a more designed look (sign-in, profile choice, Home, the other tabs)
- A shared look in `ui/Brand.kt`: the Hall Monitor colours for the always-dark parts, a backdrop with the icon's two soft circles, the logo tile, icon badges and pills.
- **Sign-in** and **profile choice** are full dark-violet screens with the logo; sign-in has the form in a rounded card with field icons; each profile is a glassy card with an icon and chips showing the tabs it gives.
- **Home**: a greeting hero ("Good evening", All good / what needs attention), kids with a progress ring of time left around their picture and a status pill, a Bedtime card with a moon, rounded cards with icon badges; on a dark screen the hero and Bedtime cards are brighter with a thin violet outline.
- **Hall Monitor tab**: the same rings and pills on rounded cards, nicer empty and key-entry states. **Smart Lists**: rounder cards and a proper empty state. **Settings**: grouped into rounded cards with icons (Appearance, This phone, Hall Monitor, Smart lists, Account, About).
- The selected tab's indicator is now violet (it was a muddy green in dark mode).
- Behaviour is unchanged. 156 unit tests pass (one new, for the greeting).
- Checked on an emulator (Android 37) in light and dark mode: sign-in, profile choice, Home, Hall Monitor, Smart Lists (empty), Settings. Not checked: the "Watching now" and "needs attention" Home states with the new look, large fonts, the Smart Lists cards with real lists, a real phone.

## 2026-10-12: neutral base, accent only for highlights
- Every colour scheme now has neutral greys for its surfaces (usual dark-mode greys; light greys in light mode) and uses the chosen accent only for highlights: buttons, the selected tab, switches, rings, badges and links. The secondary colours follow the accent, so the +15 / +30 / +60 tonal buttons look the same as every other button. The hero, Bedtime, sign-in and profile screens are neutral near-black with a soft wash of the accent.
- The accent picker (Settings, Appearance) changes all of it, including "Match my wallpaper". Good and warning colours (green, amber, red) keep their meaning and do not change with the accent.
- 156 unit tests pass (the "logo palette" test was replaced by one that checks every swatch has a neutral dark background and that tonal containers follow the accent).
- Checked on an emulator (dark, Hall Monitor purple and teal): Home, Hall Monitor, Settings. Not checked: light mode and the other swatches on a device, the sign-in and profile screens with a non-purple accent, the home-screen widget (still the fixed violet and mint look).

## 2026-10-12: widgets follow the accent; light mode, sign-in and profile screens checked
- Both home-screen widgets now use the neutral look and follow the chosen accent (title, headline, refresh icon), and follow the phone's light or dark setting (new `WidgetColors`, night colour resources). A change of accent redraws the Hall Monitor widget at once.
- The sign-in and profile screens use the vivid light form of the accent in both modes (a `LocalAccentOnDark` set by the theme), the sign-in card is lifted off the dark backdrop with a border, and their status and navigation bar icons stay light in light mode too (`DarkBackdrops`).
- Checked on an emulator (Android 37): the widget in dark and light, Home, Hall Monitor and Settings in light, sign-in and profile screens in light and dark with the teal accent. Not checked: the other swatches, the Refresh-all widget on a device, light mode with large fonts, a real phone. The widget picker still shows a blank preview (no preview layout yet).

## 2026-10-12: richer Smart lists, Server and Plugins cards on Home
- **Smart lists**: three stat tiles (lists, switched on, items), a status line (every refresh worked and when, what failed, or a progress bar while refreshing), the first three lists with a coloured dot, item count and last refresh (trouble first, then work in progress, switched-off lists last), then Refresh all and Open lists. The whole header opens the Smart Lists tab.
- **Server**: the Emby version as a headline with the server name, an "Up to date" or "Update available" pill (and "Restart pending"), stat tiles for connected, playing and transcoding, and a scheduled-tasks line with the task count.
- **Plugins**: an "N installed" pill, Hall Monitor and SmartLists rows with a version and an OK / Update / Missing pill, the catalog-updates line, and a list of all plugins with an initial tile and version.
- 158 unit tests pass (two new: the list rows and the task summary). Checked on an emulator in dark mode with three test smart lists (two on, one off; created on the test server for this check). Not checked: the cards in light mode, a failed or refreshing list, a server with failed tasks or an update available, a pending restart.

## 2026-10-12: 0.3.0 released
- Embidextrous 0.3.0 (tag `v0.3.0`, commit `0ecfa16`): tests, signing-key check, attestation and the immutable release passed. `Embidextrous-0.3.0.apk`, 2,275,382 bytes, SHA-256 `c72e955b08dc7a4c86770f1a8486d4e7e806f1304b233005ad9f475b37965128`.
- Contents: the new look (neutral surfaces, accent for highlights), administrator Home cards and Customise Home, grouped navigation (Home, Watching, Library, Server, Settings), the Server tools (Live, Tasks, Activity, Devices, Plugins) and the Libraries tab, the widget headline. Not yet tried on a real phone.
- Next: phase 9 (plugin manager), phase 8 (Tracearr).

## 2026-10-12: phase 9 (plugin manager) built
- The Server group's Plugins tab is now the manager for the GitHub Repo Plugin Install plugin (API version 1, spec in its `docs/EMBIDEXTROUS-INTEGRATION.md`): a gate that explains why it cannot be used (plugin not installed, phone API off, plugin or app too old, unreachable); a dashboard (counts, Check, Update N with the names, Update everything, live progress while a run goes, a restart banner with Restart now and Dismiss that waits for the server to come back); the plugin list (problems first); a plugin page (status and error, Check, Update or Retry, auto-update and pre-release switches, choice of file when a release has several, release notes, previous versions with Put back, Remove); Add a plugin (free text address or search the registries; needs the second switch); History; Settings (write-only GitHub token with Save, Test and Remove; restart after scheduled updates; registries on or off). Every change asks first; after every action the app looks again instead of trusting its own guess.
- The Home Plugins card now says what the manager sees ("2 GitHub plugin updates ready", "all current", or why it cannot tell) instead of "not checked yet".
- Not built: pushed events over the websocket (the app polls while a run is going instead) and background notifications; both were optional in the plugin's guide and unverified there.
- 176 unit tests pass (10 new, using the guide's own sample answers). Checked on the emulator against the test server's real plugin (version 1.0.0.0, API v1) while its phone API is **off**: the explanation screen and the Home card line. **Not tested live**: everything behind the switch (dashboard, update, restore, add, remove, restart). The two switches are changed only on the plugin's page in Emby, which the app cannot and must not do.

## 2026-10-12: phase 8 (Tracearr) built
- The Watching group's **Activity** tab (every profile) shows what Tracearr has seen, from its public API v2 (read from Tracearr's own OpenAPI definition in its repository, since its reference site is a script-driven page): **Now** (active streams with direct play / transcoding, quality and progress, a summary line), **History** (plays with poster, show and episode, who, how long, percent watched, a Watched pill, device; filter by person; "Load older plays" with the cursor), **Violations** (rule, severity, who, the title and address the rule recorded, whether it was acknowledged in Tracearr), **People** (each identity's 7 day, 30 day and all-time plays and watch time, favourite genres). A slim header shows the Tracearr version and each server's health.
- Connection: Settings has a **Tracearr** card (address and API key; Save and Disconnect), and the Activity tab shows the same card until connected. The address must be HTTPS (plain http only in debug builds, like the Emby address); the key must look like `trr_pub_…`, is stored encrypted on the phone and is sent only as a bearer token to that address; redirects are not followed; pictures are loaded only from paths on the Tracearr server itself and sent no key; ids are checked before use. Signing out removes the connection.
- Read only: no Tracearr data is changed from the app (acknowledging or dismissing violations stays in Tracearr). Tracearr limits each key to a shared 240 requests a minute (default), so the tab loads on opening and on pull to refresh and does not poll quickly.
- Not built: live events (Tracearr pushes server-sent events; the plan said the Emby SSE plugin and live events are separate things and only the REST history is used here), a Home card, stream termination (v1 only), per-title and per-library views.
- 183 unit tests pass (7 new, using shapes from Tracearr's definition). Checked on the emulator against a **stand-in server on this PC** that follows the definition (bearer check, cursor paging, a transcoding stream, two violations, three people with stats): connect, Now, History with the person filter, Violations, People, and the "could not reach" message. **Not tested against the real instance** (https://stats.int.thecgkg.com is reachable only over your VPN) or a real API key, so a field the real server sends differently would show as an empty value rather than an error: the parser is tolerant.

## 2026-10-12: plugin manager tested live
- With the plugin's two switches on (changed by the owner on the plugin's own page), checked on the emulator against the test server's real plugin: the dashboard and list, a plugin page, the registry search with "Added" marks, Add (a deliberately fake repository, listed as Failed, as expected), and Remove with its confirmation (the fake entry is gone; the plugin list on the server confirms it).
- Not run: Update, Update everything, Put back, Restart now, History and Settings changes, because they would replace plugin files or restart the shared test server.

## 2026-10-12: annoyances fixed (before the next release)
- **Alerts clear everywhere.** When someone is no longer over their limit (time added from any phone or the dashboard, a time-out set, the limit switched off), every phone that has Background alerts on now removes that person's "limit reached" notification as soon as its live connection shows it (and on the first look after the service starts, which removes a leftover one). The "+15 / +30 / +60" buttons also look first: if the person already has time they add nothing, remove the alert, and say "<name> already has time, so nothing was added", so the same time cannot be given twice. A phone with Background alerts off has no alert to clear.
- **Links from Home open the right tab.** Each group (Watching, Library, Server) remembers its open tab by name, and every link from Home names the tab it wants: Kids to Hall Monitor, Open lists to Smart lists, the Server card to Live, the Plugins card to Plugins. (A restored pager used to override the link.)
- **Swipe between a group's tabs**, as well as tapping them.
- **Server > Live** shows the poster of what is playing and each person's picture (also on the connected rows).
- **Server > Devices**: tap a device to give it a name of your own (Emby's own "CustomName"; shown here and in Emby), or go back to the original.
- **New lists use the SmartLists plugin's own defaults** (set on its settings page): list type, media types, maximum items, minimum items, play time, refresh mode, sorting, public or not and a refresh schedule. A blank list gets all of them; a template keeps what it decides (type, media types, sorting, refresh mode) and gets the rest. Copies and imported lists are left as they are. If the defaults cannot be read the app's own starting values are used.
- 193 unit tests pass. Checked on the emulator: Home links, swiping, Live with a stream, renaming a device (and the list showing it), and a blank list starting with the plugin's 400 maximum items and "Default" sorting. Not checked on a device: the notification clearing and the "already has time" refusal (unit-tested logic only; next step).

## 2026-10-12: push first, polling only as the fallback
- **One shared Emby websocket** (`push/EmbyPush.kt`) for everything Emby can push: screens take a lease on the topics they need (live sessions, scheduled tasks, the activity log, and "events" for what the server and its plugins send on their own); the connection is open only while a lease exists, switches each topic on and off, reconnects with a growing delay, and says whether pushes are flowing. `keepFresh` is the one pattern every screen uses: look once, then follow the pushes, and ask by timer **only while the push is down**.
- Server > **Live** follows Emby's session pushes (it used to ask every 5 s); **Tasks** follows the task pushes (progress moves by itself); **Activity** follows the activity push (new entries appear on top: Emby sometimes says "something was logged" with no entries, which triggers one fetch, then one more after two seconds if more arrived); **Devices** follows the sign-in activity entries. The server header and the Home Server and Plugins cards follow the session and task pushes and Emby's install and restart messages. The **plugin manager** follows the plugin's own pushed events (`GitHubRepoPluginInstall.*`) and Emby's install messages. **Smart lists** looks again on Emby's "library changed" push. Fallback timers (only while the push is down): Live 5 s, Tasks 30 s, Activity 30 s, Devices 60 s, Home cards 30 s, plugin manager 30 s (1.5 s during a run), Smart lists 60 s.
- **Tracearr** (`push/TracearrLive.kt`) uses its live event stream (server-sent events, `GET /api/v2/public/events`): the first frame of each connection means "fetch the starting state", stream and violation events are gathered into one look every 3 seconds at most (Tracearr gives each key a shared request budget), a server-health event is applied directly, and the 30-minute end of the stream is a quiet reconnect. Only while the stream is down (after 10 s) does it ask by timer: streams every 15 s, history and violations every minute. A refused key stops it and says so; a 429 waits out `Retry-After`.
- **Hall Monitor** already worked this way (its plugin pushes; the 20 s check is the fallback). **Not pushable** and so still asked: smart-list refresh *progress* (the plugin has no push; asked every 2.5 s only while a refresh runs), the list of devices when no sign-in was seen, library item counts, the plugin version cards when nothing says they changed, and waiting for Emby to come back after a restart.
- 203 unit tests pass (the websocket messages, the event-stream parser, and the Tracearr stream against a scripted server: headers, ready then events, reconnect, a refused key, a 429). Checked on the emulator against the real Emby: the session push (the Live tab follows a test stream), and the activity push (new entries appeared by themselves). Not checked against a real Tracearr (only the scripted server in tests); the tasks push (no task ran), the "library changed" push, the plugin events during a real run.

## 2026-10-12: alert clearing verified on a real notification
- Emulator, real Emby and Hall Monitor plugin, a throw-away child with a 1 minute limit: the "reached their daily limit" notification appeared (with the new icon and +15, +30 and +60 buttons); adding 15 minutes through the plugin API as "another phone" removed the notification from this phone within about two seconds; tapping +15 on a blocked child added 15 minutes and removed the alert; with this phone's alerts switched off (so it could not follow) and the old alert still showing, tapping its +15 after time had been added elsewhere added nothing and removed the alert. Not separately confirmed: the "<name> already has time" note itself (it is shown for 10 seconds; only the effect was checked). The child and the throw-away players were deleted afterwards.

## 2026-10-12: releasing 0.4.0 (in progress)
- The owner said "publish it". `docs/releases/v0.4.0.md` is written and pushed (commit `addbec2`). Next steps: wait for CI (`test`) and CodeQL (`Analyze (java-kotlin)`, `Analyze (actions)`) to be green on that exact commit, create and push the annotated tag `v0.4.0`, watch the `Release v0.4.0` run, confirm the APK size and SHA-256, then record the result here. Attestation cannot be verified on this machine (no `gh`).
- After the release: update both phones with Obtainium (Parent on Courtney's, Administrator on the owner's), switch on Background alerts on each, and the owner enters the Hall Monitor key and (on VPN) the Tracearr address and a new key. Test fixtures remain on the shared test server and emulator `We_P7` until the owner says they are done reviewing: users `we-p7-admin`, `we-p7-kid`, `we-p7-kid2`, three test smart lists; then clean up (delete users and lists, reset plugin state, remove the AVD with `emu kill` and delete `~\.android\avd\We_P7*`).
- Later ideas: bulk tag editing (Tags tab reserved), "Set up another phone" code, retire the two old apps after a few days' use, widget picker preview, Back-button check.

## 2026-10-12: Embidextrous 0.4.0 released
- Tag `v0.4.0` on `327a9e5` (CI and both CodeQL jobs green); the `Release v0.4.0` run succeeded (tests, build, pinned signing fingerprint check, provenance attestation, immutable release). Asset `Embidextrous-0.4.0.apk`, 2,406,454 bytes, SHA-256 `f88e63799f13276f846495a2b98aff2b149529e2291be9427d28216cabd8a609`. `gh attestation verify` was not run on this machine (no `gh`).

## 2026-10-12: Live shows the show's poster (after 0.4.0, not released)
- Server > Live now shows the poster of the show for an episode (its series poster) instead of the episode's thumbnail; movies and other items keep their own. Unit-tested (204 tests); not yet looked at on the emulator.

## 2026-10-12: releasing 0.4.1 (in progress)
- The owner said "publish it". Release notes `docs/releases/v0.4.1.md` pushed; next: CI and both CodeQL jobs green on that commit, tag `v0.4.1`, watch the run, record the SHA-256. Emulator check of the Live poster done (episode thumbnail gone; the test shows have no posters). The throw-away players `we-p7-live*` were deleted again.

## 2026-10-12: Embidextrous 0.4.1 released
- Tag `v0.4.1` on `3391a80` (CI and both CodeQL jobs green); the `Release v0.4.1` run succeeded. Asset `Embidextrous-0.4.1.apk`, 2,406,454 bytes, SHA-256 `99cda972c239cdcabe36189865f2a6bcecaf8a7c32781c4a3857ebdabdb3defa`. `gh attestation verify` not run locally (no `gh`). Contains: Live shows the series poster for episodes.

## 2026-10-12: test fixtures cleaned up
- Deleted from the shared test server: the throw-away users `we-p7-admin`, `we-p7-kid`, `we-p7-kid2` (`kevin` and `test1` untouched), the three test smart lists (Weekend Movies, Family Movie Night, Old Favourites, with their Emby lists), and reset the Hall Monitor plugin's test state (limiter off, phone API off, key cleared, users and log cleared). The private emulator `We_P7` was stopped and its files removed. Test-server plugin API switches for the GitHub plugin were left as the owner set them.

## 2026-10-12: rename away from Watching Eye (built, not released)
- Plugin repo (`feerlessleadr/hall-monitor-emby`): everything renamed to Hall Monitor (assembly and namespace, folders, page and resource names, routes, header, live message names, settings and data file names); version 2.0.0.0; a one-time import copies the old settings, watch-time and log files to the new names (and logs a warning, because no limit would apply, if a copy fails). Checked on the test server: with legacy-named files in place (a marker in the settings, 20 watch-time rows) the new DLL started, `HallMonitor.xml` held the marker, the 20 rows were there, `GET /emby/HallMonitor/LimitedUsersStatus` answered and `/emby/WatchingEye/LimitedUsersStatus` returned 404.
- App: classes, screens, routes, header and live messages renamed; minimum plugin version is now 2.0; a key stored under the old name is moved to the new one on first use (unit-tested). The README, SECURITY, CLAUDE.md, plan and issue templates use the new name; old release notes, this file and decisions keep history.
- Release needs the owner's "publish it": plugin 2.0.0.0 first (or together), then app 0.5.0; both phones and the server must be updated together because the old app and the new plugin cannot talk to each other.

## 2026-10-12: releasing plugin 2.0.0.0 and app 0.5.0 (in progress)
- The owner said "publish it". Plugin tag `v2.0.0.0` pushed on `fe89f32` (its checks were green); the plugin's release run builds `HallMonitor.dll`, writes the checksum, attests and creates the release. App release notes `docs/releases/v0.5.0.md` pushed next: CI and both CodeQL jobs must be green on that commit, then tag `v0.5.0`. The owner was told that the GitHub updater adds `HallMonitor.dll` without removing `WatchingEye.dll` (read in its source, not run), and that its auto-update could fetch the new file by itself.

## 2026-10-12: Hall Monitor plugin 2.0.0.0 and Embidextrous 0.5.0 released
- Plugin: tag `v2.0.0.0` on `fe89f32`; release run succeeded; assets `HallMonitor.dll` (216,064 bytes, SHA-256 `13d82caac1ee4f0dc0cab98602239b2c8e285fd619644bff1f2841b830cbc934`) and `HallMonitor.dll.sha256`.
- App: tag `v0.5.0` on `61cdcd9` (CI and both CodeQL jobs green); release run succeeded; `Embidextrous-0.5.0.apk`, 2,406,454 bytes, SHA-256 `544ab5a276e62cb307c994dbe20e7d835f9f49ee5d649fec4def0f99495bcaf7`. `gh attestation verify` not run locally (no `gh`).
- To do by the owner: on the server delete `WatchingEye.dll` from `plugins`, add `HallMonitor.dll` (or update through the GitHub plugin and then delete the old file), restart Emby once; update both phones through Obtainium; check the Hall Monitor tab connects and the key is still there.

## 2026-10-12: two fixes after the 0.5.0 update (built, not released)
- **Live no longer shuffles.** Emby sends sessions in a changing order, so the list swapped places every second or so; sessions are now always ordered by person (sessions with no person last), then device, then id. Unit-tested. Not confirmed on a real phone with two streams (the owner's report was a direct play plus a transcode; the order change came from Emby's list, not from the play method, as far as the code shows).
- **Home "Watching now" shows the show's poster** for an episode, like Live: the app asks Emby once per item which show it belongs to (`Items?Ids=...&Fields=SeriesId`), remembers the answer, and falls back to the item's own picture. Unit-tested parser; not seen on a screen.
- Plugin 2.0.1.0 (see the plugin's PROGRESS.md section 15): the settings page is listed in the dashboard's Advanced menu again.

## 2026-10-12: releasing plugin 2.0.1.0 and app 0.5.1 (in progress)
- The owner said "publish it". Plugin tag `v2.0.1.0` pushed on `3b975bb` (checks green). App notes `docs/releases/v0.5.1.md` pushed next; then CI and both CodeQL jobs must be green on that commit before tag `v0.5.1`.

## 2026-10-12: Hall Monitor plugin 2.0.1.0 and Embidextrous 0.5.1 released
- Plugin: tag `v2.0.1.0` on `3b975bb`; run succeeded; `HallMonitor.dll` 216,064 bytes, SHA-256 `13dac2eb9aadfa79326c5904fb2f2e0ca62af376fcd2334c998ac57ae3d4ace1` (the settings page is listed in Server > Advanced again).
- App: tag `v0.5.1` on `87115d1` (CI and both CodeQL jobs green); run succeeded; `Embidextrous-0.5.1.apk` 2,406,454 bytes, SHA-256 `5ec13237c4f19fef65e2219dbf5b6b5c5f547da098661e88ba9456f4fd36422d` (stable Live order, show poster on the Home watching card). `gh attestation verify` not run locally.
- Started on the two tagging plugins (designs in `C:\dev\emby-tagging`): reviewed with the owner; the survey script `C:\dev\emby-tagging\scripts\Survey-ExplicitTags.ps1` is written (the owner runs it on the machine that has the music at `V:\Music`, ffprobe at `C:\ffmpeg\bin`) and its CSV decides how much of the Explicit Tagger is needed.

## 2026-10-12: Hall Pass tab (built, on main, not released)
- Library > **Hall Pass**: reviews the music tracks the Hall Pass plugin (`C:\dev\hall-pass-emby`, plugin 1.1.0.0 or newer) could not decide. A summary card (counts per decision, dry-run note, scan progress with Stop, Scan now / Scan all again / Apply...), filter chips (Needs review, Explicit, Likely clean, Clean, All), a Tracks / Albums switch, a search box, and cards with Explicit / Clean / Clear buttons and tap-for-evidence. Albums view: Open, All explicit, All clean (asks first; covers the whole album across pages through the plugin's select-all-matching). Apply... shows what would change; it only offers to write when dry run is off. Calls the plugin's admin API with the signed-in administrator's token (no separate key).
- Code: `data/HallPass.kt` (models, tolerant parser, version gate), `hpXxx` calls in `ApiClient`, `hallpass/HallPassViewModel.kt` and `HallPassScreen.kt`, `HallPassTest.kt` (6 parser and gate tests; the whole suite passes).
- **Checked on a private emulator** (AVD Hp_Test, port 5572, removed afterwards) against the test server with a throw-away administrator (deleted): the tab loads live counts and tracks, marking a track explicit moved the counts and showed the message, the Albums list loaded, the album confirmation appeared, the Apply dialog said dry run is on. The manual decisions were cleared afterwards.
- **Not checked:** Apply with dry run off from the phone, a scan started from the phone through to the end, a library of thousands of tracks (paging is 50 at a time), the not-installed and too-old screens (written, not seen), dark theme, a real phone.

## 2026-10-12: watched totals on the Kids card and the Hall Monitor tab (built, not released)
- Needs Hall Monitor plugin 2.1.0.0 (ApiLevel 3). The Home "Kids today" card shows, per child, watched today and week, then month and year, and one footnote saying when the plugin started recording (earlier time was not recorded). The Hall Monitor tab card shows the same line. With an older plugin the card keeps its old "This week" line.
- The three-dot menu entry now reads "Reset today's time..." with a "Fresh day" dialog when the plugin is 2.1: it only gives a fresh daily allowance and keeps the totals (the plugin changed what Reset does).
- Tests: `WatchedTotalsTest` (reading the fields, old plugin, duration text, footnote). Not seen on a screen or a phone.

## 2026-10-13: Hall Pass tab tidy-up (built, not released; follows 0.7.0)
- Removed the "watched totals count from ..." footnote from the Kids card (owner's request). The Apply button no longer wraps its label: the summary buttons flow onto a second line when narrow.
- Needs review can be filtered by reason (No evidence, Some sign, Sources disagree, Faint sign of clean) with counts, so a big queue can be taken in groups; the album buttons respect that filter.
- A "What to do next" card says which step you are on (scan, review, preview, turn dry run off on the plugin page, apply), with all five steps one tap away, and the dry-run dialog says exactly where the switch is. `nextStep` is unit tested.
- Not seen on a screen or a phone. Ideas for working through hundreds of review tracks (swipe mode, artist view, bulk by reason, CSV, genre rules) are proposed to the owner, not built.

## 2026-10-13: working through a big review queue (built, not released; follows 0.7.0)
- Hall Pass tab: **Tracks / Albums / Artists**; **Order: A to Z or Most played** (plays from the plugin, all users added up); **"Decide faster"** card: swipe review and "All N explicit / All N clean" for the current view (filters, reason, search, opened album or artist; every page; asks first with the view spelled out); an artist card with Open / All explicit / All clean; plays on every card.
- **Swipe review** (full-screen dialog): one track at a time with why Hall Pass was unsure; swipe right = clean, left = explicit (30% of the width, or the buttons), Skip puts it at the end, Undo takes the last decision back (clears the manual decision). Decisions leave the list at once and go to the plugin in the background; a failure puts the track back. `swipeOutcome` and `describeView` are unit tested.
- Needs Hall Pass 1.2.0.0 for the artist list and plays; with 1.1.x the Artists view and Most played would show errors or zeros (the tab's minimum is still 1.1.0.0; raise it before release if that matters).
- **Not seen on a screen or a phone:** the swipe gesture and its feel, the bulk confirmation text, the new chips. Only unit tests and the plugin's endpoints (checked live) back this.

## 2026-10-13: Notification rules, first build (not released; follows 0.8.0)
- Settings > Notifications (Hall Monitor section): rules are a list with on/off and delete; "Add a rule" offers "used N% of their time" (1 to 99) or "used all of their time". Stored on the phone (`notif_rules`); the old Background alerts switch migrates into the "limit reached" rule.
- Rule engine: `notifications/NotifRules.kt` (pure Kotlin, `percentUsed`, `PercentRules.evaluate`); `AlertService` runs it on each live update; fired state kept in prefs (`notif_percent_state`).
- Unit tests: `NotifRulesTest` (13 cases); full suite 236 tests, all passing (`./gradlew.bat test`, offline).
- Checked on an emulator (private AVD, throw-away admin, both deleted afterwards): the rules list, the Add dialog, saving and switching a rule on, and that rules survive an app restart and a reinstall. A hint now says the rules need the Hall Monitor key (without it the background connection does not start). NOT checked: the dark theme, a real notification (needs a limited user who is watching), a real phone. Not verified with real playback (the test server's Hall Monitor had no limits configured; a throw-away limited user showed zero watched time). The test server was restored afterwards: limiter off, no limited users, throw-away user deleted.
- Not built yet: per-user rules, per-rule priority and sound, quiet hours, the Label Maker events.

## 2026-10-14: notification rules check every limit (built, not released; needs Hall Monitor plugin 2.2.0.0 for the full effect)
- The owner is adding weekly and monthly limits for the children, so the plugin (`C:\dev\watching-eye`, 2.2.0.0, committed, NOT released) now reports a `Limits` list and the app evaluates each limit on its own (`periodPercents`, `PercentRules`). Plugin 2.1 or older: the app falls back to the closest limit. The release order matters: release the plugin first, then the app.
- Verified end to end on an emulator against the test server running plugin 2.2.0.0: a throw-away limited child (2 minute daily, 1 hour weekly limit) played a track through real session reports; the plugin counted it, the app's background service saw the live status, and the phone posted "has used 87% of their daily time" with the generic lock-screen text in its public version. The Limits list showed both limits throughout. Dark theme of the Settings rules section looked right.
- NOT verified: a weekly or monthly limit actually firing (a weekly limit cannot be filled in a few minutes of real playback; the logic is covered by unit tests), the notification on a real lock screen, a real phone.
- Test server restored afterwards (plugin 2.1.0.0 and its original config, no extra users); the plugin's own log keeps a few entries from the test.

## 2026-10-14: fix for standing-state alerts, wrong 100% text and a wrong Settings path (committed, NOT released; 0.9.0 has these)
- Fixed as described in decisions.md (baseline for new or re-enabled percent rules; text at 100%; "Settings, under Hall Monitor"). 243 unit tests pass (3 new). RELEASED as 0.9.1 on 2026-10-14.
