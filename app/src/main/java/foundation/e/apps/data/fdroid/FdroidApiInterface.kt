package foundation.e.apps.data.fdroid

import foundation.e.apps.data.fdroid.models.FdroidApiModel
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Interface for retrofit calls.
 * Created from [foundation.e.apps.data.cleanapk.RetrofitModule.provideFdroidApi].
 */
interface FdroidApiInterface {

    companion object {
        const val BASE_URL = "https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/"
    }

    @GET("{packageName}.yml")
    suspend fun getFdroidInfoForPackage(@Path("packageName") packageName: String): Response<FdroidApiModel?>
}
