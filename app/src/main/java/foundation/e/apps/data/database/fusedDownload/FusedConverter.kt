package foundation.e.apps.data.database.fusedDownload

import androidx.room.TypeConverter
import com.aurora.gplayapi.data.models.File
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FusedConverter {

    @TypeConverter
    fun listToJsonString(value: List<String>): String = Gson().toJson(value)

    @TypeConverter
    fun jsonStringToList(value: String) =
        Gson().fromJson(value, Array<String>::class.java).toMutableList()

    @TypeConverter
    fun listToJsonLong(value: MutableMap<Long, Boolean>): String = Gson().toJson(value)

    @TypeConverter
    fun jsonLongToList(value: String): MutableMap<Long, Boolean> =
        Gson().fromJson(value, object : TypeToken<MutableMap<Long, Boolean>>() {}.type)

    @TypeConverter
    fun filesToJsonString(value: List<File>): String = Gson().toJson(value)

    @TypeConverter
    fun jsonStringToFiles(value: String) =
        Gson().fromJson(value, Array<File>::class.java).toMutableList()
}
