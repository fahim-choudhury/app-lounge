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

package foundation.e.apps.home

import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fused.data.FusedHome
import foundation.e.apps.ui.home.HomeViewModel
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class HomeViewModelTest {

    @Mock
    private lateinit var fusedAPIRepository: FusedAPIRepository

    private lateinit var homeViewModel: HomeViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        homeViewModel = HomeViewModel(fusedAPIRepository)
    }

    @Test
    fun `test hasAnyChange when app list sizes are not same`() {
        val oldAppList = mutableListOf(FusedApp("123"), FusedApp("124"), FusedApp("125"))
        val newAppList = mutableListOf(FusedApp("123"), FusedApp("124"))

        val oldHomeData =
            listOf(FusedHome("Top Free Apps", oldAppList), FusedHome("Top Free Games", oldAppList))
        var newHomeData =
            listOf(FusedHome("Top Free Apps", newAppList), FusedHome("Top Free Games", newAppList))

        homeViewModel.currentHomes = oldHomeData

        val hasAnyChange = homeViewModel.hasAnyChange(newHomeData)
        assert(hasAnyChange)
    }

    @Test
    fun `test hasAnyChange when contents are same`() {
        val oldAppList = mutableListOf(FusedApp("123"), FusedApp("124"), FusedApp("125"))
        val newAppList = mutableListOf(FusedApp("123"), FusedApp("124"), FusedApp("125"))

        val oldHomeData =
            listOf(FusedHome("Top Free Apps", oldAppList), FusedHome("Top Free Games", oldAppList))
        var newHomeData =
            listOf(FusedHome("Top Free Apps", newAppList), FusedHome("Top Free Games", newAppList))

        homeViewModel.currentHomes = oldHomeData

        val hasAnyChange = homeViewModel.hasAnyChange(newHomeData)
        assertFalse(hasAnyChange)
    }

    @Test
    fun `test hasAnyChange when contents are not same`() {
        val oldAppList = mutableListOf(FusedApp("123"), FusedApp("124"), FusedApp("125"))
        val newAppList = mutableListOf(FusedApp("123"), FusedApp("124", status = Status.INSTALLED), FusedApp("125"))

        val oldHomeData =
            listOf(FusedHome("Top Free Apps", oldAppList), FusedHome("Top Free Games", oldAppList))
        var newHomeData =
            listOf(FusedHome("Top Free Apps", newAppList), FusedHome("Top Free Games", newAppList))

        homeViewModel.currentHomes = oldHomeData

        val hasAnyChange = homeViewModel.hasAnyChange(newHomeData)
        assert(hasAnyChange)
    }
}