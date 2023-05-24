package foundation.e.apps.data.exodus

import androidx.room.Entity
import androidx.room.PrimaryKey

data class Trackers(
    val trackers: Map<String, Tracker>
)

@Entity
data class Tracker(
    @PrimaryKey
    val id: Long,
    val name: String,
    val description: String?,
    val creationDate: String?,
    val codeSignature: String?,
    val networkSignature: String?,
    val website: String?,
)
