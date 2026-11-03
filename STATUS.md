# Embidextrous: status

Snapshot written 2026-11-03 (overwrite, do not append). Detailed history: `docs/status.md` (dated log), `docs/HANDOFF.md` (consolidated picture).

## State
- Latest release: v0.10.1 (2026-10-15). Signed APK on GitHub releases, installed with Obtainium.
- Branch `main` was 3 commits ahead of `origin/main` at the last check (local refs, not fetched): cf808a3, 644e2f0, 10905d2 (all 2026-11-03). Working tree clean.
- Tabs built: Home, Watching (Hall Monitor, Activity), Library (Smart lists, Libraries, Hall Pass), Server (Live, Tasks, Activity, Devices, Plugins), Settings > Notifications (a section per service, quiet hours, test).
- Unit tests: 275 per `docs/HANDOFF.md` (`./gradlew.bat test --offline`).

## In progress
- Reasons for time changes (needs Hall Monitor plugin 2.5.0.0, ApiLevel 7): the app reads `RecentTimeChanges` and words notifications like "45 min added because ..." Committed locally in the 3 commits above. Unit tests pass and it was live-checked against a real push from plugin 2.5.0.0 on the test server. It was NOT run on an emulator or a phone.
- Plugin 2.5.0.0 is not released, so this app change must not be released before it.

## Next steps
1. Reconcile and release in the planned order: plugin 2.5.0.0 tag, then Home Assistant push (`C:\dev\ha`), then app release. Release only when the owner says "publish it".
2. Label Maker tab: not started in the app. The plugin (`C:\dev\label-maker-emby`) now offers the `LabelMakerEvents` and `LabelMakerStatus` channels; the app only has Label Maker notification catalog entries. Spec: Label Maker `docs/NOTIFICATIONS.md` section 13 and `docs/HANDOFF.md`. The owner has not yet told this project's sessions about the status channel.
3. Fix stale README line "Status: 0.8.0 released" (README.md line 8).

## Known issues
- Two stray tracked files in the repo root, `gqfs3jwdpc` and `xv0zkbd91n` (about 100 bytes each, they contain only a scratchpad path). They look like accidental commits in a public repo. Ask the owner before removing them.
- Old commits contain a Windows user name in a path (CLAUDE.md used to). Rewriting public history is the owner's call.
- `SendMessageToAdminSessions`-based refresh code in the plugin manager and Smart lists screens is stale: those pushes never reach the header-authenticated socket. The timers do the work.
- The README and HANDOFF still point at `C:\dev\watching-eye` as the Hall Monitor reference. That folder is deprecated: the live plugin source is `C:\dev\hall-monitor-emby`.

## Not verified
- Anything on a real phone: lock screen alerts, quiet hours, the 15-minute backup poll job firing, weekly or monthly limit alerts, dark theme of newer screens, swipe-review feel, Apply with dry run off.
- Tracearr alerts against a real Tracearr; the "Smart lists timer stopped" alert.
- The 2.5.0.0 reasons on an emulator.

## Manual steps for the owner
- Release signing key lives on the owner's other machine (see `C:\dev\embidextrous-keys\CLAUDE.md` for the uncertainty about the local copy). GitHub secrets `SIGNING_*` are the owner's.
- Say "publish it" to release.
