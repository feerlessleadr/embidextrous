# Decisions

A running log of what was decided about the app, when and why. Newest at the bottom. `docs/status.md` says what is built; this file says why it is the way it is. Add to it whenever a decision is made, however small.

## 2026-10-10: starting the combined app

- **Name: Embidextrous** ("ambidextrous" with Emby in front: it works with both hands, watch-time limits and smart lists). Chosen by the owner from a list of film-crew and word-play names. "Emby" is a trademark, so the name does not start with it and the app does not claim to be official.
- **Package `com.feerlessleadr.embidextrous`, repo `feerlessleadr/embidextrous`** (public, MIT). A new package means a new app that installs next to the old ones and cannot update them; the owner accepted re-entering settings.
- **A new signing key** for this app (not the SmartLists or Watching Eye key), so a leak affects one app. The owner creates it and adds the GitHub secrets.
- **Base = the SmartLists code (Kotlin, Compose, Material 3)**, copied at v0.4.1 (commit 79444cb of `emby.smartlists.android`), because its stack is newer (Gradle 9.5.1, AGP 9.3, OkHttp 5) and it already has the hardening (no redirects, id validation, size limits, server-side sign-out). Watching Eye (Java and XML views) is ported to Kotlin and Compose rather than hosted as is.
- **Scope of the first release:** the full Watching Eye plus only the SmartLists list overview; the list editor is added once the combined app works (owner's choice).
- **One sign-in.** An administrator account gets every tab. A regular account gets only the Watching Eye tab, because the Watching Eye plugin (1.9+) works with a regular account's token and the SmartLists plugin needs an administrator. The owner already accepts an administrator token on the phone for SmartLists.
- **Shared settings area** using the SmartLists look (theme, accent colours, density). Watching Eye keeps its own options (alerts, hiding names on the widget).
- **No app lock** (the owner decided this for Watching Eye and SmartLists independently): the phone's own lock is relied on. Notification buttons that add time still need an unlocked phone on Android 12+.
- **Old apps stay installed and their repos untouched** until this app does everything they do; then they are retired (archived with a pointer to this repo).
- **Logo: "Handshake"**: a green E and a cream mirrored E whose arms interlock, like two hands shaking (ambidextrous), on Emby-style green. Chosen by the owner from six options (handshake, three-armed E with cog/play/tag, two-sided disc, high five, pocket tool, two-winged owl). Original artwork, MIT.

## Security baseline carried over (from the two apps' reviews)
- HTTPS only in release, no cleartext outside the debug emulator config, no redirects.
- Token only in a header, Keystore AES-GCM storage, server-side logout on sign-out, key deleted.
- Server-supplied ids validated before they enter a URL; inbound sizes and depth bounded.
- Notifications: generic text on the lock screen, buttons need an unlocked phone, the add-time receiver refuses while the phone is locked.
- Releases: signed by the release key only (Gradle refuses a debug-signed release), build provenance attestation, actions pinned to commits, Dependabot.

## 2026-10-11: one app with per-phone profiles, a Home screen, Bedtime, Tracearr

- **One app, not a second "parent" app and not a Home screen bolted onto the old Watching Eye app.** The owner and their partner share one Emby account, and the partner only wants the Watching Eye part. Two apps would mean maintaining three. Instead the first launch asks how the phone is used: **Parent** (Home, Watching Eye, Settings), **Administrator** (everything) or **Custom**. The choice is stored on the phone, not derived from the Emby account (the account cannot tell the two people apart), and can be changed in Settings. It tidies the app; it does not lock anything (consistent with "no app lock").
- **The account's role still gates what can work**: Smart Lists needs an administrator account, so it only appears when the profile includes it AND the account is an administrator.
- **Home screen** (start destination): a needs-attention list with inline actions, who is watching what, each limited user's time left with this week's total, and a Bedtime button. Administrator phones also get a smart-lists snapshot, server health and plugin versions (phase 7). The Watching Eye and Smart Lists tabs stay as the detailed views.
- **Bedtime**: times out the chosen limited users (all ticked by default) with presets **1 hour** and **until 6:00 AM** (the next 6:00, so at 1 AM it means today at 6), always with a confirmation and an Undo, plus "End all time-outs". The plugin's time-out accepts 1 to 43200 minutes.
- **Home customisation and the widget** follow the profile (phase 7).
- **Tracearr** is a later phase (8) because it needs the owner's instance address and an API key stored on the phone, and the API has to be read first. The owner's instance is private (WireGuard), so it cannot be reached from the development machine; use the public docs, and test against a stub or against the owner's phone.

## 2026-10-11: Hall Monitor and the violet and mint palette

- **Watching Eye is renamed Hall Monitor**, in this app and in the plugin (repo now `feerlessleadr/hall-monitor-emby`, plugin 1.10). The owner chose it from two rounds of names (Chaperone and Embargo were the other favourites). Reason for renaming at all: the plugin is a rewrite derived from a recovered original, not a fork that continues it, and should not share its name. Only visible names change; the plugin's GUID, `WatchingEye.dll`, the `/WatchingEye/...` routes, the `X-WatchingEye-Api-Key` header, the websocket message names and the settings file names stay, so existing settings and older clients keep working. The Kotlin class and package names in this repo (`watching`, `WatchingEyeClient`, ...) are left alone too; they are internal. The tab icon changed from an eye to a timer.
- **The Hall Monitor logo** is the daily and weekly rings around a play button (chosen by the owner from about 40 ideas), colours: deep violet `#140B2E`, violet `#B388FF`, mint `#5CF2C0`. It is the plugin's image and repo avatar.
- **That palette is the whole app's default look** ("Hall Monitor" is the first accent swatch and the default; the other swatches and Material You still work). The Embidextrous launcher icon keeps its shape (two Es shaking hands) in violet and mint. With the Hall Monitor palette nothing is overridden on the Home and Hall Monitor tabs; with another accent colour those tabs keep the neutral grey "calm" treatment so the accent does not flood the cards.

## 2026-10-11: a new Embidextrous icon
- The two-Es-shaking-hands icon is replaced. The owner picked **two overlapping circles** from about 30 ideas (three rounds, in the Hall Monitor palette): a **violet circle with white list bullets**, a **mint circle with deep-violet text bars**, and a **white play button where they overlap**, on deep violet. Meaning: both hands (ambidextrous), two apps in one (lists and watch time), and list plus media. It is also the one that reads best next to the Hall Monitor rings.
- Built as a vector at 78% scale inside the adaptive-icon safe zone (so round masks do not clip the circles). The themed (one-colour) icon cuts the overlap, bullets and bars out of two black circles with an even-odd fill. Generated by a script (kept in the session scratchpad, not in the repo); `assets/logo/embidextrous-icon.svg` is the master artwork.

## 2026-10-11: plugin manager comes from the GitHub Repo Plugin Install API
- The plugin manager (earlier a "later" idea) will talk to the owner's GitHub Repo Plugin Install plugin, whose author wrote `docs/EMBIDEXTROUS-INTEGRATION.md` for this app. It is phase 9 in `plan.md`.
- Admin only. The plugin's API is off until the server owner enables it in the plugin's page, and adding or removing plugins and registries sits behind a second switch that the app can read (`GET /info`) but never change. The app explains when a switch is off instead of failing.
- Reasons for the rules we apply: refuse an `ApiVersion` the app was not built for; ignore unknown fields and map unknown enum strings to a fallback; validate ids with `seg` before building a URL (ids are letters, digits and dashes, at most 64); token in the `X-Emby-Token` header only; no redirects; 60 s read timeout for the calls that reach GitHub (first call after a server start can take about 40 s); after every action re-fetch instead of patching local state and handle 409; deleting a plugin deletes its file, so always confirm; restarting Emby always needs a confirm and the app waits for `/info` to answer again.
- Push over the Emby websocket (`GitHubRepoPluginInstall.*`) only reaches sessions authenticated as a user, and is fire and forget: treat it as a hint to refresh. Background notifications would need periodic polling (no push service exists); that is optional and unverified.
- The plugin's author verified the API against a real Emby 4.10.1 server; the app's side is not built or tested yet. Its sample client code is a sketch to adapt to this app's `ApiClient` (which already refuses redirects and validates ids), not to copy.

## 2026-10-11: the plugin's DLL keeps the name WatchingEye.dll
- The Hall Monitor plugin is renamed for people, but the file stays `WatchingEye.dll` (and the code namespace stays `WatchingEye`). A new file name would leave the old file beside the new one (two copies with one plugin GUID), break updaters that replace by file name (the GitHub-downloads plugin would see a new plugin), and could lose saved settings, which Emby stores under a name derived from the configuration class. The owner chose to leave it as is; a migration release would be the way if that ever changes.

## 2026-10-12: look, navigation and behaviour decisions (owner's choices)
- **Neutral base, accent highlights.** Surfaces are neutral dark or light greys (`withNeutralSurfaces`); the chosen accent colour is used only for highlights (buttons, selected items, rings) and is the same on every tab and in the widget. The accent is changeable in Settings; the owner uses dark mode on their phones, so dark is checked first.
- **Grouped navigation.** Bottom bar: Home, Watching, Library, Server, Settings (a Parent phone gets Home, Watching, Settings). Each group has sub-tabs along the top and can be swiped; the open sub-tab of each group is remembered by name and Home links name the tab they want. Inner titles are hidden inside a group (no double titles).
- **Smart list backups live only in Settings**, labelled "Smart list backups" so they are not confused with backing up the app.
- **The Server group shows tools, not a second overview** (the overview duplicated Home): Live, Tasks, Activity, Devices, Plugins. Library has the libraries list.
- **No in-app screen lock.** It was added and then removed at the owner's request; do not add it again.
- **Push first, polling only as the fallback.** One shared Emby websocket and Tracearr's event stream; a timer asks only while the push is down. Things nothing pushes (smart-list refresh progress, library counts, waiting after a restart) are still asked.
- **Alerts clear on every phone and a stale +15 adds nothing.** Two parents must not give the same extra time twice, so the old notification disappears everywhere once time is added and the buttons check first.
- **New smart lists use the SmartLists plugin's defaults** (its "defaults for new lists" settings), not the app's own; a template keeps what it decides.
- **Secrets stay with the owner.** The Tracearr `trr_pub_` key is typed by the owner on their own phone over their VPN and never sent to the assistant; the GitHub plugin's two API switches are flipped only by the owner on the plugin's Emby page (the assistant must not edit its settings file); keystores and tokens are never shared.
- **Release rule.** A release is made only when the owner says "publish it"; 0.4.0 contains everything on `main` since 0.3.0. The Hall Monitor plugin needs no new release (1.10.0.0 unchanged).

## 2026-10-12: nothing is called Watching Eye any more (owner's decision)
- The plugin's original author re-released the plugin as a closed-source app, so to avoid any confusion every name goes: the plugin file becomes `HallMonitor.dll` (plugin 2.0.0.0), its web addresses `/emby/HallMonitor/...`, its key header `X-HallMonitor-Api-Key`, its live messages `HallMonitorStatus`, its settings files `HallMonitor*`, and the app's classes, screens and tests are renamed to match. No old names are answered or recognised (the owner chose a clean break over a transition): the old Watching Eye v1.6 app stops working with plugin 2.0, and this app needs plugin 2.0 or newer from 0.5 on.
- The plugin keeps its ID, and its first start copies the old settings, watch-time data and log to the new file names once (it never overwrites). The owner deletes the old plugin file on the server by hand, because two copies of one plugin stop Emby from loading it.
- The app moves a Hall Monitor key saved under the old storage name to the new one on first use, so it is not typed again.
- History is left as written: past release notes, `status.md` and the plugin's `PROGRESS.md` keep the old name where they describe what happened. The README of the plugin keeps a factual provenance note (the code was recovered from a compiled plugin).
- Supersedes the 2026-10-11 decision to keep `WatchingEye.dll`.

## 2026-10-12: Hall Pass lives in Library, and uses the administrator's own token
- The Hall Pass plugin's endpoints are admin-only Emby routes, so the app calls them with the signed-in administrator's token like it does SmartLists; no second key and no phone switch to flip (unlike the plugin manager). The tab needs plugin 1.1.0.0 or newer (the first with albums and select-all-matching) and says so when it is older. The settings (word list, online lookups, roll-up tags, dry run) stay on the plugin's Emby page on purpose: they change what is sent online or written to the library, so they are not one tap away on a phone.

## 2026-10-13: Hall Pass tab decisions (0.6.0 to 0.8.0)
- **Swipe review is a full-screen Dialog**, not an inline card: the Library group is a HorizontalPager and a horizontal drag inside it would fight the tab swipe. Right = clean, left = explicit, 30% of the width, buttons as the always-available alternative; Skip and Undo exist because a swipe is easy to get wrong. Decisions leave the list at once and the plugin is told in the background (a failure restores the track): a decision per second must not wait for the network.
- **"All N explicit / All N clean" covers the whole view** (filters, reason, search, opened album or artist) on every page, through the plugin's AllMatching, and always confirms with the count and the view spelled out (`describeView`). Nothing is written to the music files by marking; only Apply writes, and only with dry run off.
- **Order by Most played** puts what the family actually listens to first; plays are all Emby users added up (the plugin sums them).
- **Reason chips** (No evidence, Some sign, Sources disagree, Faint sign of clean) with counts, because a big queue is easier to take in groups.
- **The tab's minimum plugin version is 1.2.0.0** (raised from 1.1.0.0 at 0.8.0): Artists and plays need it. Older plugins see a "too old" card and the rest of the app works.
- **Settings stay on the plugin's Emby page** (word list, online lookups, LRCLIB, roll-up, dry run): they change what is sent out or written, so they are not one tap away on a phone. The app says exactly where the dry-run switch is.
- **Hall Monitor 2.1 changes what Reset means** (a fresh day only) and adds watched totals no reset touches; the app shows them on the Kids card and the Hall Monitor card, with an older plugin it keeps the old behaviour and wording. The footnote about when counting started was removed at the owner's request.

## 2026-10-13: Notifications and the Label Maker plugin
- **Notification rules are configurable in the app, per service, as many as wanted** (owner request): see `docs/notifications-plan.md`. The existing limit-reached alert becomes the default first rule; rules live on each phone; the rule engine is pure Kotlin like `LimitAlerts`.
- **Label Maker alerts use the same mechanism as Hall Monitor**: an Emby websocket push to administrator sessions received by the existing `AlertService`, polling only as a backup, **no outside service** (no ntfy, Gotify or webhooks; the owner decided this). The plugin was checked: `SendMessageToAdminSessions` reaches a websocket authenticated as an administrator and nothing reaches an API-key-only socket.
- The planned Label Maker tab follows the Hall Pass tab pattern (administrator token, version gate, settings stay on the plugin's Emby page, lanes read-only in the app). Everything about that plugin is in `C:\dev\label-maker-emby\docs\` (HANDOFF.md first).

## 2026-10-13: Notification rules, first build (Hall Monitor part)
- **Percent-used rules use the limit closest to running out only.** The plugin reports one limiting period (`LimitingPeriod`, `SecondsRemaining`, `LimitingAllowedSeconds`) and does not report which limits are switched on. Checked live on the test server (throw-away user, daily 60 min plus weekly 10 h): the status showed the daily period only, 3600 of 3600 seconds left. So "every enabled period" needs a Hall Monitor plugin change first. The owner chose the limiting period only.
- **Rules apply to all limited users** (owner choice). Per-user rules come later.
- **A percent rule fires once per user** and rearms only when the percentage falls below the rule (the limit resets, a Reset, or time added). The calendar date is not used: Hall Monitor days start at the reset time (3 a.m. by default) and weekly limits stay high for days. On the first look after the app starts, anyone already over the rule is recorded without a notification.
- **Quiet hours later** (owner choice). Per-rule priority and sound are not built yet; all Hall Monitor alerts use the existing channel.
- **The old Background alerts switch becomes the first rule** ("limit reached", with the +15/+30/+60 buttons). The app starts the background connection while any rule is on.
