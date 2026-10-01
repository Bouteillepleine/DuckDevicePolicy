# Changelog

## 4.9
- The device-wide device-owner spoof now covers what `isDeviceOwnerApp`, `getActiveAdmins`
  and the rest actually read on the binder side, so an app no longer has to be scoped for it
  to work. It answers apps only; the framework asking itself still gets the truth.
- Releases are published as prereleases.

## 4.8
- App-facing categories now run only in apps you scope; system_server keeps the device-wide
  ones. Deletes 4.7's caller guard, and the bootloop class it was patching.
- Sideloading: the restriction bundle is filtered too, not just the boolean checks, so
  session installs and uninstalls go through.
- Unreadable settings switch the module off instead of defaulting everything on.
- Google Photos is a recommended scope entry; the README says why the Intune apps never are.
- The log names every unresolved row, per category, instead of the first 400 characters.
- All / None act on their own section, and All leaves the device-owner spoof alone.
- Settings that fail to save say so.
- Dropped the Diagnostics report card the framework can never fill.
- Clearer scope wording in the app.
- CI builds the release variant.

## 4.7
- Fixed a bootloop on devices that really have a device owner. The ownership spoof also
  answered system_server's own checks, so DevicePolicyManagerService threw at
  PHASE_LOCK_SETTINGS_READY and took the system down in a loop. It now answers apps only.
  Reported by @MootComb.

## 4.6
- Diagnostics claimed it showed the real restriction values. It didn't: the answer comes
  from system_server, where the device-wide hooks are, so it is what an app is told.
  Relabelled, with the caveat and where to get the truth. Long keys wrap properly now.

## 4.5
- Diagnostics tab shows which restrictions your device actually has, and says the hook
  report lives in the LSPosed log instead of pretending it will turn up here.

## 4.4
- Device-wide categories only worked if you ticked the right framework scope entry.
  `android`, `system` and `system_server` are all recommended now. Found by @victay44.

## 4.3
- Hides the device owner — what Google Photos reads before refusing Locked Folder.
  Device-wide variant included, off by default.
- Logs what it hooked and the first time each category fires. Quote those when reporting.

## 4.2
- Renamed to DuckDevicePolicy, same package. New UI: Status / Categories / Diagnostics.

## 4.1
- Unknown-sources installs work again: hooks `UserManagerService` in system_server,
  not just the client-side wrapper.

## 4.0
- Modern Xposed API (libxposed). Needs an LSPosed 2.x fork; 1.9.x will not load it.
  Settings do not carry over from 3.x.

## 3.1
- Outlook enrollment gate.

## 3.0
- Kotlin rewrite: working toggles, per-category UI, much wider coverage.
