# Phone Farm

**Run an entire phone farm from one Android phone.** Phone Farm is a self-contained, always-on farm
runtime that keeps dozens of web targets warm 24/7 — no root, no PC, no emulator, no second device.

It is a single small APK. It runs as an Android foreground service, schedules every node on its own
cadence, throttles itself when the phone gets hot or the battery drops, and updates itself straight
from this repository's GitHub Releases.

---

## Why it is light

| | |
|---|---|
| APK size | ~2.5 MB (R8 minified, no browser engine bundled) |
| LIGHT nodes | plain HTTP + persistent cookie jar — a few KB of RAM per node |
| SESSION nodes | a WebView is created for the cycle and destroyed afterwards, so idle RAM goes back to the OS |
| No trackers | no analytics, no telemetry, no crash SDKs, no ads |

## Features

**Runtime**
- 24/7 foreground service with a low-priority, ongoing notification and pause/stop actions
- Partial wake lock, boot restore (`BOOT_COMPLETED`) and restore after an app update
- Battery-optimisation exemption request built into the settings screen
- Crash-safe scheduling: every node has its own next-run timestamp, jitter and exponential backoff

**Three execution modes per node**
- `LIGHT` — HTTP keep-alive with per-node cookies. Cheapest; hundreds of nodes are still light.
- `SESSION` — a real WebView loads the page, runs your injected JavaScript, then is torn down.
- `MONITOR` — probe-only uptime and latency tracking.

**Intelligence**
- Adaptive power governor: reads thermal status and battery level and drops to one node at a third
  of the cadence instead of cooking the phone, and parks the farm at critical battery
- Concurrency cap (1–8 parallel nodes) with oldest-due-first scheduling
- Randomised jitter per node, so a farm never fires in lockstep
- Automatic retry with backoff on failures, plus per-node health and latency history
- Full event log (INFO / WARN / ERROR) with filters, persisted on device

**Interface**
- Strict monochrome design system — pure black, chalk white, four greys, one hairline weight
- Live uptime, cycles, errors and data counters; per-node latency sparklines
- Configuration export / import as JSON

**Updates**
- In-app updater wired to GitHub Releases: checks the feed, filters drafts/prereleases, compares
  semantic versions, downloads the APK with progress, verifies package name + version code, then
  hands it to the system installer through a `PackageInstaller` session
- Stable / Beta channel selector, manual check, and background auto-download

## Install

Grab the newest APK from [Releases](https://github.com/itriedcoding/phoney-farm/releases) and
sideload it. Android 8.0 (API 26) or newer.

For uninterrupted 24/7 operation:

1. Open **Settings → Always on** and allow battery-optimisation exemption.
2. Keep the ongoing notification — it is what keeps the runtime from being killed.
3. Enable **Restore after reboot** if you want the farm to come back automatically.

## Build

```bash
./gradlew :app:assembleRelease
```

Requires JDK 17 and the Android SDK (compile SDK 34, build tools 34.0.0). The signed APK lands in
`app/build/outputs/apk/release/`.

`signing/phonefarm.jks` is the project's public release key, checked in on purpose so that every
build of Phone Farm upgrades cleanly over the previous one and CI can sign identical artifacts.
It is a distribution key for a free MIT app — do not reuse it for anything that matters.

## Releasing an update

```bash
git tag v1.0.1
git push origin v1.0.1
```

`.github/workflows/release.yml` builds the APK and attaches it to the GitHub Release. The in-app
updater finds it automatically, because it reads this repository's release feed at runtime.

## Architecture

```
data/      FarmNode, NodeStats, Settings, FarmState — JSON persistence (atomic write + rename)
engine/    FarmEngine (scheduler, stats, logs, governor), HttpRunner, WebRunner, PowerGovernor
service/   FarmService (foreground + wake lock + notification), BootReceiver
update/    UpdateManager (GitHub Releases), InstallReceiver (PackageInstaller callbacks)
ui/        Theme (monochrome system), Common (primitives), Screens, NodeEditor, App shell
```

The engine is a single process-wide object with a one-second scheduler tick. Nodes are dispatched
oldest-due-first up to the concurrency cap; each cycle records latency, bytes, status and failure
count, then schedules its own next run. Everything (nodes, settings, statistics) is written to
`files/farm/state.json` through a temp file and an atomic rename.

## Scope and fair use

Phone Farm is a general-purpose web automation and keep-alive runner. You choose the targets. Use it
only on sites and accounts you are allowed to automate, and respect each service's terms and rate
limits. It is not a bot-detection evasion tool.

## License

MIT — see [LICENSE](LICENSE).
