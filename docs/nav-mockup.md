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
