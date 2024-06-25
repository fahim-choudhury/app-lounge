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
import foundation.e.apps.domain.parentalcontrol.model.AgeGroupValue
import foundation.e.apps.domain.parentalcontrol.model.ParentalControlState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetParentalControlStateUseCase
@Inject
constructor(@ApplicationContext private val context: Context) {
    companion object {
        private const val URI_PARENTAL_CONTROL_PROVIDER =
            "content://foundation.e.parentalcontrol.provider/age"
        private const val COLUMN_NAME = "age"
    }

    fun invoke(): ParentalControlState {
        val uri = Uri.parse(URI_PARENTAL_CONTROL_PROVIDER)

        val cursor =
            context.contentResolver.query(uri, null, null, null, null)
                ?: return ParentalControlState.Disabled

        return try {
            cursor.use {
                when {
                    it.moveToFirst() -> {
                        val ageOrdinal = it.getColumnIndexOrThrow(COLUMN_NAME).let(it::getInt)
                        val ageGroup = AgeGroupValue.values()[ageOrdinal]
                        ParentalControlState.AgeGroup(ageGroup)
                    }
                    else -> ParentalControlState.Disabled
                }
            }
        } catch (e: IllegalArgumentException) {
            ParentalControlState.Disabled
        }
    }
}
