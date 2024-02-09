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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import foundation.e.apps.ISplitInstallService
import foundation.e.apps.MainActivity
import foundation.e.apps.R
import foundation.e.apps.data.DownloadManager
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.login.AuthenticatorRepository
import foundation.e.apps.data.login.exceptions.GPlayLoginException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Collections
import java.util.TreeSet

class SplitInstallBinder(
    val context: Context,
    private val coroutineScope: CoroutineScope,
    val applicationRepository: ApplicationRepository,
    val downloadManager: DownloadManager,
    val authenticatorRepository: AuthenticatorRepository,
    private var splitInstallSystemService: foundation.e.splitinstall.ISplitInstallService?
) : ISplitInstallService.Stub() {

    private val modulesToInstall = HashMap<String, String>()

    companion object {
        const val TAG = "SplitInstallerBinder"
        const val AUTH_DATA_ERROR_MESSAGE = "Could not get auth data"
        const val NOTIFICATION_CHANNEL = "Dynamic module install"
        const val NOTIFICATION_ID_KEY = "notification_id_key"
        const val PACKAGE_NAME_KEY = "package_name_key"
        const val PREFERENCES_FILE_NAME = "packages_to_ignore"
        const val PACKAGES_LIST_KEY = "packages_list_key"
    }

    override fun installSplitModule(packageName: String, moduleName: String) {
        try {
            coroutineScope.launch {
                authenticatorRepository.getValidatedAuthData()
            }

            if (authenticatorRepository.gplayAuth == null) {
                Timber.w(AUTH_DATA_ERROR_MESSAGE)
                handleError(packageName)
                return
            }

            coroutineScope.launch {
                downloadModule(packageName, moduleName)
            }
        } catch (exception: GPlayLoginException) {
            Timber.w("$AUTH_DATA_ERROR_MESSAGE $exception")
            handleError(packageName)
        }
    }

    private fun handleError(packageName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        createNotificationChannel(context)
        showErrorNotification(context, packageName)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val descriptionText = context.getString(R.string.notification_channel_desc)
        val notificationChannel = NotificationChannel(
            NOTIFICATION_CHANNEL,
            NOTIFICATION_CHANNEL,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = descriptionText
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(notificationChannel)
    }

    private fun showErrorNotification(context: Context, packageName: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val preferences = context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
        val ignoreList = preferences.getStringSet(PACKAGES_LIST_KEY, Collections.emptySet())
        if (ignoreList != null && packageName in ignoreList) return

        val appInfo = context.packageManager.getPackageInfo(packageName, 0).applicationInfo
        val appLabel = context.packageManager.getApplicationLabel(appInfo)
        val callerUid = appInfo.uid
        val contentText = context.getString(
            R.string.split_install_warning_text,
            appLabel
        )

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.app_lounge_notification_icon)
            .setContentTitle(context.getString(R.string.split_install_warning_title, appLabel))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    context.getString(R.string.sign_in),
                    buildSignInPendingIntent(callerUid)
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    context.getString(R.string.ignore),
                    buildIgnorePendingIntent(callerUid, packageName)
                ).build()
            )

        with(NotificationManagerCompat.from(context)) {
            notify(callerUid, notificationBuilder.build())
        }
    }

    private fun buildIgnorePendingIntent(callerUid: Int, packageName: String): PendingIntent {

        val ignoreIntent = Intent(context, IgnoreReceiver::class.java).apply {
            putExtra(NOTIFICATION_ID_KEY, callerUid)
            putExtra(PACKAGE_NAME_KEY, packageName)
        }

        return PendingIntent.getBroadcast(
            context,
            callerUid,
            ignoreIntent,
            PendingIntent.FLAG_MUTABLE
        )
    }

    private fun buildSignInPendingIntent(callerUid: Int): PendingIntent {
        val signInIntent = Intent(context, SignInReceiver::class.java).apply {
            putExtra(NOTIFICATION_ID_KEY, callerUid)
        }

        return PendingIntent.getBroadcast(
            context,
            callerUid,
            signInIntent,
            PendingIntent.FLAG_MUTABLE
        )
    }

    fun setService(service: foundation.e.splitinstall.ISplitInstallService) {
        splitInstallSystemService = service
        installPendingModules()
    }

    private suspend fun downloadModule(packageName: String, moduleName: String) {
        withContext(Dispatchers.IO) {
            val versionCode = getPackageVersionCode(packageName)
            val url = fetchModuleUrl(packageName, moduleName, versionCode)

            if (url == null) {
                Timber.e("Could not find split module named $moduleName for $packageName package")
                return@withContext
            }

            downloadManager.downloadFileInExternalStorage(
                url, packageName, "$packageName.split.$moduleName.apk"
            ) { success, path ->
                if (success) {
                    Timber.i("Split module has been downloaded: $path")
                    if (splitInstallSystemService == null) {
                        Timber.i("Not connected to system service now. Adding $path to the list.")
                        modulesToInstall[path] = packageName
                    }
                    splitInstallSystemService?.installSplitModule(packageName, path)
                }
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
            splitInstallSystemService?.installSplitModule(packageName, module)
        }
    }

    class IgnoreReceiver: BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) {
                return
            }

            NotificationManagerCompat.from(context).cancel(
                intent.getIntExtra(NOTIFICATION_ID_KEY, -1)
            )

            val packageName = intent.getStringExtra(PACKAGE_NAME_KEY) ?: return
            val preferences = context.getSharedPreferences(
                PREFERENCES_FILE_NAME,
                Context.MODE_PRIVATE
            )

            val ignoreList = preferences.getStringSet(PACKAGES_LIST_KEY, Collections.emptySet())
                ?: Collections.emptySet()
            
            val newList = TreeSet(ignoreList)
            newList.add(packageName)
            preferences.edit().putStringSet(PACKAGES_LIST_KEY, newList).apply()
        }
    }

    class SignInReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) {
                return
            }

            NotificationManagerCompat.from(context).cancel(
                intent.getIntExtra(NOTIFICATION_ID_KEY, -1)
            )

            val launchAppLoungeIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(launchAppLoungeIntent)
        }
    }
}
