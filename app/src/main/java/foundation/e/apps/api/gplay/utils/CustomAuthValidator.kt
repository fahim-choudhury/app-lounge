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

package foundation.e.apps.api.gplay.utils

import com.aurora.gplayapi.GooglePlayApi
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.data.providers.HeaderProvider
import com.aurora.gplayapi.helpers.AuthValidator
import com.aurora.gplayapi.helpers.BaseHelper
import com.aurora.gplayapi.network.IHttpClient

/**
 * Custom implementation of [AuthValidator].
 * This returns [PlayResponse] for [getValidityResponse],
 * which is a replacement of [AuthValidator.isValid] which just returned a boolean.
 * A PlayResponse object allows us to look into whether the request had any network error.
 *
 * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5709
 */
class CustomAuthValidator(authData: AuthData): BaseHelper(authData) {

    override fun using(httpClient: IHttpClient) = apply {
        this.httpClient = httpClient
    }

    fun getValidityResponse(): PlayResponse {
        val endpoint: String = GooglePlayApi.URL_SYNC
        val headers = HeaderProvider.getDefaultHeaders(authData)
        return httpClient.post(endpoint, headers, hashMapOf())
    }
}