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

package foundation.e.apps.data.application.utils

import java.util.Locale

object CategoryStringFormatter {
    fun format(input: String): String {
        return when (input) {
            "unknown" -> "Unknown"
            else ->
                input.replace("_", " ").split(" ").joinToString(" ") { word ->
                    if (word.lowercase() == "and") word.lowercase(Locale.getDefault())
                    else {
                        word.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault())
                            else it.toString()
                        } // Example: books and reference -> Books and Reference
                    }
                }
        } // Capitalize each word except "and"
    }
}
