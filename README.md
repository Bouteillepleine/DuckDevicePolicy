# DuckDevicePolicy

[![CI](https://github.com/Bouteillepleine/DuckDevicePolicy/actions/workflows/build.yml/badge.svg)](https://github.com/Bouteillepleine/DuckDevicePolicy/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/Bouteillepleine/DuckDevicePolicy)](https://github.com/Bouteillepleine/DuckDevicePolicy/releases)
[![Downloads](https://img.shields.io/github/downloads/Bouteillepleine/DuckDevicePolicy/total)](https://github.com/Bouteillepleine/DuckDevicePolicy/releases)

An LSPosed / Xposed module that makes apps see **no device-policy restrictions**
on your own device. It hooks `DevicePolicyManager` and `UserManager` restriction
*checks* and returns the "no restriction" answer, per category, with a master
toggle.

> Fork and full rewrite of [liyafe1997/FuckDevicePolicy](https://github.com/liyafe1997/FuckDevicePolicy).
> The original neutralises `UserManager` policies (e.g. work-profile / Intune
> device-wide restrictions); this v3 rewrite generalises that into a per-category
> UI across `DevicePolicyManager` **and** `UserManager`.

## Features

- **Master toggle** + **per-category** switches, with **All / None** quick actions.
- Covers, per category: camera & screen-capture blocks, device-admin / owner /
  managed-profile checks, the full password/PIN policy set, keyguard feature
  limits, storage-encryption enforcement, managed app configuration, **user
  restrictions (`DISALLOW_*`, incl. `UserManager.hasUserRestriction`)**,
  **the install / uninstall block** (sideload APKs from Telegram, a browser or a
  file manager again) and **the developer-options / USB-debugging block**,
  auto-lock timeout, kiosk / lock-task, permitted IME & accessibility allow-lists,
  assorted smaller restrictions, and **Outlook's own MDM enrollment gate**
  (`requiresDeviceManagement` / `isPolicyApplied` — skips the "your organization
  requires device management" screen; only installs when scoped into
  `com.microsoft.office.outlook`).
- **Material You** dynamic colours (Android 12+) and an adaptive icon.
- Small: R8 + resource shrinking, ~1.8 MB.

## Install & scope

0. **Check your framework first.** From 4.0 this is a *modern* Xposed module
   (libxposed, `minApiVersion=101` / `targetApiVersion=102`). It needs a framework
   that implements API 101+ — the LSPosed 2.x forks. Mainline LSPosed 1.9.x stopped
   at the legacy API and will not load this build; stay on 3.1 there.
1. Install the APK from [Releases](https://github.com/Bouteillepleine/DuckDevicePolicy/releases)
   and enable **DuckDevicePolicy** in LSPosed.
2. Set the module **scope**:
   - **System Framework** (`android`) for the broadest, system-wide effect — this
     is the recommended default for work-profile / user-restriction cases.
   - or specific target app(s) whose policy view you want to change.
3. Reboot (or force-stop) the scoped processes to apply.

   <img width="424" height="924" alt="uix_duckmypolicy" src="https://github.com/user-attachments/assets/c2c223e4-3e91-42a6-a740-b9ae99703760" />

> [!IMPORTANT]
> **Do not scope the MDM app itself** (e.g. Microsoft Intune / Company Portal)
> **or Microsoft Authenticator** — hooking those can expose Xposed/root to
> detection (Authenticator in particular does heavy root/Play-Integrity
> attestation). Outlook is different and included in the default scope: it has
> no meaningful Xposed/root detection, only its own enrollment-gate check,
> which the `outlook_enrollment` category neutralises.
>
> Settings are no longer a world-readable file: the framework brokers them
> (`getRemotePreferences`), so a toggle takes effect in every already-hooked
> process immediately, with no reboot. A reboot (or force-stop) is still needed
> after a **scope** change, because that is when hooks are installed.

To see which restrictions are actually applied on your device:
`adb shell dumpsys device_policy` (look under `userRestrictions:`).

## How it works (and its limits)

Most hooks patch the **client-side** `DevicePolicyManager` / `UserManager` wrappers
inside each scoped process (installed from `onSystemServerStarting` for System
Framework and `onPackageReady` for apps), so they change what an app (or the framework) *sees*
when it queries policy. Intended for your own device.

Two categories go deeper, because a client-side wrapper is not where the answer is
decided. `android.os.UserManager` only binder-calls `UserManagerService`, and the
code that refuses an install — in `system_server` and in the package-installer UI —
asks the service directly, never the wrapper this module patched. So the **App
install / uninstall block** and **Developer options & USB debugging block**
categories hook `com.android.server.pm.UserManagerService` itself
(`hasUserRestriction`, `hasUserRestrictionOnAnyUser`, `getUserRestrictionSource`,
`getUserRestrictionSources`, `$LocalService.getUserRestriction`), which is what the
upstream module does. That answers for **every** process on the device, so unlike
*User restrictions* these two are filtered to the `DISALLOW_*` keys they are about
(`no_install_unknown_sources`, `no_install_unknown_sources_globally`,
`no_install_apps`, `no_uninstall_apps`, `no_debugging_features`) rather than
flattening every restriction the system asks about. They need **System Framework**
(`android`) scope; from an app's scope they do nothing.

The Outlook enrollment gate is a separate, narrower thing: it patches Outlook's
own private `DevicePolicy` class, not a framework or MAM-SDK type. It stops the
initial "enroll this device" prompt, but does **not** touch Intune's MAM
app-protection layer (screenshot block, copy/paste, save-to-personal, app PIN)
— that's enforced independently by the embedded `com.microsoft.intune.mam` SDK
and needs its own separate hook if you want to neutralise it too.

## Build

```bash
./gradlew :app:assembleDebug      # debug
./gradlew :app:assembleRelease    # signed release (R8 + shrink)
```
Requires JDK 17, `compileSdk 36` and `build-tools 36.0.0`. The modern Xposed API
(`io.github.libxposed:api`) comes `compileOnly` from Maven Central — it must never be
bundled, the framework rejects a module that ships its own copy of the API classes.
The app half additionally links `io.github.libxposed:service`, which is what lets the
UI share preferences with the hook and read its own scope.

The artifacts are pinned to 101.x while `module.prop` declares `targetApiVersion=102`:
the 102.x artifacts require `compileSdk 37`, hence AGP 9. The only difference in the
102 API jar is the hot-reload callbacks (defaulted on the interface) and the `API_102`
constant, and `targetApiVersion` is a promise not to call the legacy API — which this
module does not.

### Release signing
The project ships a signing key (`app/duckpolicy.jks`) that is **intentionally not
a secret** — like an Android debug key. This lets CI and local builds produce a
consistently-signed, installable APK with zero secret setup, and lets users update
in place across versions. It is not a Play Store identity. Pushing a `v*` tag runs


`.github/workflows/release.yml`, which builds and publishes the signed APK.

## Credits

- Original module and concept: [liyafe1997/FuckDevicePolicy](https://github.com/liyafe1997/FuckDevicePolicy).
- See [LICENSE](LICENSE).
