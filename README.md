<p align="center"><img src="assets/logo/embidextrous-512.png" alt="Embidextrous: two Es shaking hands" width="128"></p>

# Embidextrous

An Android app for running your **Emby** server with both hands: watch-time limits for the kids, and smart lists, from one place.
(The name is "ambidextrous" with Emby in the front. The logo is two Es shaking hands, in the app's violet and mint.)

> **Status: early (0.1.x released, 0.2 in progress).** This app combines the [Emby SmartLists app](https://github.com/feerlessleadr/emby.smartlists.android) and the
> [Watching Eye app](https://github.com/feerlessleadr/watching-eye-android-app) (its plugin is now called Hall Monitor). It has been tried on an emulator, not yet on a real phone.
> See [docs/plan.md](docs/plan.md) and [docs/status.md](docs/status.md).

## What it does (and will do)

| Area | State | Needs on the server |
|---|---|---|
| **Smart lists** (overview, refresh, enable/disable, delete, full editor, backups) | working (from SmartLists 0.4.1) | the [SmartLists plugin](https://github.com/feerlessleadr/emby.smartlists), an Emby administrator account |
| **Hall Monitor** (the former Watching Eye: time left per child, extra time with undo, time-outs, Bedtime, live updates, alerts, widget) | working | the [Hall Monitor plugin](https://github.com/feerlessleadr/hall-monitor-emby) 1.9 or newer (1.10 carries the new name) |
| Plugin manager (GitHub downloads), bulk tag editing, explicit-tag editing for music | ideas for later | |

Both plugins are separate server-side projects; this app only combines the two phone apps.

## Requirements
- Emby Server 4.10.1 or newer.
- A server address that uses **HTTPS** with a certificate your phone trusts. The app refuses plain `http://` addresses and follows no redirects.
- Android 8.0 (API 26) or newer.

## Install
The app is installed from this repository's releases, not from the Play Store. With [Obtainium](https://obtainium.imranr.dev/) (updates
itself): *Add App*, enter `https://github.com/feerlessleadr/embidextrous`. Or download `Embidextrous-<version>.apk` from the
[latest release](https://github.com/feerlessleadr/embidextrous/releases/latest). Verify a download with
`gh attestation verify Embidextrous-<version>.apk --repo feerlessleadr/embidextrous`.

## Build and test
Windows, PowerShell, Android Studio's bundled JDK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
./gradlew.bat test            # unit tests
./gradlew.bat assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

## Documentation
- [docs/plan.md](docs/plan.md): the roadmap and what is done
- [docs/decisions.md](docs/decisions.md): every decision with its reason
- [docs/status.md](docs/status.md): what was built and verified, newest last
- [CLAUDE.md](CLAUDE.md): working rules for AI assistants on this repository

## License
MIT, see [LICENSE](LICENSE). The code starts from the Emby SmartLists app (same author, also MIT). The logo is original artwork made for this app.
