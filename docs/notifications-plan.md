# Configurable notifications (requested 2026-10-13, not built)

**Owner request:** "configurable notifications across the app. For example, I should be able to configure what type of notifications I'm receiving for each service/plugin, so for Hall Monitor, I should be able to set a notification for getting a notification when my users have used up X% of their time, and when they use up all of their time. I should be able to set up as many notifications as I want. These notification configurations should live in the settings of the app, and should be configurable per plugin/service (for the ones that we've built notifications for)."

Also covers the new Label Maker plugin's alerts (see `C:\dev\label-maker-emby\docs\DESIGN.md` section 19 and `docs/HANDOFF.md`). Build this **before** the Label Maker tab, because Label Maker uses it. Recommended order: this feature, then Label Maker Phase 1 onward.

## Behaviour
- Settings > Notifications: one section per service/plugin that has notifications. Today: Hall Monitor. Soon: Label Maker. Hall Pass and others only when they get notifications.
- Each section lists rules. Add, edit, delete and switch on/off, any number of them.
- Hall Monitor events: "a user has used X% of their time" (X per rule; daily, weekly or monthly limit; all users or chosen users) and "a user has used all of their time" (limit reached, with the existing +15/+30/+60 buttons). Possible later: time-out started, extra time added.
- Label Maker events: "N or more albums need review", "an album failed", "a lane has a problem", "an album was tagged automatically".
- The current Background alerts switch becomes the first default rule ("limit reached", on), so existing behaviour is unchanged; migrate the old setting.
- Delivery options per rule: on/off, priority/sound (one Android notification channel per service), generic lock-screen text (keep the current protections: buttons need an unlocked phone), optional quiet hours.
- Rules are stored on each phone (DataStore / the existing `AppSettings`), not on the server.

## Design sketch
- `notifications/` package: `NotifRule` (id, service, event, params, delivery, enabled), `NotifRegistry` (each service declares its events and parameter types, so the settings screen is generic and a new service adds no UI code), `RuleEngine` (pure Kotlin, unit tested, no Android classes, like `LimitAlerts`), `NotifStore`, a generic settings screen.
- The engine consumes the live status updates the app already receives through `AlertService` (websocket) and the poll fallback. Rules: fire once per user, per rule, per period; reset on a new day, on a Hall Monitor Reset, or when added time lowers the percentage; no duplicate after a reconnect or restart (persist the "already fired" state with its period key); first look after start records state without firing (as `LimitAlerts` does).
- Percent used for Hall Monitor: derive from the status fields (`secondsRemaining`, `limitingAllowedSeconds`, `limitingPeriod`, `secondsWatchedDaily/Weekly/Monthly`, `effectiveDailyLimitMinutes`). Verify against a live status before relying on it; the plugin may need no change. Mind credits/extra time (`creditSecondsDaily`) and time-outs.
- Label Maker events arrive as admin pushes (`SendMessageToAdminSessions`, message names `LabelMaker.*`) through the same websocket the `AlertService` holds; the poll fallback reads `GET /emby/LabelMaker/Status`.

## Existing code to build on
`watching/AlertService.kt` (foreground service, channels `CHANNEL_MONITOR` and `CHANNEL_ALERTS`, notification buttons via `ActionReceiver`), `watching/LimitAlerts.kt` (+ its tests), `watching/LiveConnection.kt`, `data/HallMonitorModels.kt` (`UserStatus`), `data/AppSettings.kt`, `ui/SettingsScreen.kt` (Background alerts switch, battery-restriction hint, Android 13 permission flow), `watching/HomeModel.kt`.

## Decisions already made that apply
See `docs/decisions.md`: no app lock; notification protections (generic lock-screen text, buttons need an unlocked phone, receiver refuses while locked); alerts clear on every phone and a stale +15 adds nothing; push over the Emby websocket is fire-and-forget and only reaches sessions authenticated as users, so polling stays as a backup.

## Open questions for the owner (ask when starting)
1. Percent rules per user, or one setting for all users with optional per-user overrides? (Default: a rule applies to all limited users unless users are chosen.)
2. Which Hall Monitor periods (daily only, or weekly/monthly limits too)? (Default: whichever period is limiting for the user.)
3. Quiet hours: wanted now or later?
