package com.strawing.duckdevicepolicy

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
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
                scope.forEach { scCol.addView(ui.infoRow(it, if (it == "android") "system_server" else "app")) }
                scCol.addView(
                    ui.noteRow(
                        if (scope.contains("android")) {
                            "System Framework is scoped, so the device-wide categories can work. App-facing categories only affect the apps listed above."
                        } else {
                            "System Framework (android) is NOT scoped. The device-wide categories — app install / uninstall and developer options — cannot work without it."
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
            ui.noteRow("These rewrite the answer system_server gives every process, so they work without scoping each app — but they need System Framework (android) in the module scope. The device-owner spoof is off by default: it reaches apps you never scoped and can confuse Settings on a device that really is managed.")
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
        val reports = readReports()
        ui.content.addView(ui.sectionLabel("Hook reports"))
        val card = ui.outlinedCard()
        val col = ui.column()
        if (reports.isEmpty()) {
            col.addView(
                ui.bodyText(
                    "No report yet. A report appears once a scoped process has started with this build — reboot, or force-stop a scoped app, then come back.\n\nEvery report is also written to the LSPosed log, which is the only place it can appear when the framework refuses this module a file inside system_server. Look for \"installed N/M hooks\" and the \"unresolved:\" list next to it."
                )
            )
        } else {
            reports.forEachIndexed { index, report ->
                if (index > 0) col.addView(ui.thinDivider())
                col.addView(ui.infoRow(report.where, report.installed))
                report.rows.forEach { (label, value) ->
                    col.addView(ui.statRow(label, value, value == "0" || value.startsWith("missing")))
                }
            }
        }
        card.addView(col)
        ui.content.addView(card)

        ui.content.addView(ui.sectionLabel("Reporting a problem"))
        val help = ui.outlinedCard()
        val helpCol = ui.column()
        helpCol.addView(
            ui.bodyText(
                "If a restriction is still enforced, the useful facts are: your Android version and ROM, the Xposed framework and its version, whether System Framework (android) is in the scope above, and what this Diagnostics tab shows."
            )
        )
        helpCol.addView(
            ui.noteRow("adb shell dumpsys device_policy — look under userRestrictions: to see which DISALLOW_* keys your admin actually set.")
        )
        helpCol.addView(ui.linkRow("Open the issue tracker") { openUrl(ISSUES_URL) })
        help.addView(helpCol)
        ui.content.addView(help)
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

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
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
        const val ISSUES_URL = "https://github.com/Bouteillepleine/FuckDevicePolicy/issues"
    }
}
