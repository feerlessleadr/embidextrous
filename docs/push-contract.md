# Push contract for plugins that notify Embidextrous

Written 2026-10-14 for the notification revamp (`docs/notifications-plan.md`). Label Maker (`C:\dev\label-maker-emby\docs\NOTIFICATIONS.md`) is the reference implementation; Hall Pass and SmartLists follow the same shape so the app can use one parser, one catch-up routine and one settings screen for all of them. Hall Monitor and the GitHub Repo Plugin Install plugin keep their own existing pushes (documented in their repos).

## Rules (decided with the owner, 2026-10-14)
- **Push is the main delivery.** The plugin calls `ISessionManager.SendMessageToAdminSessions(name, data, cancellation)` (fire-and-forget; only administrator sessions receive it; an API-key-only socket gets nothing). Polling by the app is only a backup for when the push was missed.
- **Message name** = `<Service>.<Event>`, for example `HallPass.ScanFinished`. `Data` is a JSON object.
- **Common fields in every `Data`:** `ApiLevel` (int), `Seq` (long, grows by 1 per event, persisted so it survives restarts and never goes back), `EventId` (GUID), `ServerTimeUtc` (ISO 8601), `Event` (the short name).
- **No file paths, drive letters or share names anywhere in a payload.** Describe things by artist, album, track, list name only. Strip paths from any sentence copied from an exception.
- **Catch-up:** `GET /emby/<Service>/Events?After=<seq>&Limit=100` returns `{ LatestSeq, Truncated, Events: [ { Seq, Name, AtUtc, Data } ] }`, oldest first, from a persisted log of the last 500 events. `Truncated: true` means the log no longer reaches back that far (the app then reads the status route instead). The app dedupes by `Seq`.
- **Catalog:** `GET /emby/<Service>/Notifications/Catalog` returns `{ ApiLevel, Service: { Id, Title, ChannelId, ChannelTitle }, Events: [ { Id, Title, Description, Params: [ { Name, Label, Type: int|bool|text|choice, Min, Max, Default, Choices } ], DefaultRule: { Enabled, Params }, LockScreenText, OpensTab } ] }`. Same shape as Label Maker's (see its `docs/reference/notification-samples.json`).
- **Info:** `GET /emby/<Service>/Info` must report `ApiLevel` and a `Features` array that includes `"events"` and `"catalog"` once this is built.
- **Server-side switches:** a master "Send phone alerts" switch (default on) and one switch per event on the plugin's settings page; an event that is switched off is neither sent nor logged. The app tells the owner this.
- **Flood control:** batch (one summary per scan or run, not one per track or list); never repeat an event for state that has not changed.
- All routes need an administrator token (`X-Emby-Token`).

## Hall Pass events (`HallPass.*`)
| Event | Sent when | Event fields | App rule parameters |
|---|---|---|---|
| `ScanFinished` | A scan ended with state Completed (a quiet auto-scan of a few new tracks sends nothing: only scans that the owner started, or auto-scans that changed 25 or more tracks) | `Total`, `Scanned`, `Unchanged`, `Failed`, `NeedsReview` (queue size now), `OnlineFailed` | none (default on) |
| `ScanFailed` | A scan ended Failed or Cancelled | `State`, `Message` (plain sentence) | none (default on) |
| `ApplyFinished` | A write job (apply or undo) ended Completed | `Kind` (`Apply`/`Undo`), `Changed`, `Unchanged`, `Failed`, `Conflicts` | none (default on) |
| `ApplyFailed` | A write job ended Failed or Cancelled, or an unfinished job was found after a crash | `Kind`, `State`, `Message` | none (default on) |
| `ReviewQueue` | The number of tracks waiting for a person went **up**; at most one per 10 minutes | `Count` (waiting now), `New` | `MinCount` int 1 to 100000, default 50 (default off) |

## SmartLists events (`SmartLists.*`)
| Event | Sent when | Event fields | App rule parameters |
|---|---|---|---|
| `RefreshFailed` | A list refresh ended with an error. At most one per list per hour; several lists failing together make one event | `Count`, `Lists` (first 5: `{ ListId, ListName, ListType, Error, Trigger }`) | none (default on) |
| `RefreshRunFinished` | A "refresh all" run or a scheduled batch ended | `Total`, `Succeeded`, `Failed`, `DurationSeconds` | none (default off) |
| `TimerStopped` | The refresh scheduler timer is found not running while lists have schedules; at most once a day | `Message` | none (default on) |

## Where each setting lives
Rules, thresholds, quiet hours, sound and priority: the app, per phone. What the server sends at all: the plugin's own settings page.

## Update 2026-10-14: how the app really receives events (live finding, owner decision)
`SendMessageToAdminSessions` does **not** reach the app. Emby links a websocket to a user session only when the token is in the websocket **address** (`?api_key=`); the app sends its token in the `X-Emby-Token` **header** (a foundation: never in a URL), so the app's socket is not an "administrator session" and receives nothing. Proven live (one scan, three sockets: header token, full authorization header, `api_key` in the address; only the address one got the event). The owner decided: use the approach Hall Monitor already uses, which does not depend on sessions. Hall Monitor's plugin has a periodic websocket listener (`StatusPushListener`, an Emby `BasePeriodicWebSocketListener`): the app subscribes with a start message and the plugin pushes to that one socket.

**So every plugin that sends alerts must ALSO offer an events subscription, in addition to the routes above.** (Keep `SendMessageToAdminSessions` if you like: it still reaches Emby's own web admin sessions. The app does not rely on it.)
- **Client to server:** `{"MessageType":"<Service>EventsStart","Data":"0,30000,<afterSeq>"}` (the third value, the highest `Seq` the app has already seen, is optional) and `{"MessageType":"<Service>EventsStop"}`. `<Service>` is the plugin's name without spaces: `HallPass`, `SmartLists`, `LabelMaker`, `GitHubRepoPluginInstall`.
- **Server to client:** `{"MessageType":"<Service>Events","Data":{"ApiLevel":1,"LatestSeq":n,"Truncated":false,"Events":[{"Seq":n,"Name":"HallPass.ScanFinished","AtUtc":"...","Data":{...}}]}}`, oldest first, the same event objects the catch-up route returns. Sent (a) once right after the subscription: the events after `<afterSeq>` (none if it was omitted: only `LatestSeq` as the baseline), (b) promptly (within about a second) whenever new events are logged, only to the connections that are subscribed, and (c) as a heartbeat with an empty `Events` list every 30 seconds, so the app can tell a dead connection from a quiet one.
- **Who may subscribe:** an administrator's token, checked the way Hall Monitor's `StatusPushListener` checks it (the app connects with the administrator's token in `X-Emby-Token`). Anyone else gets `{"MessageType":"<Service>Events","Data":{"Error":"Administrator login required."}}` and nothing more.
- **Per subscription state:** the highest `Seq` already sent on that connection, so nothing is sent twice on one connection.
- The REST catch-up route stays (the app uses it after a reconnect and for the backup poll).
- **Live test must use the app's own way of connecting:** token only in the `X-Emby-Token` header, `deviceId` in the address, no `api_key`. A test that connects with `api_key` proves nothing about the app.

## Correction 2026-10-14 (found by running the app against the first build): who may subscribe
Hall Monitor's `connection.User` check does not work for the app either: Emby fills `connection.User` of a websocket only when the token is in the address, so a header-authenticated socket is "nobody" (the first Hall Pass build answered the app with "Administrator login required."). Hall Monitor itself works for the app because the app sends the plugin's own API key **in the start message body**, never in the address. The same idea applies here, without adding new secrets: **the start message carries the signed-in administrator's access token in its body**, over the same encrypted websocket (wss in release builds), never in the address.
- **Start message:** `{"MessageType":"<Service>EventsStart","Data":"0,30000,<afterSeq>,<token>"}`. `<afterSeq>` is always present (0 when the app has seen nothing); `<token>` is the access token. In Emby's listener the values after the first two arrive as `values[0] = afterSeq`, `values[1] = token`.
- **The plugin validates the token itself:** look up the session by that access token (`ISessionManager`, `GetSessionByAuthenticationToken` or the equivalent in the Emby API you build against), take its user, require `Policy.IsAdministrator`. A missing, unknown or non-administrator token gets the same `Error` reply as before. Re-check on every push (session still exists, user still an administrator). Keep accepting `connection.User` as administrator as well (a socket opened by Emby's web UI).
- **Never log, echo or persist the token** (not in the event log, not in an exception message, not in a debug line). Compare nothing in a way that leaks timing; a plain lookup through Emby's own session manager is fine.
- The live test must subscribe the way the app does: token only in the `X-Emby-Token` header (to connect) AND inside the start message body, no `api_key` in the address.
- **Clarification after the first live run:** when the app has seen nothing yet it sends the third value **empty** (`0,30000,,<token>`), never `0`. Empty means "only tell me where your log stands" (a baseline: `LatestSeq`, no events); a number means "send what came after it". `0` would replay the whole log, which would flood the phone with old alerts the first time a service is switched on.
