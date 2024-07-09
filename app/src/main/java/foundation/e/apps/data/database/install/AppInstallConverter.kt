package foundation.e.apps.data.database.install

import androidx.room.TypeConverter
import com.aurora.gplayapi.data.models.ContentRating
import com.aurora.gplayapi.data.models.File
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AppInstallConverter {

    private val gson = Gson()

    @TypeConverter
    fun listToJsonString(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun jsonStringToList(value: String) =
        gson.fromJson(value, Array<String>::class.java).toMutableList()

    @TypeConverter
    fun listToJsonLong(value: MutableMap<Long, Boolean>): String = gson.toJson(value)

    @TypeConverter
    fun jsonLongToList(value: String): MutableMap<Long, Boolean> =
        gson.fromJson(value, object : TypeToken<MutableMap<Long, Boolean>>() {}.type)

    @TypeConverter
    fun filesToJsonString(value: List<File>): String = gson.toJson(value)

    @TypeConverter
    fun jsonStringToFiles(value: String) =
        gson.fromJson(value, Array<File>::class.java).toMutableList()

    @TypeConverter
    fun fromContentRating(contentRating: ContentRating): String {
        return gson.toJson(contentRating)
    }

    @TypeConverter
    fun toContentRating(name: String): ContentRating {
        return gson.fromJson(name, ContentRating::class.java)
    }
}
