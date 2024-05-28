/*
 *  Copyright MURENA SAS 2024
 *  Apps  Quickly and easily install Android apps onto your device!
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package foundation.e.apps.data

class ParentalControlRepository {

    fun isParentalControlEnabled(): Boolean {
        return true
    }

    fun getAllowedContentRatings(): List<String> {
        return listOf(
            "PEGI 3",
            "EVERYONE",
            "All ages",
            "General",
            "All ages",
            "For all",
            "PEGI 3",
            "Parental Guidance Recommended",
//            "EVERYONE",
//            "All ages",
//            "General",
//            "All ages",
//            "For all",
//            "Rated for 3+",
//            "PEGI 3",
//            "PEGI 7",
//            "Parental Guidance Recommended",
//            "EVERYONE",
//            "All ages",
//            "USK: Ages 6 and above",
//            "General",
//            "All ages",
//            "For all",
//            "Rated for 3+",
//            "Rated for 7+",
//            "PEGI 3",
//            "PEGI 7",
//            "PEGI 12",
//            "Parental Guidance Recommended",
//            "EVERYONE",
//            "EVERYONE 10+",
//            "TEEN",
//            "All ages",
//            "USK: Ages 6 and above",
//            "USK: Ages 12 and above",
//            "General",
//            "Parental Guidance",
//            "All ages",
//            "Rated 10+",
//            "Rated 12+",
//            "For all",
//            "Rated 12+",
//            "Rated for 3+",
//            "Rated for 7+",
//            "Rated for 12+"
        )
    }
}