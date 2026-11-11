package com.example.modding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

enum class GuideVerificationResult {
    CREATED_NEW,
    UNCHANGED_SIZE_MATCH,
    REPLACED_SIZE_MISMATCH,
    FAILED
}

data class StorageInitializationReport(
    val rootDir: File,
    val gzBeastDir: File,
    val modsDir: File,
    val guideFile: File,
    val gzBeastCreated: Boolean,
    val modsCreated: Boolean,
    val guideResult: GuideVerificationResult,
    val expectedGuideSize: Long,
    val actualGuideSize: Long,
    val discoveredLuaFiles: List<File>
)

object ModStorageManager {

    const val DEFAULT_EMULATED_ROOT_PATH = "/storage/emulated/0"
    const val GZBEAST_FOLDER_NAME = "GZBeast"
    const val MODS_FOLDER_NAME = "mods"
    const val GUIDE_FILE_NAME = "guide.txt"
    const val SESSION_LOG_FILE_NAME = "session.log"

    private val storageLock = Any()

    fun hasStorageAccessPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val writeGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            readGranted && writeGranted
        }
    }

    fun resolveDefaultRootDirectory(): File {
        val explicitRoot = File(DEFAULT_EMULATED_ROOT_PATH)
        if (explicitRoot.exists()) {
            return explicitRoot
        }
        val envRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        if (envRoot != null && envRoot.exists()) {
            return envRoot
        }
        return explicitRoot
    }

    fun initializeAndVerifyStorage(
        customRootDir: File? = null,
        resetLogSession: Boolean = false
    ): StorageInitializationReport = synchronized(storageLock) {
        val rootDir = customRootDir ?: resolveDefaultRootDirectory()
        val gzBeastDir = File(rootDir, GZBEAST_FOLDER_NAME)
        val modsDir = File(gzBeastDir, MODS_FOLDER_NAME)
        val guideFile = File(modsDir, GUIDE_FILE_NAME)

        if (resetLogSession) {
            SessionLogManager.resetSessionForTest(modsDir)
        } else {
            SessionLogManager.ensureSessionStarted(modsDir)
        }

        var gzBeastCreated = false
        if (!gzBeastDir.exists()) {
            gzBeastCreated = gzBeastDir.mkdirs()
            SessionLogManager.logInfo(
                "STORAGE",
                "Created GZBeast directory at ${gzBeastDir.absolutePath} (success=$gzBeastCreated)"
            )
        } else {
            SessionLogManager.logInfo(
                "STORAGE",
                "Verified existing GZBeast directory at ${gzBeastDir.absolutePath}"
            )
        }

        var modsCreated = false
        if (!modsDir.exists()) {
            modsCreated = modsDir.mkdirs()
            SessionLogManager.logInfo(
                "STORAGE",
                "Created mods directory at ${modsDir.absolutePath} (success=$modsCreated)"
            )
        } else {
            SessionLogManager.logInfo(
                "STORAGE",
                "Verified existing mods directory at ${modsDir.absolutePath}"
            )
        }

        val expectedSize = GuideDocumentation.EXPECTED_BYTE_SIZE
        val guideResult = try {
            if (!guideFile.exists()) {
                guideFile.parentFile?.mkdirs()
                guideFile.writeBytes(GuideDocumentation.CANONICAL_GUIDE_BYTES)
                SessionLogManager.logInfo(
                    "GUIDE",
                    "Created guide.txt at ${guideFile.absolutePath} ($expectedSize bytes)"
                )
                GuideVerificationResult.CREATED_NEW
            } else {
                val currentSize = guideFile.length()
                if (currentSize == expectedSize) {
                    SessionLogManager.logInfo(
                        "GUIDE",
                        "Verified guide.txt byte size matches expected $expectedSize bytes; no replacement needed"
                    )
                    GuideVerificationResult.UNCHANGED_SIZE_MATCH
                } else {
                    guideFile.writeBytes(GuideDocumentation.CANONICAL_GUIDE_BYTES)
                    SessionLogManager.logInfo(
                        "GUIDE",
                        "Replaced guide.txt because byte size ($currentSize) differed from expected ($expectedSize)"
                    )
                    GuideVerificationResult.REPLACED_SIZE_MISMATCH
                }
            }
        } catch (t: Throwable) {
            SessionLogManager.logError("GUIDE", "Failed to verify or write guide.txt", t)
            GuideVerificationResult.FAILED
        }

        val finalGuideSize = if (guideFile.exists()) guideFile.length() else 0L
        val luaFiles = discoverLuaScripts(modsDir)
        SessionLogManager.logInfo(
            "MODS",
            "Discovered ${luaFiles.size} Lua mod script(s) in ${modsDir.absolutePath}"
        )

        StorageInitializationReport(
            rootDir = rootDir,
            gzBeastDir = gzBeastDir,
            modsDir = modsDir,
            guideFile = guideFile,
            gzBeastCreated = gzBeastCreated,
            modsCreated = modsCreated,
            guideResult = guideResult,
            expectedGuideSize = expectedSize,
            actualGuideSize = finalGuideSize,
            discoveredLuaFiles = luaFiles
        )
    }

    fun discoverLuaScripts(modsDir: File): List<File> {
        if (!modsDir.exists() || !modsDir.isDirectory) return emptyList()
        val found = ArrayList<File>()
        collectLuaFilesRecursively(modsDir, found)
        found.sortBy { it.relativeTo(modsDir).invariantSeparatorsPath.lowercase() }
        return found
    }

    private fun collectLuaFilesRecursively(dir: File, out: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                collectLuaFilesRecursively(child, out)
            } else if (child.isFile && child.name.endsWith(".lua", ignoreCase = true)) {
                out.add(child)
            }
        }
    }
}
