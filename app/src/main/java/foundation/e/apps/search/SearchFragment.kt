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
import android.content.Context
import android.database.MatrixCursor
import android.os.Bundle
import android.provider.BaseColumns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.cursoradapter.widget.CursorAdapter
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.distinctUntilChanged
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
import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.api.fused.FusedAPIInterface
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.applicationlist.ApplicationListRVAdapter
import foundation.e.apps.databinding.FragmentSearchBinding
import foundation.e.apps.login.AuthObject
import foundation.e.apps.manager.download.data.DownloadProgress
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.exceptions.GPlayLoginException
import foundation.e.apps.utils.modules.PWAManagerModule
import foundation.e.apps.utils.parentFragment.TimeoutFragment
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment :
    TimeoutFragment(R.layout.fragment_search),
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

    /*
     * Store the string from onQueryTextSubmit() and access it from loadData()
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
     */
    private var searchText = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSearchBinding.bind(view)

        searchView = binding.searchView
        shimmerLayout = binding.shimmerLayout
        recyclerView = binding.recyclerView
        searchHintLayout = binding.searchHintLayout.root
        noAppsFoundLayout = binding.noAppsFoundLayout.root

        setupSearchView()
        setupSearchViewSuggestions()

        // Setup Search Results
        val listAdapter = setupSearchResult(view)

        observeSearchResult(listAdapter)

        setupListening()

        authObjects.observe(viewLifecycleOwner) {
            val currentQuery = searchView?.query?.toString() ?: ""
            if (it == null || (currentQuery.isNotEmpty() && lastSearch == currentQuery)) return@observe
            loadData(it)
        }

        searchViewModel.exceptionsLiveData.observe(viewLifecycleOwner) {
            handleExceptionsCommon(it)
        }
    }

    private fun observeSearchResult(listAdapter: ApplicationListRVAdapter?) {
        searchViewModel.searchResult.observe(viewLifecycleOwner) {
            if (it.data?.first.isNullOrEmpty() && it.data?.second == false) {
                noAppsFoundLayout?.visibility = View.VISIBLE
            } else {
                listAdapter?.let { adapter ->
                    observeDownloadList(adapter)
                }
            }
            observeScrollOfSearchResult(listAdapter)
        }
    }

    private fun observeScrollOfSearchResult(listAdapter: ApplicationListRVAdapter?) {
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

    /**
     * @return true if Search result is updated, otherwise false
     */
    private fun updateSearchResult(
        listAdapter: ApplicationListRVAdapter?,
        it: ResultSupreme<Pair<List<FusedApp>, Boolean>>
    ): Boolean {
        val currentList = listAdapter?.currentList ?: listOf()
        if (it.data?.first != null && !searchViewModel.isAnyAppUpdated(
                it.data?.first!!,
                currentList
            )
        ) {
            return false
        }

        binding.loadingProgressBar.isVisible = it.data!!.second
        stopLoadingUI()
        noAppsFoundLayout?.visibility = View.GONE
        searchHintLayout?.visibility = View.GONE
        listAdapter?.setData(it.data?.first!!)
        return true
    }

    private fun setupSearchResult(view: View): ApplicationListRVAdapter? {
        val listAdapter = findNavController().currentDestination?.id?.let {
            ApplicationListRVAdapter(
                this,
                privacyInfoViewModel,
                appInfoFetchViewModel,
                mainActivityViewModel,
                it,
                viewLifecycleOwner
            ) { fusedApp ->
                if (!mainActivityViewModel.shouldShowPaidAppsSnackBar(fusedApp)) {
                    showPaidAppMessage(fusedApp)
                }
            }
        }

        recyclerView?.apply {
            adapter = listAdapter
            layoutManager = LinearLayoutManager(view.context)
        }
        return listAdapter
    }

    private fun setupSearchViewSuggestions() {
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
    }

    private fun setupSearchView() {
        setHasOptionsMenu(true)
        searchView?.setOnSuggestionListener(this)
        searchView?.setOnQueryTextListener(this)
        searchView?.let { configureCloseButton(it) }
    }

    private fun showPaidAppMessage(fusedApp: FusedApp) {
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

    private fun observeDownloadList(applicationListRVAdapter: ApplicationListRVAdapter) {
        mainActivityViewModel.downloadList.removeObservers(viewLifecycleOwner)
        mainActivityViewModel.downloadList.observe(viewLifecycleOwner) { list ->
            val searchList =
                searchViewModel.searchResult.value?.data?.first?.toMutableList() ?: emptyList()
            searchList.let {
                mainActivityViewModel.updateStatusOfFusedApps(searchList, list)
                updateSearchResult(applicationListRVAdapter, ResultSupreme.Success(Pair(it, true)))
            }
        }
    }

    override fun onTimeout(
        exception: Exception,
        predefinedDialog: AlertDialog.Builder
    ): AlertDialog.Builder? {
        return predefinedDialog
    }

    override fun onDataLoadError(
        exception: Exception,
        predefinedDialog: AlertDialog.Builder
    ): AlertDialog.Builder? {
        return predefinedDialog
    }

    override fun onSignInError(
        exception: GPlayLoginException,
        predefinedDialog: AlertDialog.Builder
    ): AlertDialog.Builder? {
        return predefinedDialog
    }

    override fun loadData(authObjectList: List<AuthObject>) {
        showLoadingUI()
        searchViewModel.loadData(searchText, viewLifecycleOwner, authObjectList) {
            clearAndRestartGPlayLogin()
            true
        }
    }

    override fun showLoadingUI() {
        binding.shimmerLayout.startShimmer()
        binding.shimmerLayout.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    override fun stopLoadingUI() {
        binding.shimmerLayout.stopShimmer()
        binding.shimmerLayout.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
    }

    private fun updateProgressOfInstallingApps(downloadProgress: DownloadProgress) {
        val adapter = recyclerView?.adapter as ApplicationListRVAdapter
        lifecycleScope.launch {
            adapter.currentList.forEach { fusedApp ->
                if (fusedApp.status == Status.DOWNLOADING) {
                    val progress =
                        appProgressViewModel.calculateProgress(fusedApp, downloadProgress)
                    if (progress == -1) {
                        return@forEach
                    }
                    val viewHolder = recyclerView?.findViewHolderForAdapterPosition(
                        adapter.currentList.indexOf(fusedApp)
                    )
                    viewHolder?.let {
                        (viewHolder as ApplicationListRVAdapter.ViewHolder).binding.installButton.text =
                            "$progress%"
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.shimmerLayout.startShimmer()
        addDownloadProgressObservers()

        if (shouldRefreshData()) {
            repostAuthObjects()
        }

        if (searchText.isEmpty() && (recyclerView?.adapter as ApplicationListRVAdapter).currentList.isEmpty()) {
            searchView?.requestFocus()
            showKeyboard()
        }
    }

    private fun addDownloadProgressObservers() {
        appProgressViewModel.downloadProgress.removeObservers(viewLifecycleOwner)
        appProgressViewModel.downloadProgress.observe(viewLifecycleOwner) {
            updateProgressOfInstallingApps(it)
        }
    }

    private fun shouldRefreshData() =
        searchText.isNotEmpty() && recyclerView?.adapter != null && searchViewModel.hasAnyAppInstallStatusChanged(
            (recyclerView?.adapter as ApplicationListRVAdapter).currentList
        )

    override fun onPause() {
        binding.shimmerLayout.stopShimmer()
        hideKeyboard(requireActivity())
        super.onPause()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        query?.let { text ->
            if (text.isNotEmpty()) {
                hideKeyboard(activity as Activity)
            }
            view?.requestFocus()
            searchHintLayout?.visibility = View.GONE
            shimmerLayout?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
            noAppsFoundLayout?.visibility = View.GONE
            /*
             * Set the search text and call for network result.
             */
            searchText = text
            repostAuthObjects()
        }
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        newText?.let { text ->
            authObjects.value?.find { it is AuthObject.GPlayAuth }?.run {
                searchViewModel.getSearchSuggestions(text, this as AuthObject.GPlayAuth)
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
        recyclerView?.adapter = null
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

    private fun showKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
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
