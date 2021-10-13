package foundation.e.apps.search

import android.util.Log
import androidx.lifecycle.*
import com.aurora.gplayapi.SearchSuggestEntry
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.SearchHelper
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import foundation.e.apps.api.gplay.GPlayAPIRepository
import foundation.e.apps.api.gplay.utils.OkHttpClient
import foundation.e.apps.utils.DataStoreModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val gPlayAPIRepository: GPlayAPIRepository,
    private val gson: Gson,
    private val dataStoreModule: DataStoreModule
) : ViewModel() {

    private val TAG = SearchViewModel::class.java.simpleName

    val authData: LiveData<String?> = dataStoreModule.authData.asLiveData()

    val searchSuggest: MutableLiveData<List<SearchSuggestEntry>?> = MutableLiveData()
    val searchResult: MutableLiveData<List<App>> = MutableLiveData()

    // TODO: Move below stuff to gplayimpl class and use FusedAPI
    fun getAuthData() {
        viewModelScope.launch {
            gPlayAPIRepository.fetchAuthData()
        }
    }

    fun getSearchSuggestions(query: String) {
        Log.d(TAG, query)
        viewModelScope.launch(Dispatchers.IO) {
            val data = authData.value?.let { gson.fromJson(it, AuthData::class.java) }
            data?.let {
                val searchHelper = SearchHelper(data).using(OkHttpClient)
                searchSuggest.postValue(searchHelper.searchSuggestions(query))
            }
        }
    }

    fun getSearchResults() {
        viewModelScope.launch(Dispatchers.IO) {
            val data = authData.value?.let { gson.fromJson(it, AuthData::class.java) }
            data?.let {
                val searchHelper = SearchHelper(it).using(OkHttpClient)
                searchResult.postValue(searchHelper.searchResults("telegram").appList)
            }
        }
    }


}