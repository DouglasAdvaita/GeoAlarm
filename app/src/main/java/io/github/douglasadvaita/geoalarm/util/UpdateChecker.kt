package io.github.douglasadvaita.geoalarm.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tag: String,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String
)

object UpdateChecker {
    private const val API = "https://api.github.com/repos/DouglasAdvaita/GeoAlarm/releases/latest"

    fun currentCode(ctx: Context): Int = try {
        if (Build.VERSION.SDK_INT >= 28)
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
        else
            @Suppress("DEPRECATION") ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
    } catch (_: Exception) { 1 }

    fun currentName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    suspend fun checkLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val c = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GeoAlarm")
        }
        try {
            if (c.responseCode != 200) throw Exception("GitHub responde ${c.responseCode}")
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name", "")
            val body = json.optString("body", "")
            var apkUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val url = a.optString("browser_download_url", "")
                if (url.endsWith(".apk", true)) { apkUrl = url; break }
            }
            if (apkUrl.isEmpty()) throw Exception("Release $tag sin APK adjunto")
            ReleaseInfo(tag, tagToCode(tag, body), tag.trimStart('v', 'V'), apkUrl, body)
        } finally { c.disconnect() }
    }

    private fun tagToCode(tag: String, body: String): Int {
        Regex("""versionCode\s*[:=]\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        tag.trimStart('v', 'V').substringBefore('.').toIntOrNull()?.let { return it }
        Regex("""(\d+)""").find(tag)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return 0
    }

    suspend fun downloadApk(ctx: Context, url: String, onProgress: (Int) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 30000
                setRequestProperty("User-Agent", "GeoAlarm")
                instanceFollowRedirects = true
            }
            try {
                if (c.responseCode !in 200..299) throw Exception("Descarga HTTP ${c.responseCode}")
                val total = c.contentLength
                val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.cacheDir
                val out = File(dir, "geoalarm-update.apk")
                c.inputStream.use { inp ->
                    FileOutputStream(out).use { o ->
                        val buf = ByteArray(8192)
                        var done = 0L
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(((done * 100) / total).toInt())
                        }
                    }
                }
                out
            } finally { c.disconnect() }
        }

    fun installApk(ctx: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !ctx.packageManager.canRequestPackageInstalls()
        ) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
