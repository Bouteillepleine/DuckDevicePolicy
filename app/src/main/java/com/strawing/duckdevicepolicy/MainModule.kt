package com.strawing.duckdevicepolicy

import android.content.SharedPreferences
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.io.FileOutputStream
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MainModule : XposedModule() {

    private val prefs: SharedPreferences? by lazy {
        runCatching { getRemotePreferences(Prefs.NAME) }
            .onFailure { log(Log.ERROR, TAG, "remote preferences unavailable; using defaults", it) }
            .getOrNull()
    }

    private val frameworkHooked = AtomicBoolean(false)
    private val appHooked: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private val hits = ConcurrentHashMap<String, AtomicLong>()
    private val resolution = ConcurrentHashMap<String, String>()
    private val loaderUsed = ConcurrentHashMap<String, String>()
    private var installedCount = 0
    private var totalCount = 0
    private var where = "?"
    private var isSystemServer = false
    private val lastFlush = AtomicLong(0)
    private val writer by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "duck-diag").apply { isDaemon = true } }
    }

    private fun bypass(category: String): Boolean {
        val p = prefs ?: return defaultFor(category)
        if (!p.getBoolean(Prefs.KEY_MASTER, true)) return false
        return p.getBoolean(Prefs.key(category), defaultFor(category))
    }

    private fun defaultFor(category: String): Boolean =
        Restrictions.CATEGORY_BY_KEY[category]?.defaultOn ?: Prefs.CATEGORY_DEFAULT

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "loaded proc=${param.processName} systemServer=${param.isSystemServer} " +
            "framework=$frameworkName($frameworkVersionCode) API $apiVersion")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        isSystemServer = true
        installFrameworkHooks(param.classLoader, "system_server")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        installFrameworkHooks(param.classLoader, param.packageName)
        val appSpecs = Restrictions.ALL.filter { it.pkg == param.packageName }
        if (appSpecs.isNotEmpty() && appHooked.add(param.packageName)) {
            install(appSpecs, param.classLoader, param.packageName)
        }
    }

    private fun installFrameworkHooks(classLoader: ClassLoader, where: String) {
        if (!frameworkHooked.compareAndSet(false, true)) return
        this.where = where
        install(Restrictions.ALL.filter { it.pkg == null }, classLoader, where)
    }

    /**
     * The class loader handed to an entry point is not always the one that can see the class a
     * row names. `android.app.admin.DevicePolicyManager` lives on the boot class path, but
     * `com.android.server.pm.UserManagerService` lives in services.jar, which only the system
     * server's own loader has. A row that resolved through one loader and not another is the
     * single most useful fact when a category silently does nothing, so record which one won.
     */
    private fun resolveClass(name: String, classLoader: ClassLoader): Pair<Class<*>?, String> {
        val candidates = linkedMapOf<String, ClassLoader?>(
            "param" to classLoader,
            "system" to runCatching { ClassLoader.getSystemClassLoader() }.getOrNull(),
            "self" to javaClass.classLoader,
        )
        for ((label, loader) in candidates) {
            if (loader == null) continue
            val clazz = runCatching { Class.forName(name, false, loader) }.getOrNull()
            if (clazz != null) return clazz to label
        }
        return null to "none"
    }

    private fun install(specs: List<Restrictions.Spec>, classLoader: ClassLoader, where: String) {
        var installed = 0
        for (spec in specs) {
            val (clazz, loader) = resolveClass(spec.className, classLoader)
            if (clazz == null) {
                resolution[spec.id] = "no-class"
                loaderUsed[spec.className.substringAfterLast('.')] = "none"
                continue
            }
            loaderUsed[spec.className.substringAfterLast('.')] = loader
            val params = runCatching {
                Array<Class<*>>(spec.paramTypes.size) { paramClass(spec.paramTypes[it], clazz.classLoader ?: classLoader) }
            }.getOrNull()
            if (params == null) {
                resolution[spec.id] = "no-params"
                continue
            }
            val method = findMethod(clazz, spec.method, params)
            if (method == null) {
                resolution[spec.id] = "no-method"
                continue
            }
            val hooked = runCatching { hook(method).intercept(hookerFor(spec)) }.isSuccess
            resolution[spec.id] = if (hooked) "ok" else "hook-failed"
            if (hooked) installed++
        }
        installedCount += installed
        totalCount += specs.size
        log(
            if (installed == 0) Log.ERROR else Log.INFO,
            TAG,
            "installed $installed/${specs.size} hooks in $where" +
                summariseFailures(specs).let { if (it.isEmpty()) "" else " | unresolved: $it" }
        )
        flush(force = true)
    }

    private fun summariseFailures(specs: List<Restrictions.Spec>): String =
        specs.mapNotNull { s -> resolution[s.id]?.takeIf { it != "ok" }?.let { "${s.id}=$it" } }
            .distinct()
            .joinToString(", ")
            .take(400)

    private fun paramClass(name: String, classLoader: ClassLoader): Class<*> = when (name) {
        "int" -> Integer.TYPE
        "long" -> java.lang.Long.TYPE
        "boolean" -> java.lang.Boolean.TYPE
        else -> Class.forName(name, false, classLoader)
    }

    private fun findMethod(clazz: Class<*>, name: String, params: Array<Class<*>>): Method? {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, *params)
            } catch (_: NoSuchMethodException) {
                c = c.superclass
            }
        }
        return null
    }

    /**
     * Ownership answers are for apps asking, never for the framework asking itself.
     * `DevicePolicyManagerService` and `ConnectivityService` both read device-owner state on the
     * boot path and throw if it contradicts what they already hold; a spoofed answer there took
     * system_server down in a loop at PHASE_LOCK_SETTINGS_READY. Inside system_server a call
     * that is not serving a binder transaction is the framework's own, and gets the truth.
     */
    private fun servingRemoteCaller(): Boolean =
        runCatching { Binder.getCallingPid() != Process.myPid() }.getOrDefault(true)

    private fun hookerFor(spec: Restrictions.Spec) = XposedInterface.Hooker { chain ->
        val ownershipInFramework =
            spec.category in OWNERSHIP_CATEGORIES && isSystemServer && !servingRemoteCaller()
        if (!ownershipInFramework && bypass(spec.category) && appliesTo(spec, chain)) {
            val count = hits.getOrPut(spec.category) { AtomicLong() }.incrementAndGet()
            if (count == 1L) {
                // One line per category for the life of the process. "Did this category ever
                // fire?" is the question a bug report cannot otherwise answer, and a silent
                // zero looks exactly like a category that is working.
                log(Log.INFO, TAG, "first hit: ${spec.category} via ${spec.id} in $where")
            }
            flush(force = false)
            spec.result()
        } else {
            chain.proceed()
        }
    }

    /**
     * Matches on any String argument rather than a fixed index. The restriction key sits at a
     * different position depending on the overload, and a framework is free to present the
     * argument list differently again; a key set this specific cannot collide with an unrelated
     * argument, so scanning is both safer and more portable than trusting one index.
     */
    private fun appliesTo(spec: Restrictions.Spec, chain: XposedInterface.Chain): Boolean {
        val keys = spec.keys ?: return true
        val byIndex = runCatching { chain.getArg(spec.keyArg) }.getOrNull()
        if (byIndex is String && byIndex in keys) return true
        val args = runCatching { chain.args }.getOrNull() ?: return false
        return args.any { it is String && it in keys }
    }

    // ------------------------------------------------------------------ diagnostics

    /**
     * The hook side cannot write the remote preferences — they are read-only there — so the
     * report travels through a remote file instead, which the UI opens with the same name.
     * Never on the calling thread: these hooks run inside system_server, on the path that is
     * answering a binder call.
     */
    private fun flush(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastFlush.get()
        if (!force && now - previous < FLUSH_INTERVAL_MS) return
        if (!lastFlush.compareAndSet(previous, now)) return
        runCatching { writer.execute { writeReport(force) } }
    }

    private fun writeReport(logIt: Boolean) {
        val text = buildString {
            append("where=").append(where).append('\n')
            append("installed=").append(installedCount).append('/').append(totalCount).append('\n')
            append("time=").append(System.currentTimeMillis()).append('\n')
            for ((simpleName, label) in loaderUsed.entries.sortedBy { it.key }) {
                append("loader.").append(simpleName).append('=').append(label).append('\n')
            }
            for (category in Restrictions.CATEGORIES) {
                val rows = Restrictions.ALL.filter { it.category == category.key }
                if (rows.isEmpty()) continue
                val ok = rows.count { resolution[it.id] == "ok" }
                if (ok == rows.size) continue
                append("rows.").append(category.key).append('=')
                    .append("missing ").append(rows.size - ok).append(" of ").append(rows.size)
                    .append('\n')
            }
            for ((category, count) in hits.entries.sortedBy { it.key }) {
                append("hits.").append(category).append('=').append(count.get()).append('\n')
            }
        }
        val pfd: ParcelFileDescriptor? =
            runCatching { openRemoteFile("diag-$where.txt") as ParcelFileDescriptor? }.getOrNull()
        if (pfd == null) {
            // The framework only hands out a descriptor where the module's own remote-file
            // store is reachable; inside system_server it can simply say no. The report is
            // the point, not the file, so fall back to the log the user can already read.
            if (logIt) log(Log.INFO, TAG, "report ($where)\n$text")
            return
        }
        runCatching { pfd.use { writeAll(it, text) } }
            .onFailure { if (logIt) log(Log.INFO, TAG, "report ($where)\n$text") }
    }

    private fun writeAll(pfd: ParcelFileDescriptor, text: String) {
        FileOutputStream(pfd.fileDescriptor).use { out ->
            out.channel.truncate(0)
            out.channel.position(0)
            out.write(text.toByteArray())
            out.flush()
        }
    }

    private companion object {
        const val TAG = "DuckDevicePolicy"
        val OWNERSHIP_CATEGORIES = setOf(
            Restrictions.ADMIN,
            Restrictions.OWNER,
            Restrictions.OWNER_SERVER,
        )
        const val FLUSH_INTERVAL_MS = 5000L
    }
}
