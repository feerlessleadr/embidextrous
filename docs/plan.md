# Plan

Embidextrous combines the owner's two Emby apps into one and is meant to grow. Written 2026-10-10; update it as work is done.

## Phases

| Phase | What | State |
|---|---|---|
| 0 | New repo, scaffold copied from the SmartLists app (v0.4.1), renamed, handshake logo, CI/release/Dependabot files, docs | done (this commit) |
| 1 | Shell: one sign-in, bottom navigation (Watching Eye, Smart Lists, Settings), role-aware tabs (a regular account sees only Watching Eye) | done |
| 2 | Port Watching Eye to Kotlin/Compose: plugin API client, live connection (websocket) and status repository, time-left cards with the countdown, quick +15/+30/+60 and custom minutes, undo, confirmations, time-out and resume, pull-to-refresh, "Live" indicator | done (the Kotlin port was built together with phase 1) |
| 3 | Watching Eye extras: alerts (optional foreground service, notification buttons), home-screen widget, widget name hiding, settings | done, needs more testing |
| 4 | Release 0.1.0 as the first combined app: new signing key (owner makes it, adds the GitHub secrets), signed APK, build provenance, Obtainium; retire the two old apps once this one does everything they do | done: 0.1.1 (0.1.0 was tagged first but its run failed before building) and 0.1.2 are released; the old apps are not retired yet |
| 5 | Smart-list editor (everything the SmartLists app already has, in the combined app) | done: works in the combined app, verified by hand on an emulator (see status.md for what was not tested) |
| 6 | **Profiles and the Home screen** (release 0.2.0): a per-phone choice of how the app is used (Parent, Administrator, Custom) that decides the tabs and the Home cards, independent of the Emby account (the owner and their partner share one account). Home = needs-attention list, watching now, kids today with week totals, Bedtime button. Parent phones see Home, Watching Eye, Settings only | released as 0.2.0 (2026-10-11); checked on an emulator only, not yet on a real phone |
| 7 | Administrator Home cards (smart-lists snapshot with Refresh all, server health, plugin versions), "Customise home" (show, hide, reorder cards), and the home-screen widget following the same profile (a compact "all good / needs attention" line plus each child's time left) | |
| 8 | **Tracearr** (self-hosted playback monitor, https://github.com/connorgallopo/tracearr; the owner's instance is reached over WireGuard): history and trends per child, what they watched, stream details, violations. Needs the instance's address and an API key in the app (a new secret); read the official API docs and the instance's `/api-docs` first; the Emby SSE plugin must be installed on the test server to test live data | |
| 9 | **Plugin manager** for the GitHub Repo Plugin Install plugin (admin only; hidden unless the plugin's phone API is on). Spec: its `docs/EMBIDEXTROUS-INTEGRATION.md` (https://github.com/feerlessleadr/Emby.GitHubRepoPluginInstall), API v1 under `/emby/Plugins/GitHubRepoPluginInstall`, `X-Emby-Token`. Steps: (a) `GET /info` gate (refuse `ApiVersion` below what the app was built for, explain when `ApiEnabled` or `AllowManage` is off; the switches are changed only in the Emby dashboard); (b) dashboard card from `GET /status` (updates ready, failed, Check / Update N / Update all with confirmations, progress from `Run`, restart banner with Restart now and Dismiss, wait for `/info` after a restart); (c) plugin list and detail (release notes, auto-update, pre-release, asset filter, restore with confirm); (d) add and remove plugins and registries (manage switch only; remove deletes the file, always confirm); (e) history and token settings (write-only GitHub token, test token); (f) push hints from the Emby websocket `GitHubRepoPluginInstall.*` as refresh triggers, and optional WorkManager check every 15 to 30 minutes for a local notification (not verified by the plugin's author). Also feeds the phase 7 "plugin versions" Home card | |
| later | Bulk tag editing; explicit-tag editing for music; "Set up another phone" code (server address and Watching Eye key, never the password) | ideas |

The first release (phase 4) has the full Watching Eye and only the list overview of SmartLists; the editor follows (phase 5), as the owner chose.

## Carry-over checklist (what must not be lost in the merge)
- From SmartLists: HTTPS rule and no redirects, `seg` id validation, size limits, Keystore token storage with key deletion on sign-out, the non-exported refresh receiver, the 86 unit tests.
- From Watching Eye: live push with fallback polling and reconnect, ApiLevel/NowPlaying handling for older plugins, countdown interpolation, undo, notification protections (generic lock-screen text, unlock for buttons), widget, the regular-account mode, the tests (51), server-address validation.
- Known Watching Eye plugin facts: needs plugin 1.9 for regular accounts and now-playing; 1.8 for push/undo; older plugins fall back to asking every 20 s.

## Owner steps that cannot be done by the assistant
- Create the new signing key and add the four `SIGNING_*` repository secrets (names in `release.yml`), then, optionally, move them into the `release` environment restricted to `v*` tags.
- GitHub repo settings: secret scanning, push protection, rulesets for `main` and `v*` tags, immutable releases, CodeQL, private vulnerability reporting.
