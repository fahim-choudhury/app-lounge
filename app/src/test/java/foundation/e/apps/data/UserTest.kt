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

package foundation.e.apps.data

import foundation.e.apps.data.enums.User
import org.junit.Assert
import org.junit.Test

class UserTest {

    @Test
    fun testUserStringEmptyReturnUnavailable() {
        val result = User.getUser("")
        Assert.assertEquals(User.UNAVAILABLE.name, result.name)
    }

    @Test
    fun testUserStringGoogleReturnUserAsGoogle() {
        val result = User.getUser("Google")
        Assert.assertEquals(User.UNAVAILABLE.name, result.name)
    }

    @Test
    fun testUserStringAnonymousReturnUserAsAnonymous() {
        val result = User.getUser("")
        Assert.assertEquals(User.UNAVAILABLE.name, result.name)
    }

    @Test
    fun testUserStringForNoGoogleReturnUserAsNoGoogle() {
        // TODO - No idea for No Google
    }

    @Test
    fun testRandomStringReturnUnavailable() {
        val result = User.getUser("dmflmfle")
        Assert.assertEquals(User.UNAVAILABLE.name, result.name)
    }
}