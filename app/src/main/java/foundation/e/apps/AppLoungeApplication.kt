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

package foundation.e.apps

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import dagger.hilt.android.HiltAndroidApp
import foundation.e.apps.data.Constants.TAG_APP_INSTALL_STATE
import foundation.e.apps.data.Constants.TAG_AUTHDATA_DUMP
import foundation.e.apps.data.preference.AppLoungeDataStore
import foundation.e.apps.data.preference.AppLoungePreference
import foundation.e.apps.install.pkg.PkgManagerBR
import foundation.e.apps.install.pkg.AppLoungePackageManager
import foundation.e.apps.install.updates.UpdatesWorkManager
import foundation.e.apps.install.workmanager.InstallWorkManager
import foundation.e.apps.ui.setup.tos.TOS_VERSION
import foundation.e.apps.utils.CustomUncaughtExceptionHandler
import foundation.e.lib.telemetry.Telemetry
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.Forest.plant
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
@DelicateCoroutinesApi
class AppLoungeApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var appLoungePackageManager: AppLoungePackageManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appLoungeDataStore: AppLoungeDataStore

    @Inject
    lateinit var appLoungePreference: AppLoungePreference

    @Inject
    lateinit var uncaughtExceptionHandler: CustomUncaughtExceptionHandler

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler)

        InstallWorkManager.context = this
        // Register broadcast receiver for package manager
        val pkgManagerBR = object : PkgManagerBR() {}
        registerReceiver(pkgManagerBR, appLoungePackageManager.getFilter(), RECEIVER_EXPORTED)

        val currentVersion = runBlocking { appLoungeDataStore.tosVersion.first() }
        if (!currentVersion.contentEquals(TOS_VERSION)) {
            MainScope().launch {
                appLoungeDataStore.saveTOCStatus(false, "")
            }
        }

        if (BuildConfig.DEBUG) {
            plant(Timber.DebugTree())
        } else {
            // Allow enabling telemetry only for release builds.
            Telemetry.init(BuildConfig.SENTRY_DSN, this)
            plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    if (priority <= Log.WARN && !listOf(
                            TAG_AUTHDATA_DUMP,
                            TAG_APP_INSTALL_STATE
                        ).contains(tag)
                    ) {
                        return
                    }
                    Log.println(priority, tag, message)
                }
            })
        }

        UpdatesWorkManager.enqueueWork(
            this,
            appLoungePreference.getUpdateInterval(),
            ExistingPeriodicWorkPolicy.KEEP
        )
    }

    override fun getWorkManagerConfiguration() =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(Executors.newSingleThreadExecutor())
            .build()
}
