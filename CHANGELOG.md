# Changelog

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
