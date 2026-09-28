package com.strawing.duckdevicepolicy

import android.content.Context
import android.app.admin.DevicePolicyManager
import android.content.SharedPreferences
import android.os.Bundle
import android.os.UserManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import io.github.libxposed.service.XposedService
import java.io.FileInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var ui: DuckUi
    private lateinit var prefs: SharedPreferences
    private var usingRemotePrefs = false
    private var tab = DuckUi.TAB_STATUS

    private val serviceListener: (XposedService?) -> Unit = { svc ->
        runOnUiThread {
            if ((svc != null) != usingRemotePrefs && !isFinishing) recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Theming.restore(this)
        super.onCreate(savedInstanceState)
        prefs = resolvePrefs()
        ui = DuckUi(this)
        setContentView(ui.scaffold({ id -> tab = id; render() }, tab))
        render()
    }

    override fun onStart() {
        super.onStart()
        App.addListener(serviceListener)
    }

    override fun onStop() {
        App.removeListener(serviceListener)
        super.onStop()
    }

    private fun render() {
        ui.setHeader(getString(R.string.app_name)) { recreate() }
        ui.content.removeAllViews()
        when (tab) {
            DuckUi.TAB_CATEGORIES -> renderCategories()
            DuckUi.TAB_DIAGNOSTICS -> renderDiagnostics()
            else -> renderStatus()
        }
        ui.content.addView(ui.footer("DuckDevicePolicy • ${BuildConfig.VERSION_NAME}"))
    }

    // ------------------------------------------------------------------ status

    private fun renderStatus() {
        val master = prefs.getBoolean(Prefs.KEY_MASTER, true)
        val scope = moduleScope
        val health = when {
            !usingRemotePrefs -> Health.BAD
            scope.isNullOrEmpty() -> Health.BAD
            !master -> Health.PAUSED
            else -> Health.GOOD
        }
        val detail = when {
            !usingRemotePrefs -> "No Xposed framework bound"
            scope.isNullOrEmpty() -> "Scope is empty — nothing is hooked"
            !master -> getString(R.string.status_inactive)
            else -> "${enabledCount()} of ${Restrictions.CATEGORIES.size} categories on"
        }
        ui.content.addView(
            ui.heroCard(
                getString(R.string.master_toggle),
                detail,
                health,
                if (master) "On" else "Off",
                master,
                usingRemotePrefs,
            ) { checked ->
                prefs.edit()?.putBoolean(Prefs.KEY_MASTER, checked)?.apply()
                render()
            }
        )

        ui.content.addView(ui.sectionLabel("Framework"))
        val fw = ui.outlinedCard()
        val fwCol = ui.column()
        val svc = App.service
        if (svc == null) {
            fwCol.addView(ui.bodyText(getString(R.string.hint_no_framework)))
        } else {
            fwCol.addView(ui.infoRow("Implementation", runCatching { svc.frameworkName }.getOrDefault("?")))
            fwCol.addView(ui.thinDivider())
            fwCol.addView(ui.infoRow("Version", runCatching { svc.frameworkVersion }.getOrDefault("?")))
            fwCol.addView(ui.thinDivider())
            fwCol.addView(ui.infoRow("Xposed API", runCatching { svc.apiVersion.toString() }.getOrDefault("?")))
        }
        fw.addView(fwCol)
        ui.content.addView(fw)

        ui.content.addView(ui.sectionLabel("Scope"))
        val sc = ui.outlinedCard()
        val scCol = ui.column()
        when {
            scope == null -> scCol.addView(ui.bodyText(getString(R.string.hint_no_framework)))
            scope.isEmpty() -> scCol.addView(ui.bodyText(getString(R.string.hint_empty_scope)))
            else -> {
                scope.forEach {
                    scCol.addView(ui.infoRow(it, if (it in FRAMEWORK_SCOPES) "framework" else "app"))
                }
                val framework = scope.filter { it in FRAMEWORK_SCOPES }
                scCol.addView(
                    ui.noteRow(
                        if (framework.isEmpty()) {
                            "No framework entry is scoped, so the device-wide categories cannot work. Managers name it differently — android, system or system_server — and ticking the wrong one does nothing. Tick every one your manager offers."
                        } else {
                            "Scoped as ${framework.joinToString(", ")}. If a device-wide category still does nothing, tick the other framework entries too: managers disagree about which of android / system / system_server actually reaches system_server, and only the right one works. The LSPosed log line \"installed N/M hooks in system_server\" is the proof."
                        }
                    )
                )
            }
        }
        sc.addView(scCol)
        ui.content.addView(sc)
    }

    // ------------------------------------------------------------------ categories

    private fun renderCategories() {
        val master = prefs.getBoolean(Prefs.KEY_MASTER, true)
        val enabled = master && usingRemotePrefs

        ui.content.addView(ui.sectionLabel("Device-wide", allNoneActions(enabled)))
        addCategoryCard(Restrictions.CATEGORIES.filter { it.deviceWide }, enabled)
        ui.content.addView(
            ui.noteRow("These rewrite the answer system_server gives every process, so they work without scoping each app — but they need a framework entry in the module scope — android, system or system_server, depending on what your manager calls it; tick all of them. The device-owner spoof is off by default: it reaches apps you never scoped and can confuse Settings on a device that really is managed.")
        )

        ui.content.addView(ui.sectionLabel("App-facing"))
        addCategoryCard(Restrictions.CATEGORIES.filter { !it.deviceWide }, enabled)
        ui.content.addView(
            ui.noteRow("These change what a scoped app sees when it asks. An app that is not in the module scope is unaffected.")
        )
    }

    private fun allNoneActions(enabled: Boolean): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        alpha = if (enabled) 1f else 0.45f
        addView(ui.textAction("All") { if (enabled) setAll(true) })
        addView(ui.textAction("None") { if (enabled) setAll(false) })
    }

    private fun addCategoryCard(categories: List<Restrictions.Category>, enabled: Boolean) {
        if (categories.isEmpty()) return
        val card = ui.outlinedCard()
        val col = ui.column(4, 4)
        categories.forEachIndexed { index, cat ->
            if (index > 0) col.addView(ui.thinDivider())
            col.addView(
                ui.toggleRow(
                    iconFor(cat.key),
                    cat.title,
                    cat.subtitle,
                    prefs.getBoolean(Prefs.key(cat.key), cat.defaultOn),
                    enabled,
                ) { value ->
                    prefs.edit()?.putBoolean(Prefs.key(cat.key), value)?.apply()
                }
            )
        }
        card.addView(col)
        ui.content.addView(card)
    }

    private fun setAll(value: Boolean) {
        if (!prefs.getBoolean(Prefs.KEY_MASTER, true)) return
        val edit = prefs.edit() ?: return
        Restrictions.CATEGORIES.forEach { edit.putBoolean(Prefs.key(it.key), value) }
        edit.apply()
        render()
    }

    private fun enabledCount(): Int =
        Restrictions.CATEGORIES.count { prefs.getBoolean(Prefs.key(it.key), it.defaultOn) }

    // ------------------------------------------------------------------ diagnostics

    private fun renderDiagnostics() {
        ui.content.addView(ui.sectionLabel("What an app sees right now"))
        val state = ui.outlinedCard()
        val stateCol = ui.column()
        val um = getSystemService(UserManager::class.java)
        var anySet = false
        RESTRICTION_KEYS.forEach { key ->
            val set = runCatching { um?.hasUserRestriction(key) == true }.getOrDefault(false)
            if (set) anySet = true
            stateCol.addView(ui.statRow(key, if (set) "set" else "not set", set))
        }
        val admins = runCatching {
            getSystemService(DevicePolicyManager::class.java)?.activeAdmins?.size ?: 0
        }.getOrDefault(0)
        stateCol.addView(ui.thinDivider())
        stateCol.addView(ui.statRow("active device admins", admins.toString(), admins > 0))
        stateCol.addView(ui.noteRow("The admin count comes from a different path and is not touched by the device-wide hooks."))
        val deviceWideOn = prefs.getBoolean(Prefs.KEY_MASTER, true) && DEVICE_WIDE_KEYS.any {
            prefs.getBoolean(Prefs.key(it), Restrictions.CATEGORY_BY_KEY[it]?.defaultOn ?: true)
        }
        stateCol.addView(
            ui.noteRow(
                "Asked live, the same way any app asks. The answer comes from system_server, which is where the device-wide hooks live — so this is what an app is told, not what your admin set." +
                    if (deviceWideOn) {
                        " With those categories on, a key reads \"not set\" whether it was never set or the bypass is clearing it. For the unfiltered truth: adb shell dumpsys user."
                    } else {
                        " The device-wide categories are off, so nothing is being cleared and these are the set values."
                    }
            )
        )
        state.addView(stateCol)
        ui.content.addView(state)

        ui.content.addView(ui.sectionLabel("Where the hook report is"))
        val where = ui.outlinedCard()
        val whereCol = ui.column()
        whereCol.addView(
            ui.bodyText(
                "In the LSPosed log, not here. The framework's file store for a module is root-owned, so a hooked process cannot write into it — this screen has no way to receive the report."
            )
        )
        whereCol.addView(ui.thinDivider())
        whereCol.addView(ui.statRow("installed N/M hooks in …", "per process", false))
        whereCol.addView(ui.statRow("first hit: <category> …", "when it fires", false))
        whereCol.addView(
            ui.noteRow(
                "Open your manager's log, filter for this module, and look for those two. The first names anything that did not resolve; the second appears the first time a category actually does something. If a category never appears, it never fired — usually a scope that does not reach system_server."
            )
        )
        readReports().forEach { report ->
            whereCol.addView(ui.thinDivider())
            whereCol.addView(ui.infoRow(report.where, report.installed))
            report.rows.forEach { (label, value) ->
                whereCol.addView(ui.statRow(label, value, value == "0" || value.startsWith("missing")))
            }
        }
        where.addView(whereCol)
        ui.content.addView(where)
    }

    private data class Report(val where: String, val installed: String, val rows: List<Pair<String, String>>)

    private fun readReports(): List<Report> {
        val svc = App.service ?: return emptyList()
        val names = runCatching { svc.listRemoteFiles() }.getOrNull() ?: return emptyList()
        return names.filter { it.startsWith(DIAG_PREFIX) && it.endsWith(".txt") }.sorted().mapNotNull { name ->
            val text = runCatching {
                svc.openRemoteFile(name)?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { it.readBytes().decodeToString() }
                }
            }.getOrNull() ?: return@mapNotNull null
            if (text.isBlank()) return@mapNotNull null
            parseReport(name, text)
        }
    }

    private fun parseReport(name: String, text: String): Report {
        val map = LinkedHashMap<String, String>()
        text.lineSequence().forEach { line ->
            val i = line.indexOf('=')
            if (i > 0) map[line.substring(0, i).trim()] = line.substring(i + 1).trim()
        }
        val where = map["where"] ?: name.removePrefix(DIAG_PREFIX).removeSuffix(".txt")
        val installed = map["installed"] ?: "?"
        val rows = map.entries
            .filter { it.key != "where" && it.key != "installed" }
            .map { it.key to it.value }
        return Report(where, installed, rows)
    }

    // ------------------------------------------------------------------ preferences

    private fun resolvePrefs(): SharedPreferences {
        App.service?.let { svc ->
            runCatching { svc.getRemotePreferences(Prefs.NAME) }.getOrNull()?.let { remote ->
                usingRemotePrefs = true
                importLegacyPrefs(remote)
                return remote
            }
        }
        usingRemotePrefs = false
        return getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
    }

    private fun importLegacyPrefs(remote: SharedPreferences) {
        if (remote.getBoolean(Prefs.KEY_IMPORTED, false)) return
        @Suppress("DEPRECATION")
        val legacy = runCatching {
            getSharedPreferences(Prefs.NAME, Context.MODE_WORLD_READABLE)
        }.getOrElse {
            runCatching { getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE) }.getOrNull()
        }
        val edit = remote.edit() ?: return
        legacy?.let {
            for (key in Prefs.allKeys()) {
                if (it.contains(key)) edit.putBoolean(key, it.getBoolean(key, true))
            }
        }
        edit.putBoolean(Prefs.KEY_IMPORTED, true).apply()
    }

    private val moduleScope: List<String>? by lazy {
        runCatching { App.service?.scope }.getOrNull()
    }

    private fun iconFor(key: String): Int = when (key) {
        Restrictions.CAMERA -> R.drawable.ic_camera
        Restrictions.SCREEN_CAPTURE, Restrictions.KEYGUARD, Restrictions.MAX_LOCK -> R.drawable.ic_screen
        Restrictions.ADMIN, Restrictions.OWNER, Restrictions.OWNER_SERVER -> R.drawable.ic_shield
        Restrictions.PASSWORD, Restrictions.ENCRYPTION -> R.drawable.ic_key
        Restrictions.PACKAGE_INSTALL -> R.drawable.ic_install
        Restrictions.DEBUGGING -> R.drawable.ic_debug
        Restrictions.OUTLOOK_ENROLLMENT -> R.drawable.ic_mail
        else -> R.drawable.ic_rules
    }

    private companion object {
        const val DIAG_PREFIX = "diag-"
        val DEVICE_WIDE_KEYS = listOf(Restrictions.PACKAGE_INSTALL, Restrictions.DEBUGGING)
        val RESTRICTION_KEYS = listOf(
            "no_install_unknown_sources",
            "no_install_unknown_sources_globally",
            "no_install_apps",
            "no_uninstall_apps",
            "no_debugging_features",
        )
        val FRAMEWORK_SCOPES = setOf("android", "system", "system_server")
    }
}
