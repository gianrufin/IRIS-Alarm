package com.iris.alarm.data.update

import android.os.Build
import com.iris.alarm.BuildConfig
import com.iris.alarm.domain.model.AppUpdate
import com.iris.alarm.domain.model.ReleaseCandidate
import com.iris.alarm.domain.model.pickAsset
import com.iris.alarm.domain.model.pickUpdate
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Reads the repository's GitHub Releases for a build newer than this one.
 *
 * Deliberately no HTTP or JSON library: one GET of a small document does not
 * justify a dependency, and `HttpURLConnection` plus `org.json` are both in the
 * platform.
 */
@Singleton
class GitHubUpdateSource @Inject constructor() {

    /**
     * Null when there is nothing to install: either the newest release is not
     * newer than this build, or the project has published none at all, which
     * GitHub reports as a 404 and which is not an error to show the user.
     *
     * This reads the *list* of releases rather than `releases/latest`, because
     * that endpoint silently skips pre-releases — and this project publishes
     * pre-releases, so it answered 404 and the app cheerfully reported being up
     * to date while a newer build sat on the releases page.
     */
    suspend fun latestUpdate(): Result<AppUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(RELEASES_URL) ?: return@runCatching null
            val releases = JSONArray(body)

            val candidates = (0 until releases.length())
                .map(releases::getJSONObject)
                .map { release ->
                    val assets = release.optJSONArray("assets")
                    val assetNames = (0 until (assets?.length() ?: 0))
                        .map { assets!!.getJSONObject(it).optString("name") }

                    ReleaseCandidate(
                        tag = release.optString("tag_name")
                            .ifBlank { release.optString("name") },
                        isDraft = release.optBoolean("draft"),
                        assetNames = assetNames,
                        notes = release.optString("body").take(NOTES_LIMIT),
                    )
                }

            val deviceAbis = Build.SUPPORTED_ABIS?.toList().orEmpty()
            val chosen = pickUpdate(candidates, BuildConfig.VERSION_NAME, deviceAbis)
                ?: return@runCatching null
            val assetName = pickAsset(chosen.assetNames, deviceAbis)
                ?: return@runCatching null

            // Re-read the raw asset to get its download URL and size.
            val asset = (0 until releases.length())
                .map(releases::getJSONObject)
                .firstOrNull { it.optString("tag_name") == chosen.tag }
                ?.optJSONArray("assets")
                ?.let { assets ->
                    (0 until assets.length())
                        .map(assets::getJSONObject)
                        .firstOrNull { it.optString("name") == assetName }
                }
                ?: return@runCatching null

            AppUpdate(
                versionName = chosen.tag.removePrefix("v"),
                downloadUrl = asset.optString("browser_download_url"),
                sizeBytes = asset.optLong("size"),
                notes = chosen.notes,
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

    /** Null when GitHub says there is no such release yet. */
    private fun get(url: String): String? {
        val connection = connect(url)
        if (connection.responseCode == HTTP_NOT_FOUND) {
            connection.disconnect()
            return null
        }
        check(connection.responseCode in 200..299) {
            "GitHub returned HTTP ${connection.responseCode}"
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return body
    }

    private fun open(url: String): HttpURLConnection = connect(url).apply {
        check(responseCode in 200..299) { "GitHub returned HTTP $responseCode" }
    }

    private fun connect(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "IRIS-Alarm")
        }

    private companion object {
        // The list endpoint, not releases/latest: that one hides pre-releases.
        // per_page keeps the response small; nobody needs a build ten back.
        const val RELEASES_URL =
            "https://api.github.com/repos/gianrufin/SNAP-WAKE/releases?per_page=10"
        const val TIMEOUT_MILLIS = 20_000
        const val PROGRESS_STEP = 0.01f
        const val NOTES_LIMIT = 500
        const val HTTP_NOT_FOUND = 404
    }
}
