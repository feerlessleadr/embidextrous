# Decisions (Embidextrous)

Append-only, newest at the bottom. Never rewrite an entry; to reverse one, add a new entry that references it.

**Existing detailed log:** `docs/decisions.md` holds every decision made so far with its reason (2026-10-10 onward), and `docs/push-contract.md` and `docs/notifications-plan.md` hold the notification decisions. Search them when a past decision matters. The entries below summarize the ones that shape the whole project, so a fresh session can find them quickly. New decisions go here (and may also be added to `docs/decisions.md`).

### 2026-10-10, One combined app named Embidextrous
- **Context:** The owner had two apps: SmartLists (Kotlin/Compose) and Watching Eye (Java/XML).
- **Options considered:** Keep two apps; add a Home screen to Watching Eye; build one new app on the SmartLists code.
- **Decision:** One new app, package `com.feerlessleadr.embidextrous`, base = SmartLists v0.4.1 code, Watching Eye ported to Kotlin/Compose. New signing key. Old apps stay untouched until this one replaces them.
- **Rationale:** The SmartLists stack is newer and already hardened. A new package installs beside the old apps, so a leak of one key affects one app. "Emby" is a trademark, so the name does not start with it.
- **Impact:** `emby.smartlists.android` and `watching-eye-android-app` become superseded. Settings are re-entered once on the phone.

### 2026-10-10, No app lock, no FLAG_SECURE
- **Context:** The owner decided this independently for both old apps, then again here.
- **Options considered:** In-app PIN or biometric lock.
- **Decision:** No app lock and no FLAG_SECURE. Rely on the phone's own screen lock. Notification action buttons need an unlocked phone.
- **Rationale:** Owner's choice (an in-app lock was added and removed at their request).
- **Impact:** Do not add a lock again without new evidence.

### 2026-10-10, Security baseline
- **Context:** Carried over from the security reviews of both old apps.
- **Decision:** HTTPS only in release, system CAs, no redirects, token only in `X-Emby-Token` (Keystore AES-GCM), password never stored, server-supplied ids validated through `ApiClient.seg`, bounded inbound sizes, Gradle refuses debug-signed releases.
- **Rationale:** Losing a phone should not hand over the server.
- **Impact:** All API code in `data/`; see `CLAUDE.md` foundations.

### 2026-10-11, Per-phone profiles and one sign-in
- **Context:** The owner and their partner share one Emby account; the partner only wants Hall Monitor.
- **Options considered:** A separate parent app; a Home screen in the old app.
- **Decision:** One app. First launch asks Parent, Administrator or Custom, stored on the phone. The account role still gates what works (SmartLists needs an administrator).
- **Rationale:** Two apps would mean maintaining three. The account cannot tell the two people apart.
- **Impact:** Tabs and Home cards follow the profile.

### 2026-10-12, Nothing is called Watching Eye any more
- **Context:** The original plugin's author re-released it as a closed-source app.
- **Options considered:** Keep old names for compatibility; clean break.
- **Decision:** Clean break to Hall Monitor: `HallMonitor.dll`, `/emby/HallMonitor/...`, `X-HallMonitor-Api-Key`, `HallMonitorStatus` messages. Old names are not answered. App 0.5+ needs plugin 2.0+.
- **Rationale:** Avoid confusion with the original. The plugin keeps its GUID and migrates settings once.
- **Impact:** Old Watching Eye app v1.6 stops working with plugin 2.0+. Hall Monitor plugin repo is `feerlessleadr/hall-monitor-emby`.

### 2026-10-14, Plugin pushes use a subscription with the token in the message body
- **Context:** Emby's `SendMessageToAdminSessions` reaches only sockets that carry the token in the address, and the app keeps its token in a header.
- **Options considered:** Put the token in `?api_key=` (leaks into server logs); polling only; a per-plugin subscription.
- **Decision:** Each plugin offers `<Plugin>EventsStart` with `0,30000,<afterSeq or empty>,<token>`, answered by `<Plugin>Events`. REST catch-up `GET .../Events?After=`. The app owns the shared shape (`docs/push-contract.md`).
- **Rationale:** Keeps the token out of URLs and logs. The plugin validates the token itself.
- **Impact:** Every plugin with alerts (Hall Pass, SmartLists, Label Maker, GitHub plugin manager) implements this. Changing a message name or field needs its catalog, samples and docs changed together.

### 2026-10-12, Release rule
- **Context:** Releases are signed and public.
- **Decision:** Tag and release only when the owner says "publish it". Order: release plugins first when the app depends on them, notes in `docs/releases/vX.Y.Z.md` committed first, CI and both CodeQL jobs green on that exact commit, then tag.
- **Rationale:** Owner control over public artifacts.
- **Impact:** Do not tag or push tags otherwise. Ordinary commits may be pushed (standing permission).

### 2026-11-03, Time-change notifications come from the plugin's change list, not from the credit total
- **Context:** The engine used to compare `creditSecondsDaily` between statuses and announce a rise of 60 s or more. The credit alone cannot say why it moved, and it also falls on resets, so a drop cannot be told from a take-away.
- **Options considered:** Keep diffing the credit and guess the cause; add a reason field per user; read an explicit change list from the plugin.
- **Decision:** Read `recentTimeChanges` (plugin 2.5.0.0, ApiLevel 7). The engine remembers the newest change id per user and announces each newer change. Take-aways are announced only from the list. Against an older plugin (list absent) the old credit-diff path stays for added time, without a reason. The first status after a start only records the newest id, so nothing old is replayed.
- **Rationale:** The app is push-based (the plugin pushes full status on every change), so the reason arrives with the new time and no polling was added. The app never receives "what changed" events, only successive full statuses.
- **Impact:** Needs plugin 2.5.0.0 for reasons and take-away alerts. Wording is in `HallMonitorEngine.kt` (`timeChangeTitle`, `timeChangeText`, `timeChangeLine`). Contract in `docs/push-contract.md` and `docs/decisions.md`. Tests: `HallMonitorEngineTest`, `LiveMessagesTest`, resource `hall-monitor-2.5-real-push.json` (a real push captured from plugin 2.5.0.0).

### 2026-11-03, One alert rule for added and taken-away time, new wording
- **Context:** The owner wanted reductions notified too (the app's "-30 min", Undo, and Home Assistant's trade script) and wanted the reason in the text.
- **Options considered:** A second rule for reductions; widen the existing rule.
- **Decision:** Widen `HM_EXTRA_TIME` (id unchanged, title now "Time was added or taken away") so the owner's existing switch carries over. Text, as approved: "15 min added because the Feed Chloe chore was marked completed.", "30 min added manually by a parent.", "30 min taken away: Traded for play time.", "5 min taken away manually by a parent.", each followed by "<name> now has <time> left today." Titles: "was given more time", "had time taken away", "'s time changed". No parent name and no app-versus-plugin-page distinction in the text. The app's own +30, -30 and Undo send `Source: "app"`. The lock-screen version stays generic.
- **Rationale:** Owner's wording choices, 2026-11-03; no new switch to find.
- **Impact:** `NotifCatalog.kt`, `HallMonitorClient.kt` (`SOURCE_APP`). Chore label text comes from Home Assistant (see `C:\dev\ha\DECISIONS.md`).

### 2026-11-03, Released 0.11.0 after an emulator check
- **Context:** The reasons feature was only unit tested and checked against a captured real push. The owner asked for an emulator run before anything went out, then "publish everything".
- **Decision:** Ran the debug build on a private emulator against the test server with plugin 2.5.0.0, a throw-away admin and child, and the rule switched on. All four texts matched. Then tagged `v0.11.0` on 6a30de9 after `test` and both CodeQL jobs were green.
- **Impact:** `Embidextrous-0.11.0.apk`, 2,576,294 bytes, SHA-256 `20295b011e86d315f7e07f6b8b41b87449dc3c759d7d639aefe44678258a0b0e`. Row added to `docs/HANDOFF.md`. Still never run on a real phone.

### 2026-11-03, Label Maker tab in Library (version 1 scope)
- **Context:** The owner asked for the Label Maker tab. The plugin (0.1.11.0) already has the album and review routes, the alert channel and the status channel (`C:\dev\label-maker-emby\docs\API.md`, `NOTIFICATIONS.md` sections 12 and 13). Notifications already pointed at `library/labelmaker`.
- **Options considered:** (a) mirror the plugin page completely (lanes, thresholds, track mapping); (b) status, review and quick switches only, as the owner decided on 2026-10-13 ("settings stay on the plugin's Emby page").
- **Decision:** (b). Library > Label Maker has a status card (counts, dry run switch, Pause, Check inbox now), filter chips (Needs review first) and lane chips, an album list, and a bottom sheet per album: best match, editions found, search by hand, pasted MusicBrainz link, a preview of what filing would do, the duplicate choices (keep both, replace, file only the new files), Keep my tags, Skip, Check again, Discard and Restore, Undo, Split. Not built: manual track mapping (the plugin's `Mapping` field), lane editing, thresholds. Those stay on the plugin page.
- **Rationale:** Matches the owner's earlier decision and keeps a phone screen usable. Every destructive or irreversible choice (turn dry run off, keep my tags, discard, undo, split) asks first. Dry run is shown as a switch because the owner listed it as a quick switch; turning it off asks for confirmation, turning it on does not.
- **Live updates:** a new push topic `EmbyTopic.LabelMakerStatus` (start message `0,30000,<token>`, no event number, so it is not an alert subscription and `AlertService` does not hold it). The tab holds it only while it is on screen (`keepFresh`); a newer `Version` reloads the status, the list and the open album. A plugin without the `statuspush` feature (before 0.1.11.0) is asked every 20 seconds instead.
- **Gate:** plugin 0.1.8.0 or newer (the duplicate choices and the later-tracks fix are in 0.1.8.0), `ApiLevel` 1. Older versions get a plain "too old" card; a 404 gives "not installed".
- **Impact:** `data/LabelMaker.kt`, `ApiClient.lm*`, `push/PushProtocol.kt`, `labelmaker/`, `MainActivity.kt`, `LabelMakerTest.kt` (12 tests). WORKSPACE.md connection 7 updated. The plugin session's note "the Embidextrous session has not been told about section 13" is now out of date; the owner should tell that session.
