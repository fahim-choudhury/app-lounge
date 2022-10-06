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

package foundation.e.apps.updates

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.AppInfoFetchViewModel
import foundation.e.apps.AppProgressViewModel
import foundation.e.apps.MainActivityViewModel
import foundation.e.apps.PrivacyInfoViewModel
import foundation.e.apps.R
import foundation.e.apps.api.fused.FusedAPIInterface
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.applicationlist.ApplicationListRVAdapter
import foundation.e.apps.databinding.FragmentUpdatesBinding
import foundation.e.apps.login.AuthObject
import foundation.e.apps.manager.download.data.DownloadProgress
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.manager.workmanager.InstallWorkManager.INSTALL_WORK_NAME
import foundation.e.apps.updates.manager.UpdatesWorkManager
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.exceptions.GPlayException
import foundation.e.apps.utils.exceptions.GPlayLoginException
import foundation.e.apps.utils.modules.CommonUtilsModule.safeNavigate
import foundation.e.apps.utils.modules.PWAManagerModule
import foundation.e.apps.utils.parentFragment.TimeoutFragment
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UpdatesFragment : TimeoutFragment(R.layout.fragment_updates), FusedAPIInterface {

    private var _binding: FragmentUpdatesBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var pkgManagerModule: PkgManagerModule

    @Inject
    lateinit var pwaManagerModule: PWAManagerModule

    private val updatesViewModel: UpdatesViewModel by viewModels()
    private val privacyInfoViewModel: PrivacyInfoViewModel by viewModels()
    private val appInfoFetchViewModel: AppInfoFetchViewModel by viewModels()
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()
    private val appProgressViewModel: AppProgressViewModel by viewModels()

    private var isDownloadObserverAdded = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUpdatesBinding.bind(view)

        binding.button.isEnabled = false

        /*
         * Explanation of double observers in HomeFragment.kt
         */

        /*mainActivityViewModel.internetConnection.observe(viewLifecycleOwner) {
            if (!updatesViewModel.updatesList.value?.first.isNullOrEmpty()) {
                return@observe
            }
            refreshDataOrRefreshToken(mainActivityViewModel)
        }
        mainActivityViewModel.authData.observe(viewLifecycleOwner) {
            refreshDataOrRefreshToken(mainActivityViewModel)
        }*/

        setupListening()

        authObjects.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            if (!updatesViewModel.updatesList.value?.first.isNullOrEmpty()) {
                return@observe
            }
            loadData(it)
        }

        updatesViewModel.exceptionsLiveData.observe(viewLifecycleOwner) {
            handleExceptionsCommon(it)
        }

        val recyclerView = binding.recyclerView
        val listAdapter = findNavController().currentDestination?.id?.let {
            ApplicationListRVAdapter(
                this,
                privacyInfoViewModel,
                appInfoFetchViewModel,
                mainActivityViewModel,
                it,
                viewLifecycleOwner,
            ) { fusedApp ->
                if (!mainActivityViewModel.shouldShowPaidAppsSnackBar(fusedApp)) {
                    showPurchasedAppMessage(fusedApp)
                }
            }
        }

        recyclerView.apply {
            adapter = listAdapter
            layoutManager = LinearLayoutManager(view.context)
        }

        updatesViewModel.updatesList.observe(viewLifecycleOwner) {
            listAdapter?.setData(it.first)
            if (!isDownloadObserverAdded) {
                observeDownloadList()
                isDownloadObserverAdded = true
            }
            stopLoadingUI()
            if (!it.first.isNullOrEmpty()) {
                binding.button.isEnabled = true
                binding.noUpdates.visibility = View.GONE
            } else {
                binding.noUpdates.visibility = View.VISIBLE
                binding.button.isEnabled = false
            }

            WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData(INSTALL_WORK_NAME)
                .observe(viewLifecycleOwner) { workInfoList ->
                    lifecycleScope.launchWhenResumed {
                        binding.button.isEnabled = !(
                            it.first.isNullOrEmpty() ||
                                updatesViewModel.checkWorkInfoListHasAnyUpdatableWork(
                                    workInfoList
                                )
                            )
                    }
                }

            /*if (it.second != ResultStatus.OK) {
                onTimeout()
            }*/
        }
    }

    private fun showPurchasedAppMessage(fusedApp: FusedApp) {
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
        ).show(childFragmentManager, "UpdatesFragment")
    }

    /*override fun onTimeout() {
        if (!isTimeoutDialogDisplayed()) {
            stopLoadingUI()
            displayTimeoutAlertDialog(
                timeoutFragment = this,
                activity = requireActivity(),
                message =
                if (updatesViewModel.getApplicationCategoryPreference() == FusedAPIImpl.APP_TYPE_ANY) {
                    getString(R.string.timeout_desc_gplay)
                } else {
                    getString(R.string.timeout_desc_cleanapk)
                },
                positiveButtonText = getString(R.string.retry),
                positiveButtonBlock = {
                    showLoadingUI()
                    resetTimeoutDialogLock()
                    mainActivityViewModel.retryFetchingTokenAfterTimeout()
                },
                negativeButtonText =
                if (updatesViewModel.getApplicationCategoryPreference() == FusedAPIImpl.APP_TYPE_ANY) {
                    getString(R.string.open_settings)
                } else null,
                negativeButtonBlock = {
                    openSettings()
                },
                allowCancel = true,
            )
        }
    }*/

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
        }
    }

    override fun onSignInError(
        exception: GPlayLoginException,
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
        showLoadingUI()
        updatesViewModel.loadData(authObjectList) {
            clearAndRestartGPlayLogin()
            true
        }
        binding.button.setOnClickListener {
            UpdatesWorkManager.startUpdateAllWork(requireContext().applicationContext)
            observeUpdateWork()
            binding.button.isEnabled = false
        }
    }

    private fun observeUpdateWork() {
        WorkManager.getInstance(requireContext())
            .getWorkInfosByTagLiveData(UpdatesWorkManager.UPDATES_WORK_NAME)
            .observe(viewLifecycleOwner) {
                val errorStates =
                    listOf(
                        WorkInfo.State.FAILED,
                        WorkInfo.State.BLOCKED,
                        WorkInfo.State.CANCELLED
                    )
                if (!it.isNullOrEmpty() && errorStates.contains(it.last().state)) {
                    binding.button.isEnabled = true
                }
            }
    }

    override fun showLoadingUI() {
        binding.button.isEnabled = false
        binding.noUpdates.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.INVISIBLE
    }

    override fun stopLoadingUI() {
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        appProgressViewModel.downloadProgress.observe(viewLifecycleOwner) {
            updateProgressOfDownloadingItems(binding.recyclerView, it)
        }
//        resetTimeoutDialogLock()
    }

    private fun observeDownloadList() {
        mainActivityViewModel.downloadList.observe(viewLifecycleOwner) { list ->
            val appList = updatesViewModel.updatesList.value?.first?.toMutableList() ?: emptyList()
            appList.let {
                mainActivityViewModel.updateStatusOfFusedApps(appList, list)
            }
            updatesViewModel.updatesList.apply { value = Pair(appList, value?.second) }
        }
    }

    private fun updateProgressOfDownloadingItems(
        recyclerView: RecyclerView,
        downloadProgress: DownloadProgress
    ) {
        val adapter = recyclerView.adapter as ApplicationListRVAdapter
        lifecycleScope.launch {
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
                            "$progress%"
                    }
                }
            }
        }
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

    private fun openSettings() {
        view?.findNavController()
            ?.safeNavigate(R.id.updatesFragment, R.id.action_updatesFragment_to_SettingsFragment)
    }
}
