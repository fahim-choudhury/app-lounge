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

package foundation.e.apps.ui.applicationlist

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.application.ApplicationInstaller
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.GPlayLoginException
import foundation.e.apps.databinding.FragmentApplicationListBinding
import foundation.e.apps.install.download.data.DownloadProgress
import foundation.e.apps.install.pkg.PWAManager
import foundation.e.apps.install.pkg.AppLoungePackageManager
import foundation.e.apps.ui.AppInfoFetchViewModel
import foundation.e.apps.ui.AppProgressViewModel
import foundation.e.apps.ui.MainActivityViewModel
import foundation.e.apps.ui.PrivacyInfoViewModel
import foundation.e.apps.ui.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.ui.parentFragment.TimeoutFragment
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ApplicationListFragment :
    TimeoutFragment(R.layout.fragment_application_list),
    ApplicationInstaller {

    // protected to avoid SyntheticAccessor
    protected val args: ApplicationListFragmentArgs by navArgs()

    @Inject
    lateinit var appLoungePackageManager: AppLoungePackageManager

    @Inject
    lateinit var pwaManager: PWAManager

    // protected to avoid SyntheticAccessor
    protected val viewModel: ApplicationListViewModel by viewModels()
    private val privacyInfoViewModel: PrivacyInfoViewModel by viewModels()
    private val appInfoFetchViewModel: AppInfoFetchViewModel by viewModels()
    override val mainActivityViewModel: MainActivityViewModel by activityViewModels()
    private val appProgressViewModel: AppProgressViewModel by viewModels()

    private var _binding: FragmentApplicationListBinding? = null
    private val binding get() = _binding!!

    private lateinit var listAdapter: ApplicationListRVAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentApplicationListBinding.bind(view)

        updateToolbar(view)
        setupRecyclerView(view)
        observeAppListLiveData()

        setupListening()

        authObjects.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            loadDataWhenNetworkAvailable(it)
        }

        viewModel.exceptionsLiveData.observe(viewLifecycleOwner) {
            handleExceptionsCommon(it)
        }
    }

    private fun updateToolbar(view: View) {
        binding.toolbarTitleTV.text = args.translation
        binding.toolbar.apply {
            setNavigationOnClickListener {
                view.findNavController().navigateUp()
            }
        }
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView = initRecyclerView()
        findNavController().currentDestination?.id?.let {
            listAdapter = initAppListAdapter(it)
        }

        recyclerView.apply {
            adapter = listAdapter
            layoutManager = LinearLayoutManager(view.context)
        }
    }

    private fun observeDownloadList(
        adapter: ApplicationListRVAdapter,
        applicationResult: ResultSupreme<List<Application>>
    ) {
        mainActivityViewModel.downloadList.removeObservers(viewLifecycleOwner)
        mainActivityViewModel.downloadList.observe(viewLifecycleOwner) { list ->
            val appList = viewModel.appListLiveData.value?.data?.toMutableList() ?: emptyList()

            appList.let {
                mainActivityViewModel.updateStatusOfFusedApps(it, list)
                if (isFusedAppsUpdated(applicationResult, listAdapter.currentList)) {
                    adapter.setData(it, args.translation)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.recyclerView?.adapter = null
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        addDownloadProgressObserver()

        if (listAdapter.currentList.isNotEmpty() && viewModel.hasAnyAppInstallStatusChanged(
                listAdapter.currentList
            )
        ) {
            repostAuthObjects()
        }
    }

    private fun addDownloadProgressObserver() {
        appProgressViewModel.downloadProgress.removeObservers(viewLifecycleOwner)
        appProgressViewModel.downloadProgress.observe(viewLifecycleOwner) {
            updateProgressOfDownloadingItems(binding.recyclerView, it)
        }
    }

    private fun observeAppListLiveData() {
        viewModel.appListLiveData.observe(viewLifecycleOwner) {
            stopLoadingUI()
            if (it != null && it.isSuccess()) {
                observeDownloadList(listAdapter, it)
            }
        }
    }

    private fun initAppListAdapter(
        currentDestinationId: Int
    ): ApplicationListRVAdapter {
        return ApplicationListRVAdapter(
            this,
            privacyInfoViewModel,
            appInfoFetchViewModel,
            mainActivityViewModel,
            currentDestinationId,
            viewLifecycleOwner
        ) { fusedApp ->
            if (!mainActivityViewModel.shouldShowPaidAppsSnackBar(fusedApp)) {
                showPaidAppMessage(fusedApp)
            }
        }
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
        ).show(childFragmentManager, "HomeFragment")
    }

    private fun initRecyclerView(): RecyclerView {
        val recyclerView = binding.recyclerView
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 0)
        return recyclerView
    }

    private fun isFusedAppsUpdated(
        applicationResult: ResultSupreme<List<Application>>,
        currentList: MutableList<Application>?
    ) = currentList.isNullOrEmpty() || applicationResult.data != null && viewModel.isFusedAppUpdated(
        applicationResult.data!!,
        currentList
    )

    override fun loadData(authObjectList: List<AuthObject>) {

        /*
         * If details are once loaded, do not load details again,
         * Only set the scroll listeners.
         *
         * Here "details" word means:
         * For GPlay apps - first set of data
         * For cleanapk apps - all apps.
         *
         * Issue: https://gitlab.e.foundation/e/os/backlog/-/issues/478
         */
        showLoadingUI()
        viewModel.loadData(args.category, args.source, authObjectList) {
            clearAndRestartGPlayLogin()
            true
        }

        if (args.source != "Open Source" && args.source != "PWA") {
            /*
             * For Play store apps we try to load more apps on reaching end of list.
             * Source: https://stackoverflow.com/a/46342525
             */
            binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (!recyclerView.canScrollVertically(1)) {
                        viewModel.loadMore(
                            authObjectList.find { it is AuthObject.GPlayAuth },
                            args.category
                        )
                    }
                }
            })
            /*
             * This listener comes handy in the case where only 2-3 apps are loaded
             * in the first cluster.
             * In that case, unless the user scrolls, the above listener will not be
             * triggered. Setting this onPlaceHolderShow() callback loads new data
             * automatically if the initial data is less.
             */
            binding.recyclerView.adapter.apply {
                if (this is ApplicationListRVAdapter) {
                    onPlaceHolderShow = {
                        viewModel.loadMore(
                            authObjectList.find { it is AuthObject.GPlayAuth },
                            args.category
                        )
                    }
                }
            }
        }
    }

    override fun onTimeout(
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

    override fun onDataLoadError(
        exception: Exception,
        predefinedDialog: AlertDialog.Builder
    ): AlertDialog.Builder? {
        return predefinedDialog
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

    private fun updateProgressOfDownloadingItems(
        recyclerView: RecyclerView,
        downloadProgress: DownloadProgress
    ) {
        val adapter = recyclerView.adapter as ApplicationListRVAdapter
        viewLifecycleOwner.lifecycleScope.launch {
            adapter.currentList.forEach { fusedApp ->
                if (fusedApp.status == Status.DOWNLOADING) {
                    val progress =
                        appProgressViewModel.calculateProgress(fusedApp, downloadProgress)
                    if (progress == -1) {
                        return@forEach
                    }
                    val viewHolder = recyclerView.findViewHolderForAdapterPosition(
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

    override fun onPause() {
        binding.shimmerLayout.stopShimmer()
        super.onPause()
    }

    override fun installApplication(app: Application) {
        mainActivityViewModel.getApplication(app)
    }

    override fun cancelDownload(app: Application) {
        mainActivityViewModel.cancelDownload(app)
    }
}
