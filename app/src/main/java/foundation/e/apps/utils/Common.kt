/*
 * Copyright (C) 2019-2021  E FOUNDATION
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
 */

package foundation.e.apps.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.os.LocaleListCompat
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import foundation.e.apps.categories.model.Category
import foundation.e.apps.utils.Constants.MICROG_PACKAGE
import foundation.e.apps.utils.Constants.MICROG_SHARED_PREF
import java.net.URL
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import kotlin.math.roundToInt

/**
 * Provides various useful methods to be used by every feature in the project
 */
object Common {
    val EXECUTOR = Executors.newCachedThreadPool()!!

    /**
     * Provides an instance of [KeyDeserializer] class which implements the
     * required functions to be used internally
     */
    private class LocalKeyDeserializer : KeyDeserializer() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun deserializeKey(p0: String?, p1: DeserializationContext?): Any? {
            return Paths.get(p0)
        }
    }

    /**
     * Checks if device has internet connection available or not
     * @param context [Context]
     * @return true if internet connection is available, false otherwise
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        if (capabilities != null) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Converts the given [bytes] into mebibyte
     * @param bytes bytes to convert
     * @return converted mebibyte
     */
    fun toMiB(bytes: Int): Double {
        val inMiB = bytes.div(1048576.0)
        return inMiB.times(10.0).roundToInt().div(10.0)
    }

    /**
     * Creates a secure network connection to the given URL and method
     * @param url URL
     * @param requestMethod request method defined in [HttpsURLConnection.setRequestMethod]
     * @return an [HttpsURLConnection] to the given network
     */
    fun createConnection(url: String, requestMethod: String): HttpsURLConnection {
        val preferredLanguage = getAcceptedLanguageHeaderValue()
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.setRequestProperty("Accept-Language", preferredLanguage)
        connection.requestMethod = requestMethod
        connection.connectTimeout = Constants.CONNECT_TIMEOUT
        connection.readTimeout = Constants.READ_TIMEOUT
        return connection
    }

    /**
     * Checks if the given [packageName] is a system app or not
     * @param packageManager [PackageManager]
     * @param packageName package to verify
     * @return true if the app is system app. false otherwise
     */
    fun isSystemApp(packageManager: PackageManager, packageName: String?): Boolean {
        if (packageName != null) {
            return try {
                val info = packageManager.getPackageInfo(packageName, 0)
                (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (exception: Exception) {
                false
            }
        }
        return false
    }

    /**
     * Checks if the given package has a launch activity or not
     * @param context [Context]
     * @param packageName package to verify
     * @return [Boolean] indicating whether the package has a launch activity or not
     */
    fun appHasLaunchActivity(context: Context, packageName: String?): Boolean {
        return (packageName?.let { context.packageManager.getLaunchIntentForPackage(it) } != null)
    }

    /**
     * Provides an [ObjectMapper] instance to work with Jackson library
     */
    fun getObjectMapper(): ObjectMapper {
        val objectMapper = ObjectMapper()
        val simpleModule = SimpleModule()
        simpleModule.addKeyDeserializer(Category::class.java, LocalKeyDeserializer())
        objectMapper.registerModule(simpleModule)
        objectMapper.registerKotlinModule()
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.registerKotlinModule()
        return objectMapper
    }

    /**
     * Provides the accepted language header value
     */
    private fun getAcceptedLanguageHeaderValue(): String {
        var weight = 1.0F
        return getPreferredLocaleList()
            .map { it.toLanguageTag() }
            .reduce { accumulator, languageTag ->
                weight -= 0.1F
                "$accumulator,$languageTag;q=$weight"
            }
    }

    /**
     * Provides a list containing preferred [Locale]
     */
    private fun getPreferredLocaleList(): List<Locale> {
        val adjustedLocaleListCompat = LocaleListCompat.getAdjustedDefault()
        val preferredLocaleList = mutableListOf<Locale>()
        for (index in 0 until adjustedLocaleListCompat.size()) {
            preferredLocaleList.add(adjustedLocaleListCompat.get(index))
        }
        return preferredLocaleList
    }

    /**
     * Updates shared preferences related to microG EN
     * @param context [Context]
     */
    fun updateMicroGStatus(context: Context) {
        val packageInfo = context.packageManager.getPackageInfo(MICROG_PACKAGE, 0)
        val microgENversion = packageInfo.versionName
        if (microgENversion.endsWith("-noen")) {
            PreferenceStorage(context).save(MICROG_SHARED_PREF, false)
        } else {
            PreferenceStorage(context).save(MICROG_SHARED_PREF, true)
        }
    }

    /**
     * Returns system default accent color
     * @param context [Context]
     * @return default accent color for the system
     */
    fun getAccentColor(context: Context): Int {
        return context.getColor(foundation.e.apps.R.color.colorAccent)
    }
}
