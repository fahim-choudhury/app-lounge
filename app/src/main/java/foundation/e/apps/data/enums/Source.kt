/*
 * Copyright (C) 2019-2022  MURENA SAS
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

package foundation.e.apps.data.enums

enum class Source {
    GPLAY,
    OPEN,
    PWA;

    companion object {
        fun fromString(source: String): Source {
            return when (source) {
                "Open Source" -> OPEN
                "PWA" -> PWA
                else -> GPLAY
            }
        }
    }
}
