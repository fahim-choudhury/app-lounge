package foundation.e.apps.di.network

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Instant
import java.time.format.DateTimeFormatter

//todo Instant is not available in Android API 25 which is the minimum used.
// 3 option: replace Instant by another class, use a third party library to make retrocompatibility
// or update android minimum api to at least API 26
class InstantJsonAdapter {
    @ToJson
    fun toJson(instant: Instant): String {
        return DateTimeFormatter.ISO_INSTANT.format(instant)
    }

    @FromJson
    fun fromJson(instantString: String): Instant {
        return Instant.parse(instantString)
    }
}