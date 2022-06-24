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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.AppInfoFetchViewModel
import foundation.e.apps.AppProgressViewModel
import foundation.e.apps.MainActivityViewModel
import foundation.e.apps.R
import foundation.e.apps.api.fused.FusedAPIImpl
import foundation.e.apps.api.fused.FusedAPIInterface
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.databinding.FragmentHomeBinding
import foundation.e.apps.home.model.HomeChildRVAdapter
import foundation.e.apps.home.model.HomeParentRVAdapter
import foundation.e.apps.manager.download.data.DownloadProgress
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.User
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

        mainActivityViewModel.userType.observe(viewLifecycleOwner) { user ->
            if (user.isNullOrEmpty() || User.valueOf(user) == User.UNAVAILABLE) {
                mainActivityViewModel.tocStatus.observe(viewLifecycleOwner) { tosAccepted ->
                    onTosAccepted(tosAccepted)
                }
            }
        }

        /*
         * Previous code:
         * internetConnection.observe {
         *     authData.observe {
         *         // refresh data here.
         *     }
         * }
         *
         * Code regarding data fetch is placed in two separate observers compared to nested
         * observers as was done previously.
         *
         * refreshDataOrRefreshToken() already checks for internet connectivity and authData.
         * If authData is null, it requests to fetch new token data.
         *
         * With previous nested observer code (commit 8ca1647d), try the following:
         * 1. Put garbage value in "Proxy" of APN settings of device,
         *    this will cause host unreachable error.
         * 2. Open App Lounge. Let it show timeout dialog.
         * 3. Click "Open Settings", now immediately open Home tab again.
         * 4. Home keeps loading without any timeout error.
         *
         * Why is this happening?
         * In case of host unreachable error, the authData is itself blank/null. This does not allow
         * it to get "observed". But mainActivityViewModel.internetConnection always has a value,
         * and is observable.
         * When we open Home tab again from Settings tab, no refresh action is performed as
         * authData.observe {} does not observe anything.
         *
         * In the new code, the first observer will always be executed on fragment attach
         * (as mainActivityViewModel.internetConnection always has a value and is observable),
         * this will call refreshDataOrRefreshToken(), which will refresh authData if it is null.
         * Now with new valid authData, the second observer (authData.observe{}) will again call
         * refreshDataOrRefreshToken() which will now fetch correct data.
         *
         *
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
         */

        mainActivityViewModel.internetConnection.observe(viewLifecycleOwner) {
            refreshDataOrRefreshToken(mainActivityViewModel)
        }
        mainActivityViewModel.authData.observe(viewLifecycleOwner) {
            refreshDataOrRefreshToken(mainActivityViewModel)
        }

        homeParentRVAdapter = HomeParentRVAdapter(
            this,
            pkgManagerModule,
            pwaManagerModule,
            User.valueOf(mainActivityViewModel.userType.value ?: User.UNAVAILABLE.name),
            mainActivityViewModel, appInfoFetchViewModel, viewLifecycleOwner
        ) { fusedApp ->
            if (!mainActivityViewModel.shouldShowPaidAppsSnackBar(fusedApp)) {
                ApplicationDialogFragment(
                    title = getString(R.string.dialog_title_paid_app, fusedApp.name),
                    message = getString(R.string.dialog_paidapp_message, fusedApp.name, fusedApp.price),
                    positiveButtonText = getString(R.string.dialog_confirm),
                    positiveButtonAction = {
                        getApplication(fusedApp)
                    },
                    cancelButtonText = getString(R.string.dialog_cancel),
                ).show(childFragmentManager, "HomeFragment")
            }
        }

        binding.parentRV.apply {
            adapter = homeParentRVAdapter
            layoutManager = LinearLayoutManager(view.context)
        }

        homeViewModel.homeScreenData.observe(viewLifecycleOwner) {
            stopLoadingUI()
            if (it.second == ResultStatus.OK) {
                if (!homeParentRVAdapter?.currentList.isNullOrEmpty()) {
                    return@observe
                }
                dismissTimeoutDialog()
                homeParentRVAdapter?.setData(it.first)
            } else {
                onTimeout()
            }
        }
    }

    override fun onTimeout() {
        if (homeViewModel.isFusedHomesEmpty() && !isTimeoutDialogDisplayed()) {
            mainActivityViewModel.uploadFaultyTokenToEcloud("From " + this::class.java.name)
            stopLoadingUI()
            displayTimeoutAlertDialog(
                timeoutFragment = this,
                activity = requireActivity(),
                message =
                if (homeViewModel.getApplicationCategoryPreference() == FusedAPIImpl.APP_TYPE_ANY) {
                    getString(R.string.timeout_desc_gplay)
                } else {
                    getString(R.string.timeout_desc_cleanapk)
                },
                positiveButtonText = getString(R.string.retry),
                positiveButtonBlock = {
                    showLoadingUI()
                    resetTimeoutDialogLock()
                    mainActivityViewModel.checkTokenOnTimeout()
                },
                negativeButtonText =
                if (homeViewModel.getApplicationCategoryPreference() == FusedAPIImpl.APP_TYPE_ANY) {
                    getString(R.string.open_settings)
                } else null,
                negativeButtonBlock = {
                    openSettings()
                },
                allowCancel = false,
            )
        }
    }

    override fun refreshData(authData: AuthData) {
        showLoadingUI()
        homeViewModel.getHomeScreenData(authData)
    }

    private fun showLoadingUI() {
        binding.shimmerLayout.startShimmer()
        binding.shimmerLayout.visibility = View.VISIBLE
        binding.parentRV.visibility = View.GONE
    }

    private fun stopLoadingUI() {
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
            adapter.currentList.forEach { fusedApp ->
                if (fusedApp.status == Status.DOWNLOADING) {
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
        }
    }

    override fun onResume() {
        super.onResume()
        resetTimeoutDialogLock()
        binding.shimmerLayout.startShimmer()
        appProgressViewModel.downloadProgress.observe(viewLifecycleOwner) {
            updateProgressOfDownloadingAppItemViews(homeParentRVAdapter, it)
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
