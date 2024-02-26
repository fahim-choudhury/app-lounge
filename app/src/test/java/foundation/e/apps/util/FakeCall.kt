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

package foundation.e.apps.util

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout

class FakeCall : Call {

    var willThrow401 = false
    var willThrow429 = false

    companion object {
        const val FAKE_URL = "https://murena.test"
    }

    private val fakeRequest = Request.Builder().url(FAKE_URL).build()
    override fun cancel() {
        TODO("Not yet implemented")
    }

    override fun clone(): Call {
        TODO("Not yet implemented")
    }

    override fun enqueue(responseCallback: Callback) {
        TODO("Not yet implemented")
    }

    override fun execute(): Response {
        val builder = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .message("")
            .code(401)
            .body("".toResponseBody())

        if (willThrow401) {
            builder.code(401)
        } else if (willThrow429) {
            builder.code(429)
        }

        return builder.build()
    }

    override fun isCanceled(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isExecuted(): Boolean {
        TODO("Not yet implemented")
    }

    override fun request(): Request {
        TODO("Not yet implemented")
    }

    override fun timeout(): Timeout {
        TODO("Not yet implemented")
    }
}
