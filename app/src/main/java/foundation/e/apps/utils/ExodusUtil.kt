/*
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

package foundation.e.apps.utils

import android.net.Uri
import java.util.Locale

object ExodusUtil {
    const val DEFAULT_URL = "https://exodus-privacy.eu.org"

    fun buildReportUri(reportId: Long): Uri {
        val language = getLanguage(Locale.getDefault().language)

        return Uri.Builder()
            .scheme("https")
            .authority("reports.exodus-privacy.eu.org")
            .appendPath(language)
            .appendPath("reports")
            .appendPath(reportId.toString())
            .build()  // Example: https://reports.exodus-privacy.eu.org/es/reports/511980/
    }

    fun buildRequestReportUri(packageName: String): Uri {
        val language = getLanguage(Locale.getDefault().language)

        return Uri.Builder()
            .scheme("https")
            .authority("reports.exodus-privacy.eu.org")
            .appendPath(language)
            .appendPath("analysis")
            .appendPath("submit")
            .fragment(packageName)
            .build()  // Example: https://reports.exodus-privacy.eu.org/en/analysis/submit/#packagename
    }

    private fun getLanguage(param: String): String {
        return SupportedLanguage.values().find { it.language == param }?.language
            ?: SupportedLanguage.English.language
    }

    private enum class SupportedLanguage(val language: String) {
        English("en"),
        French("fr"),
        German("de"),
        Italian("it"),
        Spanish("es"),
        Greek("el")
    }
}
