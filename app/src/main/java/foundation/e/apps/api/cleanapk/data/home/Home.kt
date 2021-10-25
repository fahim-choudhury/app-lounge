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

package foundation.e.apps.api.cleanapk.data.home

import foundation.e.apps.api.fused.data.HomeApp

data class Home(
    val top_updated_apps: List<HomeApp>,
    val top_updated_games: List<HomeApp>,
    val banner_apps: List<HomeApp>,
    val popular_apps_in_last_24_hours: List<HomeApp>,
    val popular_games_in_last_24_hours: List<HomeApp>,
    val discover: List<HomeApp>
)
