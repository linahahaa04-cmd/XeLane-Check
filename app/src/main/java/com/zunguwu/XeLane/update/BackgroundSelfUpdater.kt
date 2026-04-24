package com.zunguwu.XeLane.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.core.content.ContextCompat
import com.zunguwu.XeLane.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Fetches remote update info from [BuildConfig.REMOTE_UPDATE_METADATA_URL].
 * Only XeLane JSON manifests are supported.
 */
object BackgroundSelfUpdater {

    private const val TAG = "XeLaneUpdate"
    private const val METADATA_CACHE_FILE = "update_info.txt"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val executor = Executors.newSingleThreadExecutor()

    /** Serial queue for checks and downloads (foreground + background continuation). */
    fun execute(task: Runnable) {
        executor.execute(task)
    }

    private data class RemoteUpdate(
        val version: String,
        val versionCode: Int?,
        val link: String,
        val note: String,
        val sha256: String?
    )

    /** Blocking; do not call on main thread. */
    fun checkForUpdate(context: Context): UpdateCheckResult {
        val metadataFile = File(context.cacheDir, "updates/$METADATA_CACHE_FILE")
        metadataFile.parentFile?.mkdirs()

        if (!fetchMetadataToFile(BuildConfig.REMOTE_UPDATE_METADATA_URL, metadataFile)) {
            metadataFile.delete()
            return UpdateCheckResult.FetchFailed
        }

        try {
            var body = metadataFile.readText()
            if (body.startsWith("\uFEFF")) {
                body = body.removePrefix("\uFEFF")
            }
            val remote = parseRemoteUpdate(body)
            if (remote == null) {
                Log.w(TAG, "update info: invalid format")
                return UpdateCheckResult.MetadataInvalid
            }
            if (!isHttpUrl(remote.link)) {
                Log.w(TAG, "download URL must start with http:// or https://")
                return UpdateCheckResult.MetadataInvalid
            }
            remote.sha256?.let { expected ->
                val apkPath = context.applicationInfo.sourceDir ?: return UpdateCheckResult.FetchFailed
                val installed = File(apkPath)
                if (!installed.isFile) return UpdateCheckResult.FetchFailed
                val localSha256 = computeSha256(installed)
                if (localSha256.equals(expected, ignoreCase = true)) {
                    return UpdateCheckResult.UpToDate
                }
            }

            val localVersionCode = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            }.getOrElse {
                Log.w(TAG, "cannot resolve local versionCode", it)
                return UpdateCheckResult.FetchFailed
            }

            remote.versionCode?.let { remoteCode ->
                return if (remoteCode > localVersionCode) {
                    UpdateCheckResult.UpdateAvailable(
                        version = remote.version,
                        versionCode = remote.versionCode,
                        downloadUrl = remote.link,
                        changelog = resolveChangelog(remote),
                        sha256 = remote.sha256
                    )
                } else {
                    UpdateCheckResult.UpToDate
                }
            }
            return UpdateCheckResult.MetadataInvalid
        } finally {
            if (!metadataFile.delete()) {
                Log.w(TAG, "could not delete metadata cache file")
            }
        }
    }

    private fun parseRemoteUpdate(body: String): RemoteUpdate? {
        return parseMagiskStyleJson(body)
    }

    // XeLane style:
    // { "xelane": { "version": "2.1", "versionCode": 8, "link": "https://...", "note": "..." } }
    private fun parseMagiskStyleJson(body: String): RemoteUpdate? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val payload = root.optJSONObject("xelane") ?: return null

        val link = payload.optString("link")
            .ifBlank { payload.optString("url") }
            .ifBlank { return null }
        val version = payload.optString("version")
        val versionCode = payload.extractPositiveInt("versionCode")
            ?: payload.extractPositiveInt("version_code")
        val note = payload.optString("note")
        val sha256 = payload.optString("sha256")
            .trim()
            .lowercase()
            .takeIf { it.matches(Regex("^[a-f0-9]{64}$")) }

        if (versionCode == null) return null
        return RemoteUpdate(
            version = version,
            versionCode = versionCode,
            link = link.trim(),
            note = note,
            sha256 = sha256
        )
    }

    private fun isHttpUrl(url: String): Boolean {
        val t = url.trim()
        return t.startsWith("https://", ignoreCase = true) || t.startsWith("http://", ignoreCase = true)
    }

    private fun resolveChangelog(remote: RemoteUpdate): String {
        val note = remote.note.trim()
        if (note.isEmpty()) return ""
        if (!isHttpUrl(note)) return note
        return runCatching { fetchString(note) }
            .getOrNull()
            ?.trim()
            .orEmpty()
    }

    /**
     * Blocking download with progress callbacks on the **calling** thread (call from [execute]).
     */
    fun downloadApkWithProgress(
        url: String,
        dest: File,
        onProgress: (bytesRead: Long, contentLength: Long) -> Unit
    ): Boolean =
        try {
            dest.parentFile?.mkdirs()
            val req = Request.Builder().url(url).applyStandardHeaders().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "apk HTTP ${resp.code}")
                    return false
                }
                val contentLength = resp.body.contentLength()
                resp.body.byteStream().use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            total += n.toLong()
                            onProgress(total, contentLength)
                        }
                    }
                }
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "apk download failed", e)
            false
        }

    fun installApk(context: Context, apkFile: File) {
        ensureEnoughStorageForInstall(apkFile)
        installWithSession(context.applicationContext, apkFile)
    }

    fun verifyDownloadedSha256(file: File, expectedSha256: String): Boolean {
        if (!file.isFile) return false
        val actual = computeSha256(file)
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    private fun fetchMetadataToFile(url: String, dest: File): Boolean =
        try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json,text/plain,*/*")
                .applyStandardHeaders()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "metadata HTTP ${resp.code}")
                    return false
                }
                resp.body.byteStream().use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                }
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "metadata fetch failed", e)
            false
        }

    private fun fetchString(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "text/plain,*/*")
            .applyStandardHeaders()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            return resp.body.string()
        }
    }

    private fun installWithSession(context: Context, apkFile: File) {
        val pm = context.packageManager
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
        }

        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        val action = "${context.packageName}.action.PACKAGE_INSTALL_COMMIT_${sessionId}"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                try {
                    context.applicationContext.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                val msg = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    apkFile.delete()
                } else {
                    Log.w(TAG, "install status=$status msg=$msg")
                    apkFile.delete()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.applicationContext.registerReceiver(
                receiver,
                IntentFilter(action),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            sessionId,
            Intent(action).setPackage(context.packageName),
            pendingIntentFlags
        )

        try {
            val size = apkFile.length()
            session.openWrite("base.apk", 0, size).use { out ->
                FileInputStream(apkFile).use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            session.commit(pendingIntent.intentSender)
        } catch (e: Exception) {
            try {
                session.abandon()
            } catch (_: Exception) {
            }
            try {
                context.applicationContext.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun Request.Builder.applyStandardHeaders(): Request.Builder {
        header(
            "User-Agent",
            "XeLane/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"
        )
        return this
    }

    private fun JSONObject.extractPositiveInt(key: String): Int? {
        if (!has(key)) return null
        val asInt = optInt(key, -1)
        if (asInt > 0) return asInt
        return optString(key).toIntOrNull()?.takeIf { it > 0 }
    }

    private fun ensureEnoughStorageForInstall(apkFile: File) {
        val statFs = StatFs(Environment.getDataDirectory().absolutePath)
        val available = statFs.availableBytes
        val required = (apkFile.length() * 2L) + (20L * 1024L * 1024L)
        if (available < required) {
            throw IllegalStateException("INSUFFICIENT_STORAGE")
        }
    }

    private fun computeSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }
}
