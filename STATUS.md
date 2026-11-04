# Embidextrous: status

Snapshot written 2026-11-03 (overwrite, do not append). Detailed history: `docs/status.md` (dated log), `docs/HANDOFF.md` (consolidated picture).

## State
- Latest release: **v0.11.0** (2026-11-03, reasons for time changes). Signed APK on GitHub releases, installed with Obtainium.
- Branch **`feat/label-maker-tab`** (cut from `main` at 8013139, NOT committed yet): the Label Maker tab (Library > Label Maker) plus the README fix. `main` is untouched and in sync with origin.
- Tabs built: Home, Watching (Hall Monitor, Activity), Library (Smart lists, Libraries, Hall Pass, **Label Maker**), Server (Live, Tasks, Activity, Devices, Plugins), Settings > Notifications.
- Unit tests: all pass (`./gradlew.bat test --offline`), including 12 new ones in `LabelMakerTest`.

## Done this session
- README: status line now 0.11.0; the Notifications row no longer says "not released"; a Label Maker row was added.
- Label Maker tab: status card (counts, dry run switch with a confirm when turning it off, Pause, Check inbox now), filters and lane chips, album list, album sheet (best match, editions, search by hand, pasted link, preview, duplicate choices, file, Keep my tags, Skip, Check again, Discard/Restore, Undo, Split). Live status by the plugin's status channel (new push topic `LabelMakerStatus`), polling every 20 s for a plugin without `statuspush`. Gate: plugin 0.1.8.0. Decisions: `DECISIONS.md`.
- Checked on a private emulator against the real plugin v0.1.11.0 (private Emby instance, albums in six states): status, filters, review, preview, keep both, undo, discard/restore, live push (a REST Pause showed in the app in 4.5 s), dark theme. It found four UI problems (sheet hid the snackbar, a misleading "No edition fits" on a Waiting album, stale hand-search results after Restore, identical-looking editions); all four are fixed in code.

## Next steps
1. Owner: review the branch, then say "commit" (and whether to merge to `main`). A release needs "publish it" (it would be 0.12.0: write `docs/releases/v0.12.0.md`, bump the version, wait for CI and CodeQL).
2. Owner: tell the Label Maker session that the app now uses the status channel (`NOTIFICATIONS.md` section 13).
3. Re-run the emulator check for the four fixes, plus what was not checked (below).
4. Later: manual track mapping in the review sheet (plugin `Mapping` field), a Home card for albums needing review.

## Known issues
- Two stray tracked files in the repo root, `gqfs3jwdpc` and `xv0zkbd91n` (about 100 bytes each, only a scratchpad path). Ask the owner before removing them.
- Old commits contain a Windows user name in a path (CLAUDE.md used to). Rewriting public history is the owner's call.
- `SendMessageToAdminSessions`-based refresh code in the plugin manager and Smart lists screens is stale: those pushes never reach the header-authenticated socket. The timers do the work.
- The README and HANDOFF still point at `C:\dev\watching-eye` as the Hall Monitor reference. That folder is deprecated: the live plugin source is `C:\dev\hall-monitor-emby`.

## Not verified
- The four Label Maker UI fixes on a device (built and unit tested only).
- Label Maker: the "not installed" and "too old" cards, the 20 s polling fallback, whether the push topic is released when leaving the tab, Replace and File only the new files, Skip, a notification tap opening the tab.
- Anything on a real phone: lock screen alerts, quiet hours, the backup poll job, weekly or monthly limit alerts, dark theme of newer screens, swipe-review feel, Apply with dry run off, the reason texts, the whole Label Maker tab.
- Tracearr alerts against a real Tracearr; the "Smart lists timer stopped" alert.

## Manual steps for the owner
- Release signing key lives on the owner's other machine (see `C:\dev\embidextrous-keys\CLAUDE.md`). GitHub secrets `SIGNING_*` are the owner's.
- Say "commit" to commit the branch, "publish it" to release.
