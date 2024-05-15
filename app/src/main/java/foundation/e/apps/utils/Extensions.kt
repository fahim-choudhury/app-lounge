/*
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.apps.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.appcompat.app.AlertDialog
import com.aurora.gplayapi.data.models.ContentRating
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

fun ContentRating.isValid() = title.isNotBlank() && artwork.url.isNotBlank()
