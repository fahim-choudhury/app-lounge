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

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import com.android.vending.splitinstall.ISplitInstallServiceCallback
import foundation.e.apps.MainActivity
import foundation.e.apps.R
import foundation.e.apps.data.DownloadManager
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.login.AuthenticatorRepository
import foundation.e.apps.data.login.exceptions.GPlayLoginException
import foundation.e.apps.ISplitInstallService as ISplitInstallAppLoungeService
import foundation.e.splitinstall.ISplitInstallService as ISplitInstallSystemService
import foundation.e.splitinstall.ISplitInstallSystemServiceCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class SplitInstallBinder(
    val context: Context,
    private val coroutineScope: CoroutineScope,
    val applicationRepository: ApplicationRepository,
    val downloadManager: DownloadManager,
    val authenticatorRepository: AuthenticatorRepository,
    private var splitInstallSystemService: ISplitInstallSystemService?
) : ISplitInstallAppLoungeService.Stub() {

    private val modulesToInstall = HashMap<String, String>()
    private val callbacks = HashMap<String, ISplitInstallSystemServiceCallback>()

    companion object {
        const val TAG = "SplitInstallerBinder"
        const val NOTIFICATION_ID = 667
        const val NOTIFICATION_CHANNEL = "AuthToken"
        const val PLAY_STORE_NOT_FOUND_ERROR = -14
    }

    fun setService(service: foundation.e.splitinstall.ISplitInstallService) {
        splitInstallSystemService = service
        installPendingModules()
    }

    override fun installSplitModule(
        packageName: String,
        moduleName: String,
        callback: ISplitInstallServiceCallback
    ) {
        try {
            if (authenticatorRepository.gplayAuth == null) {
                handleError(packageName, callback)
                return
            }

            coroutineScope.launch {
                downloadModule(packageName, moduleName, callback)
            }
        } catch (exception: GPlayLoginException) {
            Timber.w(TAG, "Could not get auth data", exception)
            handleError(packageName, callback)
            return
        }
    }

    private fun handleError(packageName: String, callback: ISplitInstallServiceCallback) {
        if (VERSION.SDK_INT < VERSION_CODES.O) {
            return
        }

        createNotificationChannel(context)
        showNotification(context, packageName)
        callback.onError(PLAY_STORE_NOT_FOUND_ERROR)
    }

    @RequiresApi(VERSION_CODES.O)
    private fun createNotificationChannel(context: Context) {
        val descriptionText = context.getString(R.string.notification_channel_desc)
        val notificationChannel = NotificationChannel(
            NOTIFICATION_CHANNEL,
            NOTIFICATION_CHANNEL,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = descriptionText
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(notificationChannel)
    }

    private fun showNotification(context: Context, packageName: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.app_lounge_notification_icon)
            .setContentTitle(context.getString(R.string.split_install_warning_title, packageName))
            .setContentText(context.getString(R.string.split_install_warning_text))
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    private suspend fun downloadModule(
        packageName: String,
        moduleName: String,
        callback: ISplitInstallServiceCallback
    ) {
        withContext(Dispatchers.IO) {
            val versionCode = getPackageVersionCode(packageName)
            val url = fetchModuleUrl(packageName, moduleName, versionCode)

            if (url == null) {
                Timber.e("Could not find split module named $moduleName for $packageName package")
                callback.onError(PLAY_STORE_NOT_FOUND_ERROR)
                return@withContext
            }

            downloadManager.downloadFileInExternalStorage(
                url, packageName, "$packageName.split.$moduleName.apk"
            ) { success, path ->
                if (!success) {
                    return@downloadFileInExternalStorage
                }

                Timber.i("Split module has been downloaded: $path")
                if (splitInstallSystemService == null) {
                    Timber.i("Not connected to system service now. Adding $path to the list.")
                    modulesToInstall[path] = packageName
                }

                callbacks[moduleName] = object : ISplitInstallSystemServiceCallback.Stub() {
                    override fun onStartInstall(sessionId: Int) {
                        callback.onStartInstall(sessionId)
                    }

                    override fun onInstalled(sessionId: Int) {
                        callback.onInstalled(sessionId)
                    }

                    override fun onError(errorCode: Int) {
                        callback.onError(errorCode)
                    }
                }

                splitInstallSystemService?.installSplitModule(
                    packageName,
                    path,
                    callbacks[moduleName]
                )
            }
        }
    }

    private fun getPackageVersionCode(packageName: String): Int {
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        val longVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        return longVersionCode.toInt()
    }

    private suspend fun fetchModuleUrl(
        packageName: String,
        moduleName: String,
        versionCode: Int
    ): String? {
        var url = applicationRepository.getOnDemandModule(packageName, moduleName, versionCode, 1)

        if (url == null) {
            url = applicationRepository.getOnDemandModule(
                packageName,
                "config.$moduleName",
                versionCode,
                1
            )
        }

        return url
    }

    private fun installPendingModules() {
        for (module in modulesToInstall.keys) {
            val packageName = modulesToInstall[module]
            splitInstallSystemService?.installSplitModule(packageName, module, callbacks[module])
        }
    }
}
