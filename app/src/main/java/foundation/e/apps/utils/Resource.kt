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

package foundation.e.apps.utils

/**
 * Class represents the different states of a resource for user case layer
 */
sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    /**
     * Represents a successful state of the resource with data.
     * @param data The data associated with the resource.
     */
    class Success<T>(data: T) : Resource<T>(data)

    /**
     * Represents an error state of the resource with an error message.
     * @param message The error message associated with the resource.
     */
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)

    /**
     * Represents a loading state of the resource.
     */
    class Loading<T>(data: T? = null) : Resource<T>(data)
}
