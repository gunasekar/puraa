package com.puraa.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The `update.json` asset CI attaches to every GitHub Release, fetched from
 * the `releases/latest/download/` permalink (see [Updater]).
 *
 * Why a manifest instead of reading the release's tag: [versionCode] is the
 * same monotonic commit count the build stamps into the APK (see
 * `app/build.gradle.kts`), so "is there something newer?" is one integer
 * comparison — no tag-string parsing and no SemVer edge cases.
 *
 * Unknown keys are ignored on purpose: a future Puraa can add fields to
 * `update.json` without the manifest becoming unreadable to versions already
 * in the field — which would strand them with no way to update.
 */
@Serializable
data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apk: String,
    val sha256: String,
    /** APK size in bytes, when known — lets the installer size its session. */
    val size: Long? = null,
    val notes: String? = null,
) {
    init {
        // Constructed only from remote JSON, so validate here rather than at
        // every use: an UpdateManifest that exists is one worth acting on.
        // [parse] wraps this, so a malformed manifest surfaces as a failed
        // check, not a crash.
        require(versionCode > 0) { "versionCode must be positive, was $versionCode" }
        require(versionName.isNotBlank()) { "versionName must not be blank" }
        require(SHA256.matches(sha256)) { "sha256 must be 64 hex chars" }
        // Plain http would let anything on the path swap the bytes. The digest
        // below still guards the APK itself, but the digest arrives in this
        // same document — so the document's own transport has to be trusted.
        require(apk.startsWith("https://")) { "apk must be an https URL" }
    }

    /** [sha256] case-folded once, ready to compare against a computed digest. */
    val expectedSha256: String = sha256.lowercase()

    /** True if this release is newer than the [installed] version code. */
    fun isNewerThan(installed: Long): Boolean = versionCode > installed

    companion object {
        private val SHA256 = Regex("[0-9a-fA-F]{64}")

        private val json = Json { ignoreUnknownKeys = true }

        /** Decodes and validates raw `update.json`. Throws on malformed input. */
        fun parse(raw: String): UpdateManifest = json.decodeFromString(serializer(), raw)
    }
}
