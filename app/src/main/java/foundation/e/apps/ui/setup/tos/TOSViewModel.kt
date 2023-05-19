package foundation.e.apps.ui.setup.tos

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.utils.modules.DataStoreModule
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TOSViewModel @Inject constructor(
    private val dataStoreModule: DataStoreModule
) : ViewModel() {

    val tocStatus: LiveData<Boolean> = dataStoreModule.tocStatus.asLiveData()

    fun saveTOCStatus(status: Boolean) {
        viewModelScope.launch {
            dataStoreModule.saveTOCStatus(status, TOS_VERSION)
        }
    }
}

const val TOS_VERSION = "1.0.3"
