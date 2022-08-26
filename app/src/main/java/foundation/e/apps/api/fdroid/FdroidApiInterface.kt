package foundation.e.apps.api.fdroid

import foundation.e.apps.api.fdroid.models.FdroidApiModel
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Interface for retrofit calls.
 * Created from [foundation.e.apps.api.cleanapk.RetrofitModule.provideFdroidApi].
 */
interface FdroidApiInterface {

    companion object {
        const val BASE_URL = "https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/"
    }

    @GET("{packageName}.yml")
    suspend fun getFdroidInfoForPackage(@Path("packageName") packageName: String): Response<FdroidApiModel?>
}
