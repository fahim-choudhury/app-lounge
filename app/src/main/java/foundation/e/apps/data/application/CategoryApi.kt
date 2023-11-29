package foundation.e.apps.data.application

import com.aurora.gplayapi.data.models.AuthData
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Category
import foundation.e.apps.data.application.utils.CategoryType
import foundation.e.apps.data.enums.ResultStatus

interface CategoryApi {

    /*
        * Return three elements from the function.
        * - List<FusedCategory> : List of categories.
        * - String : String of application type - By default it is the value in preferences.
        * In case there is any failure, for a specific type in handleAllSourcesCategories(),
        * the string value is of that type.
        * - ResultStatus : ResultStatus - by default is ResultStatus.OK. But in case there is a failure in
        * any application category type, then it takes value of that failure.
        *
        * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
        */
    suspend fun getCategoriesList(
        type: CategoryType,
    ): Triple<List<Category>, String, ResultStatus>

    suspend fun getGplayAppsByCategory(
        authData: AuthData,
        category: String,
        pageUrl: String?
    ): ResultSupreme<Pair<List<Application>, String>>

    suspend fun getPWAApps(category: String): ResultSupreme<Pair<List<Application>, String>>

    suspend fun getOpenSourceApps(category: String): ResultSupreme<Pair<List<Application>, String>>
}
