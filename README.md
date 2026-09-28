# DuckDevicePolicy

[![CI](https://github.com/Bouteillepleine/DuckDevicePolicy/actions/workflows/build.yml/badge.svg)](https://github.com/Bouteillepleine/DuckDevicePolicy/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/Bouteillepleine/DuckDevicePolicy)](https://github.com/Bouteillepleine/DuckDevicePolicy/releases)
[![Downloads](https://img.shields.io/github/downloads/Bouteillepleine/DuckDevicePolicy/total)](https://github.com/Bouteillepleine/DuckDevicePolicy/releases)

Makes apps see **no device-policy restrictions** on your own device —
`DevicePolicyManager` / `UserManager` checks answered "no restriction", per
category, behind a master toggle. Formerly DuckPolicy, same package. Fork of
[liyafe1997/FuckDevicePolicy](https://github.com/liyafe1997/FuckDevicePolicy).

<img src="screenshot.png" width="300" alt="DuckDevicePolicy" />

**Needs an LSPosed 2.x fork** (Xposed API 101+). Mainline 1.9.x will not load it.

**Scope matters.** a framework entry (`android`, `system` or `system_server` — tick all three, managers disagree) for the device-wide categories —
app install / uninstall, developer options, device-owner spoof. The target app for
everything else, including the device-owner checks Google Photos reads for Locked
Folder. Never scope Intune / Company Portal / Authenticator.

**Not working?** The LSPosed log has `installed N/M hooks in <process>`, which
names anything that did not resolve, and `first hit: <category> ...` the first time
one fires. Quote both when reporting.

Build with `./gradlew :app:assembleRelease` (JDK 17, compileSdk 36). The signing key
is in the repo on purpose, like a debug key; a `v*` tag publishes a release.

[CHANGELOG](CHANGELOG.md) · [LICENSE](LICENSE)
