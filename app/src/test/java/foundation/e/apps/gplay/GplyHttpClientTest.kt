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

package foundation.e.apps.gplay

import com.aurora.gplayapi.data.models.PlayResponse
import foundation.e.apps.data.playstore.utils.GPlayHttpClient
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.util.FakeCall
import foundation.e.apps.util.MainCoroutineRule
import foundation.e.apps.utils.SystemInfoProvider
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any

@OptIn(ExperimentalCoroutinesApi::class)
class GplyHttpClientTest {

    @Mock
    private lateinit var cache: Cache

    @Mock
    private lateinit var okHttpClient: OkHttpClient

    private lateinit var call: FakeCall

    private lateinit var gPlayHttpClient: GPlayHttpClient

    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule
    val coroutineTestRule = MainCoroutineRule()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        gPlayHttpClient = GPlayHttpClient(cache)
        gPlayHttpClient.okHttpClient = this.okHttpClient
        call = FakeCall()
    }

    @Test
    fun testPostWithMapFailedWhenStatus401() = runTest {
        initMocks()
        val response = gPlayHttpClient.post("http://abc.abc", mapOf(), mapOf())
        assertResponse(response)
    }

    @Test
    fun testPostWithRequestBodyFailedWhenStatus401() = runTest {
        initMocks()
        val response = gPlayHttpClient.post("http://abc.abc", mapOf(), "".toRequestBody())
        assertResponse(response)
    }

    @Test
    fun testPostWithByteArrayFailedWhenStatus401() = runTest {
        initMocks()
        val response = gPlayHttpClient.post("http://abc.abc", mapOf(), "".toByteArray())
        assertResponse(response)
    }

    @Test
    fun testGetWithoutParamsFailedWhenStatus401() = runTest {
        initMocks()
        val response = gPlayHttpClient.get(FakeCall.FAKE_URL, mapOf())
        assertResponse(response)
    }

    @Test
    fun testGetWithStringParamsFailedWhenStatus401() = runTest {
        initMocks()
        val response = gPlayHttpClient.get(FakeCall.FAKE_URL, mapOf(), "")
        assertResponse(response)
    }

    @Test
    fun testGetWithMapParamsFailedWhenStatus401() = runTest {
        initMocks()
        val response = gPlayHttpClient.get(FakeCall.FAKE_URL, mapOf(), mapOf())
        assertResponse(response)
    }

    @Test
    fun testPostAuthFailedWhenStatus401() = runTest {
        initMocks()
        val response = gPlayHttpClient.postAuth("http://abc.abc", "".toByteArray())
        assertResponse(response)
    }

    private fun initMocks() {
        call.willThrow401 = true
        mockkObject(SystemInfoProvider)
        every { SystemInfoProvider.getAppBuildInfo() } returns ""
        Mockito.`when`(okHttpClient.newCall(any())).thenReturn(call)
    }
    private suspend fun assertResponse(response: PlayResponse) {
        assertFalse(response.isSuccessful)
        assertTrue(response.code == 401)
        val event = EventBus.events.first()
        assertTrue(event is AppEvent.InvalidAuthEvent)
        assertTrue(event.data is String)
        assertTrue(event.data == AuthObject.GPlayAuth::class.java.simpleName)
    }
}
