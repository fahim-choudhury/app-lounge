/*
 * Copyright (C) 2019-2022  E FOUNDATION
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

package foundation.e.apps.utils.enums

/**
 * Use this class for various levels of filtering.
 *
 * Example 1: Searching for "Wild rift" should display the app, but show "N/A" for most cases.
 * This is because in some countries, the app is downloadable and in some countries it is not,
 * hence completely filtering it out of the search results is not the best thing to do.
 * Instead if we detect that the app is not downloadable for a region, we use [UI] level
 * filter; if it is downloadable for a different region, we then use [NONE] filter.
 *
 * Similar app: de.tlllr.tlllrfan
 *
 * Example 2: Some apps like "com.skype.m2" can not only be not downloaded, even its details
 * page cannot be opened. Such apps cannot be shown on lists. Hence we use the [DATA] filter.
 *
 * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5720
 */
enum class FilterLevel {
    UI, // Show the app in lists, but show "N/A" in the install button.
    DATA, // Filter the app out from lists and search results, don't show the app at all.
    NONE, // No restrictions
    UNKNOWN, // Not initialised yet
}

fun FilterLevel.isUnFiltered(): Boolean = this == FilterLevel.NONE
fun FilterLevel.isInitialized(): Boolean = this != FilterLevel.UNKNOWN
