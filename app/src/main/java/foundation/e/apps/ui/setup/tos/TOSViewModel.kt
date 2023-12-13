package foundation.e.apps.ui.setup.tos

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.data.preference.AppLoungeDataStore
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TOSViewModel @Inject constructor(
    private val appLoungeDataStore: AppLoungeDataStore
) : ViewModel() {

    val tocStatus: LiveData<Boolean> = appLoungeDataStore.tocStatus.asLiveData()

    fun saveTOCStatus(status: Boolean) {
        viewModelScope.launch {
            appLoungeDataStore.saveTOCStatus(status, TOS_VERSION)
        }
    }
}

const val TOS_VERSION = "1.0.3"
