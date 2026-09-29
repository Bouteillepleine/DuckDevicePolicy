package com.strawing.duckdevicepolicy

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    private val installedCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)
    @Volatile private var where = "?"
    @Volatile private var isSystemServer = false

    private fun bypass(category: String): Boolean {
        val p = prefs ?: return false
        if (!p.getBoolean(Prefs.KEY_MASTER, true)) return false
        return p.getBoolean(Prefs.key(category), defaultFor(category))
    }

    private fun defaultFor(category: String): Boolean =
        Restrictions.CATEGORY_BY_KEY[category]?.defaultOn ?: Prefs.CATEGORY_DEFAULT

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        isSystemServer = param.isSystemServer
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
        val rows = Restrictions.ALL.filter {
            it.pkg == null && Restrictions.isDeviceWide(it) == isSystemServer
        }
        install(rows, classLoader, where)
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
                resolution[spec.key] = "no-class"
                loaderUsed[spec.className.substringAfterLast('.')] = "none"
                continue
            }
            loaderUsed[spec.className.substringAfterLast('.')] = loader
            val params = runCatching {
                Array<Class<*>>(spec.paramTypes.size) { paramClass(spec.paramTypes[it], clazz.classLoader ?: classLoader) }
            }.getOrNull()
            if (params == null) {
                resolution[spec.key] = "no-params"
                continue
            }
            val method = findMethod(clazz, spec.method, params)
            if (method == null) {
                resolution[spec.key] = "no-method"
                continue
            }
            val hooked = runCatching { hook(method).intercept(hookerFor(spec)) }.isSuccess
            resolution[spec.key] = if (hooked) "ok" else "hook-failed"
            if (hooked) installed++
        }
        installedCount.addAndGet(installed)
        totalCount.addAndGet(specs.size)
        val unresolved = specs.mapNotNull { s ->
            resolution[s.key]?.takeIf { it != "ok" }?.let { "${s.category}/${s.id}=$it" }
        }.distinct()
        log(
            if (installed == 0) Log.ERROR else Log.INFO,
            TAG,
            "installed $installed/${specs.size} hooks in $where" +
                if (unresolved.isEmpty()) "" else " | ${unresolved.size} unresolved"
        )
        unresolved.chunked(UNRESOLVED_PER_LINE).forEachIndexed { index, chunk ->
            log(Log.ERROR, TAG, "unresolved in $where [${index + 1}]: ${chunk.joinToString(", ")}")
        }
        logReport()
    }

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

    private fun hookerFor(spec: Restrictions.Spec) = XposedInterface.Hooker { chain ->
        val transform = spec.transform
        if (!bypass(spec.category) || !appliesTo(spec, chain)) {
            chain.proceed()
        } else if (transform == null) {
            countHit(spec)
            spec.result()
        } else {
            val answer = chain.proceed()
            val filtered = transform(answer)
            if (filtered !== answer) countHit(spec)
            filtered
        }
    }

    private fun countHit(spec: Restrictions.Spec) {
        val count = hits.getOrPut(spec.category) { AtomicLong() }.incrementAndGet()
        if (count == 1L) {
            // One line per category for the life of the process. "Did this category ever
            // fire?" is the question a bug report cannot otherwise answer, and a silent
            // zero looks exactly like a category that is working.
            log(Log.INFO, TAG, "first hit: ${spec.category} via ${spec.id} in $where")
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
     * The log, not a file. A hooked process cannot write the module's own remote-file store —
     * it is root-owned, and inside system_server the framework simply says no — so a report
     * written there would be one nobody can read back.
     */
    private fun logReport() {
        val text = buildString {
            append("where=").append(where).append('\n')
            append("installed=").append(installedCount.get()).append('/').append(totalCount.get()).append('\n')
            for ((simpleName, label) in loaderUsed.entries.sortedBy { it.key }) {
                append("loader.").append(simpleName).append('=').append(label).append('\n')
            }
            for (category in Restrictions.CATEGORIES) {
                val rows = Restrictions.ALL.filter {
                    it.category == category.key && resolution.containsKey(it.key)
                }
                if (rows.isEmpty()) continue
                val ok = rows.count { resolution[it.key] == "ok" }
                if (ok == rows.size) continue
                append("rows.").append(category.key).append('=')
                    .append("missing ").append(rows.size - ok).append(" of ").append(rows.size)
                    .append('\n')
            }
        }
        log(Log.INFO, TAG, "report ($where)\n$text")
    }

    private companion object {
        const val TAG = "DuckDevicePolicy"
        const val UNRESOLVED_PER_LINE = 10
    }
}
