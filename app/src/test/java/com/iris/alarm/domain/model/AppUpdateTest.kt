package com.iris.alarm.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    @Test
    fun `a higher patch, minor or major version is newer`() {
        assertTrue(isNewerVersion("0.2.1", "0.2.0"))
        assertTrue(isNewerVersion("0.3.0", "0.2.9"))
        assertTrue(isNewerVersion("1.0.0", "0.99.99"))
    }

    @Test
    fun `the same version is not an update`() {
        assertFalse(isNewerVersion("0.2.0", "0.2.0"))
        // Downgrades must never be offered: Android would reject the install.
        assertFalse(isNewerVersion("0.1.9", "0.2.0"))
    }

    @Test
    fun `a leading v is ignored`() {
        assertTrue(isNewerVersion("v0.3.0", "0.2.0"))
        assertFalse(isNewerVersion("v0.2.0", "0.2.0"))
    }

    @Test
    fun `missing components count as zero`() {
        assertFalse(isNewerVersion("0.2", "0.2.0"))
        assertTrue(isNewerVersion("0.2.1", "0.2"))
    }

    @Test
    fun `pre-release suffixes are ignored rather than crashing`() {
        assertTrue(isNewerVersion("0.3.0-beta1", "0.2.0"))
        assertFalse(isNewerVersion("0.2.0-beta1", "0.2.0"))
    }

    @Test
    fun `a version with numbers we cannot parse does not throw`() {
        assertFalse(isNewerVersion("nightly", "0.2.0"))
        assertTrue(isNewerVersion("1.x", "0.2.0"))
    }

    @Test
    fun `double digit components compare numerically, not alphabetically`() {
        assertTrue(isNewerVersion("0.10.0", "0.9.0"))
        assertFalse(isNewerVersion("0.9.0", "0.10.0"))
    }
}
