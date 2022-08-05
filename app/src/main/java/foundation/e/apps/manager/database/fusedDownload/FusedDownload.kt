package foundation.e.apps.manager.database.fusedDownload

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aurora.gplayapi.data.models.File
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.Type

@Entity
data class FusedDownload(
    @PrimaryKey val id: String = String(),
    val origin: Origin = Origin.CLEANAPK,
    var status: Status = Status.UNAVAILABLE,
    val name: String = String(),
    val packageName: String = String(),
    var downloadURLList: MutableList<String> = mutableListOf(),
    var downloadIdMap: MutableMap<Long, Boolean> = mutableMapOf(),
    val orgStatus: Status = Status.UNAVAILABLE,
    val type: Type = Type.NATIVE,
    val iconByteArray: String = String(),
    val versionCode: Int = 1,
    val offerType: Int = -1,
    val isFree: Boolean = true,
    val appSize: Long = 0,
    var files: List<File> = mutableListOf(),
    var signature: String = String()
)
