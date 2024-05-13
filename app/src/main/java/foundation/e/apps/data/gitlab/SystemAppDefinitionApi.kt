package foundation.e.apps.data.gitlab

import foundation.e.apps.data.gitlab.models.UpdateDefinition
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface SystemAppDefinitionApi {

    companion object {
        const val BASE_URL =
            "https://gitlab.e.foundation/api/v4/projects/"
    }

    @GET("{projectId}/releases/permalink/latest/downloads/json/{releaseType}.json")
    suspend fun getSystemAppUpdateInfo(
        @Path("projectId") projectId: Int,
        @Path("releaseType") releaseType: String,
    ): Response<UpdateDefinition>

}