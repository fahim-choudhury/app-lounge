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

package foundation.e.apps.applicationlist

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aurora.gplayapi.data.models.AuthData
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
import foundation.e.apps.databinding.FragmentApplicationListBinding
import foundation.e.apps.manager.download.data.DownloadProgress
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.User
import foundation.e.apps.utils.parentFragment.TimeoutFragment
import foundation.e.apps.utils.modules.PWAManagerModule
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ApplicationListFragment : TimeoutFragment(R.layout.fragment_application_list), FusedAPIInterface {

    private val args: ApplicationListFragmentArgs by navArgs()

    @Inject
    lateinit var pkgManagerModule: PkgManagerModule

    @Inject
    lateinit var pwaManagerModule: PWAManagerModule

    private val viewModel: ApplicationListViewModel by viewModels()
    private val privacyInfoViewModel: PrivacyInfoViewModel by viewModels()
    private val appInfoFetchViewModel: AppInfoFetchViewModel by viewModels()
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()
    private val appProgressViewModel: AppProgressViewModel by viewModels()

    private var _binding: FragmentApplicationListBinding? = null
    private val binding get() = _binding!!
    private var isDownloadObserverAdded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentApplicationListBinding.bind(view)

        binding.toolbarTitleTV.text = args.translation
        binding.toolbar.apply {
            setNavigationOnClickListener {
                view.findNavController().navigate(R.id.categoriesFragment)
            }
        }
    }

    private fun observeDownloadList() {
        mainActivityViewModel.downloadList.observe(viewLifecycleOwner) { list ->
            val appList = viewModel.appListLiveData.value?.first?.toMutableList() ?: emptyList()
            appList.let {
                mainActivityViewModel.updateStatusOfFusedApps(it, list)
            }

            /*
             * Done in one line, so that on Ctrl+click on appListLiveData,
             * we can see that it is being updated here.
             */
            viewModel.appListLiveData.apply { value = Pair(appList, value?.second) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        binding.shimmerLayout.startShimmer()

        val recyclerView = binding.recyclerView
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 0)
        val listAdapter =
            findNavController().currentDestination?.id?.let {
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
                        ).show(childFragmentManager, "HomeFragment")
                    }
                }
            }

        recyclerView.apply {
            adapter = listAdapter
            layoutManager = LinearLayoutManager(view?.context)
        }

        appProgressViewModel.downloadProgress.observe(viewLifecycleOwner) {
            updateProgressOfDownloadingItems(recyclerView, it)
        }

        viewModel.appListLiveData.observe(viewLifecycleOwner) {
            listAdapter?.setData(it.first)
            if (!isDownloadObserverAdded) {
                observeDownloadList()
                isDownloadObserverAdded = true
            }
            stopLoadingUI()
            if (it.second != ResultStatus.OK) {
                onTimeout()
            }
        }

        /*
         * Explanation of double observers in HomeFragment.kt
         */

        mainActivityViewModel.internetConnection.observe(viewLifecycleOwner) {
            refreshDataOrRefreshToken(mainActivityViewModel)
        }
        mainActivityViewModel.authData.observe(viewLifecycleOwner) {
            refreshDataOrRefreshToken(mainActivityViewModel)
        }
    }

    override fun onTimeout() {
        if (!isTimeoutDialogDisplayed()) {
            stopLoadingUI()
            displayTimeoutAlertDialog(
                timeoutFragment = this,
                activity = requireActivity(),
                message = getString(R.string.timeout_desc_cleanapk),
                positiveButtonText = getString(R.string.retry),
                positiveButtonBlock = {
                    showLoadingUI()
                    resetTimeoutDialogLock()
                    mainActivityViewModel.retryFetchingTokenAfterTimeout()
                },
                negativeButtonText = getString(android.R.string.ok),
                negativeButtonBlock = {},
                allowCancel = true,
            )
        }
    }

    override fun refreshData(authData: AuthData) {
        showLoadingUI()

        /*
         * Code moved from onResume()
         */

        viewModel.getList(
            args.category,
            args.browseUrl,
            authData,
            args.source
        )

        if (args.source != "Open Source" && args.source != "PWA") {
            /*
             * For Play store apps we try to load more apps on reaching end of list.
             * Source: https://stackoverflow.com/a/46342525
             */
            binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (!recyclerView.canScrollVertically(1)) {
                        viewModel.getPlayStoreAppsOnScroll(args.browseUrl, authData)
                    }
                }
            })
        }
    }

    private fun showLoadingUI() {
        binding.shimmerLayout.startShimmer()
        binding.shimmerLayout.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun stopLoadingUI() {
        binding.shimmerLayout.stopShimmer()
        binding.shimmerLayout.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
    }

    private fun updateProgressOfDownloadingItems(
        recyclerView: RecyclerView,
        it: DownloadProgress
    ) {
        val adapter = recyclerView.adapter as ApplicationListRVAdapter
        lifecycleScope.launch {
            adapter.currentList.forEach { fusedApp ->
                if (fusedApp.status == Status.DOWNLOADING) {
                    val progress = appProgressViewModel.calculateProgress(fusedApp, it)
                    val downloadProgress =
                        ((progress.second / progress.first.toDouble()) * 100).toInt()
                    val viewHolder = recyclerView.findViewHolderForAdapterPosition(
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

    override fun onPause() {
        isDownloadObserverAdded = false
        binding.shimmerLayout.stopShimmer()
        super.onPause()
    }

    override fun getApplication(app: FusedApp, appIcon: ImageView?) {
        mainActivityViewModel.getApplication(app, appIcon)
    }

    override fun cancelDownload(app: FusedApp) {
        mainActivityViewModel.cancelDownload(app)
    }
}
