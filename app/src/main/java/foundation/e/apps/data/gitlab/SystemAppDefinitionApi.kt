/*
 * Copyright (C) 2021-2024 MURENA SAS
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

package foundation.e.apps.data.gitlab

import foundation.e.apps.data.gitlab.models.SystemAppInfo
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/*
Those API method client must fit with gitlab releases API
https://docs.gitlab.com/ee/api/releases/#download-a-release-asset
TODO Option to consider at the end of the implementation:
Can we rename this interface into: GitlabReleaseApi ?
 */
interface SystemAppDefinitionApi {

    companion object {
        const val BASE_URL =
            "https://gitlab.e.foundation/api/v4/projects/"

        private const val PROJECT_ID_PLACEHOLDER = "projectId"
        private const val RELEASE_TYPE_PLACEHOLDER = "releaseType"
        private const val TAG_NAME_PLACEHOLDER = "gitlabTagName"

        private const val LIST_RELEASES_URL_SEGMENT =
            "{$PROJECT_ID_PLACEHOLDER}/releases"
        private const val UPDATE_INFO_BY_TAG_URL_SEGMENT =
            "{$PROJECT_ID_PLACEHOLDER}/releases/{$TAG_NAME_PLACEHOLDER}/downloads/json/{$RELEASE_TYPE_PLACEHOLDER}.json"
        private const val LATEST_UPDATE_INFO_URL_SEGMENT =
            "{$PROJECT_ID_PLACEHOLDER}/releases/permalink/latest/downloads/json/{$RELEASE_TYPE_PLACEHOLDER}.json"
    }


    @GET(LIST_RELEASES_URL_SEGMENT)
    suspend fun getSystemAppReleases(
        @Path(PROJECT_ID_PLACEHOLDER) projectId: Int
    )//: Response<@TODO>

    @GET(UPDATE_INFO_BY_TAG_URL_SEGMENT)
    suspend fun getSystemAppUpdateInfoByTag(
        @Path(PROJECT_ID_PLACEHOLDER) projectId: Int,
        @Path(TAG_NAME_PLACEHOLDER) gitlabTagName: String,
        @Path(RELEASE_TYPE_PLACEHOLDER) releaseType: String,
    ): Response<SystemAppInfo>

    @GET(LATEST_UPDATE_INFO_URL_SEGMENT)
    suspend fun getLatestSystemAppUpdateInfo(
        @Path(PROJECT_ID_PLACEHOLDER) projectId: Int,
        @Path(RELEASE_TYPE_PLACEHOLDER) releaseType: String,
    ): Response<SystemAppInfo>
}
