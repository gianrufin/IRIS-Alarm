package com.iris.alarm.data.update

import android.os.Build
import com.iris.alarm.BuildConfig
import com.iris.alarm.domain.model.AppUpdate
import com.iris.alarm.domain.model.isNewerVersion
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Reads the repository's GitHub Releases for a build newer than this one.
 *
 * Deliberately no HTTP or JSON library: one GET of a small document does not
 * justify a dependency, and `HttpURLConnection` plus `org.json` are both in the
 * platform.
 */
@Singleton
class GitHubUpdateSource @Inject constructor() {

    /** Null when the newest release is not newer than the installed build. */
    suspend fun latestUpdate(): Result<AppUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(get(LATEST_RELEASE_URL))
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            if (tag.isBlank()) return@runCatching null
            if (!isNewerVersion(tag, BuildConfig.VERSION_NAME)) return@runCatching null

            val assets = json.optJSONArray("assets") ?: return@runCatching null
            val candidates = (0 until assets.length()).map(assets::getJSONObject)
            val asset = candidates.firstOrNull { it.matchesDeviceAbi() }
                ?: candidates.firstOrNull { it.optString("name").contains(UNIVERSAL) }
                ?: return@runCatching null

            AppUpdate(
                versionName = tag.removePrefix("v"),
                downloadUrl = asset.optString("browser_download_url"),
                sizeBytes = asset.optLong("size"),
                notes = json.optString("body").take(NOTES_LIMIT),
            ).takeIf { it.downloadUrl.isNotBlank() }
        }
    }

    /**
     * Streams the APK to [destination], reporting 0f..1f as it goes.
     *
     * Everything is written to a temporary file and moved into place only once
     * the whole body has arrived, so a connection dropped halfway can never leave
     * a truncated APK that the installer would reject.
     */
    suspend fun download(
        update: AppUpdate,
        destination: File,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val partial = File(destination.parentFile, destination.name + ".part")
            partial.delete()

            val connection = open(update.downloadUrl)
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val total = connection.contentLengthLong.takeIf { it > 0 }
                        ?: update.sizeBytes
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    var lastReported = 0f

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read

                        if (total > 0) {
                            val fraction = (written.toFloat() / total).coerceIn(0f, 1f)
                            // Reporting every chunk would spam the UI thread.
                            if (fraction - lastReported >= PROGRESS_STEP) {
                                lastReported = fraction
                                onProgress(fraction)
                            }
                        }
                    }
                }
            }
            connection.disconnect()

            destination.delete()
            check(partial.renameTo(destination)) { "Could not move the downloaded APK into place" }
            onProgress(1f)
            destination
        }
    }

    private fun get(url: String): String = open(url).run {
        val body = inputStream.bufferedReader().use { it.readText() }
        disconnect()
        body
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "IRIS-Alarm")
            val code = responseCode
            check(code in 200..299) { "GitHub returned HTTP $code" }
        }

    /** Matches the release asset built for this device's primary ABI. */
    private fun JSONObject.matchesDeviceAbi(): Boolean {
        val name = optString("name")
        if (!name.endsWith(".apk")) return false
        return Build.SUPPORTED_ABIS.any { abi -> name.contains(abi) }
    }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/gianrufin/snap-wake/releases/latest"
        const val UNIVERSAL = "universal"
        const val TIMEOUT_MILLIS = 20_000
        const val PROGRESS_STEP = 0.01f
        const val NOTES_LIMIT = 500
    }
}
