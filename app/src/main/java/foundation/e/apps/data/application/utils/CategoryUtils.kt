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

package foundation.e.apps.data.application.utils

import foundation.e.apps.R
import foundation.e.apps.data.application.data.Category
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.enums.AppTag

object CategoryUtils {

    private const val CATEGORY_OPEN_GAMES_ID = "game_open_games"
    private const val CATEGORY_OPEN_GAMES_TITLE = "Open games"
    private const val CATEGORY_TITLE_REPLACEABLE_CONJUNCTION = "&"
    private const val CATEGORY_TITLE_CONJUNCTION = "and"

    private val categoryIconMap = mapOf(
        "comics" to R.drawable.ic_cat_comics,
        "connectivity" to R.drawable.ic_cat_connectivity,
        "development" to R.drawable.ic_cat_development,
        "education" to R.drawable.ic_cat_education,
        "graphics" to R.drawable.ic_cat_graphics,
        "internet" to R.drawable.ic_cat_internet,
        "music_and_audio" to R.drawable.ic_cat_music_and_audio,
        "entertainment" to R.drawable.ic_cat_entertainment,
        "tools" to R.drawable.ic_cat_tools,
        "security" to R.drawable.ic_cat_security,
        "system" to R.drawable.ic_cat_system,
        "system_apps" to R.drawable.ic_cat_system,
        "communication" to R.drawable.ic_cat_communication,
        "medical" to R.drawable.ic_cat_medical,
        "lifestyle" to R.drawable.ic_cat_lifestyle,
        "video_players" to R.drawable.ic_cat_video_players,
        "video_players_and_editors" to R.drawable.ic_cat_video_players,
        "events" to R.drawable.ic_cat_events,
        "productivity" to R.drawable.ic_cat_productivity,
        "house_and_home" to R.drawable.ic_cat_house_and_home,
        "art_and_design" to R.drawable.ic_art_and_design,
        "photography" to R.drawable.ic_cat_photography,
        "auto_and_vehicles" to R.drawable.ic_auto_and_vehicles,
        "books_and_reference" to R.drawable.ic_books_and_reference,
        "social" to R.drawable.ic_cat_social,
        "travel_and_local" to R.drawable.ic_cat_travel_and_local,
        "beauty" to R.drawable.ic_beauty,
        "personalization" to R.drawable.ic_cat_personalization,
        "business" to R.drawable.ic_business,
        "health_and_fitness" to R.drawable.ic_cat_health_and_fitness,
        "dating" to R.drawable.ic_cat_dating,
        "news_and_magazines" to R.drawable.ic_cat_news_and_magazine,
        "finance" to R.drawable.ic_cat_finance,
        "food_and_drink" to R.drawable.ic_cat_food_and_drink,
        "shopping" to R.drawable.ic_cat_shopping,
        "libraries_and_demo" to R.drawable.ic_cat_libraries_and_demo,
        "sports" to R.drawable.ic_cat_sports,
        "maps_and_navigation" to R.drawable.ic_cat_maps_and_navigation,
        "parenting" to R.drawable.ic_cat_parenting,
        "weather" to R.drawable.ic_cat_weather,
        "topic/family" to R.drawable.ic_cat_family,
        "game_card" to R.drawable.ic_cat_game_card,
        "game_action" to R.drawable.ic_cat_game_action,
        "game_board" to R.drawable.ic_cat_game_board,
        "game_role_playing" to R.drawable.ic_cat_game_role_playing,
        "game_arcade" to R.drawable.ic_cat_game_arcade,
        "game_casino" to R.drawable.ic_cat_game_casino,
        "game_adventure" to R.drawable.ic_cat_game_adventure,
        "game_casual" to R.drawable.ic_cat_game_casual,
        "game_puzzle" to R.drawable.ic_cat_game_puzzle,
        "game_strategy" to R.drawable.ic_cat_game_strategy,
        "game_educational" to R.drawable.ic_cat_game_educational,
        "game_music" to R.drawable.ic_cat_game_music,
        "game_racing" to R.drawable.ic_cat_game_racing,
        "game_simulation" to R.drawable.ic_cat_game_simulation,
        "game_sports" to R.drawable.ic_cat_game_sports,
        "game_trivia" to R.drawable.ic_cat_game_trivia,
        "game_word" to R.drawable.ic_cat_game_word,
        "game_open_games" to R.drawable.ic_cat_open_games,
        "pwa_education" to R.drawable.ic_cat_education,
        "pwa_entertainment" to R.drawable.ic_cat_entertainment,
        "food & drink" to R.drawable.ic_cat_food_nd_drink,
        "pwa_lifestyle" to R.drawable.ic_cat_lifestyle,
        "music" to R.drawable.ic_cat_game_music,
        "news" to R.drawable.ic_cat_news,
        "pwa_games" to R.drawable.ic_cat_game_action,
        "reference" to R.drawable.ic_cat_reference,
        "pwa_shopping" to R.drawable.ic_cat_shopping,
        "pwa_social" to R.drawable.ic_cat_social,
        "pwa_sports" to R.drawable.ic_cat_sports,
        "travel" to R.drawable.ic_cat_travel,
        "pwa_business" to R.drawable.ic_business,
        "watch_face" to R.drawable.ic_watchface,
        "android_wear" to R.drawable.ic_watch_apps,
    )

    fun provideAppsCategoryIconResource(categoryId: String) =
        categoryIconMap[categoryId] ?: R.drawable.ic_cat_default

    fun getCategories(
        categories: Categories,
        categoryNames: List<String>,
        tag: AppTag
    ) = categoryNames.map { category ->
        Category(
            id = category,
            title = getCategoryTitle(category, categories),
            drawable = provideAppsCategoryIconResource(category),
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

    fun getCategoryIconName(category: Category): String {
        var categoryTitle =
            if (category.tag.getOperationalTag().contentEquals(AppTag.GPlay().getOperationalTag()))
                category.id else category.title

        if (categoryTitle.contains(CATEGORY_TITLE_REPLACEABLE_CONJUNCTION)) {
            categoryTitle = categoryTitle.replace(
                CATEGORY_TITLE_REPLACEABLE_CONJUNCTION,
                CATEGORY_TITLE_CONJUNCTION
            )
        }

        categoryTitle = categoryTitle.replace(' ', '_')
        return categoryTitle.lowercase()
    }
}

enum class CategoryType {
    APPLICATION, GAMES
}
