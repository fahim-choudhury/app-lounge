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

package foundation.e.apps.di.network

import junit.framework.TestCase.assertEquals
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import java.util.Locale

class InterceptorModuleTest {

    private lateinit var module: InterceptorModule
    private lateinit var chain: Interceptor.Chain
    private lateinit var request: Request
    private lateinit var response: Response

    @Before
    fun setUp() {
        module = InterceptorModule()

        // Mocking the chain and request
        chain = mock()
        request = Request.Builder()
            .url("http://test.com")
            .build()

        // Mocking the response
        response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("Response body".toResponseBody())
            .build()

        whenever(chain.request()).thenReturn(request)
    }

    @Test
    fun `provideInterceptor should add correct headers to request`() {
        // Mock the chain's proceed method to return a valid response
        whenever(chain.proceed(any())).thenReturn(response)

        val interceptor = module.provideInterceptor()

        // Intercept the request
        val interceptedResponse = interceptor.intercept(chain)

        // Verify the headers
        val requestCaptor = argumentCaptor<Request>()
        verify(chain).proceed(requestCaptor.capture())
        val capturedRequest = requestCaptor.firstValue

        assertEquals(
            InterceptorModule.HEADER_USER_AGENT_VALUE,
            capturedRequest.header("User-Agent")
        )
        assertEquals(Locale.getDefault().language, capturedRequest.header("Accept-Language"))
        assertEquals(response, interceptedResponse)
    }
}
