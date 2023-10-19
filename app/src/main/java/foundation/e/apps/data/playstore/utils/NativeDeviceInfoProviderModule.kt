/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
 * Copyright (C) 2021  E FOUNDATION
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

package foundation.e.apps.data.playstore.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.res.Configuration
import android.os.Build
import android.text.TextUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Properties
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NativeDeviceInfoProviderModule {

    @Singleton
    @Provides
    fun provideNativeDeviceProperties(
        @ApplicationContext context: Context,
    ): Properties {
        val properties = Properties().apply {
            // Build Props
            setProperty("UserReadableName", "${Build.DEVICE}-default")
            setProperty("Build.HARDWARE", Build.HARDWARE)
            setProperty(
                "Build.RADIO",
                if (Build.getRadioVersion() != null)
                    Build.getRadioVersion()
                else
                    "unknown"
            )
            setProperty("Build.FINGERPRINT", Build.FINGERPRINT)
            setProperty("Build.BRAND", Build.BRAND)
            setProperty("Build.DEVICE", Build.DEVICE)
            setProperty("Build.VERSION.SDK_INT", "${Build.VERSION.SDK_INT}")
            setProperty("Build.VERSION.RELEASE", Build.VERSION.RELEASE)
            setProperty("Build.MODEL", Build.MODEL)
            setProperty("Build.MANUFACTURER", Build.MANUFACTURER)
            setProperty("Build.PRODUCT", Build.PRODUCT)
            setProperty("Build.ID", Build.ID)
            setProperty("Build.BOOTLOADER", Build.BOOTLOADER)

            val config = context.resources.configuration
            setProperty("TouchScreen", "${config.touchscreen}")
            setProperty("Keyboard", "${config.keyboard}")
            setProperty("Navigation", "${config.navigation}")
            setProperty("ScreenLayout", "${config.screenLayout and 15}")
            setProperty("HasHardKeyboard", "${config.keyboard == Configuration.KEYBOARD_QWERTY}")
            setProperty(
                "HasFiveWayNavigation",
                "${config.navigation == Configuration.NAVIGATIONHIDDEN_YES}"
            )

            // Display Metrics
            val metrics = context.resources.displayMetrics
            setProperty("Screen.Density", "${metrics.densityDpi}")
            setProperty("Screen.Width", "${metrics.widthPixels}")
            setProperty("Screen.Height", "${metrics.heightPixels}")

            // Supported Platforms
            setProperty("Platforms", Build.SUPPORTED_ABIS.joinToString(separator = ","))

            // Supported Features
            setProperty("Features", getFeatures(context).joinToString(separator = ","))
            // Shared Locales
            setProperty("Locales", getLocales(context).joinToString(separator = ","))
            // Shared Libraries
            setProperty(
                "SharedLibraries",
                getSharedLibraries(context).joinToString(separator = ",")
            )
            // GL Extensions
            val activityManager =
                context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            setProperty(
                "GL.Version",
                activityManager.deviceConfigurationInfo.reqGlEsVersion.toString()
            )
            setProperty(
                "GL.Extensions",
                EglExtensionProvider.eglExtensions.joinToString(separator = ",")
            )

            // Google Related Props
            val gsfVersionProvider = NativeGsfVersionProvider(context)
            setProperty("Client", "android-google")
            setProperty("GSF.version", "${gsfVersionProvider.getGsfVersionCode(true)}")
            setProperty("Vending.version", "${gsfVersionProvider.getVendingVersionCode(true)}")
            setProperty("Vending.versionString", gsfVersionProvider.getVendingVersionString(true))

            // MISC
            setProperty("Roaming", "mobile-notroaming")
            setProperty("TimeZone", "UTC-10")

            // Telephony (USA 3650 AT&T)
            setProperty("CellOperator", "310")
            setProperty("SimOperator", "38")
        }
        return properties
    }

    private fun getFeatures(context: Context): List<String> {
        val featureStringList: MutableList<String> = ArrayList()
        try {
            val availableFeatures = context.packageManager.systemAvailableFeatures
            for (feature in availableFeatures) {
                if (feature.name.isNotEmpty()) {
                    featureStringList.add(feature.name)
                }
            }
        } catch (e: Exception) {
        }
        return featureStringList
    }

    private fun getLocales(context: Context): List<String> {
        val localeList: MutableList<String> = ArrayList()
        localeList.addAll(listOf(*context.assets.locales))
        val locales: MutableList<String> = ArrayList()
        for (locale in localeList) {
            if (TextUtils.isEmpty(locale)) {
                continue
            }
            locales.add(locale.replace("-", "_"))
        }
        return locales
    }

    private fun getSharedLibraries(context: Context): List<String> {
        val systemSharedLibraryNames = context.packageManager.systemSharedLibraryNames
        val libraries: MutableList<String> = ArrayList()
        try {
            if (systemSharedLibraryNames != null) {
                libraries.addAll(listOf(*systemSharedLibraryNames))
            }
        } catch (e: Exception) {
        }
        return libraries
    }
}
