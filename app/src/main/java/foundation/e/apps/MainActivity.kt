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
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.aurora.gplayapi.exceptions.ApiException
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.databinding.ActivityMainBinding
import foundation.e.apps.manager.database.fusedDownload.FusedDownload
import foundation.e.apps.manager.workmanager.InstallWorkManager
import foundation.e.apps.purchase.AppPurchaseFragmentDirections
import foundation.e.apps.setup.signin.SignInViewModel
import foundation.e.apps.updates.UpdatesNotifier
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.modules.CommonUtilsModule
import foundation.e.apps.utils.parentFragment.TimeoutFragment
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.UUID

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var signInViewModel: SignInViewModel
    private lateinit var binding: ActivityMainBinding
    private val TAG = MainActivity::class.java.simpleName
    private lateinit var viewModel: MainActivityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d(">>> onCreate")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bottomNavigationView = binding.bottomNavigationView
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment) as NavHostFragment
        val navController = navHostFragment.navController
        bottomNavigationView.setupWithNavController(navController)

        var hasInternet = true

        viewModel = ViewModelProvider(this)[MainActivityViewModel::class.java]
        signInViewModel = ViewModelProvider(this)[SignInViewModel::class.java]

        // navOptions and activityNavController for TOS and SignIn Fragments
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.navigation_resource, true)
            .build()
        navOptions.shouldLaunchSingleTop()

        viewModel.tocStatus.observe(this) {
            if (it != true) {
                navController.navigate(R.id.TOSFragment, null, navOptions)
            }
        }

        viewModel.internetConnection.observe(this) { isInternetAvailable ->
            hasInternet = isInternetAvailable
            if (isInternetAvailable) {
                binding.noInternet.visibility = View.GONE
                binding.fragment.visibility = View.VISIBLE

                // Watch and refresh authentication data
                if(viewModel.authDataJson.value == null) {
                    viewModel.authDataJson.observe(this) {
                        viewModel.handleAuthDataJson()
                    }
                }

            }
        }

        Timber.d(">>> userTypeLiveData: ${viewModel.userType.value}")
        Timber.d(">>> authDataJsonLiveData: ${viewModel.authDataJson.value}")
        viewModel.userType.observe(this) { user ->
            viewModel.handleAuthDataJson()
        }

        if(signInViewModel.authLiveData.value == null) {
            signInViewModel.authLiveData.observe(this) {
                viewModel.updateAuthData(it)
            }
        }

        viewModel.authValidity.observe(this) {
            viewModel.handleAuthValidity(it) {
                Timber.d("Timeout validating auth data!")
                val lastFragment = navHostFragment.childFragmentManager.fragments[0]
                if (lastFragment is TimeoutFragment) {
                    Timber.d("Displaying timeout from MainActivity on fragment: " + Fragment.java.name)
                    lastFragment.onTimeout()
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
                AppPurchaseFragmentDirections.actionGlobalAppPurchaseFragment(it.packageName)
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

        if (!CommonUtilsModule.isNetworkAvailable(this)) {
            showNoInternet()
        }

        viewModel.updateAppWarningList()
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
            Timber.d( "===> onCreate: AWAITING ${it.name}")
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
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showNoInternet() {
        binding.noInternet.visibility = View.VISIBLE
        binding.fragment.visibility = View.GONE
    }

    // TODO: move storage availability code to FileManager Class
    private fun isStorageAvailable(fusedDownload: FusedDownload): Boolean {
        var availableSpace = 0L
        availableSpace = calculateAvailableDiskSpace()
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
                Timber.e( "calculateAvailableDiskSpace: ${e.stackTraceToString()}")
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

    override fun onResume() {
        Timber.d(">>> onResume")
        super.onResume()
    }

    override fun onDestroy() {
        Timber.d(">>> onDestroy")
        super.onDestroy()
    }
}
