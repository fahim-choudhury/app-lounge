package foundation.e.apps.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Date.getFormattedString(format: String, locale: Locale = Locale.getDefault()): String {
    val dateFormat = SimpleDateFormat(format, locale)
    return dateFormat.format(this)
}
