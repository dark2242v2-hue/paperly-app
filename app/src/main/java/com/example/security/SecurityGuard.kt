package com.example.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import java.security.MessageDigest

private const val TAG = "SecurityGuard"

data class IntegrityReport(
    val isTrusted: Boolean,
    val isSignatureValid: Boolean,
    val isInstallerValid: Boolean,
    val failureReason: String? = null
)

object SecurityGuard {

    private const val GOOGLE_PLAY_INSTALLER = "com.android.vending"

    // Add your Play App Signing SHA-256 fingerprint here (or pass via RELEASE_CERT_SHA256 in CI).
    // Supports comma-separated colon or non-colon SHA-256 hex strings.
    private const val OFFICIAL_RELEASE_CERT_SHA256 = ""

    @Volatile
    private var cachedReport: IntegrityReport? = null

    fun verifyReleaseIntegrity(context: Context): IntegrityReport {
        if (BuildConfig.DEBUG) {
            return IntegrityReport(
                isTrusted = true,
                isSignatureValid = true,
                isInstallerValid = true
            )
        }

        cachedReport?.let { return it }

        val allowedHashes = buildAllowedSha256Set()
        val actualHashes = getSigningCertificateSha256s(context)
        val signatureMatches = allowedHashes.isNotEmpty() && actualHashes.any { it in allowedHashes }

        val installerPackage = getInstallerPackageNameCompat(context)
        val installerMatches = installerPackage == GOOGLE_PLAY_INSTALLER

        val isTrusted = signatureMatches && installerMatches
        val failureReason = when {
            !signatureMatches && !installerMatches ->
                "Unverified APK signature and non-Play installer ($installerPackage)."
            !signatureMatches ->
                "APK signing certificate mismatch."
            !installerMatches ->
                "App was not installed from Google Play Store ($installerPackage)."
            else -> null
        }

        if (!isTrusted) {
            Log.w(TAG, "Integrity verification failed: $failureReason")
        }

        return IntegrityReport(
            isTrusted = isTrusted,
            isSignatureValid = signatureMatches,
            isInstallerValid = installerMatches,
            failureReason = failureReason
        ).also {
            cachedReport = it
        }
    }

    fun isAppTrusted(context: Context): Boolean = verifyReleaseIntegrity(context).isTrusted

    private fun buildAllowedSha256Set(): Set<String> {
        val rawCombined = listOf(OFFICIAL_RELEASE_CERT_SHA256, BuildConfig.RELEASE_CERT_SHA256)
            .joinToString(",")
        return rawCombined
            .split(',', ';', ' ')
            .map { normalizeSha256(it) }
            .filter { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'A'..'F' } }
            .toSet()
    }

    private fun normalizeSha256(input: String): String =
        input.replace(":", "").trim().uppercase()

    @Suppress("DEPRECATION")
    private fun getSigningCertificateSha256s(context: Context): Set<String> {
        return runCatching {
            val pm = context.packageManager
            val packageName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo ?: return emptySet()
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                info.signatures
            } ?: return emptySet()

            val digest = MessageDigest.getInstance("SHA-256")
            signatures.map { sig ->
                digest.reset()
                val hashBytes = digest.digest(sig.toByteArray())
                hashBytes.joinToString("") { byte -> "%02X".format(byte) }
            }.toSet()
        }.getOrElse { error ->
            Log.w(TAG, "Failed to inspect signing certificates", error)
            emptySet()
        }
    }

    @Suppress("DEPRECATION")
    private fun getInstallerPackageNameCompat(context: Context): String? {
        return runCatching {
            val pm = context.packageManager
            val packageName = context.packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                pm.getInstallerPackageName(packageName)
            }
        }.getOrNull()
    }
}
