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
import androidx.lifecycle.flowWithLifecycle
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
import foundation.e.apps.api.ResultSupreme
import foundation.e.apps.api.fused.FusedAPIInterface
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.applicationlist.ApplicationListRVAdapter
import foundation.e.apps.databinding.FragmentUpdatesBinding
import foundation.e.apps.login.AuthObject
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.download.data.DownloadProgress
import foundation.e.apps.manager.pkg.PkgManagerModule
import foundation.e.apps.manager.workmanager.InstallWorkManager.INSTALL_WORK_NAME
import foundation.e.apps.updates.manager.UpdatesWorkManager
import foundation.e.apps.utils.enums.ResultStatus
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import foundation.e.apps.utils.exceptions.GPlayException
import foundation.e.apps.utils.exceptions.GPlayLoginException
import foundation.e.apps.utils.modules.CommonUtilsModule.safeNavigate
import foundation.e.apps.utils.modules.PWAManagerModule
import foundation.e.apps.utils.parentFragment.TimeoutFragment
import foundation.e.apps.utils.toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber
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
        observeAppInstallationWork()
        observeUpdateList(listAdapter)

        viewLifecycleOwner.lifecycleScope.launch {
            EventBus.events.flowWithLifecycle(viewLifecycleOwner.lifecycle)
                .filter { appEvent -> appEvent is AppEvent.UpdateEvent }.collectLatest {
                    handleUpdateEvent(it)
                }
        }
    }

    private fun observeUpdateList(listAdapter: ApplicationListRVAdapter?) {
        updatesViewModel.updatesList.observe(viewLifecycleOwner) {
            listAdapter?.setData(it.first)
            if (!isDownloadObserverAdded) {
                handleStateNoUpdates(it.first)
                observeDownloadList()
                isDownloadObserverAdded = true
            }

            stopLoadingUI()

            Timber.d("===>> observeupdate list called")
            if (it.second != ResultStatus.OK) {
                val exception = GPlayException(it.second == ResultStatus.TIMEOUT)
                val alertDialogBuilder = AlertDialog.Builder(requireContext())
                onTimeout(exception, alertDialogBuilder)
            }
        }
    }

    private fun handleStateNoUpdates(list: List<FusedApp>?) {
        if (!list.isNullOrEmpty()) {
            binding.button.isEnabled = true
            initUpdataAllButton()
            binding.noUpdates.visibility = View.GONE
        } else {
            binding.noUpdates.visibility = View.VISIBLE
            binding.button.isEnabled = false
        }
    }

    private fun observeAppInstallationWork() {
        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(INSTALL_WORK_NAME)
            .observe(viewLifecycleOwner) { workInfoList ->
                lifecycleScope.launchWhenResumed {
                    binding.button.isEnabled = shouldUpdateButtonEnable(workInfoList)
                }
            }
    }

    private fun shouldUpdateButtonEnable(workInfoList: MutableList<WorkInfo>) =
        !updatesViewModel.updatesList.value?.first.isNullOrEmpty() &&
            (
                workInfoList.isNullOrEmpty() ||
                    (
                        !updatesViewModel.checkWorkInfoListHasAnyUpdatableWork(
                            workInfoList
                        ) &&
                            updatesViewModel.hasAnyUpdatableApp()
                        )
                )

    private fun handleUpdateEvent(appEvent: AppEvent) {
        val event = appEvent.data as ResultSupreme.WorkError<*>
        when (event.data) {
            ResultStatus.RETRY -> {
                requireContext().toast(getString(R.string.message_retry))
            }
            else -> {
                handleUnknownErrorEvent(event)
            }
        }
    }

    private fun handleUnknownErrorEvent(event: ResultSupreme.WorkError<*>) {
        if (event.otherPayload == null) {
            requireContext().toast(getString(R.string.message_update_failed))
            return
        }

        if (event.otherPayload is FusedDownload) {
            requireContext().toast(
                getString(
                    R.string.message_update_failure_single_app,
                    (event.otherPayload as FusedDownload).name
                )
            )
        }
    }

    private fun showPurchasedAppMessage(fusedApp: FusedApp) {
        ApplicationDialogFragment(
            title = getString(R.string.dialog_title_paid_app, fusedApp.name),
            message = getString(
                R.string.dialog_paidapp_message, fusedApp.name, fusedApp.price
            ),
            positiveButtonText = getString(R.string.dialog_confirm),
            positiveButtonAction = {
                getApplication(fusedApp)
            },
            cancelButtonText = getString(R.string.dialog_cancel),
        ).show(childFragmentManager, "UpdatesFragment")
    }

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
        initUpdataAllButton()
    }

    private fun initUpdataAllButton() {
        binding.button.setOnClickListener {
            val interval = updatesViewModel.getUpdateInterval()
            UpdatesWorkManager.startUpdateAllWork(requireContext())
            observeUpdateWork()
            binding.button.isEnabled = false
        }
    }

    private fun observeUpdateWork() {
        WorkManager.getInstance(requireContext())
            .getWorkInfosByTagLiveData(UpdatesWorkManager.TAG)
            .observe(viewLifecycleOwner) {
                binding.button.isEnabled = hasAnyPendingUpdates(it)
            }
    }

    private fun hasAnyPendingUpdates(
        workInfoList: MutableList<WorkInfo>
    ): Boolean {
        val errorStates = listOf(
            WorkInfo.State.FAILED,
            WorkInfo.State.BLOCKED,
            WorkInfo.State.CANCELLED,
            WorkInfo.State.SUCCEEDED
        )
        return !workInfoList.isNullOrEmpty() && errorStates.contains(workInfoList.last().state) &&
            updatesViewModel.hasAnyUpdatableApp() && !updatesViewModel.hasAnyPendingAppsForUpdate()
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
        mainActivityViewModel.downloadList.removeObservers(viewLifecycleOwner)
        isDownloadObserverAdded = false
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
