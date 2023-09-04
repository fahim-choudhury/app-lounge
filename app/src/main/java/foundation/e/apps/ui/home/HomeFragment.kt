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

package foundation.e.apps.ui.home

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aurora.gplayapi.data.models.AuthData
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.R
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.fused.FusedAPIInterface
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fused.data.FusedHome
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.GPlayException
import foundation.e.apps.data.login.exceptions.GPlayLoginException
import foundation.e.apps.databinding.FragmentHomeBinding
import foundation.e.apps.di.CommonUtilsModule.safeNavigate
import foundation.e.apps.install.download.data.DownloadProgress
import foundation.e.apps.install.pkg.PWAManagerModule
import foundation.e.apps.presentation.login.LoginState
import foundation.e.apps.presentation.login.LoginViewModel
import foundation.e.apps.ui.AppInfoFetchViewModel
import foundation.e.apps.ui.AppProgressViewModel
import foundation.e.apps.ui.MainActivityViewModel
import foundation.e.apps.ui.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.ui.home.model.HomeChildRVAdapter
import foundation.e.apps.ui.home.model.HomeParentRVAdapter
import foundation.e.apps.ui.parentFragment.TimeoutFragment
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home), FusedAPIInterface {

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

    private val loginViewModel: LoginViewModel by lazy {
        ViewModelProvider(requireActivity())[LoginViewModel::class.java]
    }

    private var authData: AuthData? = null

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
            if (!it.isSuccess()) {
                return@observe
            }

            if (!isHomeDataUpdated(it)) {
                return@observe
            }

            homeParentRVAdapter?.setData(it.data!!)
        }
    }

    private fun initHomeParentRVAdapter() = HomeParentRVAdapter(
        this,
        mainActivityViewModel, appInfoFetchViewModel, viewLifecycleOwner
    ) { fusedApp ->
        if (!mainActivityViewModel.shouldShowPaidAppsSnackBar(fusedApp)) {
            showPaidAppMessage(fusedApp)
        }
    }

    private fun loadHomePageData() {

        loginViewModel.loginState.observe(viewLifecycleOwner) {
            if (it.isLoggedIn) {
                // TODO : check for network and wait if network is unavailable
                this.authData = it.authData
                loadData()
            }
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

    private fun isHomeDataUpdated(homeScreenResult: ResultSupreme<List<FusedHome>>) =
        homeParentRVAdapter?.currentList?.isEmpty() == true || homeViewModel.isHomeDataUpdated(
            homeScreenResult.data!!,
            homeParentRVAdapter?.currentList as List<FusedHome>
        )

    fun loadData() {
        homeViewModel.loadData(authData, viewLifecycleOwner)
    }

    fun showLoadingUI() {
        binding.shimmerLayout.startShimmer()
        binding.shimmerLayout.visibility = View.VISIBLE
        binding.parentRV.visibility = View.GONE
    }

    fun stopLoadingUI() {
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
                    String.format("%d%%", progress)
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
            loadData()
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
        mainActivityViewModel.getApplication(app)
    }

    override fun cancelDownload(app: FusedApp) {
        mainActivityViewModel.cancelDownload(app)
    }
}
