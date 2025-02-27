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

package foundation.e.apps.install.pkg

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller.Session
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.OpenForTesting
import foundation.e.apps.data.application.search.SearchApi
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.enums.Type
import foundation.e.apps.data.install.models.AppInstall
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.DelicateCoroutinesApi
import timber.log.Timber

@Singleton
@OpenForTesting
class AppLoungePackageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val ERROR_PACKAGE_INSTALL = "ERROR_PACKAGE_INSTALL"
        const val PACKAGE_NAME = "packageName"
        const val FAKE_STORE_PACKAGE_NAME = "com.android.vending"
        private const val UNKNOWN_VALUE = ""
    }

    private val packageManager = context.packageManager

    fun isInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            }

            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isUpdatable(packageName: String, versionCode: Int, versionName: String): Boolean {
        val packageInfo = getPackageInfo(packageName) ?: return false
        val installedVersionNumber = PackageInfoCompat.getLongVersionCode(packageInfo)
        val installedVersionName = packageInfo.versionName

        val isVersionNumberHigher = versionCode.toLong() > installedVersionNumber
        val isVersionNameHigher =
            versionName.isNotBlank() && versionName > installedVersionName

        return isVersionNumberHigher || isVersionNameHigher
    }

    fun getLaunchIntent(packageName: String): Intent? {
        return packageManager.getLaunchIntentForPackage(packageName)
    }

    private fun getPackageInfo(packageName: String): PackageInfo? {
        return try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: NameNotFoundException) {
            Timber.e("getPackageInfo: ${e.localizedMessage}")
            null
        }
    }

    /**
     * This method should be only used for native apps!
     * If you are using for any FusedApp, please consider that it can be a PWA!
     *
     * Recommended to use: [SearchApi.getFusedAppInstallationStatus].
     */
    fun getPackageStatus(
        packageName: String,
        versionCode: Int,
        versionName: String = "",
    ): Status {
        return if (isInstalled(packageName)) {
            if (isUpdatable(packageName, versionCode, versionName)) {
                Status.UPDATABLE
            } else {
                Status.INSTALLED
            }
        } else {
            Status.UNAVAILABLE
        }
    }

    /**
     * Sets an installed app's installer as FakeStore if its source / origin is from Google play.
     * If the origin is not Google play, no operation is performed.
     *
     * [See issue 2237](https://gitlab.e.foundation/e/backlog/-/issues/2237)
     *
     * Surrounded by try-catch to prevent exception is case App Lounge and FakeStore's
     * signing certificate is not the same.
     */
    fun setFakeStoreAsInstallerIfNeeded(appInstall: AppInstall?) {
        if (appInstall == null || appInstall.packageName.isBlank()) {
            return
        }
        if (appInstall.origin == Origin.GPLAY) {
            if (appInstall.type == Type.NATIVE && isInstalled(FAKE_STORE_PACKAGE_NAME)) {
                val targetPackage = appInstall.packageName
                try {
                    packageManager.setInstallerPackageName(targetPackage, FAKE_STORE_PACKAGE_NAME)
                    Timber.d("Changed installer to $FAKE_STORE_PACKAGE_NAME for $targetPackage")
                } catch (e: Exception) {
                    Timber.w(e)
                }
            }
        }
    }

    fun getInstallerName(packageName: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val installerInfo = packageManager.getInstallSourceInfo(packageName)
                installerInfo.originatingPackageName ?: installerInfo.installingPackageName ?: UNKNOWN_VALUE
            } else {
                packageManager.getInstallerPackageName(packageName) ?: UNKNOWN_VALUE
            }
        } catch (e: NameNotFoundException) {
            Timber.e("getInstallerName -> $packageName : ${e.localizedMessage}")
            UNKNOWN_VALUE
        } catch (e: IllegalArgumentException) {
            Timber.e("getInstallerName -> $packageName : ${e.localizedMessage}")
            UNKNOWN_VALUE
        }
    }

    /**
     * For an installed app, get the path to the base.apk.
     */
    fun getBaseApkPath(packageName: String): String {
        val packageInfo = getPackageInfo(packageName)
        return packageInfo?.applicationInfo?.publicSourceDir ?: UNKNOWN_VALUE
    }

    fun getVersionCode(packageName: String): String {
        val packageInfo = getPackageInfo(packageName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode?.toString() ?: UNKNOWN_VALUE
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toString() ?: UNKNOWN_VALUE
        }
    }

    fun getVersionName(packageName: String): String {
        val packageInfo = getPackageInfo(packageName)
        return packageInfo?.versionName?.toString() ?: UNKNOWN_VALUE
    }

    /**
     * Installs the given package using system API
     * @param list List of [File] to be written to install session.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun installApplication(list: List<File>, packageName: String) {

        val sessionId = createInstallSession(packageName, SessionParams.MODE_FULL_INSTALL)
        val session = packageManager.packageInstaller.openSession(sessionId)

        try {
            // Install the package using the provided stream
            list.forEach {
                syncFile(session, it)
            }

            val callBackIntent = Intent(context, InstallerService::class.java)
            callBackIntent.putExtra(PACKAGE_NAME, packageName)

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE else
                PendingIntent.FLAG_UPDATE_CURRENT
            val servicePendingIntent = PendingIntent.getService(
                context,
                sessionId,
                callBackIntent,
                flags
            )
            session.commit(servicePendingIntent.intentSender)
        } catch (e: Exception) {
            Timber.e(
                "Initiating Install Failed for $packageName exception: ${e.localizedMessage}",
                e
            )
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(ERROR_PACKAGE_INSTALL),
                PendingIntent.FLAG_IMMUTABLE
            )
            session.commit(pendingIntent.intentSender)
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    private fun createInstallSession(packageName: String, mode: Int): Int {

        val packageInstaller = packageManager.packageInstaller
        val params = SessionParams(mode).apply {
            setAppPackageName(packageName)
            setOriginatingUid(android.os.Process.myUid())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        return packageInstaller.createSession(params)
    }

    private fun syncFile(session: Session, file: File) {

        val inputStream = file.inputStream()
        val outputStream = session.openWrite(file.nameWithoutExtension, 0, -1)
        inputStream.copyTo(outputStream)
        session.fsync(outputStream)
        inputStream.close()
        outputStream.close()
    }

    fun getFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addDataScheme("package")
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addAction(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(ERROR_PACKAGE_INSTALL)
        return filter
    }

    fun getAllUserApps(): List<ApplicationInfo> {
        val userPackages = mutableListOf<ApplicationInfo>()
        val allPackages = packageManager.getInstalledApplications(0)
        allPackages.forEach {
            if (it.flags and ApplicationInfo.FLAG_SYSTEM == 0) userPackages.add(it)
        }
        return userPackages
    }

    fun getAppNameFromPackageName(packageName: String): String {
        val packageManager = context.packageManager
        return packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        ).toString()
    }
}
