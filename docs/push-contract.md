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
