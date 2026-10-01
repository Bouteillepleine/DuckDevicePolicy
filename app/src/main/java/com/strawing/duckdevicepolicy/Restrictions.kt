package com.strawing.duckdevicepolicy

import android.os.Bundle

object Restrictions {

    private const val DPM = "android.app.admin.DevicePolicyManager"
    private const val UM = "android.os.UserManager"
    private const val RM = "android.content.RestrictionsManager"
    private const val UMS = "com.android.server.pm.UserManagerService"
    private const val UMS_LOCAL = "com.android.server.pm.UserManagerService\$LocalService"
    private const val DPMS = "com.android.server.devicepolicy.DevicePolicyManagerService"
    private const val OUTLOOK_DP = "com.microsoft.office.outlook.olmcore.managers.mdm.DevicePolicy"
    private const val OUTLOOK_PKG = "com.microsoft.office.outlook"

    private const val CN = "android.content.ComponentName"
    private const val STR = "java.lang.String"
    private const val INT = "int"
    private const val BOOL = "boolean"

    private const val USER_NULL = -10000

    const val CAMERA = "camera"
    const val SCREEN_CAPTURE = "screen_capture"
    const val ADMIN = "admin"
    const val OWNER = "owner"
    const val PASSWORD = "password"
    const val KEYGUARD = "keyguard"
    const val ENCRYPTION = "encryption"
    const val APP_RESTRICTIONS = "app_restrictions"
    const val USER_RESTRICTIONS = "user_restrictions"
    const val PACKAGE_INSTALL = "package_install"
    const val DEBUGGING = "debugging"
    const val OWNER_SERVER = "owner_server"
    const val MAX_LOCK = "max_lock"
    const val LOCK_TASK = "lock_task"
    const val PERMITTED = "permitted"
    const val MISC = "misc"
    const val OUTLOOK_ENROLLMENT = "outlook_enrollment"

    data class Category(
        val key: String,
        val title: String,
        val subtitle: String,
        val deviceWide: Boolean = false,
        val defaultOn: Boolean = true,
    )

    val CATEGORIES: List<Category> = listOf(
        Category(CAMERA, "Camera block", "Report the camera as not disabled"),
        Category(SCREEN_CAPTURE, "Screenshot / screen-record block", "Allow screen capture"),
        Category(ADMIN, "Device admin & managed-profile checks", "Look unmanaged (no admin, no managed profile)"),
        Category(OWNER, "Device-owner / fully-managed checks", "Hide the device owner — what Google Photos reads for Locked Folder"),
        Category(PASSWORD, "Password & PIN policy", "Drop length, complexity, expiry and wipe rules"),
        Category(KEYGUARD, "Lock-screen feature limits", "Re-enable camera, notifications, etc. on keyguard"),
        Category(ENCRYPTION, "Storage-encryption enforcement", "Report encryption as not required"),
        Category(APP_RESTRICTIONS, "Managed app configuration", "Return empty app-restriction bundles"),
        Category(USER_RESTRICTIONS, "User restrictions (DISALLOW_*)", "Clear the restriction view inside scoped processes"),
        Category(MAX_LOCK, "Auto-lock timeout", "Remove the forced maximum time-to-lock"),
        Category(LOCK_TASK, "Kiosk / lock-task mode", "Report lock-task as permitted / unrestricted"),
        Category(PERMITTED, "Allowed IMEs & accessibility", "Remove input-method / accessibility allow-lists"),
        Category(MISC, "Misc (auto-time, cross-profile, BT)", "Clear assorted smaller restrictions"),
        Category(OUTLOOK_ENROLLMENT, "Outlook enrollment gate", "Report the device as already MDM-enrolled inside Outlook"),
        Category(
            PACKAGE_INSTALL,
            "App install / uninstall block",
            "Sideload APKs from any app (Telegram, browsers, file managers)",
            deviceWide = true,
        ),
        Category(
            DEBUGGING,
            "Developer options & USB debugging block",
            "Re-enable developer settings and ADB",
            deviceWide = true,
        ),
        Category(
            OWNER_SERVER,
            "Device-owner spoof, device-wide",
            "Answer \"no device owner\" to apps, without scoping each one. Leave off unless you need it",
            deviceWide = true,
            defaultOn = false,
        ),
    )

    val CATEGORY_BY_KEY: Map<String, Category> = CATEGORIES.associateBy { it.key }

    fun isDeviceWide(spec: Spec): Boolean = CATEGORY_BY_KEY[spec.category]?.deviceWide == true

    data class Spec(
        val category: String,
        val className: String,
        val method: String,
        val paramTypes: Array<String>,
        val pkg: String? = null,
        val keys: Set<String>? = null,
        val keyArg: Int = 0,
        val transform: ((Any?) -> Any?)? = null,
        val result: () -> Any? = { null },
    ) {
        val id: String get() = "${className.substringAfterLast('.')}#$method"
        val key: String get() = "$category|$id|${paramTypes.joinToString(",")}"
    }

    private fun bundle(): Any = Bundle()

    private fun without(keys: Set<String>): (Any?) -> Any? = { value ->
        if (value is Bundle && keys.any { value.getBoolean(it) }) {
            Bundle(value).apply { keys.forEach { remove(it) } }
        } else {
            value
        }
    }

    private val INSTALL_KEYS = setOf(
        "no_install_unknown_sources",
        "no_install_unknown_sources_globally",
        "no_install_apps",
        "no_uninstall_apps",
    )

    private val DEBUGGING_KEYS = setOf("no_debugging_features")

    private fun restrictionRows(category: String, keys: Set<String>): List<Spec> = listOf(
        Spec(category, UMS, "hasUserRestriction", arrayOf(STR, INT), keys = keys) { false },
        Spec(category, UMS, "hasUserRestrictionOnAnyUser", arrayOf(STR), keys = keys) { false },
        Spec(category, UMS, "getUserRestrictionSource", arrayOf(STR, INT), keys = keys) { 0 },
        Spec(category, UMS, "getUserRestrictionSources", arrayOf(STR, INT), keys = keys) { emptyList<Any>() },
        Spec(category, UMS_LOCAL, "getUserRestriction", arrayOf(INT, STR), keys = keys, keyArg = 1) { false },
        Spec(category, UMS, "getUserRestrictions", arrayOf(INT), transform = without(keys)),
    )

    val ALL: List<Spec> = listOf(
        Spec(CAMERA, DPM, "getCameraDisabled", arrayOf(CN)) { false },

        Spec(SCREEN_CAPTURE, DPM, "getScreenCaptureDisabled", arrayOf(CN)) { false },

        Spec(ADMIN, DPM, "isAdminActive", arrayOf(CN)) { false },
        Spec(ADMIN, DPM, "isDeviceOwnerApp", arrayOf(STR)) { false },
        Spec(ADMIN, DPM, "isProfileOwnerApp", arrayOf(STR)) { false },
        Spec(ADMIN, DPM, "isManagedProfile", arrayOf(CN)) { false },
        Spec(ADMIN, DPM, "isDeviceManaged", arrayOf()) { false },
        Spec(ADMIN, DPM, "getActiveAdmins", arrayOf()) { null },
        Spec(ADMIN, DPM, "getActiveAdminsAsUser", arrayOf(INT)) { null },
        Spec(ADMIN, UM, "isManagedProfile", arrayOf()) { false },

        Spec(OWNER, DPM, "getDeviceOwner", arrayOf()) { null },
        Spec(OWNER, DPM, "getDeviceOwnerComponentOnAnyUser", arrayOf()) { null },
        Spec(OWNER, DPM, "getDeviceOwnerNameOnAnyUser", arrayOf()) { null },
        Spec(OWNER, DPM, "getDeviceOwnerOrganizationName", arrayOf()) { null },
        Spec(OWNER, DPM, "isDeviceOwnerAppOnAnyUser", arrayOf(STR)) { false },
        Spec(OWNER, DPM, "getProfileOwner", arrayOf()) { null },
        Spec(OWNER, DPM, "getProfileOwnerAsUser", arrayOf(INT)) { null },
        Spec(OWNER, DPM, "getProfileOwnerNameAsUser", arrayOf(INT)) { null },
        Spec(OWNER, DPM, "isOrganizationOwnedDeviceWithManagedProfile", arrayOf()) { false },
        Spec(OWNER, DPM, "getUserProvisioningState", arrayOf()) { 0 },
        Spec(OWNER, DPM, "getDevicePolicyManagementRoleHolderPackage", arrayOf()) { null },
        Spec(OWNER, DPM, "isDeviceFinanced", arrayOf()) { false },
        Spec(OWNER, DPM, "isAffiliatedUser", arrayOf()) { false },

        Spec(PASSWORD, DPM, "getPasswordQuality", arrayOf(CN)) { 0 },
        Spec(PASSWORD, DPM, "getPasswordMinimumLength", arrayOf(CN)) { 0 },
        Spec(PASSWORD, DPM, "getPasswordMinimumLetters", arrayOf(CN)) { 0 },
        Spec(PASSWORD, DPM, "getPasswordMinimumNumeric", arrayOf(CN)) { 0 },
        Spec(PASSWORD, DPM, "getPasswordMinimumSymbols", arrayOf(CN)) { 0 },
        Spec(PASSWORD, DPM, "getPasswordMinimumUpperCase", arrayOf(CN)) { 0 },
        Spec(PASSWORD, DPM, "getPasswordMinimumLowerCase", arrayOf(CN)) { 0 },
        Spec(PASSWORD, DPM, "getPasswordMinimumNonLetter", arrayOf(CN)) { 0 },
        Spec(PASSWORD, DPM, "getPasswordHistoryLength", arrayOf(CN)) { 0 },
        Spec(PASSWORD, DPM, "getPasswordExpiration", arrayOf(CN)) { 0L },
        Spec(PASSWORD, DPM, "getPasswordExpirationTimeout", arrayOf(CN)) { 0L },
        Spec(PASSWORD, DPM, "getMaximumFailedPasswordsForWipe", arrayOf(CN)) { 0 },
        Spec(PASSWORD, DPM, "getRequiredPasswordComplexity", arrayOf()) { 0 },
        Spec(PASSWORD, DPM, "isActivePasswordSufficient", arrayOf()) { true },

        Spec(KEYGUARD, DPM, "getKeyguardDisabledFeatures", arrayOf(CN)) { 0 },

        Spec(ENCRYPTION, DPM, "getStorageEncryption", arrayOf(CN)) { false },

        Spec(APP_RESTRICTIONS, DPM, "getApplicationRestrictions", arrayOf(CN, STR)) { bundle() },
        Spec(APP_RESTRICTIONS, RM, "getApplicationRestrictions", arrayOf()) { bundle() },

        Spec(USER_RESTRICTIONS, DPM, "getUserRestrictions", arrayOf(CN)) { bundle() },
        Spec(USER_RESTRICTIONS, UM, "getUserRestrictions", arrayOf()) { bundle() },
        Spec(USER_RESTRICTIONS, UM, "hasUserRestriction", arrayOf(STR)) { false },

        Spec(MAX_LOCK, DPM, "getMaximumTimeToLock", arrayOf(CN)) { 0L },

        Spec(LOCK_TASK, DPM, "isLockTaskPermitted", arrayOf(STR)) { true },
        Spec(LOCK_TASK, DPM, "getLockTaskFeatures", arrayOf(CN)) { 0 },

        Spec(PERMITTED, DPM, "getPermittedInputMethods", arrayOf(CN)) { null },
        Spec(PERMITTED, DPM, "getPermittedAccessibilityServices", arrayOf(CN)) { null },

        Spec(MISC, DPM, "getAutoTimeRequired", arrayOf()) { false },
        Spec(MISC, DPM, "getBluetoothContactSharingDisabled", arrayOf(CN)) { false },
        Spec(MISC, DPM, "getCrossProfileCallerIdDisabled", arrayOf(CN)) { false },
        Spec(MISC, DPM, "getCrossProfileContactsSearchDisabled", arrayOf(CN)) { false },

        Spec(OUTLOOK_ENROLLMENT, OUTLOOK_DP, "requiresDeviceManagement", arrayOf(), OUTLOOK_PKG) { false },
        Spec(OUTLOOK_ENROLLMENT, OUTLOOK_DP, "isPolicyApplied", arrayOf(), OUTLOOK_PKG) { true },

        Spec(OWNER_SERVER, DPMS, "hasDeviceOwner", arrayOf()) { false },
        Spec(OWNER_SERVER, DPMS, "getDeviceOwnerName", arrayOf()) { null },
        Spec(OWNER_SERVER, DPMS, "getDeviceOwnerOrganizationName", arrayOf()) { null },
        Spec(OWNER_SERVER, DPMS, "isOrganizationOwnedDeviceWithManagedProfile", arrayOf()) { false },
        Spec(OWNER_SERVER, DPMS, "getUserProvisioningState", arrayOf(INT)) { 0 },
        Spec(OWNER_SERVER, DPMS, "getDeviceOwnerComponent", arrayOf(BOOL)) { null },
        Spec(OWNER_SERVER, DPMS, "getDeviceOwnerComponentOnUser", arrayOf(INT)) { null },
        Spec(OWNER_SERVER, DPMS, "getDeviceOwnerUserId", arrayOf()) { USER_NULL },
        Spec(OWNER_SERVER, DPMS, "getActiveAdmins", arrayOf(INT)) { null },
    ) + restrictionRows(PACKAGE_INSTALL, INSTALL_KEYS) + restrictionRows(DEBUGGING, DEBUGGING_KEYS)
}
