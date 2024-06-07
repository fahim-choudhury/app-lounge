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

package foundation.e.apps.parentalcontrol

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.ContentRating
import foundation.e.apps.data.application.ApplicationRepository
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.login.AuthenticatorRepository
import foundation.e.apps.data.parentalcontrol.AppInstallationPermissionState.Allowed
import foundation.e.apps.data.parentalcontrol.AppInstallationPermissionState.Denied
import foundation.e.apps.data.parentalcontrol.AppInstallationPermissionState.DeniedOnDataLoadError
import foundation.e.apps.data.parentalcontrol.GetAppInstallationPermissionUseCaseImpl
import foundation.e.apps.data.parentalcontrol.gplayrating.GooglePlayContentRatingGroup
import foundation.e.apps.data.parentalcontrol.gplayrating.GooglePlayContentRatingsRepository
import foundation.e.apps.data.playstore.PlayStoreRepository
import foundation.e.apps.data.preference.DataStoreManager
import foundation.e.apps.domain.parentalcontrol.GetAppInstallationPermissionUseCase
import foundation.e.apps.domain.parentalcontrol.GetParentalControlStateUseCase
import foundation.e.apps.domain.parentalcontrol.model.AgeGroupValue
import foundation.e.apps.domain.parentalcontrol.model.ParentalControlState
import foundation.e.apps.util.MainCoroutineRule
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class GetAppInstallationPermissionUseCaseTest {

    // Run tasks synchronously
    @Rule @JvmField val instantExecutorRule = InstantTaskExecutorRule()

    // Sets the main coroutines dispatcher to a TestCoroutineScope for unit testing.
    @ExperimentalCoroutinesApi @get:Rule var mainCoroutineRule = MainCoroutineRule()

    private lateinit var useCase: GetAppInstallationPermissionUseCase

    @Mock private lateinit var applicationRepository: ApplicationRepository

    @Mock private lateinit var dataStoreManager: DataStoreManager

    @Mock private lateinit var contentRatingsRepository: GooglePlayContentRatingsRepository

    @Mock private lateinit var getParentalControlStateUseCase: GetParentalControlStateUseCase

    @Mock private lateinit var playStoreRepository: PlayStoreRepository

    @Mock private lateinit var authenticatorRepository: AuthenticatorRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        useCase =
            GetAppInstallationPermissionUseCaseImpl(
                applicationRepository,
                dataStoreManager,
                contentRatingsRepository,
                getParentalControlStateUseCase,
                playStoreRepository)
    }

    @Test
    fun `allow app installation when parental control is disabled`() {
        runTest {
            val appPendingInstallation = AppInstall()

            Mockito.`when`(getParentalControlStateUseCase.invoke())
                .thenReturn(ParentalControlState.Disabled)

            val installationPermissionState = useCase.invoke(appPendingInstallation)

            assertEquals(Allowed, installationPermissionState)
        }
    }

    @Test
    fun `allow app installation when parental control is disabled and F-Droid app has no anti-features`() {
        runTest {
            val appPendingInstallation =
                AppInstall().apply {
                    isFDroidApp = true
                    antiFeatures = emptyList()
                }

            Mockito.`when`(getParentalControlStateUseCase.invoke())
                .thenReturn(ParentalControlState.Disabled)

            val installationPermissionState = useCase.invoke(appPendingInstallation)

            assertEquals(Allowed, installationPermissionState)
        }
    }

    @Test
    fun `allow app installation when parental control is disabled and F-Droid app has anti-features other than NSFW`() {
        runTest {
            val appPendingInstallation =
                AppInstall().apply {
                    isFDroidApp = true
                    antiFeatures =
                        listOf(
                            mapOf(
                                "NonFreeAssets" to
                                    "Artwork, layouts and prerecorded voices are under a non-commercial license"))
                }

            Mockito.`when`(getParentalControlStateUseCase.invoke())
                .thenReturn(ParentalControlState.Disabled)

            val installationPermissionState = useCase.invoke(appPendingInstallation)

            assertEquals(Allowed, installationPermissionState)
        }
    }

    @Test
    fun `allow app installation when parental control is enabled and F-Droid app has anti-features other than NSFW`() {
        runTest {
            val appPendingInstallation =
                AppInstall().apply {
                    isFDroidApp = true
                    antiFeatures =
                        listOf(
                            mapOf(
                                "NonFreeAssets" to
                                    "Artwork, layouts and prerecorded voices are under a non-commercial license"))
                }

            Mockito.`when`(getParentalControlStateUseCase.invoke())
                .thenReturn(ParentalControlState.AgeGroup(AgeGroupValue.THREE))

            val installationPermissionState = useCase.invoke(appPendingInstallation)

            assertEquals(Allowed, installationPermissionState)
        }
    }

    @Test
    fun `deny app installation when parental control is enabled and F-Droid app has NSFW anti-features`() {
        runTest {
            val appPendingInstallation =
                AppInstall().apply {
                    isFDroidApp = true
                    antiFeatures = listOf(mapOf("NSFW" to "Shows explicit content."))
                }

            Mockito.`when`(getParentalControlStateUseCase.invoke())
                .thenReturn(ParentalControlState.AgeGroup(AgeGroupValue.THREE))

            val installationPermissionState = useCase.invoke(appPendingInstallation)

            assertEquals(Denied, installationPermissionState)
        }
    }

    @Test
    fun `allow app installation when parental control is enabled and Google Play app's rating is equal to child's age group`() {
        runTest {
            val appPackage = "com.unit.test"
            val contentRatingTitle = "Rated for 3+"
            val contentRatingId = contentRatingTitle.lowercase()

            val googlePlayContentRatingGroup =
                listOf(
                    GooglePlayContentRatingGroup(
                        id = "THREE",
                        ageGroup = "0-3",
                        ratings =
                            listOf("rated for 3+") // ratings will be parsed as lowercase in real
                        ))

            val email = "test@test.com"
            val token = "token"

            val contentRating = ContentRating(title = contentRatingTitle)
            val contentRatingWithId =
                ContentRating(id = contentRatingId, title = contentRatingTitle)

            val appPendingInstallation: AppInstall =
                AppInstall(packageName = appPackage).apply {
                    this.isFDroidApp = false
                    this.contentRating = contentRating
                }

            val application = Application(isFDroidApp = false, contentRating = contentRating)

            val authData = AuthData(email, token)

            Mockito.`when`(getParentalControlStateUseCase.invoke())
                .thenReturn(ParentalControlState.AgeGroup(AgeGroupValue.THREE))

            Mockito.`when`(contentRatingsRepository.contentRatingGroups)
                .thenReturn(googlePlayContentRatingGroup)

            Mockito.`when`(authenticatorRepository.gplayAuth).thenReturn(authData)

            Mockito.`when`(dataStoreManager.getAuthData()).thenReturn(authData)

            Mockito.`when`(
                    applicationRepository.getApplicationDetails(
                        appPendingInstallation.id,
                        appPendingInstallation.packageName,
                        authData,
                        appPendingInstallation.origin))
                .thenReturn(Pair(application, ResultStatus.OK))

            Mockito.`when`(playStoreRepository.getContentRatingWithId(appPackage, contentRating))
                .thenReturn(contentRatingWithId)

            val installationPermissionState = useCase.invoke(appPendingInstallation)

            assertEquals(Allowed, installationPermissionState)
        }
    }

    @Test
    fun `deny app installation when parental control is enabled and Google Play app's rating exceeds child's age group`() {
        runTest {
            val appPackage = "com.unit.test"
            val contentRatingTitle = "Rated for 7+"
            val contentRatingId = contentRatingTitle.lowercase()

            val googlePlayContentRatingGroup =
                listOf(
                    GooglePlayContentRatingGroup(
                        id = "THREE",
                        ageGroup = "0-3",
                        ratings =
                            listOf("rated for 3+") // ratings will be parsed as lowercase in real
                        ))

            val email = "test@test.com"
            val token = "token"

            val contentRating = ContentRating(title = contentRatingTitle)
            val contentRatingWithId =
                ContentRating(id = contentRatingId, title = contentRatingTitle)

            val appPendingInstallation: AppInstall =
                AppInstall(packageName = appPackage).apply {
                    this.isFDroidApp = false
                    this.contentRating = contentRating
                }

            val application = Application(isFDroidApp = false, contentRating = contentRating)

            val authData = AuthData(email, token)

            Mockito.`when`(getParentalControlStateUseCase.invoke())
                .thenReturn(ParentalControlState.AgeGroup(AgeGroupValue.THREE))

            Mockito.`when`(contentRatingsRepository.contentRatingGroups)
                .thenReturn(googlePlayContentRatingGroup)

            Mockito.`when`(authenticatorRepository.gplayAuth).thenReturn(authData)

            Mockito.`when`(dataStoreManager.getAuthData()).thenReturn(authData)

            Mockito.`when`(
                    applicationRepository.getApplicationDetails(
                        appPendingInstallation.id,
                        appPendingInstallation.packageName,
                        authData,
                        appPendingInstallation.origin))
                .thenReturn(Pair(application, ResultStatus.OK))

            Mockito.`when`(playStoreRepository.getContentRatingWithId(appPackage, contentRating))
                .thenReturn(contentRatingWithId)

            val installationPermissionState = useCase.invoke(appPendingInstallation)

            assertEquals(Denied, installationPermissionState)
        }
    }

    @Test
    fun `deny app installation on data load error when parental control is enabled and Google Play app has no content rating`() {
        runTest {
            val appPackage = "com.unit.test"
            val contentRatingTitle = ""
            val contentRatingId = contentRatingTitle.lowercase()

            val googlePlayContentRatingGroup =
                listOf(
                    GooglePlayContentRatingGroup(
                        id = "THREE",
                        ageGroup = "0-3",
                        ratings =
                            listOf("rated for 3+") // ratings will be parsed as lowercase in real
                        ))

            val email = "test@test.com"
            val token = "token"

            val contentRating = ContentRating(title = contentRatingTitle)
            val contentRatingWithId =
                ContentRating(id = contentRatingId, title = contentRatingTitle)

            val appPendingInstallation: AppInstall =
                AppInstall(packageName = appPackage).apply {
                    this.isFDroidApp = false
                    this.contentRating = contentRating
                }

            val application = Application(isFDroidApp = false, contentRating = contentRating)

            val authData = AuthData(email, token)

            Mockito.`when`(getParentalControlStateUseCase.invoke())
                .thenReturn(ParentalControlState.AgeGroup(AgeGroupValue.THREE))

            Mockito.`when`(contentRatingsRepository.contentRatingGroups)
                .thenReturn(googlePlayContentRatingGroup)

            Mockito.`when`(authenticatorRepository.gplayAuth).thenReturn(authData)

            Mockito.`when`(dataStoreManager.getAuthData()).thenReturn(authData)

            Mockito.`when`(
                    applicationRepository.getApplicationDetails(
                        appPendingInstallation.id,
                        appPendingInstallation.packageName,
                        authData,
                        appPendingInstallation.origin))
                .thenReturn(Pair(application, ResultStatus.OK))

            Mockito.`when`(playStoreRepository.getContentRatingWithId(appPackage, contentRating))
                .thenReturn(contentRatingWithId)

            val installationPermissionState = useCase.invoke(appPendingInstallation)

            assertEquals(DeniedOnDataLoadError, installationPermissionState)
        }
    }

    @Test
    fun `deny app installation on data load error when parental control is enabled and Google Play app has no content rating and app details can't be loaded`() {
        runTest {
            val appPackage = "com.unit.test"
            val contentRatingTitle = ""
            val contentRatingId = contentRatingTitle.lowercase()

            val googlePlayContentRatingGroup =
                listOf(
                    GooglePlayContentRatingGroup(
                        id = "THREE",
                        ageGroup = "0-3",
                        ratings =
                            listOf("rated for 3+") // ratings will be parsed as lowercase in real
                        ))

            val email = "test@test.com"
            val token = "token"

            val contentRating = ContentRating(title = contentRatingTitle)
            val contentRatingWithId =
                ContentRating(id = contentRatingId, title = contentRatingTitle)

            val appPendingInstallation: AppInstall =
                AppInstall(packageName = appPackage).apply {
                    this.isFDroidApp = false
                    this.contentRating = contentRating
                }

            val application = Application(isFDroidApp = false, contentRating = contentRating)

            val authData = AuthData(email, token)

            Mockito.`when`(getParentalControlStateUseCase.invoke())
                .thenReturn(ParentalControlState.AgeGroup(AgeGroupValue.THREE))

            Mockito.`when`(contentRatingsRepository.contentRatingGroups)
                .thenReturn(googlePlayContentRatingGroup)

            Mockito.`when`(authenticatorRepository.gplayAuth).thenReturn(authData)

            Mockito.`when`(dataStoreManager.getAuthData()).thenReturn(authData)

            Mockito.`when`(
                    applicationRepository.getApplicationDetails(
                        appPendingInstallation.id,
                        appPendingInstallation.packageName,
                        authData,
                        appPendingInstallation.origin))
                .thenReturn(Pair(application, ResultStatus.UNKNOWN))

            Mockito.`when`(playStoreRepository.getContentRatingWithId(appPackage, contentRating))
                .thenReturn(contentRatingWithId)

            val installationPermissionState = useCase.invoke(appPendingInstallation)

            assertEquals(DeniedOnDataLoadError, installationPermissionState)
        }
    }

    // Note: This case is unlikely to happen
    @Test
    fun `deny app installation when parental control is enabled and parental control state can't match with Google Play content ratings`() {
        runTest {
            val appPackage = "com.unit.test"
            val contentRatingTitle = "Rated for 3+"
            val contentRatingId = contentRatingTitle.lowercase()

            val googlePlayContentRatingGroup =
                listOf(
                    GooglePlayContentRatingGroup(
                        id = "EIGHTEEN",
                        ageGroup = "18+",
                        ratings =
                            listOf("rated for 18+") // ratings will be parsed as lowercase in real
                        ))

            val email = "test@test.com"
            val token = "token"

            val contentRating = ContentRating(title = contentRatingTitle)
            val contentRatingWithId =
                ContentRating(id = contentRatingId, title = contentRatingTitle)

            val appPendingInstallation: AppInstall =
                AppInstall(packageName = appPackage).apply {
                    this.isFDroidApp = false
                    this.contentRating = contentRating
                }

            val application = Application(isFDroidApp = false, contentRating = contentRating)

            val authData = AuthData(email, token)

            Mockito.`when`(getParentalControlStateUseCase.invoke())
                .thenReturn(ParentalControlState.AgeGroup(AgeGroupValue.THREE))

            Mockito.`when`(contentRatingsRepository.contentRatingGroups)
                .thenReturn(googlePlayContentRatingGroup)

            Mockito.`when`(authenticatorRepository.gplayAuth).thenReturn(authData)

            Mockito.`when`(dataStoreManager.getAuthData()).thenReturn(authData)

            Mockito.`when`(
                    applicationRepository.getApplicationDetails(
                        appPendingInstallation.id,
                        appPendingInstallation.packageName,
                        authData,
                        appPendingInstallation.origin))
                .thenReturn(Pair(application, ResultStatus.UNKNOWN))

            Mockito.`when`(playStoreRepository.getContentRatingWithId(appPackage, contentRating))
                .thenReturn(contentRatingWithId)

            val installationPermissionState = useCase.invoke(appPendingInstallation)

            assertEquals(Denied, installationPermissionState)
        }
    }
}
