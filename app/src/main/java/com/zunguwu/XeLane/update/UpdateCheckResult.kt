package com.zunguwu.XeLane.update

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()

    data class UpdateAvailable(
        val version: String,
        val versionCode: Int?,
        val downloadUrl: String,
        val changelog: String,
        val sha256: String?
    ) : UpdateCheckResult()

    data object MetadataInvalid : UpdateCheckResult()
    data object FetchFailed : UpdateCheckResult()
}
