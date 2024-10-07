package foundation.e.apps.data.exodus

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface ExodusTrackerApi {

    companion object {
        const val BASE_URL = "https://exodus.ecloud.global/api/"
        const val CACHE_DURATION_SECONDS = 86400 // 1 day
    }

    @Headers("Cache-Control: public, max-age=$CACHE_DURATION_SECONDS")
    @GET("trackers")
    suspend fun getTrackerList(@Query("date") date: String): Response<Trackers>

    @Headers("Cache-Control: public, max-age=$CACHE_DURATION_SECONDS")
    @GET("search/{appHandle}/details")
    suspend fun getTrackerInfoOfApp(
        @Path("appHandle") appHandle: String,
        @Query("v") versionCode: Int,
    ): Response<List<Report>>
}
