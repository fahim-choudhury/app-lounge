package app.lounge.extension

import com.google.gson.Gson
import java.util.Properties

/** Convert Properties parameter to byte array*/
fun Properties.toByteArray() = Gson().toJson(this).toByteArray()