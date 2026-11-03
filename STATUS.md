# Embidextrous: status

Snapshot written 2026-11-03 (overwrite, do not append). Detailed history: `docs/status.md` (dated log), `docs/HANDOFF.md` (consolidated picture).

## State
- Latest release: **v0.11.0** (2026-11-03, reasons for time changes). Signed APK `Embidextrous-0.11.0.apk` on GitHub releases, installed with Obtainium. Before it: v0.10.1 (2026-10-15).
- Branch `main` is in sync with `origin/main` (the release notes and the HANDOFF row were pushed after the tag).
- Tabs built: Home, Watching (Hall Monitor, Activity), Library (Smart lists, Libraries, Hall Pass), Server (Live, Tasks, Activity, Devices, Plugins), Settings > Notifications (a section per service, quiet hours, test).
- Unit tests: all pass (`./gradlew.bat test --offline`).

## Done this session
- Time-change notifications say why (chore marked done in Home Assistant, or a parent) and announce time taken away. Needs Hall Monitor plugin 2.5.0.0 (ApiLevel 7). Older plugins keep the old added-time wording without a reason.
- Checked on a private emulator against the test server (four texts verified), and against a real push captured from plugin 2.5.0.0. Details in `DECISIONS.md` and `hall-monitor-emby/docs/reason-for-time-changes.md`.

## Next steps
1. Owner: install the app update (Obtainium), after the plugin update and Emby restart, and the HA pull.
2. Label Maker tab: not started in the app. The plugin (`C:\dev\label-maker-emby`) offers the `LabelMakerEvents` and `LabelMakerStatus` channels; the app only has notification catalog entries. Spec: Label Maker `docs/NOTIFICATIONS.md` section 13 and `docs/HANDOFF.md`.
3. Fix stale README line "Status: 0.8.0 released" (README.md line 8).

## Known issues
- Two stray tracked files in the repo root, `gqfs3jwdpc` and `xv0zkbd91n` (about 100 bytes each, only a scratchpad path). Ask the owner before removing them.
- Old commits contain a Windows user name in a path (CLAUDE.md used to). Rewriting public history is the owner's call.
- `SendMessageToAdminSessions`-based refresh code in the plugin manager and Smart lists screens is stale: those pushes never reach the header-authenticated socket. The timers do the work.
- The README and HANDOFF still point at `C:\dev\watching-eye` as the Hall Monitor reference. That folder is deprecated: the live plugin source is `C:\dev\hall-monitor-emby`.

## Not verified
- Anything on a real phone: lock screen alerts, quiet hours, the 15-minute backup poll job firing, weekly or monthly limit alerts, dark theme of newer screens, swipe-review feel, Apply with dry run off, the new reason texts.
- Tracearr alerts against a real Tracearr; the "Smart lists timer stopped" alert.

## Manual steps for the owner
- Release signing key lives on the owner's other machine (see `C:\dev\embidextrous-keys\CLAUDE.md`). GitHub secrets `SIGNING_*` are the owner's.
- Say "publish it" to release.
