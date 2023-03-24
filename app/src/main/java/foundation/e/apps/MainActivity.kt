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

package foundation.e.apps

import android.app.usage.StorageStatsManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.exceptions.ApiException
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.databinding.ActivityMainBinding
import foundation.e.apps.login.AuthObject
import foundation.e.apps.login.LoginViewModel
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.workmanager.InstallWorkManager
import foundation.e.apps.purchase.AppPurchaseFragmentDirections
import foundation.e.apps.settings.SettingsFragment
import foundation.e.apps.setup.signin.SignInViewModel
import foundation.e.apps.updates.UpdatesNotifier
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import foundation.e.apps.utils.exceptions.GPlayValidationException
import foundation.e.apps.utils.modules.CommonUtilsFunctions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.UUID

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var signInViewModel: SignInViewModel
    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityMainBinding
    private val TAG = MainActivity::class.java.simpleName
    private lateinit var viewModel: MainActivityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bottomNavigationView = binding.bottomNavigationView
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment) as NavHostFragment
        val navController = navHostFragment.navController
        bottomNavigationView.setupWithNavController(navController)
        setupBottomNavItemSelectedListener(bottomNavigationView, navHostFragment, navController)

        var hasInternet = true

        viewModel = ViewModelProvider(this)[MainActivityViewModel::class.java]
        signInViewModel = ViewModelProvider(this)[SignInViewModel::class.java]
        loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        // navOptions and activityNavController for TOS and SignIn Fragments
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.navigation_resource, true)
            .build()
        navOptions.shouldLaunchSingleTop()

        viewModel.tocStatus.distinctUntilChanged().observe(this) {
            if (it != true) {
                navController.navigate(R.id.TOSFragment, null, navOptions)
            } else {
                loginViewModel.startLoginFlow()
            }
        }

        viewModel.setupConnectivityManager(this)

        viewModel.internetConnection.observe(this) { isInternetAvailable ->
            hasInternet = isInternetAvailable
            if (isInternetAvailable) {
                binding.noInternet.visibility = View.GONE
                binding.fragment.visibility = View.VISIBLE
            }
        }

        loginViewModel.authObjects.distinctUntilChanged().observe(this) {
            when {
                it == null -> return@observe
                it.isEmpty() -> {
                    // No auth type defined means user has not logged in yet
                    // Pop back stack to prevent showing TOSFragment on pressing back button.
                    navController.popBackStack()
                    navController.navigate(R.id.signInFragment)
                }
                else -> {}
            }

            it.find { it is AuthObject.GPlayAuth }?.result?.run {
                if (isSuccess()) {
                    viewModel.gPlayAuthData = data as AuthData
                } else if (exception is GPlayValidationException) {
                    val email = otherPayload.toString()
                    viewModel.uploadFaultyTokenToEcloud(
                        email,
                        CommonUtilsFunctions.getAppBuildInfo()
                    )
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (!hasInternet) {
                showNoInternet()
            }
            when (destination.id) {
                R.id.applicationFragment,
                R.id.applicationListFragment,
                R.id.screenshotFragment,
                R.id.descriptionFragment,
                R.id.TOSFragment,
                R.id.googleSignInFragment,
                R.id.signInFragment -> {
                    bottomNavigationView.visibility = View.GONE
                }
                else -> {
                    bottomNavigationView.visibility = View.VISIBLE
                }
            }
        }

        if (intent.hasExtra(UpdatesNotifier.UPDATES_NOTIFICATION_CLICK_EXTRA)) {
            bottomNavigationView.selectedItemId = R.id.updatesFragment
        }

        // Create notification channel on post-nougat devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            viewModel.createNotificationChannels()
        }

        // Observe and handle downloads
        viewModel.downloadList.observe(this) { list ->
            list.forEach {
                if (it.status == Status.QUEUED) {
                    handleFusedDownloadQueued(it, viewModel)
                }
            }
        }

        viewModel.purchaseAppLiveData.observe(this) {
            val action =
                AppPurchaseFragmentDirections.actionGlobalAppPurchaseFragment()
            action.packageName = it.packageName
            findNavController(R.id.fragment).navigate(action)
        }

        viewModel.errorMessage.observe(this) {
            when (it) {
                is ApiException.AppNotPurchased -> showSnackbarMessage(getString(R.string.message_app_available_later))
                else -> showSnackbarMessage(
                    it.localizedMessage ?: getString(R.string.unknown_error)
                )
            }
        }

        viewModel.errorMessageStringResource.observe(this) {
            showSnackbarMessage(getString(it))
        }

        viewModel.isAppPurchased.observe(this) {
            if (it.isNotEmpty()) {
                startInstallationOfPurchasedApp(viewModel, it)
            }
        }

        viewModel.purchaseDeclined.observe(this) {
            if (it.isNotEmpty()) {
                lifecycleScope.launch {
                    viewModel.updateUnavailableForPurchaseDeclined(it)
                }
            }
        }

        if (viewModel.internetConnection.value != true) {
            showNoInternet()
        }

        viewModel.updateAppWarningList()

        lifecycleScope.launchWhenResumed {
            observeInvalidAuth()

            EventBus.events.filter { appEvent ->
                appEvent is AppEvent.SignatureMissMatchError
            }.collectLatest {
                val appName = viewModel.getAppNameByPackageName(it.data.toString())
                ApplicationDialogFragment(
                    title = getString(R.string.update_error),
                    message = getString(R.string.error_signature_mismatch, appName),
                    positiveButtonText = getString(R.string.ok)
                ).show(supportFragmentManager, TAG)
            }
        }
    }

    private suspend fun observeInvalidAuth() {
        EventBus.events.filter { appEvent ->
            appEvent is AppEvent.InvalidAuthEvent
        }.distinctUntilChanged { old, new ->
            ((old.data is String) && (new.data is String) && old.data == new.data)
        }.collectLatest {
            val data = it.data as String
            if (data.isNotBlank()) {
                loginViewModel.markInvalidAuthObject(data)
            }
        }
    }

    private fun setupBottomNavItemSelectedListener(
        bottomNavigationView: BottomNavigationView,
        navHostFragment: NavHostFragment,
        navController: NavController
    ) {
        bottomNavigationView.setOnItemSelectedListener {
            val fragment =
                navHostFragment.childFragmentManager.fragments.find { fragment -> fragment is SettingsFragment }
            if (bottomNavigationView.selectedItemId == R.id.settingsFragment && fragment is SettingsFragment && !fragment.isAnyAppSourceSelected()) {
                ApplicationDialogFragment(
                    title = "",
                    message = getString(R.string.select_one_source_of_applications),
                    positiveButtonText = getString(R.string.ok)
                ).show(supportFragmentManager, TAG)
                return@setOnItemSelectedListener false
            }

            return@setOnItemSelectedListener NavigationUI.onNavDestinationSelected(
                it,
                navController
            )
        }
    }

    private fun handleFusedDownloadQueued(
        it: FusedDownload,
        viewModel: MainActivityViewModel
    ) {
        lifecycleScope.launch {
            if (!isStorageAvailable(it)) {
                showSnackbarMessage(getString(R.string.not_enough_storage))
                viewModel.updateUnAvailable(it)
                return@launch
            }
            if (viewModel.internetConnection.value == false) {
                showNoInternet()
                viewModel.updateUnAvailable(it)
                return@launch
            }
            viewModel.updateAwaiting(it)
            InstallWorkManager.enqueueWork(it)
            Timber.d("===> onCreate: AWAITING ${it.name}")
        }
    }

    private fun startInstallationOfPurchasedApp(
        viewModel: MainActivityViewModel,
        it: String
    ) {
        lifecycleScope.launch {
            val fusedDownload = viewModel.updateAwaitingForPurchasedApp(it)
            if (fusedDownload != null) {
                InstallWorkManager.enqueueWork(fusedDownload)
                ApplicationDialogFragment(
                    title = getString(R.string.purchase_complete),
                    message = getString(R.string.download_automatically_message),
                    positiveButtonText = getString(R.string.ok)
                ).show(supportFragmentManager, TAG)
            } else {
                ApplicationDialogFragment(
                    title = getString(R.string.purchase_error),
                    message = getString(R.string.something_went_wrong),
                    positiveButtonText = getString(R.string.ok)
                ).show(supportFragmentManager, TAG)
            }
        }
    }

    fun showSnackbarMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showNoInternet() {
        binding.noInternet.visibility = View.VISIBLE
        binding.fragment.visibility = View.GONE
    }

    // TODO: move storage availability code to FileManager Class
    private fun isStorageAvailable(fusedDownload: FusedDownload): Boolean {
        val availableSpace = calculateAvailableDiskSpace()
        return availableSpace > fusedDownload.appSize + (500 * (1000 * 1000))
    }

    private fun calculateAvailableDiskSpace(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val storageManager = getSystemService(STORAGE_SERVICE) as StorageManager
            val statsManager = getSystemService(STORAGE_STATS_SERVICE) as StorageStatsManager
            val uuid = storageManager.primaryStorageVolume.uuid
            try {
                if (uuid != null) {
                    statsManager.getFreeBytes(UUID.fromString(uuid))
                } else {
                    statsManager.getFreeBytes(StorageManager.UUID_DEFAULT)
                }
            } catch (e: Exception) {
                Timber.e("calculateAvailableDiskSpace: ${e.stackTraceToString()}")
                getAvailableInternalMemorySize()
            }
        } else {
            getAvailableInternalMemorySize()
        }
    }

    private fun getAvailableInternalMemorySize(): Long {
        val path: File = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        return availableBlocks * blockSize
    }
}
