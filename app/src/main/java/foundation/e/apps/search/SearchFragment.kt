/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2021  E FOUNDATION
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.search

import android.app.Activity
import android.database.MatrixCursor
import android.os.Bundle
import android.provider.BaseColumns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.SearchView
import androidx.cursoradapter.widget.CursorAdapter
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aurora.gplayapi.SearchSuggestEntry
import com.facebook.shimmer.ShimmerFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.AppInfoFetchViewModel
import foundation.e.apps.AppProgressViewModel
import foundation.e.apps.MainActivityViewModel
import foundation.e.apps.PrivacyInfoViewModel
import foundation.e.apps.R
import foundation.e.apps.api.fused.FusedAPIInterface
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.applicationlist.model.ApplicationListRVAdapter
import foundation.e.apps.databinding.FragmentSearchBinding
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.User
import foundation.e.apps.utils.modules.PWAManagerModule
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment :
    Fragment(R.layout.fragment_search),
    SearchView.OnQueryTextListener,
    SearchView.OnSuggestionListener,
    FusedAPIInterface {

    @Inject
    lateinit var pkgManagerModule: PkgManagerModule

    @Inject
    lateinit var pwaManagerModule: PWAManagerModule

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val searchViewModel: SearchViewModel by viewModels()
    private val privacyInfoViewModel: PrivacyInfoViewModel by viewModels()
    private val appInfoFetchViewModel: AppInfoFetchViewModel by viewModels()
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()
    private val appProgressViewModel: AppProgressViewModel by viewModels()

    private val SUGGESTION_KEY = "suggestion"
    private var lastSearch = ""

    private var searchView: SearchView? = null
    private var shimmerLayout: ShimmerFrameLayout? = null
    private var recyclerView: RecyclerView? = null
    private var searchHintLayout: LinearLayout? = null
    private var noAppsFoundLayout: LinearLayout? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSearchBinding.bind(view)

        searchView = binding.searchView
        shimmerLayout = binding.shimmerLayout
        recyclerView = binding.recyclerView
        searchHintLayout = binding.searchHintLayout.root
        noAppsFoundLayout = binding.noAppsFoundLayout.root

        // Setup SearchView
        setHasOptionsMenu(true)
        searchView?.setOnSuggestionListener(this)
        searchView?.setOnQueryTextListener(this)
        searchView?.let { configureCloseButton(it) }

        // Setup SearchView Suggestions
        val from = arrayOf(SUGGESTION_KEY)
        val to = intArrayOf(android.R.id.text1)
        searchView?.suggestionsAdapter = SimpleCursorAdapter(
            context,
            R.layout.custom_simple_list_item, null, from, to,
            CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER
        )

        searchViewModel.searchSuggest.observe(viewLifecycleOwner) {
            it?.let { populateSuggestionsAdapter(it) }
        }

        // Setup Search Results
        val listAdapter = findNavController().currentDestination?.id?.let {
            ApplicationListRVAdapter(
                this,
                privacyInfoViewModel,
                appInfoFetchViewModel,
                mainActivityViewModel,
                it,
                pkgManagerModule,
                pwaManagerModule,
                User.valueOf(mainActivityViewModel.userType.value ?: User.UNAVAILABLE.name),
                viewLifecycleOwner
            ) { fusedApp ->
                if (!mainActivityViewModel.shouldShowPaidAppsSnackBar(fusedApp)) {
                    ApplicationDialogFragment(
                        title = getString(R.string.dialog_title_paid_app, fusedApp.name),
                        message = getString(
                            R.string.dialog_paidapp_message,
                            fusedApp.name,
                            fusedApp.price
                        ),
                        positiveButtonText = getString(R.string.dialog_confirm),
                        positiveButtonAction = {
                            getApplication(fusedApp)
                        },
                        cancelButtonText = getString(R.string.dialog_cancel),
                    ).show(childFragmentManager, "SearchFragment")
                }
            }
        }

        recyclerView?.apply {
            adapter = listAdapter
            layoutManager = LinearLayoutManager(view.context)
        }

        appProgressViewModel.downloadProgress.observe(viewLifecycleOwner) {
            val adapter = recyclerView?.adapter as ApplicationListRVAdapter
            lifecycleScope.launch {
                adapter.currentList.forEach { fusedApp ->
                    if (fusedApp.status == Status.DOWNLOADING) {
                        val progress = appProgressViewModel.calculateProgress(fusedApp, it)
                        val downloadProgress =
                            ((progress.second / progress.first.toDouble()) * 100).toInt()
                        val viewHolder = recyclerView?.findViewHolderForAdapterPosition(
                            adapter.currentList.indexOf(fusedApp)
                        )
                        viewHolder?.let {
                            (viewHolder as ApplicationListRVAdapter.ViewHolder).binding.installButton.text =
                                "$downloadProgress%"
                        }
                    }
                }
            }
        }

        mainActivityViewModel.downloadList.observe(viewLifecycleOwner) { list ->

            val searchList = searchViewModel.searchResult.value?.toMutableList()
            searchList?.let {
                mainActivityViewModel.updateStatusOfFusedApps(searchList, list)
            }
            searchViewModel.searchResult.value = searchList
        }

        searchViewModel.searchResult.observe(viewLifecycleOwner) {

            if (it.isNullOrEmpty()) {
                noAppsFoundLayout?.visibility = View.VISIBLE
            } else {

                listAdapter?.setData(it)
                shimmerLayout?.visibility = View.GONE
                recyclerView?.visibility = View.VISIBLE
                noAppsFoundLayout?.visibility = View.GONE
            }
            listAdapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    searchView?.run {
                        /*
                         * Only scroll back to 0 position for a new search.
                         *
                         * If we are getting new results from livedata for the old search query,
                         * do not scroll to top as the user may be scrolling to see already
                         * populated results.
                         */
                        if (lastSearch != query?.toString()) {
                            recyclerView?.scrollToPosition(0)
                            lastSearch = query.toString()
                        }
                    }
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        binding.shimmerLayout.startShimmer()
    }

    override fun onPause() {
        binding.shimmerLayout.stopShimmer()
        super.onPause()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {

        query?.let { text ->
            hideKeyboard(activity as Activity)
            view?.requestFocus()
            searchHintLayout?.visibility = View.GONE
            shimmerLayout?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
            noAppsFoundLayout?.visibility = View.GONE
            mainActivityViewModel.authData.value?.let { searchViewModel.getSearchResults(text, it, this) }
        }
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        newText?.let { text ->
            mainActivityViewModel.authData.value?.let {
                searchViewModel.getSearchSuggestions(text, it)
            }
        }
        return true
    }

    override fun onSuggestionSelect(position: Int): Boolean {
        return true
    }

    override fun onSuggestionClick(position: Int): Boolean {

        searchViewModel.searchSuggest.value?.let {

            searchView?.setQuery(it[position].suggestedQuery, true)
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        searchView = null
        shimmerLayout = null
        recyclerView = null
        searchHintLayout = null
        noAppsFoundLayout = null
    }

    private fun configureCloseButton(searchView: SearchView) {
        val searchClose = searchView.javaClass.getDeclaredField("mCloseButton")
        searchClose.isAccessible = true
        val closeImage = searchClose.get(searchView) as ImageView
        closeImage.setImageResource(R.drawable.ic_close)
    }

    private fun hideKeyboard(activity: Activity) {
        val inputMethodManager =
            activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = activity.currentFocus
        inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun populateSuggestionsAdapter(suggestions: List<SearchSuggestEntry>?) {
        val cursor = MatrixCursor(arrayOf(BaseColumns._ID, SUGGESTION_KEY))
        suggestions?.let {
            for (i in it.indices) {
                cursor.addRow(arrayOf(i, it[i].suggestedQuery))
            }
        }
        searchView?.suggestionsAdapter?.changeCursor(cursor)
    }

    override fun getApplication(app: FusedApp, appIcon: ImageView?) {
        mainActivityViewModel.getApplication(app, appIcon)
    }

    override fun cancelDownload(app: FusedApp) {
        mainActivityViewModel.cancelDownload(app)
    }
}
