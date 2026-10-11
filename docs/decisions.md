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
