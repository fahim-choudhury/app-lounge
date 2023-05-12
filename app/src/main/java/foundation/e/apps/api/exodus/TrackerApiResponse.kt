package foundation.e.apps.api.exodus

import com.squareup.moshi.Json

data class TrackerInfo(
    val name: String,
    val creator: String,
    val reports: List<Report>
)

data class Report(
    val report: Long = -1L,
    @Json(name = "updated") val updatedAt: String,
    @Json(name = "version_name") val version: String,
    @Json(name = "version_code") val versionCode: String,
    val source: String,
    val trackers: List<Long>,
    val permissions: List<String> = listOf()
)
