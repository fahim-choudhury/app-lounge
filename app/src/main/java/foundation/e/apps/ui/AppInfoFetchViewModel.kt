package foundation.e.apps.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.blockedApps.BlockedAppRepository
import foundation.e.apps.data.faultyApps.FaultyAppRepository
import foundation.e.apps.data.fdroid.FdroidRepository
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.gplay.GplayStoreRepository
import foundation.e.apps.data.preference.DataStoreModule
import javax.inject.Inject
import javax.inject.Named

/**
 *
 */
@HiltViewModel
class AppInfoFetchViewModel @Inject constructor(
    private val fdroidRepository: FdroidRepository,
    @Named("gplayRepository") private val gplayRepository: GplayStoreRepository,
    private val faultyAppRepository: FaultyAppRepository,
    private val dataStoreModule: DataStoreModule,
    private val blockedAppRepository: BlockedAppRepository,
    private val gson: Gson
) : ViewModel() {

    fun getAuthorName(fusedApp: FusedApp) = liveData {
        val authorName = fdroidRepository.getAuthorName(fusedApp)
        emit(authorName)
    }

    fun isAppPurchased(app: FusedApp): LiveData<Boolean> {
        return liveData {
            val authData = gson.fromJson(dataStoreModule.getAuthDataSync(), AuthData::class.java)
            try {
                gplayRepository.getDownloadInfo(
                    app.package_name,
                    app.latest_version_code,
                    app.offer_type,
                )
                app.isPurchased = true
                emit(true)
            } catch (e: Exception) {
                app.isPurchased = false
                emit(false)
            }
        }
    }

    fun isAppInBlockedList(fusedApp: FusedApp): Boolean {
        return blockedAppRepository.getBlockedAppPackages().contains(fusedApp.package_name)
    }

    fun isAppFaulty(fusedApp: FusedApp) = liveData<Pair<Boolean, String>> {
        val faultyApp = faultyAppRepository.getAllFaultyApps()
            .find { faultyApp -> faultyApp.packageName.contentEquals(fusedApp.package_name) }
        val faultyAppResult = Pair(faultyApp != null, faultyApp?.error ?: "")
        emit(faultyAppResult)
    }
}
