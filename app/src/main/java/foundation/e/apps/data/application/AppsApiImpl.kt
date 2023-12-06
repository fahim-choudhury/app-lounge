package foundation.e.apps.data.application

import android.content.Context
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.cleanapk.data.app.Application as CleanApkApplication
import foundation.e.apps.data.application.utils.toApplication
import foundation.e.apps.data.cleanapk.data.search.Search
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.FilterLevel
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.enums.isUnFiltered
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.preference.PreferenceManagerModule
import foundation.e.apps.ui.applicationlist.ApplicationDiffUtil
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Named

class AppsApiImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManagerModule: PreferenceManagerModule,
    @Named("gplayRepository") private val gplayRepository: PlayStoreRepository,
    @Named("cleanApkAppsRepository") private val cleanApkAppsRepository: CleanApkRepository,
    private val applicationDataManager: ApplicationDataManager
) : AppsApi {

    companion object {
        private const val KEY_SEARCH_PACKAGE_NAME = "package_name"
    }

    override suspend fun getCleanapkAppDetails(packageName: String): Pair<Application, ResultStatus> {
        var application = Application()
        val result = handleNetworkResult {
            val result = cleanApkAppsRepository.getSearchResult(
                packageName,
                KEY_SEARCH_PACKAGE_NAME
            ).body()

            if (result?.hasSingleResult() == true) {
                application =
                    (cleanApkAppsRepository.getAppDetails(result.apps[0]._id) as Response<CleanApkApplication>)
                        .body()?.app ?: Application()
            }

            application.updateFilterLevel(null)
        }
        return Pair(application, result.getResultStatus())
    }

    /*
     * Handy method to run on an instance of FusedApp to update its filter level.
     */
    private suspend fun Application.updateFilterLevel(authData: AuthData?) {
        this.filterLevel = applicationDataManager.getAppFilterLevel(this, authData)
    }

    override suspend fun getApplicationDetails(
        packageNameList: List<String>,
        authData: AuthData,
        origin: Origin
    ): Pair<List<Application>, ResultStatus> {
        val list = mutableListOf<Application>()

        val response: Pair<List<Application>, ResultStatus> =
            if (origin == Origin.CLEANAPK) {
                getAppDetailsListFromCleanApk(packageNameList)
            } else {
                getAppDetailsListFromGPlay(packageNameList, authData)
            }

        response.first.forEach {
            if (it.package_name.isNotBlank()) {
                applicationDataManager.updateStatus(it)
                it.updateType()
                list.add(it)
            }
        }

        return Pair(list, response.second)
    }

    /*
     * Get app details of a list of apps from cleanapk.
     * Returns list of FusedApp and ResultStatus - which will reflect error if even one app fails.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
     */
    private suspend fun getAppDetailsListFromCleanApk(
        packageNameList: List<String>,
    ): Pair<List<Application>, ResultStatus> {
        var status = ResultStatus.OK
        val applicationList = mutableListOf<Application>()

        for (packageName in packageNameList) {
            val result = getCleanApkSearchResultByPackageName(packageName, applicationList)
            status = result.getResultStatus()

            if (status != ResultStatus.OK) {
                return Pair(applicationList, status)
            }
        }

        return Pair(applicationList, status)
    }

    private suspend fun getAppDetailsListFromGPlay(
        packageNameList: List<String>,
        authData: AuthData,
    ): Pair<List<Application>, ResultStatus> {
        val applicationList = mutableListOf<Application>()

        val result = handleNetworkResult {
            gplayRepository.getAppsDetails(packageNameList).forEach { app ->
                handleFilteredApps(app, authData, applicationList)
            }
        }

        return Pair(applicationList, result.getResultStatus())
    }

    /*
     * Some apps are restricted to locations. Example "com.skype.m2".
     * For restricted apps, check if it is possible to get their specific app info.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5174
     */
    private suspend fun handleFilteredApps(
        app: App,
        authData: AuthData,
        applicationList: MutableList<Application>
    ) {
        val filter = applicationDataManager.getAppFilterLevel(app.toApplication(context), authData)
        if (filter.isUnFiltered()) {
            applicationList.add(
                app.toApplication(context).apply {
                    filterLevel = filter
                }
            )
        }
    }

    private suspend fun getCleanApkSearchResultByPackageName(
        packageName: String,
        applicationList: MutableList<Application>
    ) = handleNetworkResult {
        cleanApkAppsRepository.getSearchResult(
            packageName,
            KEY_SEARCH_PACKAGE_NAME
        ).body()?.run {
            handleCleanApkSearch(applicationList)
        }
    }

    private suspend fun Search.handleCleanApkSearch(
        applicationList: MutableList<Application>
    ) {
        if (hasSingleResult()) {
            applicationList.add(
                apps[0].apply {
                    updateFilterLevel(null)
                }
            )
        }
    }

    private fun Search.hasSingleResult() =
        apps.isNotEmpty() && numberOfResults == 1

    override suspend fun getApplicationDetails(
        id: String,
        packageName: String,
        authData: AuthData,
        origin: Origin
    ): Pair<Application, ResultStatus> {
        var application: Application?

        val result = handleNetworkResult {
            application = if (origin == Origin.CLEANAPK) {
                (cleanApkAppsRepository.getAppDetails(id) as Response<CleanApkApplication>).body()?.app
            } else {
                val app = gplayRepository.getAppDetails(packageName) as App?
                app?.toApplication(context)
            }

            application?.let {
                applicationDataManager.updateStatus(it)
                it.updateType()
                it.updateSource(context)
                it.updateFilterLevel(authData)
            }
            application
        }

        return Pair(result.data ?: Application(), result.getResultStatus())
    }

    override fun getFusedAppInstallationStatus(application: Application): Status {
        return applicationDataManager.getFusedAppInstallationStatus(application)
    }

    override suspend fun getAppFilterLevel(
        application: Application,
        authData: AuthData?
    ): FilterLevel {
        return applicationDataManager.getAppFilterLevel(application, authData)
    }

    override fun isAnyFusedAppUpdated(
        newApplications: List<Application>,
        oldApplications: List<Application>
    ): Boolean {
        if (newApplications.size != oldApplications.size) {
            return true
        }

        return areApplicationsChanged(newApplications, oldApplications)
    }

    private fun areApplicationsChanged(
        newApplications: List<Application>,
        oldApplications: List<Application>
    ): Boolean {
        val fusedAppDiffUtil = ApplicationDiffUtil()
        newApplications.forEach {
            val indexOfNewFusedApp = newApplications.indexOf(it)
            if (!fusedAppDiffUtil.areContentsTheSame(it, oldApplications[indexOfNewFusedApp])) {
                return true
            }
        }
        return false
    }

    override fun isAnyAppInstallStatusChanged(currentList: List<Application>): Boolean {
        currentList.forEach {
            if (it.status == Status.INSTALLATION_ISSUE) {
                return@forEach
            }

            val currentAppStatus = getFusedAppInstallationStatus(it)
            if (it.status != currentAppStatus) {
                return true
            }
        }
        return false
    }

    override fun isOpenSourceSelected() = preferenceManagerModule.isOpenSourceSelected()
}
