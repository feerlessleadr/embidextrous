# Grouped navigation mockup (branch `nav-grouped`, not merged)

A working prototype of the grouped bottom bar, to look at and judge before it is built for real.

- **Bottom bar** (administrator phone): Home, Watching, Library, Server, Settings. A Parent phone gets Home, Watching, Settings; Custom adds Library when Smart Lists is on.
- **Groups hold sections as tabs along the top**, so a new service is a new tab, not a new bottom-bar item:
  - Watching: Hall Monitor, Activity (Tracearr, planned)
  - Library: Smart lists, Tags (bulk tag editing, planned). Backups stay in Settings, labelled "Smart list backups"
  - Server: Overview (server and plugin cards), Plugins (plugin manager, planned)
- Planned sections show a "Coming soon" page in **debug builds only**; a release build shows only what exists, and a group with one section shows no tab row.
- The Home cards open their group (Smart lists to Library, Server and Plugins to Server).
- Inside a group with a tab row the screens leave their own title out (the tab says where you are); they keep it when a group has a single section.
- Not done in the prototype: the leftover app bar under the tab row is taller than it needs to be; the Back button behaviour is unchanged from `main`; the Server group still needs its real content.

## Update: the Server and Library groups are built (branch `nav-grouped`)
- **Server** (administrator): a slim header (Emby version, Up to date or Update available, Restart pending, a menu with Restart Emby behind a confirmation) over tabs **Live**, **Tasks**, **Activity**, **Devices**, **Plugins**.
  - **Live**: who is playing right now (title, progress, direct play / direct stream / transcoding with the reason, user, device, address) with Stop, then who is connected; speakers and idle apps are tucked behind a button. Looks again every 5 seconds while on screen.
  - **Tasks**: scheduled tasks, running ones first, then failed ones; progress for a running task; Run now or Stop, each asking first; system tasks behind a chip.
  - **Activity**: the server's log with filters (All, Problems, Sign-ins, Playback) and "Load older entries"; failed sign-ins and warnings are highlighted.
  - **Devices**: every app and browser that signed in, "This phone" marked, Sign out asking first (with an extra warning for this phone).
  - **Plugins**: the plugin card plus a "Plugin manager, coming soon" note; the GitHub Repo Plugin Install manager (phase 9) builds on this tab.
- **Library** (administrator with Smart Lists): **Smart lists**, **Libraries** (each library with how many movies, shows, albums and so on it holds, Scan and Scan all libraries, each asking first), **Tags** (placeholder in debug builds).
- All of it uses Emby's own admin API and works with the accent colour and light and dark. 166 unit tests pass (the parsers).
- Checked on an emulator against the test server: every tab with real data; a stream shown and the Stop confirmation; the Sign out confirmation including this phone's warning; the Run now confirmation (cancelled, so no task was started on the shared server). Not checked: Restart Emby (deliberately not pressed on the shared server), Scan (same reason), a transcoding session, a failed task, a failed sign-in, light mode.
