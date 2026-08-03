package com.iris.alarm.domain.model

/** A release as listed by GitHub, reduced to what choosing between them needs. */
data class ReleaseCandidate(
    val tag: String,
    val isDraft: Boolean,
    val assetNames: List<String>,
    val notes: String = "",
)

/**
 * Picks the release to offer, or null when none is newer than [installedVersion].
 *
 * Ordering is worked out here rather than trusting the order GitHub returns, and
 * pre-releases are included on purpose: this project publishes them, and the
 * `releases/latest` endpoint silently skips them — which is exactly how the
 * updater ended up insisting it was up to date while a newer build sat on the
 * releases page.
 */
fun pickUpdate(
    candidates: List<ReleaseCandidate>,
    installedVersion: String,
    deviceAbis: List<String>,
): ReleaseCandidate? {
    val newest = candidates
        .filterNot { it.isDraft }
        .filter { it.tag.isNotBlank() }
        // A release with no APK this device can run is no use, however new it is.
        .filter { pickAsset(it.assetNames, deviceAbis) != null }
        .maxWithOrNull { a, b ->
            when {
                isNewerVersion(a.tag, b.tag) -> 1
                isNewerVersion(b.tag, a.tag) -> -1
                else -> 0
            }
        }
        ?: return null

    return newest.takeIf { isNewerVersion(it.tag, installedVersion) }
}

/**
 * The APK built for this device, preferring an exact ABI match and falling back
 * to the universal build. [deviceAbis] is in the platform's own preference
 * order, so the first match is the best one.
 */
fun pickAsset(assetNames: List<String>, deviceAbis: List<String>): String? {
    val apks = assetNames.filter { it.endsWith(".apk", ignoreCase = true) }
    deviceAbis.forEach { abi ->
        apks.firstOrNull { it.contains(abi, ignoreCase = true) }?.let { return it }
    }
    return apks.firstOrNull { it.contains("universal", ignoreCase = true) }
}
