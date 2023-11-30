/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
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

package foundation.e.apps.data.application

import android.content.Context
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.StreamCluster
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Category
import foundation.e.apps.data.application.utils.CategoryType
import foundation.e.apps.data.application.utils.CategoryUtils
import foundation.e.apps.data.application.utils.toApplication
import foundation.e.apps.data.application.utils.toCategory
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.AppTag
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.enums.isUnFiltered
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.preference.PreferenceManagerModule
import javax.inject.Inject
import javax.inject.Named

class CategoryApiImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManagerModule: PreferenceManagerModule,
    @Named("gplayRepository") private val gplayRepository: PlayStoreRepository,
    @Named("cleanApkAppsRepository") private val cleanApkAppsRepository: CleanApkRepository,
    @Named("cleanApkPWARepository") private val cleanApkPWARepository: CleanApkRepository,
    private val applicationDataManager: ApplicationDataManager
) : CategoryApi {

    /*
     * Return three elements from the function.
     * - List<FusedCategory> : List of categories.
     * - String : String of application type - By default it is the value in preferences.
     * In case there is any failure, for a specific type in handleAllSourcesCategories(),
     * the string value is of that type.
     * - ResultStatus : ResultStatus - by default is ResultStatus.OK. But in case there is a failure in
     * any application category type, then it takes value of that failure.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
     */
    override suspend fun getCategoriesList(type: CategoryType): Pair<List<Category>, ResultStatus> {
        val categoriesList = mutableListOf<Category>()
        var apiStatus = handleAllSourcesCategories(categoriesList, type)

        categoriesList.sortBy { item -> item.title.lowercase() }
        return Pair(categoriesList, apiStatus)
    }

    /*
     * Function to populate a given category list, from all GPlay categories, open source categories,
     * and PWAs.
     *
     * Returns: Pair of:
     * - ResultStatus - by default ResultStatus.OK, but can be different in case of an error in any category.
     * - String - Application category type having error. If no error, then blank string.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
     */
    private suspend fun handleAllSourcesCategories(
        categoriesList: MutableList<Category>,
        type: CategoryType,
    ): ResultStatus {
        var categoryResult: ResultStatus = ResultStatus.OK

        if (preferenceManagerModule.isOpenSourceSelected()) {
            categoryResult = fetchCategoryResult(categoriesList, type, Source.OPEN)
        }

        if (preferenceManagerModule.isPWASelected()) {
            categoryResult = fetchCategoryResult(categoriesList, type, Source.PWA)
        }

        if (preferenceManagerModule.isGplaySelected()) {
            categoryResult = fetchCategoryResult(categoriesList, type, Source.GPLAY)
        }

        return categoryResult
    }

    private suspend fun fetchCategoryResult(
        categoriesList: MutableList<Category>,
        type: CategoryType,
        source: Source
    ): ResultStatus {
        val categoryResult = when (source) {
            Source.OPEN -> {
                fetchCleanApkCategories(type, Source.OPEN)
            }

            Source.PWA -> {
                fetchCleanApkCategories(type, Source.PWA)
            }

            else -> {
                fetchGplayCategories(type)
            }
        }

        categoryResult.let {
            categoriesList.addAll(it.first)
        }

        return categoryResult.second
    }

    private suspend fun fetchGplayCategories(
        type: CategoryType,
    ): Pair<List<Category>, ResultStatus> {
        val categoryList = mutableListOf<Category>()
        val result = handleNetworkResult {
            val playResponse = gplayRepository.getCategories(type).map { app ->
                val category = app.toCategory()
                category.drawable =
                    CategoryUtils.provideAppsCategoryIconResource(
                        CategoryUtils.getCategoryIconName(category)
                    )
                category
            }

            categoryList.addAll(playResponse)
            categoryList
        }

        return Pair(result.data ?: listOf(), result.getResultStatus())
    }

    private suspend fun fetchCleanApkCategories(
        type: CategoryType,
        source: Source
    ): Pair<List<Category>, ResultStatus> {
        val categoryList = mutableListOf<Category>()
        var tag: AppTag? = null

        val result = handleNetworkResult {
            val categories = when (source) {
                Source.OPEN -> {
                    tag = AppTag.OpenSource(context.getString(R.string.open_source))
                    cleanApkAppsRepository.getCategories().body()
                }

                Source.PWA -> {
                    tag = AppTag.PWA(context.getString(R.string.pwa))
                    cleanApkPWARepository.getCategories().body()
                }

                else -> null
            }

            categories?.let {
                categoryList.addAll(getFusedCategoryBasedOnCategoryType(it, type, tag!!))
            }
        }

        return Pair(categoryList, result.getResultStatus())
    }

    private fun getFusedCategoryBasedOnCategoryType(
        categories: Categories,
        categoryType: CategoryType,
        tag: AppTag
    ): List<Category> {
        return when (categoryType) {
            CategoryType.APPLICATION -> {
                CategoryUtils.getCategories(categories, categories.apps, tag)
            }

            CategoryType.GAMES -> {
                CategoryUtils.getCategories(categories, categories.games, tag)
            }
        }
    }

    override suspend fun getGplayAppsByCategory(
        authData: AuthData,
        category: String,
        pageUrl: String?
    ): ResultSupreme<Pair<List<Application>, String>> {
        var applicationList: MutableList<Application> = mutableListOf()
        var nextPageUrl = ""

        return handleNetworkResult {
            val streamCluster =
                gplayRepository.getAppsByCategory(category, pageUrl) as StreamCluster

            val filteredAppList = filterRestrictedGPlayApps(authData, streamCluster.clusterAppList)
            filteredAppList.data?.let {
                applicationList = it.toMutableList()
            }

            nextPageUrl = streamCluster.clusterNextPageUrl
            if (nextPageUrl.isNotEmpty()) {
                applicationList.add(Application(isPlaceHolder = true))
            }
            Pair(applicationList, nextPageUrl)
        }
    }

    /*
     * Filter out apps which are restricted, whose details cannot be fetched.
     * If an app is restricted, we do try to fetch the app details inside a
     * try-catch block. If that fails, we remove the app, else we keep it even
     * if it is restricted.
     *
     * Popular example: "com.skype.m2"
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5174
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
     */
    private suspend fun filterRestrictedGPlayApps(
        authData: AuthData,
        appList: List<App>,
    ): ResultSupreme<List<Application>> {
        val filteredApplications = mutableListOf<Application>()
        return handleNetworkResult {
            appList.forEach {
                val filter = applicationDataManager.getAppFilterLevel(
                    it.toApplication(context),
                    authData
                )

                if (filter.isUnFiltered()) {
                    filteredApplications.add(
                        it.toApplication(context).apply {
                            this.filterLevel = filter
                        }
                    )
                }
            }
            filteredApplications
        }
    }

    override suspend fun getCleanApkAppsByCategory(
        category: String,
        source: Source
    ): ResultSupreme<Pair<List<Application>, String>> {
        val list = mutableListOf<Application>()
        val result = handleNetworkResult {
            val response = getCleanApkAppsResponse(source, category)

            response?.apps?.forEach {
                applicationDataManager.updateStatus(it)
                it.updateType()
                applicationDataManager.updateFilterLevel(null, it)
                list.add(it)
            }
        }
        return ResultSupreme.create(result.getResultStatus(), Pair(list, ""))
    }

    private suspend fun getCleanApkAppsResponse(
        source: Source,
        category: String
    ) = when (source) {
        Source.OPEN -> {
            cleanApkAppsRepository.getAppsByCategory(category).body()
        }

        Source.PWA -> {
            cleanApkPWARepository.getAppsByCategory(category).body()
        }

        else -> null
    }
}
