package com.example.modding

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object SessionLogManager {

    private val entries = ArrayList<String>(512)
    private val lock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val hookInstalled = AtomicBoolean(false)
    private val sessionActive = AtomicBoolean(false)

    @Volatile
    var activeModsDirectory: File = File("/storage/emulated/0/GZBeast/mods")

    fun ensureSessionStarted(modsDir: File? = null) {
        if (modsDir != null) {
            activeModsDirectory = modsDir
        }
        installExitHooksIfNeeded()
        if (sessionActive.compareAndSet(false, true)) {
            synchronized(lock) {
                entries.clear()
            }
            logInfo("SYSTEM", "GZBeast session started. Target mods path: ${activeModsDirectory.absolutePath}")
        }
    }

    fun resetSessionForTest(modsDir: File) {
        activeModsDirectory = modsDir
        sessionActive.set(true)
        installExitHooksIfNeeded()
        synchronized(lock) {
            entries.clear()
        }
        logInfo("SYSTEM", "GZBeast session started. Target mods path: ${activeModsDirectory.absolutePath}")
    }

    fun logInfo(tag: String, message: String) {
        appendEntry("INFO", tag, message)
    }

    fun logWarn(tag: String, message: String) {
        appendEntry("WARN", tag, message)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "$message\n$sw".trimEnd()
        } else {
            message
        }
        appendEntry("ERROR", tag, fullMessage)
    }

    fun logMod(modName: String, message: String) {
        appendEntry("MOD:$modName", "LUA", message)
    }

    private fun appendEntry(level: String, tag: String, message: String) {
        val timestamp = synchronized(dateFormat) {
            dateFormat.format(Date())
        }
        val formatted = "[$timestamp] [$level] [$tag] $message"
        synchronized(lock) {
            entries.add(formatted)
        }
    }

    fun snapshotEntries(): List<String> {
        synchronized(lock) {
            return ArrayList(entries)
        }
    }

    fun flushToDisk(customModsDir: File? = null): Boolean {
        val targetDir = customModsDir ?: activeModsDirectory
        return try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val sessionLogFile = File(targetDir, "session.log")
            val payload = synchronized(lock) {
                val closeTime = synchronized(dateFormat) { dateFormat.format(Date()) }
                val builder = StringBuilder(entries.size * 80 + 128)
                for (line in entries) {
                    builder.append(line).append('\n')
                }
                builder.append("[$closeTime] [INFO] [SYSTEM] Session log flushed on application exit.\n")
                builder.toString()
            }
            sessionLogFile.writeText(payload, Charsets.UTF_8)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun installExitHooksIfNeeded() {
        if (!hookInstalled.compareAndSet(false, true)) return
        try {
            Runtime.getRuntime().addShutdownHook(Thread {
                flushToDisk()
            })
        } catch (_: Throwable) {
        }
        try {
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    logError("CRASH", "Uncaught exception on thread ${thread.name}", throwable)
                    flushToDisk()
                } catch (_: Throwable) {
                } finally {
                    previousHandler?.uncaughtException(thread, throwable)
                }
            }
        } catch (_: Throwable) {
        }
    }
}
