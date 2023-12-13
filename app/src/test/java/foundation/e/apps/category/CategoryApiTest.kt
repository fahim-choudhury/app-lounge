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

package foundation.e.apps.category

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.aurora.gplayapi.data.models.Category
import foundation.e.apps.FakePreferenceModule
import foundation.e.apps.R
import foundation.e.apps.data.application.ApplicationDataManager
import foundation.e.apps.data.application.category.CategoryApi
import foundation.e.apps.data.application.category.CategoryApiImpl
import foundation.e.apps.data.application.utils.CategoryType
import foundation.e.apps.data.cleanapk.data.categories.Categories
import foundation.e.apps.data.cleanapk.repositories.CleanApkRepository
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.install.pkg.PWAManagerModule
import foundation.e.apps.install.pkg.PkgManagerModule
import foundation.e.apps.util.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.eq
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryApiTest {

    // Run tasks synchronously
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var pwaManagerModule: PWAManagerModule

    @Mock
    private lateinit var pkgManagerModule: PkgManagerModule

    @Mock
    private lateinit var cleanApkAppsRepository: CleanApkRepository

    @Mock
    private lateinit var cleanApkPWARepository: CleanApkRepository

    @Mock
    private lateinit var gPlayAPIRepository: PlayStoreRepository

    private lateinit var preferenceManagerModule: FakePreferenceModule

    private lateinit var categoryApi: CategoryApi

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        preferenceManagerModule = FakePreferenceModule(context)
        val applicationDataManager =
            ApplicationDataManager(gPlayAPIRepository, pkgManagerModule, pwaManagerModule)
        categoryApi = CategoryApiImpl(
            context,
            preferenceManagerModule,
            gPlayAPIRepository,
            cleanApkAppsRepository,
            cleanApkPWARepository,
            applicationDataManager
        )
    }

    @Test
    fun `getCategory when only pwa is selected`() = runTest {
        val categories =
            Categories(listOf("app one", "app two", "app three"), listOf("game 1", "game 2"), true)
        val response = Response.success(categories)
        preferenceManagerModule.isPWASelectedFake = true
        preferenceManagerModule.isOpenSourceelectedFake = false
        preferenceManagerModule.isGplaySelectedFake = false

        Mockito.`when`(
            cleanApkPWARepository.getCategories()
        ).thenReturn(response)

        Mockito.`when`(context.getString(eq(R.string.pwa))).thenReturn("PWA")

        val categoryListResponse =
            categoryApi.getCategoriesList(CategoryType.APPLICATION)

        Assert.assertEquals("getCategory", 3, categoryListResponse.first.size)
    }

    @Test
    fun `getCategory when only open source is selected`() = runTest {
        val categories =
            Categories(listOf("app one", "app two", "app three"), listOf("game 1", "game 2"), true)
        val response = Response.success(categories)

        preferenceManagerModule.isPWASelectedFake = false
        preferenceManagerModule.isOpenSourceelectedFake = true
        preferenceManagerModule.isGplaySelectedFake = false

        Mockito.`when`(
            cleanApkAppsRepository.getCategories()
        ).thenReturn(response)
        Mockito.`when`(context.getString(eq(R.string.open_source))).thenReturn("Open source")

        val categoryListResponse =
            categoryApi.getCategoriesList(CategoryType.APPLICATION)

        Assert.assertEquals("getCategory", 3, categoryListResponse.first.size)
    }

    @Test
    fun `getCategory when gplay source is selected`() = runTest {
        val categories = listOf(Category(), Category(), Category(), Category())

        preferenceManagerModule.isPWASelectedFake = false
        preferenceManagerModule.isOpenSourceelectedFake = false
        preferenceManagerModule.isGplaySelectedFake = true

        Mockito.`when`(
            gPlayAPIRepository.getCategories(CategoryType.APPLICATION)
        ).thenReturn(categories)

        val categoryListResponse =
            categoryApi.getCategoriesList(CategoryType.APPLICATION)

        Assert.assertEquals("getCategory", 4, categoryListResponse.first.size)
    }

    @Test
    fun `getCategory when gplay source is selected return error`() = runTest {
        preferenceManagerModule.isPWASelectedFake = false
        preferenceManagerModule.isOpenSourceelectedFake = false
        preferenceManagerModule.isGplaySelectedFake = true

        Mockito.`when`(
            gPlayAPIRepository.getCategories(CategoryType.APPLICATION)
        ).thenThrow()

        val categoryListResponse =
            categoryApi.getCategoriesList(CategoryType.APPLICATION)

        Assert.assertEquals("getCategory", 0, categoryListResponse.first.size)
        Assert.assertEquals("getCategory", ResultStatus.UNKNOWN, categoryListResponse.second)
    }

    @Test
    fun `getCategory when All source is selected`() = runTest {
        val gplayCategories = listOf(Category(), Category(), Category(), Category())
        val openSourcecategories = Categories(
            listOf("app one", "app two", "app three", "app four"), listOf("game 1", "game 2"), true
        )
        val openSourceResponse = Response.success(openSourcecategories)
        val pwaCategories =
            Categories(listOf("app one", "app two", "app three"), listOf("game 1", "game 2"), true)
        val pwaResponse = Response.success(pwaCategories)

        Mockito.`when`(
            cleanApkAppsRepository.getCategories()
        ).thenReturn(openSourceResponse)

        Mockito.`when`(
            cleanApkPWARepository.getCategories()
        ).thenReturn(pwaResponse)

        Mockito.`when`(
            gPlayAPIRepository.getCategories(CategoryType.APPLICATION)
        ).thenReturn(gplayCategories)

        Mockito.`when`(context.getString(eq(R.string.open_source))).thenReturn("Open source")
        Mockito.`when`(context.getString(eq(R.string.pwa))).thenReturn("pwa")

        preferenceManagerModule.isPWASelectedFake = true
        preferenceManagerModule.isOpenSourceelectedFake = true
        preferenceManagerModule.isGplaySelectedFake = true

        val categoryListResponse =
            categoryApi.getCategoriesList(CategoryType.APPLICATION)

        Assert.assertEquals("getCategory", 11, categoryListResponse.first.size)
    }
}