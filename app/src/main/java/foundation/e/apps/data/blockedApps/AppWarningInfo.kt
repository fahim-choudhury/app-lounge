/*
 * Copyright (C) 2022 E FOUNDATION
 * Copyright (C) 2024 MURENA SAS
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
 *
 */

package foundation.e.apps.data.blockedApps

import com.google.gson.annotations.SerializedName

data class AppWarningInfo(
    @SerializedName("not_working_apps") val notWorkingApps: List<String>,
    @SerializedName("zero_privacy_apps") val zeroPrivacyApps: List<String>,
    @SerializedName("third_party_store_apps") val thirdPartyStoreApps: List<String> = emptyList()
)
