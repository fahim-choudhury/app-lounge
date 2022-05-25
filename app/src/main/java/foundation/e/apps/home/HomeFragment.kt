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
import androidx.fragment.app.Fragment
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
import foundation.e.apps.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.databinding.FragmentHomeBinding
import foundation.e.apps.home.model.HomeChildRVAdapter
import foundation.e.apps.home.model.HomeParentRVAdapter
import foundation.e.apps.manager.download.data.DownloadProgress
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.User
import foundation.e.apps.utils.modules.CommonUtilsModule.safeNavigate
import foundation.e.apps.utils.modules.PWAManagerModule
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home), FusedAPIInterface {

    private lateinit var homeParentRVAdapter: HomeParentRVAdapter
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

        mainActivityViewModel.internetConnection.observe(viewLifecycleOwner) {
            mainActivityViewModel.authData.observe(viewLifecycleOwner) {
                refreshHomeData()
            }
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
            binding.shimmerLayout.visibility = View.GONE
            binding.parentRV.visibility = View.VISIBLE
            if (!homeViewModel.isFusedHomesEmpty(it.first)) {
                homeParentRVAdapter.setData(it.first)
            } else if (!mainActivityViewModel.isTimeoutDialogDisplayed()) {
                mainActivityViewModel.uploadFaultyTokenToEcloud("From " + this::class.java.name)
                mainActivityViewModel.displayTimeoutAlertDialog(requireActivity(), {
                    showLoadingShimmer()
                    mainActivityViewModel.retryFetchingTokenAfterTimeout()
                }, {
                    openSettings()
                }, it.second)
            }
        }
    }

    /*
     * Offload loading home data to a different function, to allow retrying mechanism.
     */
    private fun refreshHomeData() {
        if (mainActivityViewModel.internetConnection.value == true) {
            mainActivityViewModel.authData.value?.let { authData ->
                mainActivityViewModel.dismissTimeoutDialog()
                homeViewModel.getHomeScreenData(authData)
            }
        }
    }

    private fun showLoadingShimmer() {
        binding.shimmerLayout.visibility = View.VISIBLE
        binding.parentRV.visibility = View.GONE
    }

    private fun updateProgressOfDownloadingAppItemViews(
        homeParentRVAdapter: HomeParentRVAdapter,
        downloadProgress: DownloadProgress
    ) {
        homeParentRVAdapter.currentList.forEach { fusedHome ->
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
                    val downloadProgress =
                        ((progress.second / progress.first.toDouble()) * 100).toInt()
                    val childViewHolder = childRV.findViewHolderForAdapterPosition(
                        adapter.currentList.indexOf(fusedApp)
                    )
                    childViewHolder?.let {
                        (childViewHolder as HomeChildRVAdapter.ViewHolder).binding.installButton.text =
                            "$downloadProgress%"
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
