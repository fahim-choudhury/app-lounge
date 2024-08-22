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

package foundation.e.apps.category

import foundation.e.apps.data.application.utils.CategoryStringFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryStringFormatterTest {

    @Test
    fun testFormatString_withUnderscores() {
        val input = "health_and_fitness"
        val expected = "Health and Fitness"
        val result = CategoryStringFormatter.format(input)
        assertEquals(expected, result)
    }

    @Test
    fun testFormatString_withMultipleWords() {
        val input = "mental_health_and_fitness"
        val expected = "Mental Health and Fitness"
        val result = CategoryStringFormatter.format(input)
        assertEquals(expected, result)
    }

    @Test
    fun testFormatString_withLeadingAndTrailingUnderscores() {
        val input = "_health_and_fitness_"
        val expected = "Health and Fitness"
        val result =
            CategoryStringFormatter.format(input.trim('_')) // Trimming underscores for testing
        assertEquals(expected, result)
    }

    @Test
    fun testFormatString_withNoUnderscores() {
        val input = "health"
        val expected = "Health"
        val result = CategoryStringFormatter.format(input)
        assertEquals(expected, result)
    }

    @Test
    fun testFormatString_withOnlyAnd() {
        val input = "and"
        val expected = "and"
        val result = CategoryStringFormatter.format(input)
        assertEquals(expected, result)
    }

    @Test
    fun testFormatString_withEmptyString() {
        val input = ""
        val expected = ""
        val result = CategoryStringFormatter.format(input)
        assertEquals(expected, result)
    }

    @Test
    fun testFormatString_unknown() {
        val input = "unknown"
        val expected = "Unknown"
        val result = CategoryStringFormatter.format(input)
        assertEquals(expected, result)
    }
}
