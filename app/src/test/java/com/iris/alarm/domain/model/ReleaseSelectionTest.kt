package com.iris.alarm.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseSelectionTest {

    private val arm64 = listOf("arm64-v8a", "armeabi-v7a")

    private fun release(tag: String, draft: Boolean = false) = ReleaseCandidate(
        tag = tag,
        isDraft = draft,
        assetNames = listOf(
            "iris-alarm-${tag.removePrefix("v")}-arm64-v8a.apk",
            "iris-alarm-${tag.removePrefix("v")}-universal.apk",
        ),
    )

    @Test
    fun `a pre-release is still an update`() {
        // The bug this exists to prevent: every IRIS release is published as a
        // pre-release, and releases/latest hides those, so the app reported being
        // up to date while a newer build sat on the releases page.
        val candidates = listOf(release("v0.4.0"), release("v0.3.0"))

        val picked = pickUpdate(candidates, installedVersion = "0.3.0", deviceAbis = arm64)

        assertEquals("v0.4.0", picked?.tag)
    }

    @Test
    fun `the newest is chosen regardless of the order they arrive in`() {
        val candidates = listOf(release("v0.2.0"), release("v0.10.0"), release("v0.4.0"))

        val picked = pickUpdate(candidates, installedVersion = "0.3.0", deviceAbis = arm64)

        assertEquals("v0.10.0", picked?.tag)
    }

    @Test
    fun `nothing is offered when the installed build is the newest`() {
        val candidates = listOf(release("v0.4.0"), release("v0.3.0"))

        assertNull(pickUpdate(candidates, installedVersion = "0.4.0", deviceAbis = arm64))
        // And a build ahead of every release is never asked to downgrade.
        assertNull(pickUpdate(candidates, installedVersion = "0.5.0", deviceAbis = arm64))
    }

    @Test
    fun `drafts are ignored`() {
        val candidates = listOf(release("v0.9.0", draft = true), release("v0.4.0"))

        val picked = pickUpdate(candidates, installedVersion = "0.3.0", deviceAbis = arm64)

        assertEquals("v0.4.0", picked?.tag)
    }

    @Test
    fun `a release with nothing this device can install is skipped`() {
        val armOnly = ReleaseCandidate(
            tag = "v0.9.0",
            isDraft = false,
            assetNames = listOf("iris-alarm-0.9.0-armeabi-v7a.apk"),
        )
        val usable = release("v0.4.0")

        val picked = pickUpdate(
            listOf(armOnly, usable),
            installedVersion = "0.3.0",
            deviceAbis = listOf("x86_64"),
        )

        // v0.9.0 has no x86_64 or universal build, so the usable one wins.
        assertEquals("v0.4.0", picked?.tag)
    }

    @Test
    fun `no releases at all offers nothing`() {
        assertNull(pickUpdate(emptyList(), installedVersion = "0.3.0", deviceAbis = arm64))
    }

    @Test
    fun `the asset matching the device ABI is preferred over universal`() {
        val assets = listOf(
            "iris-alarm-0.4.0-universal.apk",
            "iris-alarm-0.4.0-arm64-v8a.apk",
            "iris-alarm-0.4.0-x86_64.apk",
        )

        assertEquals("iris-alarm-0.4.0-arm64-v8a.apk", pickAsset(assets, arm64))
    }

    @Test
    fun `the device's own ABI order decides which match wins`() {
        val assets = listOf(
            "iris-alarm-0.4.0-arm64-v8a.apk",
            "iris-alarm-0.4.0-armeabi-v7a.apk",
        )

        // A 64-bit phone lists arm64 first and must not be handed the 32-bit build.
        assertEquals("iris-alarm-0.4.0-arm64-v8a.apk", pickAsset(assets, arm64))
        assertEquals(
            "iris-alarm-0.4.0-armeabi-v7a.apk",
            pickAsset(assets, listOf("armeabi-v7a")),
        )
    }

    @Test
    fun `universal is the fallback when no ABI matches`() {
        val assets = listOf("iris-alarm-0.4.0-universal.apk", "iris-alarm-0.4.0-arm64-v8a.apk")

        assertEquals("iris-alarm-0.4.0-universal.apk", pickAsset(assets, listOf("riscv64")))
    }

    @Test
    fun `non-apk assets are never offered as a download`() {
        val assets = listOf("checksums.txt", "mapping.txt")

        assertNull(pickAsset(assets, arm64))
    }
}
