package com.iris.alarm.domain.model

/** A release newer than the installed build, with the APK for this device. */
data class AppUpdate(
    val versionName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val notes: String,
)

/** Where the update flow has got to. Drives the whole settings section. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val fraction: Float) : UpdateState

    /**
     * A release is waiting but Android will not let IRIS hand an APK to the
     * installer until "install unknown apps" is granted. Distinct from [Failed]
     * because the fix is one settings page away and the flow can resume itself
     * on the way back — telling the user to go and find it is not a fix.
     */
    data class NeedsInstallPermission(val update: AppUpdate) : UpdateState

    /** The APK is downloaded; the system installer dialog is next. */
    data class ReadyToInstall(val update: AppUpdate) : UpdateState
    data object Installing : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * Compares dotted version names, ignoring a leading "v" and any suffix such as
 * "-beta". Missing components count as zero, so "0.2" and "0.2.0" are equal.
 */
fun isNewerVersion(candidate: String, installed: String): Boolean {
    fun parts(value: String): List<Int> = value
        .removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { it.trim().toIntOrNull() ?: 0 }

    val a = parts(candidate)
    val b = parts(installed)
    for (i in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrElse(i) { 0 }
        val right = b.getOrElse(i) { 0 }
        if (left != right) return left > right
    }
    return false
}
