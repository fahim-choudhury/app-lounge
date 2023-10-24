package foundation.e.apps.util

import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fused.FusedAPIRepository
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fused.data.FusedHome
import foundation.e.apps.ui.home.HomeViewModel
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
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
    fun testHasAnyChangeWhenAppListSizeIsNotSame() {
        val oldAppList = mutableListOf(FusedApp("123"), FusedApp("124"), FusedApp("125"))
        val newAppList = mutableListOf(FusedApp("123"), FusedApp("124"))

        val oldHomeData =
            listOf(FusedHome("Top Free Apps", oldAppList), FusedHome("Top Free Games", oldAppList))
        var newHomeData =
            listOf(FusedHome("Top Free Apps", newAppList), FusedHome("Top Free Games", newAppList))

        homeViewModel.currentList = oldHomeData

        val hasAnyChange = homeViewModel.hasAnyChange(newHomeData)
        assert(hasAnyChange)
    }

    @Test
    fun testHasAnyChangeWhenContentsAreSame() {
        val oldAppList = mutableListOf(FusedApp("123"), FusedApp("124"), FusedApp("125"))
        val newAppList = mutableListOf(FusedApp("123"), FusedApp("124"), FusedApp("125"))

        val oldHomeData =
            listOf(FusedHome("Top Free Apps", oldAppList), FusedHome("Top Free Games", oldAppList))
        var newHomeData =
            listOf(FusedHome("Top Free Apps", newAppList), FusedHome("Top Free Games", newAppList))

        homeViewModel.currentList = oldHomeData

        val hasAnyChange = homeViewModel.hasAnyChange(newHomeData)
        assert(!hasAnyChange)
    }

    @Test
    fun testHasAnyChangeWhenContentsAreNotSame() {
        val oldAppList = mutableListOf(FusedApp("123"), FusedApp("124"), FusedApp("125"))
        val newAppList = mutableListOf(FusedApp("123"), FusedApp("124", status = Status.INSTALLED), FusedApp("125"))

        val oldHomeData =
            listOf(FusedHome("Top Free Apps", oldAppList), FusedHome("Top Free Games", oldAppList))
        var newHomeData =
            listOf(FusedHome("Top Free Apps", newAppList), FusedHome("Top Free Games", newAppList))

        homeViewModel.currentList = oldHomeData

        val hasAnyChange = homeViewModel.hasAnyChange(newHomeData)
        assert(hasAnyChange)
    }

}