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

package foundation.e.apps.data.exodus.repositories

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import foundation.e.apps.data.exodus.Report
import foundation.e.apps.data.exodus.models.AppPrivacyInfo
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.exodus.ApiResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.reflect.Modifier
import javax.inject.Inject
import javax.inject.Singleton
import foundation.e.apps.data.Result
import okio.IOException

@Singleton
class AppPrivacyInfoRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : IAppPrivacyInfoRepository {
    companion object {
        private const val EXODUS_PRIVACY_URL = "https://reports.exodus-privacy.eu.org/api"
    }

    override suspend fun getAppPrivacyInfo(
        application: Application,
        appHandle: String
    ): Result<AppPrivacyInfo> {
        if (application.is_pwa) {
            return Result.success(AppPrivacyInfo())
        }

        val reports = fetchReports(application.package_name)
        if (reports.firstOrNull()?.handle != application.package_name) {
            return Result.error("Could not fetch reports for ${application.package_name}")
        }

        updateApplication(application, reports.first())
        return Result.success(buildPrivacyInfo(reports.first()))
    }

    private fun fetchReports(packageName: String) : List<Report> {
        val requestBody = mapOf(
            "type" to "application",
            "query" to packageName,
            "limit" to 5
        )

        val jsonBody = Gson().toJson(requestBody)
        val request = Request.Builder()
            .url("$EXODUS_PRIVACY_URL/search")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    return parseReports(responseBody ?: "")
                } else {
                    throw IllegalStateException("Failed to fetch reports")
                }
            }
        } catch (exception: IOException) {
           exception.printStackTrace()
        }

        return emptyList()
    }

    private fun parseReports(response: String): List<Report> {
        try {
            val gson = GsonBuilder()
                .excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.STATIC)
                .create()

            val list = gson.fromJson<ApiResponse>(response)
            return list.results
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private inline fun <reified T> Gson.fromJson(json: String): T {
        val type = object : TypeToken<T>() {}.type
        return this.fromJson(json, type)
    }

    private fun updateApplication(application: Application, report: Report) {
        application.numberOfTracker = report.trackersCount
        application.numberOfPermission = report.permissionsCount
        application.reportId = report.id.toLong()
    }

    private fun buildPrivacyInfo(report: Report): AppPrivacyInfo {
        return AppPrivacyInfo(
            report.trackersCount,
            report.permissionsCount,
            report.id.toLong()
        )
    }
}
