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

package foundation.e.apps.data.parentalcontrol

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParentalControlRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val URI_PARENTAL_CONTROL_PROVIDER =
            "content://foundation.e.parentalcontrol.provider/age"
    }

    fun getSelectedAgeGroup(): Age {
        val uri = Uri.parse(URI_PARENTAL_CONTROL_PROVIDER)
        val cursor = context.contentResolver.query(uri, null, null, null, null)

        cursor?.use {
            if (it.moveToFirst()) {
                val ageOrdinal = it.getInt(it.getColumnIndexOrThrow("age"))
                return Age.values()[ageOrdinal]
            }
        }

        return Age.PARENTAL_CONTROL_DISABLED
    }
}

enum class Age {
    THREE,
    SIX,
    ELEVEN,
    FIFTEEN,
    SEVENTEEN,
    PARENTAL_CONTROL_DISABLED
}
