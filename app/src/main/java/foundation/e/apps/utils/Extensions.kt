package foundation.e.apps.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.appcompat.app.AlertDialog
import foundation.e.apps.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Date.getFormattedString(format: String, locale: Locale = Locale.getDefault()): String {
    val dateFormat = SimpleDateFormat(format, locale)
    return dateFormat.format(this)
}

fun Context.showGoogleSignInAlertDialog(
    onYesClickListener: () -> Unit,
    onCancelClickListener: () -> Unit
) {
    AlertDialog.Builder(this)
        .setCancelable(true)
        .setMessage(R.string.google_login_alert_message)
        .setPositiveButton(R.string.proceed_to_google_login) { _, _ -> onYesClickListener() }
        .setNegativeButton(R.string.cancel) { _, _ -> onCancelClickListener() }
        .show()
}

fun Context.isNetworkAvailable(): Boolean {
    val connectivityManager =
        this.getSystemService(ConnectivityManager::class.java)

    val capabilities =
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?: return false

    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    ) {
        return true
    }

    return false
}
