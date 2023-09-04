package foundation.e.apps.data.exodus

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ExodusTrackerApi {

    companion object {
        const val BASE_URL = "https://exodus.ecloud.global/api/"
    }

    @GET("trackers")
    suspend fun getTrackerList(@Query("date") date: String): Response<Trackers>

    @GET("search/{appHandle}/details")
    suspend fun getTrackerInfoOfApp(
        @Path("appHandle") appHandle: String,
        @Query("v") versionCode: Int
    ): Response<List<Report>>
}
