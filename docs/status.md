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
