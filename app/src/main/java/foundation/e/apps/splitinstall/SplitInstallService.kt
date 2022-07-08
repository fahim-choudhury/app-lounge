/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021-2022  E FOUNDATION
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

package foundation.e.apps.splitinstall

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.api.DownloadManager
import foundation.e.apps.api.fused.FusedAPIRepository
import foundation.e.apps.utils.modules.DataStoreModule
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplitInstallService : LifecycleService() {

    companion object {
        const val TAG = "SplitInstallService"
        const val NOTIFICATION_CHANNEL_ID = "SplitInstallNotificationChannel"
        const val NOTIFICATION_CHANNEL_NAME = "SplitInstaller"
    }

    @Inject lateinit var dataStoreModule: DataStoreModule
    @Inject lateinit var fusedAPIRepository: FusedAPIRepository
    @Inject lateinit var downloadManager: DownloadManager
    @Inject lateinit var gson: Gson
    private var authData: AuthData? = null

    override fun onCreate() {
        super.onCreate()

        SplitInstallNotification.createNotificationChannel(applicationContext)

        lifecycleScope.launch {
            fetchAuthData()
        }
    }

    private suspend fun fetchAuthData() {
        dataStoreModule.authData.collect {
            authData = gson.fromJson(it, AuthData::class.java)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return SplitInstallBinder(
            applicationContext,
            lifecycleScope,
            fusedAPIRepository,
            downloadManager,
            authData
        )
    }
}