# Embidextrous: status

Snapshot written 2026-11-03 (overwrite, do not append). Detailed history: `docs/status.md` (dated log), `docs/HANDOFF.md` (consolidated picture).

## State
- Latest release: **v0.12.0** (2026-11-03, the Label Maker tab). Signed APK `Embidextrous-0.12.0.apk` (2,625,446 bytes) on GitHub releases, installed with Obtainium. Before it: v0.11.0 (reasons for time changes).
- `main` holds everything (v0.12.0 is tagged on 16165e3; a later docs-only commit records the release). The branch `feat/label-maker-tab` is merged and can be deleted.
- Tabs built: Home, Watching (Hall Monitor, Activity), Library (Smart lists, Libraries, Hall Pass, Label Maker), Server (Live, Tasks, Activity, Devices, Plugins), Settings > Notifications.
- Unit tests: all pass (`./gradlew.bat test --offline`), including 12 in `LabelMakerTest`.

## Done this session
- README status line fixed (0.11.0, then a Label Maker row).
- Label Maker tab (Library > Label Maker): status card with dry run, Pause and Check inbox now; state and lane filters; album list; review sheet (best match, editions, search by hand, pasted link, preview, duplicate choices, file, Keep my tags, Skip, Check again, Discard/Restore, Undo, Split). Live status by the plugin's status channel (push topic `LabelMakerStatus`), 20 s polling for a plugin without `statuspush`. Gate: plugin 0.1.8.0. Decisions: `DECISIONS.md`.
- Checked on a private emulator against the real plugin v0.1.11.0: worked, with four small UI problems that were fixed afterwards.
- Released as 0.12.0: merged straight to `main` (no `gh` for a pull request), CI and both CodeQL jobs green on 16165e3, then tagged.

## Next steps
1. Owner: update the app with Obtainium and the Label Maker plugin to 0.1.11.0; try the tab against the real inbox (dry run is on by default) and report what looks wrong.
2. Owner: tell the Label Maker session that the app now uses its status channel (`NOTIFICATIONS.md` section 13) and that the tab is released.
3. Re-run an emulator check of the four post-check UI fixes and the unchecked items (below).
4. Later: manual track mapping in the review sheet (plugin `Mapping` field), a Home card for albums needing review, delete the merged branch.

## Known issues
- Two stray tracked files in the repo root, `gqfs3jwdpc` and `xv0zkbd91n` (about 100 bytes each, only a scratchpad path). Ask the owner before removing them.
- Old commits contain a Windows user name in a path (CLAUDE.md used to). Rewriting public history is the owner's call.
- `SendMessageToAdminSessions`-based refresh code in the plugin manager and Smart lists screens is stale: those pushes never reach the header-authenticated socket. The timers do the work.
- The README and HANDOFF still point at `C:\dev\watching-eye` as the Hall Monitor reference. That folder is deprecated: the live plugin source is `C:\dev\hall-monitor-emby`.

## Not verified
- The four Label Maker UI fixes (snackbar inside the sheet, "not matched yet" wording, stale search results cleared, release id on editions) on any device: built and unit tested only.
- Label Maker: the "not installed" and "too old" cards, the 20 s polling fallback, whether the push topic is released when leaving the tab, Replace and File only the new files, Skip, a notification tap opening the tab.
- The release APK's SHA-256 is GitHub's recorded digest; the file was not downloaded and re-hashed.
- Anything on a real phone: lock screen alerts, quiet hours, the backup poll job, weekly or monthly limit alerts, dark theme of newer screens, swipe-review feel, Apply with dry run off, the reason texts, the whole Label Maker tab. Also Tracearr alerts against a real Tracearr and the "Smart lists timer stopped" alert.

## Manual steps for the owner
- Release signing key lives on the owner's other machine (see `C:\dev\embidextrous-keys\CLAUDE.md`). GitHub secrets `SIGNING_*` are the owner's.
- Say "publish it" to release.
