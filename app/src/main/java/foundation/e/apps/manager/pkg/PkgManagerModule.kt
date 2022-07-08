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

package foundation.e.apps.manager.pkg

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller.Session
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.OpenForTesting
import foundation.e.apps.api.fused.FusedAPIImpl
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.splitinstall.SplitInstallBroadcastReceiver
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.Type
import kotlinx.coroutines.DelicateCoroutinesApi
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OpenForTesting
class PkgManagerModule @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val ERROR_PACKAGE_INSTALL = "ERROR_PACKAGE_INSTALL"
        const val PACKAGE_NAME = "packageName"
        private const val TAG = "PkgManagerModule"
    }
    private val packageManager = context.packageManager

    fun isInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isUpdatable(packageName: String, versionCode: Int): Boolean {
        return try {
            val packageInfo = getPackageInfo(packageName)
            packageInfo?.let {
                return versionCode.toLong() > PackageInfoCompat.getLongVersionCode(it)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getLaunchIntent(packageName: String): Intent? {
        return packageManager.getLaunchIntentForPackage(packageName)
    }

    private fun getPackageInfo(packageName: String): PackageInfo? {
        return packageManager.getPackageInfo(packageName, 0)
    }

    /**
     * This method should be only used for native apps!
     * If you are using for any FusedApp, please consider that it can be a PWA!
     *
     * Recommended to use: [FusedAPIImpl.getFusedAppInstallationStatus].
     */
    fun getPackageStatus(packageName: String, versionCode: Int): Status {
        return if (isInstalled(packageName)) {
            if (isUpdatable(packageName, versionCode)) {
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
    fun setFakeStoreAsInstallerIfNeeded(fusedDownload: FusedDownload?) {
        if (fusedDownload == null || fusedDownload.packageName.isBlank()) {
            return
        }
        if (fusedDownload.origin == Origin.GPLAY) {
            val fakeStorePackageName = "com.android.vending"
            if (fusedDownload.type == Type.NATIVE && isInstalled(fakeStorePackageName)) {
                val targetPackage = fusedDownload.packageName
                try {
                    packageManager.setInstallerPackageName(targetPackage, fakeStorePackageName)
                    Timber.d("Changed installer to $fakeStorePackageName for $targetPackage")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
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
            Timber.e("$packageName: \n${e.stackTraceToString()}")
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

    fun installSplitModule(moduleFile: File, packageName: String) {

        val sessionId = createInstallSession(packageName, SessionParams.MODE_INHERIT_EXISTING)
        val session = packageManager.packageInstaller.openSession(sessionId)

        try {
            syncFile(session, moduleFile)

            val callBackIntent = Intent(context, SplitInstallBroadcastReceiver::class.java)
            callBackIntent.action = SplitInstallBroadcastReceiver.ACTION_MODULE_INSTALLED

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE else
                PendingIntent.FLAG_UPDATE_CURRENT
            val servicePendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                callBackIntent,
                flags
            )
            session.commit(servicePendingIntent.intentSender)
        } catch (e: Exception) {
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

    /**
     * Un-install the given package
     * @param packageName Name of the package
     */
    fun uninstallApplication(packageName: String) {
        val packageInstaller = packageManager.packageInstaller
        val params = SessionParams(SessionParams.MODE_FULL_INSTALL)

        val sessionId = packageInstaller.createSession(params)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(Intent.ACTION_PACKAGE_REMOVED),
            PendingIntent.FLAG_IMMUTABLE
        )

        packageInstaller.uninstall(packageName, pendingIntent.intentSender)
    }

    fun getFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addDataScheme("package")
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addAction(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(ERROR_PACKAGE_INSTALL)
        return filter
    }

    fun getLongVersionCode(versionCode: String): Long {
        val version = versionCode.split(" ")[0]
        return version.replace("[.]".toRegex(), "").toLong()
    }

    fun getAllUserApps(): List<ApplicationInfo> {
        val userPackages = mutableListOf<ApplicationInfo>()
        val allPackages = packageManager.getInstalledApplications(0)
        allPackages.forEach {
            if (it.flags and ApplicationInfo.FLAG_SYSTEM == 0) userPackages.add(it)
        }
        return userPackages
    }

    fun getAllSystemApps(): List<ApplicationInfo> {
        return packageManager.getInstalledApplications(PackageManager.MATCH_SYSTEM_ONLY)
    }

    fun getAppNameFromPackageName(packageName: String): String {
        val packageManager = context.packageManager
        return packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        ).toString()
    }
}
