# RAM Watch

**A free, open-source, ultralight (~40 KB) system monitor and game overlay for Android.**
No ads. No subscriptions. No tracking. Ever.

## Features
- **Live dashboard** in a glass-style UI: RAM, CPU load and clocks, GPU, battery, storage, network
- **Hardware info** like CPU-Z: SoC, cores, GPU renderer, display, kernel, Android version
- **Status bar value**: RAM %, CPU %, battery temperature or network speed next to the clock
- **Pinned notification** with the stats you choose
- **Game overlay**: a slim, colour-coded pill (FPS, CPU, RAM, temperatures, speed) over any game or app
- **Network**: live download/upload speed, Wi-Fi and mobile signal, link speed, band
- Pauses when the screen is off. Android 7 to 15.

## Get it — convenience or control
| Option | How |
| --- | --- |
| Ready-made APK | [Latest GitHub Release](https://github.com/atharvkmore/ramwatch/releases/latest) |
| Play Store | Coming soon |
| F-Droid | Coming soon (built from this source, suggestion cards removed) |
| Build it yourself | See below |

## Build from source
Requires JDK 17 and Gradle 8.10+ (or just fork the repo: GitHub Actions builds the APK for you).
```
gradle assembleRelease            # standard build
gradle assembleRelease -Pfdroid   # without suggestion cards
```
Unsigned builds use the debug key. To sign, set `RW_KEYSTORE`, `RW_STORE_PASS`, `RW_KEY_ALIAS`, `RW_KEY_PASS`.

## How RAM Watch is funded
The app is free and stays free. When the app detects a real problem (phone overheating, battery fault, RAM always full) it may show **one** optional product suggestion on the Home tab. Those links can earn a small commission. You can turn them off in Settings, and the F-Droid build has none. Nothing about you is collected or sent anywhere — read the code to check.

**Need a custom app, dashboard or overlay for your business, café or team?** [Get in touch](https://github.com/atharvkmore/ramwatch/issues/new?title=Custom%20app%20or%20overlay%20request).

## About FPS
The overlay measures how fast the screen refreshes. Android only lets privileged apps (root or Shizuku) read a game's exact frame rate; Shizuku support is planned.

## Licence
RAM Watch is licensed under the **GNU GPL v3.0** — see [LICENSE](LICENSE). You may use, study, change and share it; if you distribute a modified version, you must publish its source under the same licence.
Want to ship it closed-source or rebranded? A **commercial licence** is available — see [COMMERCIAL-LICENSE.md](COMMERCIAL-LICENSE.md).

"RAM Watch" and the RAM Watch logo are trademarks of the author. Forks must use a different name and logo.

[Privacy policy](PRIVACY.md) · [Contributing](CONTRIBUTING.md)
