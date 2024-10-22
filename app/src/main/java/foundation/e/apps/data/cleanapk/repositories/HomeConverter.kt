package foundation.e.apps.data.cleanapk.repositories

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import foundation.e.apps.R
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.data.cleanapk.data.home.CleanApkHome
import javax.inject.Inject

class HomeConverter @Inject constructor(
    @ApplicationContext val context: Context,
    private val applicationDataManager: ApplicationDataManager
) {

    suspend fun toGenericHome(cleanApkHome: CleanApkHome, appType: String): List<Home> {
        val list = mutableListOf<Home>()

        openSourceCategories.forEach { (key, value) ->
            when (key) {
                "top_updated_apps" -> {
                    applicationDataManager.prepareApps(cleanApkHome.top_updated_apps, list, value)
                }

                "top_updated_games" -> {
                    applicationDataManager.prepareApps(cleanApkHome.top_updated_games, list, value)
                }

                "popular_apps" -> {
                    applicationDataManager.prepareApps(cleanApkHome.popular_apps, list, value)
                }

                "popular_games" -> {
                    applicationDataManager.prepareApps(cleanApkHome.popular_games, list, value)
                }

                "popular_apps_in_last_24_hours" -> {
                    applicationDataManager.prepareApps(
                        cleanApkHome.popular_apps_in_last_24_hours,
                        list,
                        value
                    )
                }

                "popular_games_in_last_24_hours" -> {
                    applicationDataManager.prepareApps(
                        cleanApkHome.popular_games_in_last_24_hours,
                        list,
                        value
                    )
                }

                "discover" -> {
                    applicationDataManager.prepareApps(cleanApkHome.discover, list, value)
                }
            }
        }

        return list.map {
            it.source = appType
            it
        }
    }

    private val openSourceCategories: Map<String, String> by lazy {
        mapOf(
            "top_updated_apps" to context.getString(R.string.top_updated_apps),
            "top_updated_games" to context.getString(R.string.top_updated_games),
            "popular_apps_in_last_24_hours" to context.getString(R.string.popular_apps_in_last_24_hours),
            "popular_games_in_last_24_hours" to context.getString(R.string.popular_games_in_last_24_hours),
            "popular_apps" to context.getString(R.string.popular_apps),
            "popular_games" to context.getString(R.string.popular_games),
            "discover" to context.getString(R.string.discover)
        )
    }
}