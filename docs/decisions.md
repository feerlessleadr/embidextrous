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
