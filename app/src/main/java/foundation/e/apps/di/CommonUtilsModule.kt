/*
 * Apps  Quickly and easily install Android apps onto your device!
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

package foundation.e.apps.di

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.annotation.IdRes
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import foundation.e.apps.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import java.lang.reflect.Modifier
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommonUtilsModule {

    val LIST_OF_NULL = listOf("null")

    /**
     * Check supported ABIs by device
     * @return An ordered list of ABIs supported by this device
     */
    @Singleton
    @Provides
    fun provideArchitecture(): Array<String> {
        return Build.SUPPORTED_ABIS
    }

    /**
     * Check system build type
     * @return Type of the system build, like "release" or "test"
     */
    @Singleton
    @Provides
    @Named("buildType")
    fun provideBuildType(): String {
        return Build.TAGS.split("-")[0]
    }

    /**
     * Path to application's external cache directory.
     * ExternalCacheDir is required because we use downloadManager to download & save file.
     * DownloadManager doesn't have access to cacheDir (which is app private).
     * @param context [Context]
     * @return absolute path to cache directory or hardcoded string (/storage/emulated/0/Android/data/<application id>/cache) if not available
     */
    @Singleton
    @Provides
    @Named("cacheDir")
    fun provideCacheDir(@ApplicationContext context: Context): String {
        return context.externalCacheDir?.absolutePath.let {
            if (it.isNullOrBlank()) "/storage/emulated/0/Android/data/${BuildConfig.APPLICATION_ID}/cache" else it
        }
    }

    /**
     * Available free space on the device in bytes
     * @return available bytes in the data directory
     */
    @Singleton
    @Provides
    fun provideAvailableMegaBytes(): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        return stat.availableBytes
    }

    /**
     * Provides an instance of [Gson] to work with
     * @return an instance of [Gson]
     */
    @Singleton
    @Provides
    fun provideGsonInstance(): Gson {
        return GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.STATIC)
            .create()
    }

    @Singleton
    @Provides
    fun provideCache(@ApplicationContext context: Context): Cache {
        val cacheSize = (30 * 1024 * 1024).toLong() // 30 MB
        return Cache(context.cacheDir, cacheSize)
    }

    /**
     * Prevents calling a route if the navigation is already done, i.e. prevents duplicate calls.
     * Source: https://nezspencer.medium.com/navigation-components-a-fix-for-navigation-action-cannot-be-found-in-the-current-destination-95b63e16152e
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5166
     * Also related: https://gitlab.e.foundation/ecorp/apps/apps/-/merge_requests/28
     */
    fun NavController.safeNavigate(
        @IdRes currentDestinationId: Int,
        @IdRes id: Int,
        args: Bundle? = null
    ) {
        try {
            if (currentDestinationId == currentDestination?.id) {
                navigate(id, args)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Provides
    @Named("ioCoroutineScope")
    fun getIOCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Singleton
    @Provides
    fun provideClipboardService(@ApplicationContext context: Context): ClipboardManager {
        return context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
}
