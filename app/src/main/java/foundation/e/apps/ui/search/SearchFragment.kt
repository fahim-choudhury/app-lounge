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

package foundation.e.apps.ui.search

import android.app.Activity
import android.content.Context
import android.database.MatrixCursor
import android.os.Bundle
import android.provider.BaseColumns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.cursoradapter.widget.CursorAdapter
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aurora.gplayapi.SearchSuggestEntry
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.application.ApplicationInstaller
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.GPlayLoginException
import foundation.e.apps.databinding.FragmentSearchBinding
import foundation.e.apps.install.download.data.DownloadProgress
import foundation.e.apps.install.pkg.PWAManager
import foundation.e.apps.ui.AppInfoFetchViewModel
import foundation.e.apps.ui.AppProgressViewModel
import foundation.e.apps.ui.MainActivityViewModel
import foundation.e.apps.ui.PrivacyInfoViewModel
import foundation.e.apps.ui.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.ui.applicationlist.ApplicationListRVAdapter
import foundation.e.apps.ui.parentFragment.TimeoutFragment
import foundation.e.apps.utils.isNetworkAvailable
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment :
    TimeoutFragment(R.layout.fragment_search),
    SearchView.OnQueryTextListener,
    SearchView.OnSuggestionListener,
    ApplicationInstaller {

    @Inject
    lateinit var pwaManager: PWAManager

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    protected val searchViewModel: SearchViewModel by viewModels()
    private val privacyInfoViewModel: PrivacyInfoViewModel by viewModels()
    private val appInfoFetchViewModel: AppInfoFetchViewModel by viewModels()
    override val mainActivityViewModel: MainActivityViewModel by activityViewModels()
    private val appProgressViewModel: AppProgressViewModel by viewModels()

    private val SUGGESTION_KEY = "suggestion"
    private var lastSearch = ""

    private var searchView: SearchView? = null
    private var shimmerLayout: ShimmerFrameLayout? = null
    private var recyclerView: RecyclerView? = null
    private var searchHintLayout: LinearLayout? = null
    private var noAppsFoundLayout: LinearLayout? = null

    lateinit var filterChipNoTrackers: Chip
    lateinit var filterChipOpenSource: Chip
    lateinit var filterChipPWA: Chip

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

        filterChipNoTrackers = binding.filterChipNoTrackers
        filterChipOpenSource = binding.filterChipOpenSource
        filterChipPWA = binding.filterChipPWA

        setupSearchView()
        setupSearchViewSuggestions()

        // Setup Search Results
        val listAdapter = setupSearchResult(view)

        preventLoadingLessResults()
        observeSearchResult(listAdapter)

        setupSearchFilters()

        setupListening()

        authObjects.observe(viewLifecycleOwner) {
            val currentQuery = searchView?.query?.toString() ?: ""
            if (it == null || shouldIgnore(it, currentQuery)) {
                return@observe
            }

            if (currentQuery.isNotEmpty()) searchText = currentQuery

            val applicationListRVAdapter = recyclerView?.adapter as ApplicationListRVAdapter
            applicationListRVAdapter.setData(mutableListOf())

            loadDataWhenNetworkAvailable(it)
        }

        searchViewModel.exceptionsLiveData.observe(viewLifecycleOwner) {
            handleExceptionsCommon(it)
        }

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (!recyclerView.canScrollVertically(1)) {
                    if (!requireContext().isNetworkAvailable()) {
                        return
                    }

                    if (authObjects.value?.none { it is AuthObject.GPlayAuth } == true) {
                        return
                    }

                    searchViewModel.loadMore(searchText)
                }
            }
        })
    }

    private fun shouldIgnore(
        authObjectList: List<AuthObject>?,
        currentQuery: String
    ) = currentQuery.isNotEmpty() && searchViewModel.isAuthObjectListSame(authObjectList) &&
        lastSearch == currentQuery

    private fun observeSearchResult(listAdapter: ApplicationListRVAdapter?) {
        searchViewModel.searchResult.observe(viewLifecycleOwner) {
            if (it.data?.first.isNullOrEmpty() && it.data?.second == false) {
                noAppsFoundLayout?.visibility = View.VISIBLE
            } else if (searchViewModel.shouldIgnoreResults()) {
                return@observe
            } else {
                listAdapter?.let { adapter ->
                    observeDownloadList(adapter)
                }
            }
            observeScrollOfSearchResult(listAdapter)
        }
    }

    private fun preventLoadingLessResults() {
        searchViewModel.gplaySearchLoaded.observe(viewLifecycleOwner) {
            if (!it) return@observe

            searchViewModel.loadMoreDataIfNeeded(searchText)
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
                     *
                     * Compare lastSearch with searchText to avoid falsely updating to
                     * current query text even before submitting the new search.
                     */
                    if (lastSearch != searchText) {
                        recyclerView?.scrollToPosition(0)
                        lastSearch = searchText
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
        appList: List<Application>,
    ): Boolean {
        val currentList = listAdapter?.currentList ?: listOf()
        if (!searchViewModel.isAnyAppUpdated(appList, currentList)) {
            return false
        }

        showData()
        listAdapter?.setData(appList)
        return true
    }

    private fun showData() {
        stopLoadingUI()
        noAppsFoundLayout?.visibility = View.GONE
        searchHintLayout?.visibility = View.GONE
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

    private fun setupSearchFilters() {

        binding.filterChipGroup.isSingleSelection = true

        val listener = OnCheckedChangeListener { _, _ ->
            showLoadingUI()
            searchViewModel.setFilterFlags(
                flagNoTrackers = filterChipNoTrackers.isChecked,
                flagOpenSource = filterChipOpenSource.isChecked,
                flagPWA = filterChipPWA.isChecked,
            )

            recyclerView?.scrollToPosition(0)
        }

        filterChipNoTrackers.setOnCheckedChangeListener(listener)
        filterChipOpenSource.setOnCheckedChangeListener(listener)
        filterChipPWA.setOnCheckedChangeListener(listener)
    }

    private fun setupSearchView() {
        setHasOptionsMenu(true)
        searchView?.setOnSuggestionListener(this)
        searchView?.setOnQueryTextListener(this)
        searchView?.let { configureCloseButton(it) }
    }

    private fun showPaidAppMessage(application: Application) {
        ApplicationDialogFragment(
            title = getString(R.string.dialog_title_paid_app, application.name),
            message = getString(
                R.string.dialog_paidapp_message,
                application.name,
                application.price
            ),
            positiveButtonText = getString(R.string.dialog_confirm),
            positiveButtonAction = {
                installApplication(application)
            },
            cancelButtonText = getString(R.string.dialog_cancel),
        ).show(childFragmentManager, "SearchFragment")
    }

    private fun observeDownloadList(applicationListRVAdapter: ApplicationListRVAdapter) {
        mainActivityViewModel.downloadList.removeObservers(viewLifecycleOwner)
        mainActivityViewModel.downloadList.observe(viewLifecycleOwner) { fusedDownloadList ->
            refreshUI(fusedDownloadList, applicationListRVAdapter)
        }
    }

    private fun refreshUI(
        fusedDownloadList: List<FusedDownload>,
        applicationListRVAdapter: ApplicationListRVAdapter
    ) {
        val searchList =
            searchViewModel.searchResult.value?.data?.first?.toMutableList() ?: emptyList()

        mainActivityViewModel.updateStatusOfFusedApps(searchList, fusedDownloadList)
        updateSearchResult(applicationListRVAdapter, searchList)
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
        shimmerLayout?.visibility = View.VISIBLE
        shimmerLayout?.startShimmer()
    }

    override fun stopLoadingUI() {
        shimmerLayout?.stopShimmer()
        shimmerLayout?.visibility = View.GONE
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
                            String.format("%d%%", progress)
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
            if (binding.recyclerView.adapter is ApplicationListRVAdapter) {
                val searchAdapter = binding.recyclerView.adapter as ApplicationListRVAdapter
                observeDownloadList(searchAdapter)
            }
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
        searchText.isNotEmpty() && recyclerView?.adapter != null

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
            if (it.isNotEmpty()) {
                searchView?.setQuery(it[position].suggestedQuery, true)
            }
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
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        searchView?.javaClass?.getDeclaredField("mSearchSrcTextView")?.runCatching {
            isAccessible = true
            get(searchView) as EditText
        }?.onSuccess {
            inputMethodManager.showSoftInput(it, InputMethodManager.SHOW_FORCED)
        }
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

    override fun installApplication(app: Application) {
        mainActivityViewModel.getApplication(app)
    }

    override fun cancelDownload(app: Application) {
        mainActivityViewModel.cancelDownload(app)
    }
}
