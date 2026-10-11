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
