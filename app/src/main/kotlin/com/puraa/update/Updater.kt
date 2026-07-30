package com.puraa.update

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.puraa.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Puraa's self-updater: fetch the manifest, compare version codes, stream the
 * APK into a [PackageInstaller] session, verify its SHA-256, commit.
 *
 * Puraa is sideloaded — there is no store to push updates — so the app checks
 * a manifest attached to its own GitHub Releases. Nothing here trusts the
 * network:
 *
 *  - the manifest is fetched over HTTPS and must name an HTTPS APK URL,
 *  - the APK's SHA-256 must match the digest in the manifest, and
 *  - Android itself rejects any APK not signed by Puraa's release key
 *    (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), which is the real anchor: a
 *    tampered manifest can waste a download, never install foreign code.
 *
 * Driven only from the UI ([com.puraa.ui.UpdateDialog]) — there is no background
 * updater, by design. See ARCHITECTURE.md §13 for why.
 *
 * **The first self-update always shows Android's confirmation dialog.** Whoever
 * installed Puraa (adb, a file manager, Obtainium) is the installer of record,
 * and Android won't let a different installer replace an app silently. Once
 * Puraa has installed itself once it owns the package, and later updates skip
 * that dialog — the user's "Update now" tap is then the only interaction. On
 * Android 11 and older no dialog-free path exists at all, so the confirmation
 * appears every time.
 */
class Updater(
    private val context: Context,
    private val http: OkHttpClient = defaultHttp,
) {

    sealed interface CheckResult {
        data object UpToDate : CheckResult
        data class Available(val manifest: UpdateManifest) : CheckResult

        /** [transient] failures are worth retrying — bad JSON is not. */
        data class Failed(val reason: String, val transient: Boolean) : CheckResult
    }

    sealed interface InstallResult {
        /**
         * The session is committed and the platform has taken over: it will
         * either install silently or raise a confirmation, and report back to
         * [InstallResultReceiver]. Success is no longer ours to observe — a
         * successful install replaces this process.
         */
        data object Committed : InstallResult
        data class Failed(val reason: String, val transient: Boolean) : InstallResult
    }

    /** The running build's version code — the commit count CI stamped in. */
    val installedVersionCode: Long
        get() = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(info)
        }.getOrDefault(BuildConfig.VERSION_CODE.toLong())

    val installedVersionName: String get() = BuildConfig.VERSION_NAME

    /**
     * Whether the user has allowed Puraa to install apps ("Install unknown
     * apps" → Puraa). `REQUEST_INSTALL_PACKAGES` in the manifest is only half
     * the story; this app op is the other half.
     *
     * Used to *hint*, never to gate: an app updating its own package may be
     * exempt from this check, and refusing to try would then block a flow that
     * would have worked. So the update is always attempted, and the hint (plus
     * a shortcut to the settings page) appears only when the op is missing.
     */
    val canInstallPackages: Boolean
        get() = runCatching { context.packageManager.canRequestPackageInstalls() }
            .getOrDefault(false)

    /** Fetch `update.json` and compare it against what's installed. */
    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        val raw = try {
            val request = Request.Builder().url(BuildConfig.UPDATE_MANIFEST_URL).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 404 included: a repo with no release yet is not an error
                    // worth retrying hard, but it costs nothing to look again
                    // on the next period.
                    return@withContext CheckResult.Failed(
                        "manifest HTTP ${response.code}",
                        transient = true,
                    )
                }
                response.body?.string().orEmpty()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            return@withContext CheckResult.Failed(t.describe(), transient = true)
        }

        val manifest = try {
            UpdateManifest.parse(raw)
        } catch (t: Throwable) {
            // Malformed or dishonest manifest — retrying re-reads the same
            // bytes, so give up until it's republished.
            return@withContext CheckResult.Failed("bad update.json: ${t.describe()}", transient = false)
        }

        val installed = installedVersionCode
        if (manifest.isNewerThan(installed)) {
            Log.i(TAG, "Update available: ${manifest.versionName} (${manifest.versionCode} > $installed)")
            CheckResult.Available(manifest)
        } else {
            Log.i(TAG, "Up to date at $installedVersionName ($installed)")
            CheckResult.UpToDate
        }
    }

    /**
     * Download [manifest]'s APK straight into an install session, verify its
     * digest, and commit.
     *
     * The APK is streamed into the session rather than to a temp file and
     * copied: one pass, no cache file to clean up, and the digest is computed
     * from the very bytes that were written. A session is only staging —
     * [PackageInstaller.Session.commit] is the single irreversible step, and a
     * digest mismatch abandons the session well before that.
     */
    suspend fun downloadAndInstall(manifest: UpdateManifest): InstallResult = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        var committed = false
        try {
            // Discard anything left over from an attempt the user walked away
            // from. Sessions persist, and without this they accumulate one per
            // abandoned update — each holding a staged copy of the APK.
            installer.discardOwnSessions()
            sessionId = installer.createSession(sessionParams())
            installer.openSession(sessionId).use { session ->
                val digest = streamApkInto(session, manifest)
                if (digest != manifest.expectedSha256) {
                    throw DigestMismatch("expected ${manifest.expectedSha256}, downloaded $digest")
                }
                Log.i(TAG, "Verified ${manifest.versionName}; committing session $sessionId")
                session.commit(InstallResultReceiver.intentSender(context, manifest.versionName))
                committed = true
            }
            InstallResult.Committed
        } catch (ce: CancellationException) {
            if (sessionId != -1 && !committed) installer.abandonQuietly(sessionId)
            throw ce
        } catch (mismatch: DigestMismatch) {
            installer.abandonQuietly(sessionId)
            Log.e(TAG, "SHA-256 mismatch for ${manifest.versionName}: ${mismatch.message}")
            InstallResult.Failed("APK failed its checksum", transient = false)
        } catch (t: Throwable) {
            if (!committed) installer.abandonQuietly(sessionId)
            Log.w(TAG, "Install of ${manifest.versionName} failed: ${t.describe()}")
            InstallResult.Failed(t.describe(), transient = true)
        }
    }

    private fun sessionParams() =
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Honoured only once Puraa is its own installer of record; the
                // first update prompts regardless.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14 update ownership. On 14+ silent updates require
                // the installer to *own* the package, not merely to have
                // installed it, so claim ownership on the way through the
                // first (already-prompted) install. Puraa can never ship on
                // Play — store policy reserves SMS permissions for default SMS
                // handlers — so there is no other updater to lock out.
                setRequestUpdateOwnership(true)
            }
        }

    /**
     * Copy the APK from the network into [session], returning its lowercase
     * hex SHA-256. Throws [IOException] on any transport failure.
     */
    private fun streamApkInto(session: PackageInstaller.Session, manifest: UpdateManifest): String {
        val request = Request.Builder().url(manifest.apk).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("APK HTTP ${response.code}")
            val body = response.body ?: throw IOException("APK response had no body")
            val declaredSize = manifest.size
                ?: body.contentLength().takeIf { it > 0L }
                ?: -1L // openWrite accepts -1 for "length unknown"
            val digest = MessageDigest.getInstance("SHA-256")
            session.openWrite(APK_SESSION_NAME, 0, declaredSize).use { out ->
                val buffer = ByteArray(BUFFER_BYTES)
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                    }
                }
                session.fsync(out)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private class DigestMismatch(message: String) : IOException(message)

    private companion object {
        const val TAG = "Updater"
        const val APK_SESSION_NAME = "puraa.apk"
        const val BUFFER_BYTES = 64 * 1024

        val defaultHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Generous: this read spans the whole APK download, not one call.
            .readTimeout(5, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()

        fun PackageInstaller.abandonQuietly(sessionId: Int) {
            if (sessionId == -1) return
            runCatching { abandonSession(sessionId) }
        }

        /** Abandon every session this app still owns. Best-effort. */
        fun PackageInstaller.discardOwnSessions() {
            runCatching {
                mySessions.forEach { runCatching { abandonSession(it.sessionId) } }
            }
        }

        fun Throwable.describe(): String = message ?: this::class.java.simpleName
    }
}
