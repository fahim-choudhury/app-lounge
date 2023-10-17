/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
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

package foundation.e.apps.data.playstore.utils

import java.util.StringTokenizer
import java.util.regex.Pattern

object AC2DMUtil {
    fun parseResponse(response: String?): Map<String, String> {
        val keyValueMap: MutableMap<String, String> = HashMap()
        val st = StringTokenizer(response, "\n\r")
        while (st.hasMoreTokens()) {
            val keyValue = st.nextToken().split("=")
            if (keyValue.size >= 2) {
                keyValueMap[keyValue[0]] = keyValue[1]
            }
        }
        return keyValueMap
    }

    fun parseCookieString(cookies: String): Map<String, String> {
        val cookieList: MutableMap<String, String> = HashMap()
        val cookiePattern = Pattern.compile("([^=]+)=([^;]*);?\\s?")
        val matcher = cookiePattern.matcher(cookies)
        while (matcher.find()) {
            val cookieKey = matcher.group(1)
            val cookieValue = matcher.group(2)
            if (!cookieKey.isNullOrBlank() && !cookieValue.isNullOrBlank()) {
                cookieList[cookieKey] = cookieValue
            }
        }
        return cookieList
    }
}
