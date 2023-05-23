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

package foundation.e.apps.install.splitinstall

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.install.download.DownloadManager
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.prefrences.DataStoreModule
import foundation.e.splitinstall.ISplitInstallService
import foundation.e.splitinstall.SplitInstall
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplitInstallService : LifecycleService() {

    companion object {
        const val TAG = "SplitInstallService"
    }

    @Inject lateinit var dataStoreModule: DataStoreModule
    @Inject lateinit var fusedAPIRepository: FusedAPIRepository
    @Inject lateinit var downloadManager: DownloadManager
    @Inject lateinit var gson: Gson
    private lateinit var binder: SplitInstallBinder
    private var authData: AuthData? = null
    private var splitInstallSystemService: ISplitInstallService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            splitInstallSystemService = ISplitInstallService.Stub.asInterface(service)
            binder.setService(splitInstallSystemService!!)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            splitInstallSystemService = null
        }
    }

    override fun onCreate() {
        super.onCreate()

        val intent = Intent().apply {
            component = SplitInstall.SPLIT_INSTALL_SYSTEM_SERVICE
        }
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)

        lifecycleScope.launch {
            fetchAuthData()
        }
    }

    override fun onDestroy() {
        splitInstallSystemService?.let {
            unbindService(serviceConnection)
        }
        super.onDestroy()
    }

    private suspend fun fetchAuthData() {
        dataStoreModule.authData.collect {
            authData = gson.fromJson(it, AuthData::class.java)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        binder = SplitInstallBinder(
            applicationContext,
            lifecycleScope,
            fusedAPIRepository,
            downloadManager,
            authData,
            splitInstallSystemService
        )
        return binder
    }
}
