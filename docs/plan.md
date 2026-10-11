# Plan

Embidextrous combines the owner's two Emby apps into one and is meant to grow. Written 2026-10-10; update it as work is done.

## Phases

| Phase | What | State |
|---|---|---|
| 0 | New repo, scaffold copied from the SmartLists app (v0.4.1), renamed, handshake logo, CI/release/Dependabot files, docs | done (this commit) |
| 1 | Shell: one sign-in, bottom navigation (Watching Eye, Smart Lists, Settings), role-aware tabs (a regular account sees only Watching Eye) | done |
| 2 | Port Watching Eye to Kotlin/Compose: plugin API client, live connection (websocket) and status repository, time-left cards with the countdown, quick +15/+30/+60 and custom minutes, undo, confirmations, time-out and resume, pull-to-refresh, "Live" indicator | done (the Kotlin port was built together with phase 1) |
| 3 | Watching Eye extras: alerts (optional foreground service, notification buttons), home-screen widget, widget name hiding, settings | done, needs more testing |
| 4 | Release 0.1.0 as the first combined app: new signing key (owner makes it, adds the GitHub secrets), signed APK, build provenance, Obtainium; retire the two old apps once this one does everything they do | |
| 5 | Smart-list editor (everything the SmartLists app already has, in the combined app) | |
| later | Plugin manager for GitHub downloads; bulk tag editing; explicit-tag editing for music | ideas |

The first release (phase 4) has the full Watching Eye and only the list overview of SmartLists; the editor follows (phase 5), as the owner chose.

## Carry-over checklist (what must not be lost in the merge)
- From SmartLists: HTTPS rule and no redirects, `seg` id validation, size limits, Keystore token storage with key deletion on sign-out, the non-exported refresh receiver, the 86 unit tests.
- From Watching Eye: live push with fallback polling and reconnect, ApiLevel/NowPlaying handling for older plugins, countdown interpolation, undo, notification protections (generic lock-screen text, unlock for buttons), widget, the regular-account mode, the tests (51), server-address validation.
- Known Watching Eye plugin facts: needs plugin 1.9 for regular accounts and now-playing; 1.8 for push/undo; older plugins fall back to asking every 20 s.

## Owner steps that cannot be done by the assistant
- Create the new signing key and add the four `SIGNING_*` repository secrets (names in `release.yml`), then, optionally, move them into the `release` environment restricted to `v*` tags.
- GitHub repo settings: secret scanning, push protection, rulesets for `main` and `v*` tags, immutable releases, CodeQL, private vulnerability reporting.
