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

package foundation.e.apps.data.application.category

import android.content.Context
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.StreamCluster
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.AppSourcesContainer
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Category
import foundation.e.apps.data.application.utils.CategoryType
import foundation.e.apps.data.application.utils.CategoryUtils
import foundation.e.apps.data.application.utils.toApplication
import foundation.e.apps.data.application.utils.toCategory
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.enums.AppTag
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Source
import foundation.e.apps.data.enums.isUnFiltered
import foundation.e.apps.data.handleNetworkResult
import foundation.e.apps.data.preference.AppLoungePreference
import javax.inject.Inject

class CategoryApiImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLoungePreference: AppLoungePreference,
    private val appSources: AppSourcesContainer,
    private val applicationDataManager: ApplicationDataManager
) : CategoryApi {

    override suspend fun getCategoriesList(type: CategoryType): Pair<List<Category>, ResultStatus> {
        val categoriesList = mutableListOf<Category>()
        var apiStatus = handleAllSourcesCategories(categoriesList, type)

        categoriesList.sortBy { item -> item.title.lowercase() }
        return Pair(categoriesList, apiStatus)
    }

    private suspend fun handleAllSourcesCategories(
        categoriesList: MutableList<Category>,
        type: CategoryType,
    ): ResultStatus {
        val categoryResults: MutableList<ResultStatus> = mutableListOf()

        if (appLoungePreference.isOpenSourceSelected()) {
            categoryResults.add(fetchCategoryResult(categoriesList, type, Source.OPEN))
        }

        if (appLoungePreference.isPWASelected()) {
            categoryResults.add(fetchCategoryResult(categoriesList, type, Source.PWA))
        }

        if (appLoungePreference.isGplaySelected()) {
            categoryResults.add(fetchCategoryResult(categoriesList, type, Source.GPLAY))
        }

        return categoryResults.find { it != ResultStatus.OK } ?: ResultStatus.OK
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
            val playResponse = appSources.gplayRepo.getCategories(type).map { gplayCategory ->
                val category = gplayCategory.toCategory()
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
                    appSources.cleanApkAppsRepo.getCategories().body()
                }

                Source.PWA -> {
                    tag = AppTag.PWA(context.getString(R.string.pwa))
                    appSources.cleanApkPWARepo.getCategories().body()
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
                appSources.gplayRepo.getAppsByCategory(category, pageUrl) as StreamCluster

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
            appSources.cleanApkAppsRepo.getAppsByCategory(category).body()
        }

        Source.PWA -> {
            appSources.cleanApkPWARepo.getAppsByCategory(category).body()
        }

        else -> null
    }
}
