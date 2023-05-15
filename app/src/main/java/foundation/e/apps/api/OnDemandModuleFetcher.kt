package foundation.e.apps.api

import foundation.e.apps.manager.database.fusedDownload.FusedDownload

interface OnDemandModuleFetcher {
    suspend fun getOnDemandModule(
        packageName: String,
        moduleName: String,
        versionCode: Int,
        offerType: Int
    ): Any
}