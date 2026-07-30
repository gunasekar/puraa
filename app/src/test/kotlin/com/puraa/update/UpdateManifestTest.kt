package com.puraa.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val sha = "a".repeat(64)

    private fun json(
        versionCode: String = "42",
        versionName: String = "0.3.0",
        apk: String = "https://github.com/gunasekar/puraa/releases/download/v0.3.0/puraa-0.3.0.apk",
        sha256: String = sha,
        extra: String = "",
    ) = """
        {
          "versionCode": $versionCode,
          "versionName": "$versionName",
          "apk": "$apk",
          "sha256": "$sha256"$extra
        }
    """.trimIndent()

    @Test
    fun `parses the manifest CI publishes`() {
        val manifest = UpdateManifest.parse(json(extra = ",\"size\": 9123456, \"notes\": \"Fixes\""))
        assertEquals(42L, manifest.versionCode)
        assertEquals("0.3.0", manifest.versionName)
        assertEquals(
            "https://github.com/gunasekar/puraa/releases/download/v0.3.0/puraa-0.3.0.apk",
            manifest.apk,
        )
        assertEquals(9_123_456L, manifest.size)
        assertEquals("Fixes", manifest.notes)
    }

    @Test
    fun `size and notes are optional`() {
        val manifest = UpdateManifest.parse(json())
        assertNull(manifest.size)
        assertNull(manifest.notes)
    }

    /**
     * The compatibility guarantee that matters: a version already installed on
     * someone's phone must keep being able to read a newer manifest, or it can
     * never update again.
     */
    @Test
    fun `unknown fields are ignored`() {
        val manifest = UpdateManifest.parse(json(extra = ",\"minSdk\": 26, \"channel\": \"beta\""))
        assertEquals(42L, manifest.versionCode)
    }

    @Test
    fun `newer only when the version code increases`() {
        val manifest = UpdateManifest.parse(json(versionCode = "42"))
        assertTrue(manifest.isNewerThan(41))
        assertFalse(manifest.isNewerThan(42))
        assertFalse(manifest.isNewerThan(43))
    }

    @Test
    fun `digest is compared case-insensitively`() {
        val upper = UpdateManifest.parse(json(sha256 = "A".repeat(64)))
        assertEquals("a".repeat(64), upper.expectedSha256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a plain http apk url`() {
        UpdateManifest.parse(json(apk = "http://example.com/puraa.apk"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a truncated digest`() {
        UpdateManifest.parse(json(sha256 = "abc123"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a zero version code`() {
        UpdateManifest.parse(json(versionCode = "0"))
    }
}
