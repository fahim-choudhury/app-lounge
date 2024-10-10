package foundation.e.apps.data.gitlab.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.squareup.moshi.Json
import java.util.Date


@JsonIgnoreProperties(ignoreUnknown = true)
data class GitlabReleaseInfo(
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "name") val releaseName: String,
    @Json(name = "created_at") val createdAt: Date,
    @Json(name = "released_at") val releasedAt: Date,
)
