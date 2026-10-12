# Grouped navigation mockup (branch `nav-grouped`, not merged)

A working prototype of the grouped bottom bar, to look at and judge before it is built for real.

- **Bottom bar** (administrator phone): Home, Watching, Library, Server, Settings. A Parent phone gets Home, Watching, Settings; Custom adds Library when Smart Lists is on.
- **Groups hold sections as tabs along the top**, so a new service is a new tab, not a new bottom-bar item:
  - Watching: Hall Monitor, Activity (Tracearr, planned)
  - Library: Smart lists, Backups, Tags (bulk tag editing, planned)
  - Server: Overview (server and plugin cards), Plugins (plugin manager, planned)
- Planned sections show a "Coming soon" page in **debug builds only**; a release build shows only what exists, and a group with one section shows no tab row.
- The Home cards open their group (Smart lists to Library, Server and Plugins to Server).
- Not done in the prototype: the screens inside a group still carry their own title bar under the tab row (a real version would show the group name once); Settings still has its own Backups row; the Back button behaviour is unchanged from `main`.
