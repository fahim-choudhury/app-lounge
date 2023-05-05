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

import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.DialogFragment
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import foundation.e.apps.manager.pkg.PkgManagerBR
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.manager.workmanager.InstallWorkManager
import foundation.e.apps.setup.tos.TOS_VERSION
import foundation.e.apps.utils.modules.DataStoreModule
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.Forest.plant
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltAndroidApp
@DelicateCoroutinesApi
class AppLoungeApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var pkgManagerModule: PkgManagerModule

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var dataStoreModule: DataStoreModule

    override fun onCreate() {
        super.onCreate()

        InstallWorkManager.context = this
        // Register broadcast receiver for package manager
        val pkgManagerBR = object : PkgManagerBR() {}
        registerReceiver(pkgManagerBR, pkgManagerModule.getFilter())

        val currentVersion = dataStoreModule.getTOSVersion()
        if (!currentVersion.contentEquals(TOS_VERSION)) {
            MainScope().launch {
                dataStoreModule.saveTOCStatus(false, "")
            }
        }
        if (BuildConfig.DEBUG) {
            plant(Timber.DebugTree())
        }
    }

    override fun getWorkManagerConfiguration() =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(Executors.newSingleThreadExecutor())
            .build()
}
