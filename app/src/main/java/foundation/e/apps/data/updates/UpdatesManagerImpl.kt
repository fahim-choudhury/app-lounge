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

package foundation.e.apps.data.updates

import android.content.Context
import android.content.pm.ApplicationInfo
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.blockedApps.BlockedAppRepository
import foundation.e.apps.data.cleanapk.ApkSignatureManager
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.enums.isUnFiltered
import foundation.e.apps.data.faultyApps.FaultyAppRepository
import foundation.e.apps.data.fdroid.FdroidRepository
import foundation.e.apps.data.playstore.PlayStoreRepositoryImpl
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.search.SearchApi.Companion.APP_TYPE_ANY
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.gitlab.SystemAppsUpdatesRepository
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.preference.AppLoungePreference
import foundation.e.apps.install.pkg.AppLoungePackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class UpdatesManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLoungePackageManager: AppLoungePackageManager,
    private val applicationRepository: ApplicationRepository,
    private val faultyAppRepository: FaultyAppRepository,
    private val appLoungePreference: AppLoungePreference,
    private val fdroidRepository: FdroidRepository,
    private val blockedAppRepository: BlockedAppRepository,
    private val systemAppsUpdatesRepository: SystemAppsUpdatesRepository,
) {

    companion object {
        const val PACKAGE_NAME_F_DROID = "org.fdroid.fdroid"
        const val PACKAGE_NAME_F_DROID_PRIVILEGED = "org.fdroid.fdroid.privileged"
        const val PACKAGE_NAME_ANDROID_VENDING = "com.android.vending"
    }

    private val userApplications: List<ApplicationInfo>
        get() = appLoungePackageManager.getAllUserApps()

    suspend fun getUpdates(authData: AuthData): Pair<List<Application>, ResultStatus> {
        val updateList = mutableListOf<Application>()
        var status = ResultStatus.OK

        val openSourceInstalledApps = getOpenSourceInstalledApps().toMutableList()
        val gPlayInstalledApps = getGPlayInstalledApps().toMutableList()

        if (appLoungePreference.shouldUpdateAppsFromOtherStores()) {
            withContext(Dispatchers.IO) {
                val otherStoresInstalledApps = getAppsFromOtherStores().toMutableList()

                // This list is based on app signatures
                val updatableFDroidApps =
                    findPackagesMatchingFDroidSignatures(otherStoresInstalledApps)

                openSourceInstalledApps.addAll(updatableFDroidApps)

                otherStoresInstalledApps.removeAll(updatableFDroidApps)
                gPlayInstalledApps.addAll(otherStoresInstalledApps)
            }
        }

        openSourceInstalledApps.removeIf {
            blockedAppRepository.isBlockedApp(it)
        }

        gPlayInstalledApps.removeIf {
            blockedAppRepository.isBlockedApp(it)
        }

        // Get open source app updates
        if (openSourceInstalledApps.isNotEmpty()) {
            status = getUpdatesFromApi({
                applicationRepository.getApplicationDetails(
                    openSourceInstalledApps,
                    authData,
                    Origin.CLEANAPK
                )
            }, updateList)
        }

        // Get GPlay app updates
        if (getApplicationCategoryPreference().contains(APP_TYPE_ANY) &&
            gPlayInstalledApps.isNotEmpty()
        ) {

            val gplayStatus = getUpdatesFromApi({
                getGPlayUpdates(
                    gPlayInstalledApps,
                    authData
                )
            }, updateList)

            /**
             If any one of the sources is successful, status should be [ResultStatus.OK]
             **/
            status = if (status == ResultStatus.OK) status else gplayStatus
        }

        val systemApps = getSystemAppUpdates()
        val nonFaultyUpdateList = faultyAppRepository.removeFaultyApps(updateList)

        addSystemApps(updateList, nonFaultyUpdateList, systemApps)

        return Pair(updateList, status)
    }

    suspend fun getUpdatesOSS(): Pair<List<Application>, ResultStatus> {
        val updateList = mutableListOf<Application>()
        var status = ResultStatus.OK

        val openSourceInstalledApps = getOpenSourceInstalledApps().toMutableList()

        if (appLoungePreference.shouldUpdateAppsFromOtherStores()) {
            val otherStoresInstalledApps = getAppsFromOtherStores().toMutableList()

            // This list is based on app signatures
            val updatableFDroidApps =
                findPackagesMatchingFDroidSignatures(otherStoresInstalledApps)

            openSourceInstalledApps.addAll(updatableFDroidApps)
        }

        openSourceInstalledApps.removeIf {
            blockedAppRepository.isBlockedApp(it)
        }

        if (openSourceInstalledApps.isNotEmpty()) {
            status = getUpdatesFromApi({
                applicationRepository.getApplicationDetails(
                    openSourceInstalledApps,
                    AuthData("", ""),
                    Origin.CLEANAPK
                )
            }, updateList)
        }

        val systemApps = getSystemAppUpdates()
        val nonFaultyUpdateList = faultyAppRepository.removeFaultyApps(updateList)

        addSystemApps(updateList, nonFaultyUpdateList, systemApps)

        return Pair(updateList, status)
    }

    private suspend fun getSystemAppUpdates(): List<Application> {
        val systemApps = mutableListOf<Application>()
        getUpdatesFromApi({
            Pair(systemAppsUpdatesRepository.getSystemUpdates(), ResultStatus.OK)
        }, systemApps)
        return systemApps
    }

    /**
     * This method adds the system app updates at the beginning of the update list.
     * It will ensure our system apps are updated first, followed by other apps,
     * avoiding potential conflicts.
     *
     * Since installing an App Lounge update will cause App Lounge to be closed by the system,
     * it is added at the end of the list.
     */
    private fun addSystemApps(
        updateList: MutableList<Application>,
        nonFaultyApps: List<Application>,
        systemApps: List<Application>,
    ) {
        updateList.clear()
        updateList.addAll(systemApps)
        updateList.addAll(nonFaultyApps)

        // Move App Lounge to the end of the list
        val appLoungeItem = updateList.find {
            it.isSystemApp && it.package_name == context.packageName
        } ?: return
        updateList.remove(appLoungeItem)
        updateList.add(appLoungeItem)
    }

    /**
     * Lists apps directly updatable by App Lounge from the Open Source category.
     * (This includes apps installed by F-Droid client app, if used by the user;
     * F-Droid is not considered a third party source.)
     */
    private fun getOpenSourceInstalledApps(): List<String> {
        return userApplications.filter {
            appLoungePackageManager.getInstallerName(it.packageName) in listOf(
                context.packageName,
                PACKAGE_NAME_F_DROID,
                PACKAGE_NAME_F_DROID_PRIVILEGED,
            )
        }.map { it.packageName }
    }

    /**
     * Lists GPlay apps directly updatable by App Lounge.
     *
     * GPlay apps installed by App Lounge alone can have their installer package
     * set as "com.android.vending".
     */
    private fun getGPlayInstalledApps(): List<String> {
        return userApplications.filter {
            appLoungePackageManager.getInstallerName(it.packageName) in listOf(
                PACKAGE_NAME_ANDROID_VENDING,
            )
        }.map { it.packageName }
    }

    /**
     * Lists apps installed from other app stores.
     * (F-Droid client is not considered a third party source.)
     *
     * @return List of package names of apps installed from other app stores like
     * Aurora Store, Apk mirror, apps installed from browser, apps from ADB etc.
     */
    private fun getAppsFromOtherStores(): List<String> {
        val gplayAndOpenSourceInstalledApps = getGPlayInstalledApps() + getOpenSourceInstalledApps()
        return userApplications.filter {
            it.packageName !in gplayAndOpenSourceInstalledApps
        }.map { it.packageName }
    }

    /**
     * Runs API (GPlay api or CleanApk) and accumulates the updatable apps
     * into a provided list.
     *
     * @param apiFunction Function that calls an API method to fetch update information.
     * Apps returned is filtered to get only the apps which can be downloaded and updated.
     * @param updateAccumulationList A list into which the filtered results from
     * [apiFunction] is stored. The caller needs to read this list to get the update info.
     *
     * @return ResultStatus from calling [apiFunction].
     */
    private suspend fun getUpdatesFromApi(
        apiFunction: suspend () -> Pair<List<Application>, ResultStatus>,
        updateAccumulationList: MutableList<Application>,
    ): ResultStatus {
        val apiResult = apiFunction()
        val updatableApps = apiResult.first.filter {
            it.status == Status.UPDATABLE && it.filterLevel.isUnFiltered()
        }
        updateAccumulationList.addAll(updatableApps)
        return apiResult.second
    }

    /**
     * Bulk info from gplay api [PlayStoreRepositoryImpl.getAppsDetails] is not providing correct
     * geo restriction status of apps. So we get all individual app information asynchronously.
     * Example: in.startv.hotstar.dplus
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/7135
     */
    private suspend fun getGPlayUpdates(
        packageNames: List<String>,
        authData: AuthData
    ): Pair<List<Application>, ResultStatus> {

        val appsResults = coroutineScope {
            val deferredResults = packageNames.map { packageName ->
                async {
                    applicationRepository.getApplicationDetails(
                        "",
                        packageName,
                        authData,
                        Origin.GPLAY
                    )
                }
            }
            deferredResults.awaitAll()
        }

        val status = appsResults.find { it.second != ResultStatus.OK }?.second ?: ResultStatus.OK
        val appsList = appsResults.filter { it.first.package_name.isNotEmpty() }.map { it.first }

        return Pair(appsList, status)
    }

    /**
     * Takes a list of package names and for the apps present on F-Droid,
     * returns key value pairs of package names and their signatures.
     *
     * The signature for an app corresponds to the version currently
     * installed on the device.
     * If the current installed version for an app is (say) 7, then even if
     * the latest version is 10, we try to find the signature of version 7.
     * If signature for version 7 of the app is unavailable, then we put blank.
     *
     * If none of the apps mentioned in [installedPackageNames] are present on F-Droid,
     * then it returns an empty Map.
     *
     * Map is String : String = package name : signature
     */
    private suspend fun getFDroidAppsAndSignatures(installedPackageNames: List<String>): Map<String, String> {
        val appsAndSignatures = hashMapOf<String, String>()
        for (packageName in installedPackageNames) {
            updateAppsWithPGPSignature(packageName, appsAndSignatures)
        }
        return appsAndSignatures
    }

    private suspend fun updateAppsWithPGPSignature(
        packageName: String,
        appsAndSignatures: HashMap<String, String>
    ) {
            val cleanApkFusedApp = applicationRepository.getCleanapkAppDetails(packageName).first
            if (cleanApkFusedApp.package_name.isBlank()) {
                return
            }
            appsAndSignatures[packageName] = getPgpSignature(cleanApkFusedApp)
    }

    private suspend fun getPgpSignature(cleanApkApplication: Application): String {
        val installedVersionSignature = calculateSignatureVersion(cleanApkApplication)

        val downloadInfoResult = handleNetworkResult {
            applicationRepository
                .getOSSDownloadInfo(cleanApkApplication._id, installedVersionSignature)
                .body()?.download_data
        }

        val pgpSignature = downloadInfoResult.data?.signature ?: ""

        Timber.i(
            "Signature calculated for : ${cleanApkApplication.package_name}, " +
                    "signature version: $installedVersionSignature, " +
                    "is sig blank: ${pgpSignature.isBlank()}"
        )

        return pgpSignature
    }

    /**
     * Returns list of packages whose signature matches with the available listing on F-Droid.
     *
     * Example: If Element (im.vector.app) is installed from ApkMirror, then it's signature
     * will not match with the version of Element on F-Droid. So if Element is present
     * in [installedPackageNames], it will not be present in the list returned by this method.
     */
    private suspend fun findPackagesMatchingFDroidSignatures(
        installedPackageNames: List<String>,
    ): List<String> {
        val fDroidAppsAndSignatures = getFDroidAppsAndSignatures(installedPackageNames)

        val fDroidUpdatablePackageNames = fDroidAppsAndSignatures.filter {
            if (it.value.isEmpty()) return@filter false

            // For each installed app also present on F-droid, check signature of base APK.
            val baseApkPath = appLoungePackageManager.getBaseApkPath(it.key)
            if (baseApkPath.isEmpty()) return@filter false

            ApkSignatureManager.verifyFdroidSignature(context, baseApkPath, it.value, it.key)
        }.map { it.key }

        return fDroidUpdatablePackageNames
    }

    /**
     * Get signature version for the installed version of the app.
     * A signature version is like "update_XX" where XX is a 2 digit number.
     *
     * Example:
     * The installed versionCode of an app is (say) 7.
     * The latest available version is (say) 10, we need to update to this version.
     * The latest signature version is (say) "update_33".
     * Available builds of F-droid are (say):
     * version 10
     * version 9
     * version 8
     * version 7
     * ...
     * Index of version 7 from top is 3 (index of version 10 is 0).
     * So the corresponding signature version will be "update_(33-3)" = "update_30"
     */
    private suspend fun calculateSignatureVersion(latestCleanapkApp: Application): String {
        val packageName = latestCleanapkApp.package_name
        val latestSignatureVersion = latestCleanapkApp.latest_downloaded_version

        Timber.i("Latest signature version for $packageName : $latestSignatureVersion")

        val installedVersionCode = appLoungePackageManager.getVersionCode(packageName)
        val installedVersionName = appLoungePackageManager.getVersionName(packageName)

        Timber.i("Calculate signature for $packageName : $installedVersionCode, $installedVersionName")

        val latestSignatureVersionNumber = try {
            latestSignatureVersion.split("_")[1].toInt()
        } catch (e: Exception) {
            return ""
        }

        // Received list has build info of the latest version at the bottom.
        // We want it at the top.
        val builds = handleNetworkResult {
            fdroidRepository.getBuildVersionInfo(packageName)?.asReversed() ?: listOf()
        }.data

        val matchingIndex = builds?.find {
            it.versionCode == installedVersionCode && it.versionName == installedVersionName
        }?.run {
            builds.indexOf(this)
        } ?: return ""

        Timber.i("Build info match at index: $matchingIndex")

        /* If latest latest signature version is (say) "update_33"
         * corresponding to (say) versionCode 10, and we need to find signature
         * version of (say) versionCode 7, then we calculate signature version as:
         * "update_" + [33 (latestSignatureVersionNumber) - 3 (i.e. matchingIndex)] = "update_30"
         */
        return "update_${latestSignatureVersionNumber - matchingIndex}"
    }

    fun getApplicationCategoryPreference(): List<String> {
        return applicationRepository.getSelectedAppTypes()
    }
}
