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

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Category
import foundation.e.apps.data.application.utils.CategoryType
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Source

interface CategoryApi {

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
    suspend fun getCategoriesList(
        type: CategoryType,
    ): Pair<List<Category>, ResultStatus>

    suspend fun getGplayAppsByCategory(
        authData: AuthData,
        category: String,
        pageUrl: String?
    ): ResultSupreme<Pair<List<Application>, String>>

    suspend fun getCleanApkAppsByCategory(
        category: String,
        source: Source
    ): ResultSupreme<Pair<List<Application>, String>>
}
