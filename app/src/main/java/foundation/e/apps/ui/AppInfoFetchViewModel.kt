package foundation.e.apps.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.blockedApps.BlockedAppRepository
import foundation.e.apps.data.faultyApps.FaultyAppRepository
import foundation.e.apps.data.fdroid.FdroidRepository
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.playstore.PlayStoreRepository
import javax.inject.Inject
import javax.inject.Named

/**
 *
 */
@HiltViewModel
class AppInfoFetchViewModel @Inject constructor(
    private val fdroidRepository: FdroidRepository,
    @Named("gplayRepository") private val gplayRepository: PlayStoreRepository,
    private val faultyAppRepository: FaultyAppRepository,
    private val blockedAppRepository: BlockedAppRepository,
) : ViewModel() {

    fun getAuthorName(application: Application) = liveData {
        val authorName = fdroidRepository.getAuthorName(application)
        emit(authorName)
    }

    fun isAppPurchased(app: Application): LiveData<Boolean> {
        return liveData {
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

    fun isAppInBlockedList(application: Application): Boolean {
        return blockedAppRepository.getBlockedAppPackages().contains(application.package_name)
    }

    fun isAppFaulty(application: Application) = liveData<Pair<Boolean, String>> {
        val faultyApp = faultyAppRepository.getAllFaultyApps()
            .find { faultyApp -> faultyApp.packageName.contentEquals(application.package_name) }
        val faultyAppResult = Pair(faultyApp != null, faultyApp?.error ?: "")
        emit(faultyAppResult)
    }
}
