package app.lounge

import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.network.IHttpClient

class FakeGplayHttpClient : IHttpClient {
    override fun get(url: String, headers: Map<String, String>): PlayResponse {
        TODO("Not yet implemented")
    }

    override fun get(url: String, headers: Map<String, String>, paramString: String): PlayResponse {
        TODO("Not yet implemented")
    }

    override fun get(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>
    ): PlayResponse {
        TODO("Not yet implemented")
    }

    override fun getAuth(url: String): PlayResponse {
        TODO("Not yet implemented")
    }

    override fun post(url: String, headers: Map<String, String>, body: ByteArray): PlayResponse {
        TODO("Not yet implemented")
    }

    override fun post(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>
    ): PlayResponse {
        TODO("Not yet implemented")
    }

    override fun postAuth(url: String, body: ByteArray): PlayResponse {
        TODO("Not yet implemented")
    }
}