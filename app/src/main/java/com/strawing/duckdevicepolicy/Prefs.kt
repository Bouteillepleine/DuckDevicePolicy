package com.strawing.duckdevicepolicy

/**
 * Single source of truth for the preference contract shared between the UI (writer) and the
 * Xposed hook (reader). Both now go through the framework's remote preferences rather than a
 * world-readable file; the key names are unchanged so an import can carry old values over.
 */
object Prefs {
    const val NAME = "bypass_prefs"

    /** Master on/off. Defaults to true so a fresh install is active. */
    const val KEY_MASTER = "bypass_enabled"

    /** Per-category key, e.g. "cat_camera". */
    fun key(category: String) = "cat_$category"

    /** Per-category default (bypass everything until the user says otherwise). */
    const val CATEGORY_DEFAULT = true

    /**
     * Set once the 3.x settings have been copied into the framework's remote preferences, so
     * the import runs exactly once and never overwrites later edits.
     */
    const val KEY_IMPORTED = "prefs_imported_v4"

    /** The shipped default for any key [allKeys] returns. */
    fun defaultFor(key: String): Boolean =
        if (key == KEY_MASTER) true
        else Restrictions.CATEGORY_BY_KEY[key.removePrefix("cat_")]?.defaultOn ?: CATEGORY_DEFAULT

    /** Every key worth carrying across the move to remote preferences. */
    fun allKeys(): List<String> =
        listOf(KEY_MASTER) + Restrictions.CATEGORIES.map { key(it.key) }
}
