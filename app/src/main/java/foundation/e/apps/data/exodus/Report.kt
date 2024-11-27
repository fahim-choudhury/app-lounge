package foundation.e.apps.data.exodus

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    val results: List<Report>
)

data class Report(
    val id: Int,
    val handle: String,
    val name: String,
    val creator: String,
    val downloads: String,
    @SerializedName("app_uid") val appUid: String,
    @SerializedName("icon_phash") val iconPhash: String,
    @SerializedName("report_updated_at") val reportUpdatedAt: Double,
    @SerializedName("permissions_count") val permissionsCount: Int,
    @SerializedName("trackers_count") val trackersCount: Int,
    @SerializedName("permissions_class") val permissionsClass: String,
    @SerializedName("trackers_class") val trackersClass: String,
    val version: String
)

