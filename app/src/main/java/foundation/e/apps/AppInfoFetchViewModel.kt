package foundation.e.apps

import android.widget.TextView
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.AuthData
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.cleanapk.blockedApps.BlockedAppRepository
import foundation.e.apps.api.faultyApps.FaultyAppRepository
import foundation.e.apps.api.fdroid.FdroidRepository
import foundation.e.apps.api.fdroid.models.FdroidEntity
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.gplay.GPlayAPIRepository
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.modules.DataStoreModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 *
 */
@HiltViewModel
class AppInfoFetchViewModel @Inject constructor(
    private val fdroidRepository: FdroidRepository,
    private val gPlayAPIRepository: GPlayAPIRepository,
    private val faultyAppRepository: FaultyAppRepository,
    private val dataStoreModule: DataStoreModule,
    private val blockedAppRepository: BlockedAppRepository,
    private val gson: Gson
) : ViewModel() {

    private val fdroidEntries = mutableMapOf<String, FdroidEntity?>()

    fun setAuthorNameIfNeeded(textView: TextView, fusedApp: FusedApp) {
        viewModelScope.launch {
            var authorNameToDisplay = textView.text
            withContext(Dispatchers.Default) {
                fusedApp.run {
                    try {
                        if (author == "unknown" && origin == Origin.CLEANAPK) {

                            withContext(Dispatchers.Main) {
                                textView.text = FdroidEntity.DEFAULT_FDROID_AUTHOR_NAME
                            }

                            var result = fdroidEntries[package_name]
                            if (result == null) {
                                result = fdroidRepository.getFdroidInfo(package_name)?.also {
                                    fdroidEntries[package_name] = it
                                }
                            }
                            result?.authorName?.let {
                                authorNameToDisplay = it
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            withContext(Dispatchers.Main) {
                textView.text = authorNameToDisplay
            }
        }
    }

    fun isAppPurchased(app: FusedApp): LiveData<Boolean> {
        return liveData {
            val authData = gson.fromJson(dataStoreModule.getAuthDataSync(), AuthData::class.java)
            try {
                gPlayAPIRepository.getDownloadInfo(
                    app.package_name,
                    app.latest_version_code,
                    app.offer_type,
                    authData
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
