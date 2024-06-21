package foundation.e.apps.domain

import com.aurora.gplayapi.data.models.ContentRating
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.apps.AppsApi
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.blockedApps.Age
import foundation.e.apps.data.blockedApps.ContentRatingGroup
import foundation.e.apps.data.blockedApps.ContentRatingsRepository
import foundation.e.apps.data.blockedApps.ParentalControlRepository
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Type
import foundation.e.apps.data.install.models.AppInstall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import kotlin.test.Test


class ValidateAppAgeLimitUseCaseTest {

    @Mock
    private lateinit var contentRatingRepository: ContentRatingsRepository

    @Mock
    private lateinit var parentalControlRepository: ParentalControlRepository

    @Mock
    private lateinit var appsApi: AppsApi

    private lateinit var validateAppAgeLimitUseCase: ValidateAppAgeLimitUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        validateAppAgeLimitUseCase = ValidateAppAgeLimitUseCase(
            contentRatingRepository,
            parentalControlRepository,
            appsApi
        )
    }

    @Test
    fun parentalControlDisabled_returnsSuccessTrue() = runTest {
        `when`(parentalControlRepository.getSelectedAgeGroup()).thenReturn(Age.PARENTAL_CONTROL_DISABLED)

        val result = validateAppAgeLimitUseCase(AppInstall()) // Using a dummy AppInstall object

        assertEquals(true, result.data)
    }

    @Test
    fun knownNsfwApp_returnsSuccessFalse() = runTest {
        val app = AppInstall(packageName = "com.example.nsfwapp")
        `when`(parentalControlRepository.getSelectedAgeGroup()).thenReturn(Age.ELEVEN)
        `when`(contentRatingRepository.fDroidNSFWApps).thenReturn(listOf("com.example.nsfwapp"))

        val result = validateAppAgeLimitUseCase(app)

        assertEquals(false, result.data)
    }

    @Test
    fun cleanApkApp_isNsfw_returnsSuccessFalse() = runTest {
        val app = AppInstall(
            id = "123",
            packageName = "com.example.cleanapp",
            origin = Origin.CLEANAPK,
            type = Type.NATIVE
        )
        `when`(parentalControlRepository.getSelectedAgeGroup()).thenReturn(Age.ELEVEN)
        `when`(appsApi.getCleanapkAppDetails(app.packageName)).thenReturn(
            Pair(
                Application(
                    "com.example.cleanapp",
                    antiFeatures = listOf(mapOf(Pair("NSFW", "message")))
                ), ResultStatus.OK
            )
        )

        val result = validateAppAgeLimitUseCase(app)

        assertEquals(false, result.data)
    }

    @Test
    fun cleanApkApp_isNotNsfw_returnsSuccessTrue() = runTest {
        val app = AppInstall(
            id = "123",
            packageName = "com.example.cleanapp",
            origin = Origin.CLEANAPK,
            type = Type.NATIVE
        )
        `when`(parentalControlRepository.getSelectedAgeGroup()).thenReturn(Age.ELEVEN)
        `when`(appsApi.getCleanapkAppDetails(app.packageName)).thenReturn(
            Pair(
                Application(
                    "com.example.cleanapp",
                ), ResultStatus.OK
            )
        )

        val result = validateAppAgeLimitUseCase(app)

        assertEquals(true, result.data)
    }

    @Test
    fun cleanApkApp_getCleanApkDetails_error_returnsError() = runTest {
        val app = AppInstall(
            id = "123",
            packageName = "com.example.cleanapp",
            origin = Origin.CLEANAPK,
            type = Type.NATIVE
        )
        `when`(parentalControlRepository.getSelectedAgeGroup()).thenReturn(Age.ELEVEN)
        `when`(appsApi.getCleanapkAppDetails(app.packageName)).thenReturn(
            Pair(
                Application(), ResultStatus.UNKNOWN
            )
        )

        val result = validateAppAgeLimitUseCase(app)

        assertEquals(true, result is ResultSupreme.Error)
    }

    @Test
    fun noContentRatingOnGPlay_returnsError() = runTest {
        val app = AppInstall(packageName = "com.example.gplayapp", origin = Origin.GPLAY)
        `when`(parentalControlRepository.getSelectedAgeGroup()).thenReturn(Age.ELEVEN)
        `when`(contentRatingRepository.getEnglishContentRating(app.packageName)).thenReturn(null)

        val result = validateAppAgeLimitUseCase(app)

        assertEquals(true, result is ResultSupreme.Error)
    }

    @Test
    fun validAgeLimit_returnsSuccessTrue() = runTest {
        val app = AppInstall(packageName = "com.example.gplayapp", origin = Origin.GPLAY)
        app.contentRating = ContentRating("eleven", "11+")
        `when`(parentalControlRepository.getSelectedAgeGroup()).thenReturn(Age.ELEVEN)
        `when`(contentRatingRepository.getEnglishContentRating(app.packageName))
            .thenReturn(ContentRating("ELEVEN", "11+"))
        `when`(contentRatingRepository.contentRatingGroups).thenReturn(
            listOf(ContentRatingGroup("ELEVEN", "11+", listOf("ELEVEN"))))

        val result = validateAppAgeLimitUseCase(app)
        assertEquals(true, result.data)
    }

    @Test
    fun invalidAgeLimit_returnsSuccessFalse() = runTest {
        val app = AppInstall(packageName = "com.example.gplayapp", origin = Origin.GPLAY)
        app.contentRating = ContentRating("eighteen", "18+")
        `when`(parentalControlRepository.getSelectedAgeGroup()).thenReturn(Age.ELEVEN)
        `when`(contentRatingRepository.getEnglishContentRating(app.packageName))
            .thenReturn(ContentRating("EIGHTEEN", "18+"))
        `when`(contentRatingRepository.contentRatingGroups).thenReturn(
            listOf(
                ContentRatingGroup("ELEVEN", "11+", listOf("ELEVEN")),
                ContentRatingGroup("EIGHTEEN", "18+", listOf("EIGHTEEN"))
            )
        )

        val result = validateAppAgeLimitUseCase(app)
        assertEquals(false, result.data)
    }
}