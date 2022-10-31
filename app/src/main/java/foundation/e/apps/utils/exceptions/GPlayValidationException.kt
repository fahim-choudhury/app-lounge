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

package foundation.e.apps.utils.exceptions

import foundation.e.apps.utils.enums.User

/**
 * This exception is specifically used when a GPlay auth data could not be validated.
 * This is not the case as timeout, this exception usually means the server informed that the
 * current auth data is not valid.
 * Use [networkCode] to be sure that the server call was successful (should be 200).
 */
class GPlayValidationException(
    message: String,
    user: User,
    val networkCode: Int,
) : GPlayLoginException(false, message, user)
