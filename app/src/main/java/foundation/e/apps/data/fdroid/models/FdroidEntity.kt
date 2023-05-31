package foundation.e.apps.data.fdroid.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Store the contents from api in DB to prevent further network calls.
 * This is also the model used for views.
 */
@Entity
class FdroidEntity(@PrimaryKey val packageName: String, authorName: String) {

    companion object {
        const val DEFAULT_FDROID_AUTHOR_NAME = "F-Droid"
    }

    var authorName: String = DEFAULT_FDROID_AUTHOR_NAME
    init {
        if (authorName.isNotBlank()) this.authorName = authorName
    }
}
