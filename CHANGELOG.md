# Changelog

## 4.3
- **New category: Device-owner / fully-managed checks.** 4.1 and earlier hooked no
  device-owner getter at all, so an app asking "is this a fully managed device?"
  still got yes — which is what Google Photos reads before refusing to set up
  Locked Folder (issue #1). Now covered client-side: `getDeviceOwner`,
  `getDeviceOwnerComponentOnAnyUser`, `getDeviceOwnerNameOnAnyUser`,
  `getDeviceOwnerOrganizationName`, `isDeviceOwnerAppOnAnyUser`, `getProfileOwner`,
  `getProfileOwnerAsUser`, `getProfileOwnerNameAsUser`,
  `isOrganizationOwnedDeviceWithManagedProfile`, `getUserProvisioningState`,
  `getDevicePolicyManagementRoleHolderPackage`, `isDeviceFinanced`,
  `isAffiliatedUser` — plus `getActiveAdmins` / `getActiveAdminsAsUser` and
  `UserManager.isManagedProfile` on the existing admin category. **The app must be
  in the module scope** for these.
- **New category: Device-owner spoof, device-wide** (`DevicePolicyManagerService`),
  **off by default** — answers "no device owner" for every process without scoping
  each app. It reaches apps you never scoped and can confuse Settings on a device
  that really is managed, which is why it ships off.
- **The module now says what it hooked.** The install summary is no longer gated on
  a debug build: every process logs `installed N/M hooks in <where>` and names every
  row it could not resolve (`no-class` / `no-method` / `hook-failed`). Each category
  also logs the first time it actually fires. A category that silently does nothing
  used to be indistinguishable from one that works; it no longer is. This is what a
  report for issue #2 needs.
- Restriction keys are matched **by value across the argument list** instead of at a
  fixed index, so an overload or a framework that presents arguments differently no
  longer silently stops the match.
- Class resolution falls back through the entry-point loader, the system loader and
  the module's own, instead of giving up on the first.
- Fixed a crash: the framework may hand the hook side **null** from
  `openRemoteFile` inside system_server, which threw `NullPointerException` on the
  reporting thread every few seconds. The report falls back to the log, which is
  also the only place it can appear — the framework's remote-file store is
  root-owned, so no hooked process can write to it.

## 4.2
- Renamed to **DuckDevicePolicy** and rebuilt the UI to match the Duck module series:
  duck in the header, light/dark/system theme button, and Status / Categories /
  Diagnostics tabs instead of one long list.
- Categories are split into **Device-wide** (need System Framework in the scope) and
  **App-facing** (need the target app in the scope) — the distinction that decides
  whether a category can work at all.
- The Status tab reports the framework, its version, the Xposed API level and the
  module's actual scope, and says plainly when System Framework is missing.

## 4.1
- **Sideloading works again.** Two new categories hook
  `com.android.server.pm.UserManagerService` inside system_server — the place the
  restriction is actually enforced — rather than only the `android.os.UserManager`
  wrapper each scoped process holds:
  - **App install / uninstall block** — `no_install_unknown_sources`,
    `no_install_unknown_sources_globally`, `no_install_apps`, `no_uninstall_apps`.
    Installing an APK from a normal app (Telegram, a browser, a file manager) is no
    longer refused by the package installer.
  - **Developer options & USB debugging block** — `no_debugging_features`.
- Hooked there: `hasUserRestriction`, `hasUserRestrictionOnAnyUser`,
  `getUserRestrictionSource`, `getUserRestrictionSources`, plus
  `UserManagerService$LocalService.getUserRestriction`. This is the surface the
  upstream module patches, and it answers for **every** caller on the device, not just
  scoped processes — so unlike the existing *User restrictions* category these rows are
  filtered to the restriction keys their category names, instead of flattening every
  `DISALLOW_*` the system asks about.
- Both default on, like every other category. System Framework (`android`) scope is
  required for them — they do nothing from an app's scope.

## 4.0
Migrated to the **modern Xposed API (libxposed)**. The APK is now a modern module,
not a legacy one — the two packagings are mutually exclusive, because the framework
picks the entry point from what the APK declares.

- **Requires a framework implementing API 101+** (`minApiVersion=101`,
  `targetApiVersion=102`) — the LSPosed 2.x forks. Mainline LSPosed 1.9.x will not
  load this build; 3.1 remains the last legacy release.
- Entry points are now declared in `META-INF/xposed/` (`module.prop`,
  `java_init.list`, `scope.list`) instead of `assets/xposed_init` plus `xposed*`
  manifest meta-data and `@array/xposed_scope`. The module description the manager
  shows comes from `android:description`.
- `IXposedHookLoadPackage.handleLoadPackage` is replaced by the two entry points the
  modern API separates: `onSystemServerStarting` (System Framework) and
  `onPackageReady` (each scoped app). Outlook's own private `DevicePolicy` rows are
  tagged with their package and installed only in Outlook's process, instead of being
  attempted and silently failing everywhere else.
- `XC_MethodHook`'s before/after pair is replaced by a single `Hooker.intercept`;
  returning without `chain.proceed()` is what `param.result = ...` used to mean.
  `XposedHelpers` is gone: classes resolve through `Class.forName` and methods through
  an explicit superclass walk that reproduces `findAndHookMethod`.
- **Settings moved off `XSharedPreferences`.** They now live in the framework's remote
  preferences, which system_server can read and which the framework pushes on change —
  so toggles apply live and no `reload()` is needed in the hook. The UI falls back to a
  local file when no framework is bound, and says so.
- ⚠️ **Legacy settings do not carry over.** A modern module gets no world-readable
  redirect, so the 3.x preference file is usually unreadable; a one-shot best-effort
  import runs, and otherwise you start from defaults (everything on, as shipped).
- The UI can now read **its own LSPosed scope** and reports it, instead of only giving
  generic advice — the legacy API could not see it at all.
- `minSdk` 21 -> 26 (libxposed's floor), `compileSdk` 36, AGP 8.13.2 / Kotlin 2.3.0 /
  Gradle 8.14.3. R8 stays on, with libxposed's published rules — including
  `-adaptresourcefilecontents META-INF/xposed/java_init.list`, so the entry class can
  still be obfuscated without the list going stale.

## 3.1
- **New category: Outlook enrollment gate.** Hooks Outlook's own
  `olmcore.managers.mdm.DevicePolicy` (`requiresDeviceManagement` → false,
  `isPolicyApplied` → true) so the app never shows the "your organization
  requires device management" enrollment screen. This is a different layer
  than the DPM/UserManager rows above — it's Outlook's private compliance
  gate, not a framework or Intune-MAM-SDK check — so it only installs when
  the module is scoped into `com.microsoft.office.outlook`, added to the
  default scope suggestion.
- Note: this does not touch Intune MAM app-protection restrictions
  (screenshot block, copy/paste, PIN) inside Outlook — those are enforced
  independently via `com.microsoft.intune.mam` and need a separate hook.

## 3.0
Full rewrite (Kotlin).

- **Toggles actually work.** v2 read the setting from the *hooked app's* prefs via
  `ActivityThread.currentApplication()`, so it always defaulted to on. State is
  now shared cross-process with `XSharedPreferences`.
- **Master + per-category** toggles with All / None quick actions, replacing the
  single all-or-nothing switch.
- **Much broader coverage** — from 8 methods to ~35 across `DevicePolicyManager`
  and `UserManager` (incl. `UserManager.hasUserRestriction`, the full password
  policy set, lock-task, permitted IME/accessibility, and more), via a
  data-driven hook table with per-hook error isolation.
- **Material You** UI with an adaptive icon; edge-to-edge inset handling.
- Hook once per process; removed unused storage permissions; `targetSdk 35`;
  R8 + resource shrinking (~1.8 MB).
- Clean Gradle build + CI (`build.yml` for checks, `release.yml` for signed
  releases), replacing the workflow that regenerated the project inline.
