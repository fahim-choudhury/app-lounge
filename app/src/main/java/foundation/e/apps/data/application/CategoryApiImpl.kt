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
import foundation.e.apps.data.application.utils.transformToApplication
import foundation.e.apps.data.application.utils.transformToFusedCategory
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.AppTag
import foundation.e.apps.data.enums.ResultStatus
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

    companion object {
        private const val CATEGORY_TITLE_REPLACEABLE_CONJUNCTION = "&"
        private const val CATEGORY_OPEN_GAMES_ID = "game_open_games"
        private const val CATEGORY_OPEN_GAMES_TITLE = "Open games"
    }

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
    override suspend fun getCategoriesList(type: CategoryType): Triple<List<Category>, String, ResultStatus> {
        val categoriesList = mutableListOf<Category>()
        val preferredApplicationType = preferenceManagerModule.preferredApplicationType()
        var apiStatus: ResultStatus = ResultStatus.OK
        var applicationCategoryType = preferredApplicationType

        handleAllSourcesCategories(categoriesList, type).run {
            if (first != ResultStatus.OK) {
                apiStatus = first
                applicationCategoryType = second
            }
        }
        categoriesList.sortBy { item -> item.title.lowercase() }
        return Triple(categoriesList, applicationCategoryType, apiStatus)
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
    ): Pair<ResultStatus, String> {
        var apiStatus = ResultStatus.OK
        var errorApplicationCategory = ""

        if (preferenceManagerModule.isOpenSourceSelected()) {
            val openSourceCategoryResult = fetchOpenSourceCategories(type)
            categoriesList.addAll(openSourceCategoryResult.second)
            apiStatus = openSourceCategoryResult.first
            errorApplicationCategory = openSourceCategoryResult.third
        }

        if (preferenceManagerModule.isPWASelected()) {
            val pwaCategoriesResult = fetchPWACategories(type)
            categoriesList.addAll(pwaCategoriesResult.second)
            apiStatus = pwaCategoriesResult.first
            errorApplicationCategory = pwaCategoriesResult.third
        }

        if (preferenceManagerModule.isGplaySelected()) {
            val gplayCategoryResult = fetchGplayCategories(
                type,
            )
            categoriesList.addAll(gplayCategoryResult.data ?: listOf())
            apiStatus = gplayCategoryResult.getResultStatus()
            errorApplicationCategory = ApplicationApi.APP_TYPE_ANY
        }

        return Pair(apiStatus, errorApplicationCategory)
    }

    private suspend fun fetchGplayCategories(
        type: CategoryType,
    ): ResultSupreme<List<Category>> {
        val categoryList = mutableListOf<Category>()

        return handleNetworkResult {
            val playResponse = gplayRepository.getCategories(type).map { app ->
                val category = app.transformToFusedCategory()
                category.drawable =
                    CategoryUtils.provideAppsCategoryIconResource(getCategoryIconName(category))
                category
            }
            categoryList.addAll(playResponse)
            categoryList
        }
    }

    private fun getCategoryIconName(category: Category): String {
        var categoryTitle = if (category.tag.getOperationalTag().contentEquals(AppTag.GPlay().getOperationalTag()))
            category.id else category.title

        if (categoryTitle.contains(CATEGORY_TITLE_REPLACEABLE_CONJUNCTION)) {
            categoryTitle = categoryTitle.replace(CATEGORY_TITLE_REPLACEABLE_CONJUNCTION, "and")
        }
        categoryTitle = categoryTitle.replace(' ', '_')
        return categoryTitle.lowercase()
    }

    private suspend fun fetchPWACategories(
        type: CategoryType,
    ): Triple<ResultStatus, List<Category>, String> {
        val fusedCategoriesList = mutableListOf<Category>()
        val result = handleNetworkResult {
            cleanApkPWARepository.getCategories().body()?.let {
                fusedCategoriesList.addAll(
                    getFusedCategoryBasedOnCategoryType(
                        it, type, AppTag.PWA(context.getString(R.string.pwa))
                    )
                )
            }
        }

        return Triple(result.getResultStatus(), fusedCategoriesList, ApplicationApi.APP_TYPE_PWA)
    }

    private suspend fun fetchOpenSourceCategories(
        type: CategoryType,
    ): Triple<ResultStatus, List<Category>, String> {
        val categoryList = mutableListOf<Category>()
        val result = handleNetworkResult {
            cleanApkAppsRepository.getCategories().body()?.let {
                categoryList.addAll(
                    getFusedCategoryBasedOnCategoryType(
                        it,
                        type,
                        AppTag.OpenSource(context.getString(R.string.open_source))
                    )
                )
            }
        }

        return Triple(result.getResultStatus(), categoryList, ApplicationApi.APP_TYPE_OPEN)
    }

    private fun getFusedCategoryBasedOnCategoryType(
        categories: Categories,
        categoryType: CategoryType,
        tag: AppTag
    ): List<Category> {
        return when (categoryType) {
            CategoryType.APPLICATION -> {
                getCategories(categories, categories.apps, tag)
            }

            CategoryType.GAMES -> {
                getCategories(categories, categories.games, tag)
            }
        }
    }

    private fun getCategories(
        categories: Categories,
        categoryNames: List<String>,
        tag: AppTag
    ) = categoryNames.map { category ->
        Category(
            id = category,
            title = getCategoryTitle(category, categories),
            drawable = CategoryUtils.provideAppsCategoryIconResource(category),
            tag = tag
        )
    }

    private fun getCategoryTitle(category: String, categories: Categories): String {
        return if (category.contentEquals(CATEGORY_OPEN_GAMES_ID)) {
            CATEGORY_OPEN_GAMES_TITLE
        } else {
            categories.translations.getOrDefault(category, "")
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
            if (!nextPageUrl.isNullOrEmpty()) {
                applicationList.add(Application(isPlaceHolder = true))
            }
            Pair(applicationList, nextPageUrl)
        }
    }

    /**
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
                val filter = applicationDataManager.getAppFilterLevel(it.transformToApplication(context), authData)
                if (filter.isUnFiltered()) {
                    filteredApplications.add(
                        it.transformToApplication(context).apply {
                            this.filterLevel = filter
                        }
                    )
                }
            }
            filteredApplications
        }
    }

    override suspend fun getPWAApps(category: String): ResultSupreme<Pair<List<Application>, String>> {
        val list = mutableListOf<Application>()
        val result = handleNetworkResult {
            val response = cleanApkPWARepository.getAppsByCategory(category).body()
            response?.apps?.forEach {
                applicationDataManager.updateStatus(it)
                it.updateType()
                applicationDataManager.updateFilterLevel(null, it)
                list.add(it)
            }
        }
        return ResultSupreme.create(result.getResultStatus(), Pair(list, ""))
    }

    override suspend fun getOpenSourceApps(category: String): ResultSupreme<Pair<List<Application>, String>> {
        val list = mutableListOf<Application>()
        val result = handleNetworkResult {
            val response = cleanApkAppsRepository.getAppsByCategory(category).body()
            response?.apps?.forEach {
                applicationDataManager.updateStatus(it)
                it.updateType()
                applicationDataManager.updateFilterLevel(null, it)
                list.add(it)
            }
        }
        return ResultSupreme.create(result.getResultStatus(), Pair(list, ""))
    }
}
