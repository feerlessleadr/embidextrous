# Notifications: how they work (rebuilt for 0.10.0, 2026-10-14)

This file started as the owner's request (2026-10-13) and now describes what was built. Decisions with their reasons are in `docs/decisions.md` ("2026-10-14: notification revamp" and the two entries after it). The contract plugins follow is `docs/push-contract.md`.

## The owner's request
"configurable notifications across the app ... for each service/plugin ... for Hall Monitor, a notification when my users have used up X% of their time, and when they use up all of their time. I should be able to set up as many notifications as I want. These notification configurations should live in the settings of the app, and should be configurable per plugin/service." On 2026-10-14: a Notifications section in Settings with a sub-section per plugin, which alerts are on for which plugin, administrators get every service and non-administrators only Hall Monitor, **push is the main way and polling only a failsafe**, plus a test notification and a daily summary.

## What the owner sees
Settings > **Notifications**:
- *On this phone*: the notification permission (with an Allow button), **Pause all notifications**, **Quiet hours** (alerts arrive without sound), the battery-restriction hint.
- One row per service. Hall Monitor is always listed. Label Maker, Plugin manager, Hall Pass, Smart lists, Tracearr and Emby server are listed only for an Emby administrator account on the Administrator setup (or Custom with Smart Lists on): the same rule that decides the Library and Server tabs (`NotifAvailability`, `Profiles.showsAdminCards`). A hidden service keeps its saved rules but nothing for it runs or connects.
- Each service screen: the service switch (off by default, except Hall Monitor on phones that had alerts on), a note when the plugin is missing or too old to send alerts, **Sound and vibration** (opens Android's own screen for that service's notification channel), **Send a test**, and every alert type of that service with its rules: switch, edit, delete, "Add another" (any number of rules for alert types with settings).
- A rule can have settings (percent, minutes, minimum count, severity, time of day) and, for Hall Monitor alerts about one user, **who it covers** (every user, or chosen ones).

## Alert types (what each says)
Hall Monitor: *used a set percentage* (per user and limit; says "Used 1 h 36 min of 2 h allowed today. 24 min left."), *used all of their time* (has a **+30 min** button that needs an unlocked phone), *a time-out started / ended*, *stopped when the allowed hours ended*, *started watching*, *extra time was added*, *cannot be reached for N minutes*, *daily summary* (one line per child: watched and left). Label Maker: albums need review (minimum count), tagged automatically, an album failed, a lane has a problem, music lookups are stuck. Plugin manager: updates ready, an update failed, Emby needs a restart, a run finished. Hall Pass: a scan finished or failed, apply or undo finished or failed, tracks wait for review. Smart lists: a refresh failed, a refresh run finished, the timer stopped. Tracearr: a rule was broken (minimum severity), a server is down. Emby server: a scheduled task failed. Lock-screen text is always generic; names show only after unlocking.

## How it is built (files under `app/src/main/java/com/feerlessleadr/embidextrous/`)
- `notifications/NotifModel.kt`: services (channel, `eventsName`, API base), `EventSpec`, `NotifRule`, quiet hours, `NotifDelivery`, `NotifAvailability`.
- `notifications/NotifCatalog.kt`: every alert type (built in; a plugin's own catalog replaces its list when it sends one).
- `notifications/NotifRules.kt`: storage helpers, the 0.9 migration, seeding of default rules (once per alert type), the percentage logic with its baseline.
- `notifications/HallMonitorEngine.kt`: Hall Monitor's live status to notifications (compares one look with the last, since the plugin sends the whole status).
- `notifications/PluginEventEngine.kt`: plugin events (Label Maker, Hall Pass, Smart lists, plugin manager), Emby's task list, Tracearr violations and server health, `SeqTracker` (no event twice).
- `notifications/NotifHub.kt`: the one place that decides and shows; `NotifPoster.kt`: Android channels (one per service), quiet hours, lock-screen version, the +30 button.
- `watching/AlertService.kt`: the foreground service that holds the connections while any rule is on. `watching/BackupPollJob.kt`: the 15-minute failsafe, only while the service is not running.
- `ui/NotificationsScreen.kt`: the hub screen, the service screen and the rule editor.
- Storage (SharedPreferences `settings`): `notif_rules_v2`, `notif_seeded`, `notif_services`, `notif_quiet_hours`, `notif_paused`, `notif_event_seq`, `notif_percent_state` (also the daily-summary, task and violation markers), `notif_catalog_<service>`. The 0.9 keys `notif_rules` and `watching_alerts` are read once for the migration.

## Delivery: push first
- Hall Monitor: its plugin pushes the status over its own subscription (`HallMonitorStatusStart`, key in the message); a reconnect with growing delay; no poll.
- Label Maker, Hall Pass, Smart lists, GitHub plugin manager: **each plugin offers an events subscription** (`<Plugin>EventsStart`, answered by `<Plugin>Events`) because Emby's "send to admin sessions" does not reach a header-authenticated socket (live finding). The start message carries the last event number the app saw and the access token in its body. After a gap the app also asks `GET .../Events?After=<seq>` (catch-up) and dedupes by `Seq`.
- Emby: the scheduled-task list is a subscription topic. Tracearr: its server-sent event stream.
- Failsafe: `BackupPollJob` (JobScheduler, 15 minutes, only while the service is not running) runs the catch-up for the plugins that keep an event log.

## Not built / ideas
Per-rule sound and priority (sound is per service through Android's channel settings), Label Maker's own subscription (the plugin session must add it; until then its alerts come by catch-up and the backup poll), the Label Maker tab, a "Set up another phone" code, an Emby-update-available alert (nothing pushes it).
