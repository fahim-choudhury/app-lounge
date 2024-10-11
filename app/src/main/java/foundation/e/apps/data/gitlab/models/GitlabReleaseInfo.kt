package foundation.e.apps.data.gitlab.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class GitlabReleaseInfo(
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "name") val releaseName: String,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "released_at") val releasedAt: Instant,
)
