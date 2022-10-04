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

package foundation.e.apps.home

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.AppInfoFetchViewModel
import foundation.e.apps.AppProgressViewModel
import foundation.e.apps.MainActivityViewModel
import foundation.e.apps.R
import foundation.e.apps.api.fused.FusedAPIInterface
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.api.fused.data.FusedHome
import foundation.e.apps.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.databinding.FragmentHomeBinding
import foundation.e.apps.home.model.HomeChildRVAdapter
import foundation.e.apps.home.model.HomeParentRVAdapter
import foundation.e.apps.login.AuthObject
import foundation.e.apps.manager.download.data.DownloadProgress
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.exceptions.GPlayException
import foundation.e.apps.utils.exceptions.GPlayValidationException
import foundation.e.apps.utils.modules.CommonUtilsModule.safeNavigate
import foundation.e.apps.utils.modules.PWAManagerModule
import foundation.e.apps.utils.parentFragment.TimeoutFragment
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : TimeoutFragment(R.layout.fragment_home), FusedAPIInterface {

    /*
     * Make adapter nullable to avoid memory leaks.
     * Issue: https://gitlab.e.foundation/e/os/backlog/-/issues/485
     */
    private var homeParentRVAdapter: HomeParentRVAdapter? = null
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by viewModels()
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()
    private val appProgressViewModel: AppProgressViewModel by viewModels()
    private val appInfoFetchViewModel: AppInfoFetchViewModel by viewModels()

    @Inject
    lateinit var pkgManagerModule: PkgManagerModule

    @Inject
    lateinit var pwaManagerModule: PWAManagerModule

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        loadHomePageData()

        homeParentRVAdapter = initHomeParentRVAdapter()

        binding.parentRV.apply {
            adapter = homeParentRVAdapter
            layoutManager = LinearLayoutManager(view.context)
        }

        observeHomeScreenData()
    }

    private fun observeHomeScreenData() {
        homeViewModel.homeScreenData.observe(viewLifecycleOwner) {
            stopLoadingUI()
            if (it.second != ResultStatus.OK) {
                return@observe
            }

            if (!isHomeDataUpdated(it)) {
                return@observe
            }

            homeParentRVAdapter?.setData(it.first)
        }
    }

    private fun initHomeParentRVAdapter() = HomeParentRVAdapter(
        this,
        pkgManagerModule,
        pwaManagerModule,
        mainActivityViewModel.getUser(),
        mainActivityViewModel, appInfoFetchViewModel, viewLifecycleOwner
    ) { fusedApp ->
        if (!mainActivityViewModel.shouldShowPaidAppsSnackBar(fusedApp)) {
            showPaidAppMessage(fusedApp)
        }
    }

    private fun loadHomePageData() {
        setupListening()

        authObjects.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            loadData(it)
        }

        homeViewModel.exceptionsLiveData.observe(viewLifecycleOwner) {
            handleExceptionsCommon(it)
        }
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
        ).show(childFragmentManager, "HomeFragment")
    }

    private fun isHomeDataUpdated(homeScreenResult: Pair<List<FusedHome>, ResultStatus>) =
        homeParentRVAdapter?.currentList?.isEmpty() == true || homeViewModel.isHomeDataUpdated(
            homeScreenResult.first,
            homeParentRVAdapter?.currentList as List<FusedHome>
        )

    override fun onTimeout(
        exception: Exception,
        predefinedDialog: AlertDialog.Builder
    ): AlertDialog.Builder? {
        return predefinedDialog.apply {
            if (exception is GPlayException) {
                setMessage(R.string.timeout_desc_gplay)
                setNegativeButton(R.string.open_settings) { _, _ ->
                    openSettings()
                }
            } else {
                setMessage(R.string.timeout_desc_cleanapk)
            }
            setCancelable(false)
        }
    }

    override fun onSignInError(
        exception: GPlayValidationException,
        predefinedDialog: AlertDialog.Builder
    ): AlertDialog.Builder? {
        return predefinedDialog.apply {
            setNegativeButton(R.string.open_settings) { _, _ ->
                openSettings()
            }
        }
    }

    override fun onDataLoadError(
        exception: Exception,
        predefinedDialog: AlertDialog.Builder
    ): AlertDialog.Builder? {
        return predefinedDialog.apply {
            if (exception is GPlayException) {
                setNegativeButton(R.string.open_settings) { _, _ ->
                    openSettings()
                }
            }
        }
    }

    override fun loadData(authObjectList: List<AuthObject>) {
        homeViewModel.loadData(authObjectList) { _ ->
            clearAndRestartGPlayLogin()
            true
        }
    }

    override fun showLoadingUI() {
        binding.shimmerLayout.startShimmer()
        binding.shimmerLayout.visibility = View.VISIBLE
        binding.parentRV.visibility = View.GONE
    }

    override fun stopLoadingUI() {
        binding.shimmerLayout.stopShimmer()
        binding.shimmerLayout.visibility = View.GONE
        binding.parentRV.visibility = View.VISIBLE
    }

    private fun updateProgressOfDownloadingAppItemViews(
        homeParentRVAdapter: HomeParentRVAdapter?,
        downloadProgress: DownloadProgress
    ) {
        homeParentRVAdapter?.currentList?.forEach { fusedHome ->
            val viewHolder = binding.parentRV.findViewHolderForAdapterPosition(
                homeParentRVAdapter.currentList.indexOf(fusedHome)
            )
            viewHolder?.let { parentViewHolder ->
                val childRV =
                    (parentViewHolder as HomeParentRVAdapter.ViewHolder).binding.childRV
                val adapter = childRV.adapter as HomeChildRVAdapter
                findDownloadingItemsToShowProgress(adapter, downloadProgress, childRV)
            }
        }
    }

    private fun findDownloadingItemsToShowProgress(
        adapter: HomeChildRVAdapter,
        downloadProgress: DownloadProgress,
        childRV: RecyclerView
    ) {
        lifecycleScope.launch {
            updateDownloadProgressOfAppList(adapter, downloadProgress, childRV)
        }
    }

    private suspend fun updateDownloadProgressOfAppList(
        adapter: HomeChildRVAdapter,
        downloadProgress: DownloadProgress,
        childRV: RecyclerView
    ) {
        adapter.currentList.forEach { fusedApp ->
            if (fusedApp.status != Status.DOWNLOADING) {
                return@forEach
            }
            val progress =
                appProgressViewModel.calculateProgress(fusedApp, downloadProgress)
            if (progress == -1) {
                return@forEach
            }
            val childViewHolder = childRV.findViewHolderForAdapterPosition(
                adapter.currentList.indexOf(fusedApp)
            )
            childViewHolder?.let {
                (childViewHolder as HomeChildRVAdapter.ViewHolder).binding.installButton.text =
                    "$progress%"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.shimmerLayout.startShimmer()
        appProgressViewModel.downloadProgress.observe(viewLifecycleOwner) {
            updateProgressOfDownloadingAppItemViews(homeParentRVAdapter, it)
        }

        if (homeViewModel.isAnyAppInstallStatusChanged(homeParentRVAdapter?.currentList)) {
            repostAuthObjects()
        }
    }

    override fun onPause() {
        binding.shimmerLayout.stopShimmer()
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        /*
         * Nullify adapter to avoid leaks.
         * Issue: https://gitlab.e.foundation/e/os/backlog/-/issues/485
         */
        homeParentRVAdapter = null
    }

    override fun getApplication(app: FusedApp, appIcon: ImageView?) {
        mainActivityViewModel.getApplication(app, appIcon)
    }

    override fun cancelDownload(app: FusedApp) {
        mainActivityViewModel.cancelDownload(app)
    }

    private fun onTosAccepted(isTosAccepted: Boolean) {
        if (isTosAccepted) {
            /*
             * "safeNavigate" is an extension function, to prevent calling this navigation multiple times.
             * This is taken from:
             * https://nezspencer.medium.com/navigation-components-a-fix-for-navigation-action-cannot-be-found-in-the-current-destination-95b63e16152e
             * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5166
             * Also related: https://gitlab.e.foundation/ecorp/apps/apps/-/merge_requests/28
             */
            view?.findNavController()
                ?.safeNavigate(R.id.homeFragment, R.id.action_homeFragment_to_signInFragment)
        }
    }

    private fun openSettings() {
        view?.findNavController()
            ?.safeNavigate(R.id.homeFragment, R.id.action_homeFragment_to_SettingsFragment)
    }
}
