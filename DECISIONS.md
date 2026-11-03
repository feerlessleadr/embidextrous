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
