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

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.aurora.gplayapi.exceptions.ApiException
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.data.ResultSupreme
import foundation.e.apps.data.fusedDownload.models.FusedDownload
import foundation.e.apps.data.login.LoginSourceGPlay
import foundation.e.apps.data.preference.PreferenceManagerModule
import foundation.e.apps.databinding.ActivityMainBinding
import foundation.e.apps.domain.errors.RetryMechanism
import foundation.e.apps.install.updates.UpdatesNotifier
import foundation.e.apps.presentation.login.LoginViewModel
import foundation.e.apps.ui.MainActivityViewModel
import foundation.e.apps.ui.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.ui.errors.CentralErrorHandler
import foundation.e.apps.ui.purchase.AppPurchaseFragmentDirections
import foundation.e.apps.ui.settings.SettingsFragment
import foundation.e.apps.ui.setup.signin.SignInViewModel
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var signInViewModel: SignInViewModel
    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityMainBinding
    private val TAG = MainActivity::class.java.simpleName
    private lateinit var viewModel: MainActivityViewModel

    private val retryMechanism by lazy { RetryMechanism() }
    private val ceh by lazy { CentralErrorHandler() }

    @Inject
    lateinit var preferenceManagerModule: PreferenceManagerModule

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
                loginViewModel.checkLogin()
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

        loginViewModel.loginState.distinctUntilChanged().observe(this) {
            when {
                it.isLoading -> {
                    // TODO ?
                }
                it.error.isNotBlank() -> {
                    it.authData ?: run { showLoginScreen(navController) }
                }
                !it.isLoggedIn -> {
                    showLoginScreen(navController)
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

        viewModel.purchaseAppLiveData.observe(this) {
            goToAppPurchaseFragment(it)
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    observeInvalidAuth()
                }

                launch {
                    observeTooManyRequests()
                }

                launch {
                    observeSignatureMissMatchError()
                }

                launch {
                    observerErrorEvent()
                }

                launch {
                    observeAppPurchaseFragment()
                }

                launch {
                    observeNoInternetEvent()
                }

                launch {
                    observeDataLoadError()
                }
            }
        }
    }

    private suspend fun observeNoInternetEvent() {
        EventBus.events.filter { appEvent ->
            appEvent is AppEvent.NoInternetEvent
        }.collectLatest {
            if (!(it.data as Boolean)) {
                showNoInternet()
            }
        }
    }

    private suspend fun observeAppPurchaseFragment() {
        EventBus.events.filter { appEvent ->
            appEvent is AppEvent.AppPurchaseEvent
        }.collectLatest {
            goToAppPurchaseFragment(it.data as FusedDownload)
        }
    }

    private fun goToAppPurchaseFragment(it: FusedDownload) {
        val action =
            AppPurchaseFragmentDirections.actionGlobalAppPurchaseFragment(it.packageName)
        findNavController(R.id.fragment).navigate(action)
    }

    private suspend fun observerErrorEvent() {
        EventBus.events.filter { appEvent ->
            appEvent is AppEvent.ErrorMessageEvent
        }.collectLatest {
            showSnackbarMessage(getString(it.data as Int))
        }
    }

    private suspend fun observeSignatureMissMatchError() {
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

    private suspend fun observeInvalidAuth() {
        EventBus.events.filter { appEvent ->
            appEvent is AppEvent.InvalidAuthEvent
        }.distinctUntilChanged { old, new ->
            ((old.data is String) && (new.data is String) && old.data == new.data)
        }.collectLatest {
            val currentUser = loginViewModel.currentUser()
            retryMechanism.wrapWithRetry(
                { loginViewModel.getNewToken() },
                {
                    ceh.getDialogForUnauthorized(
                        context = this@MainActivity,
                        logToDisplay = it.data.toString(),
                        user = currentUser,
                        retryAction = { loginViewModel.getNewToken() },
                        logoutAction = { loginViewModel.logout() }
                    ).run { ceh.dismissAllAndShow(this) }
                }
            )
        }
    }

    private suspend fun observeDataLoadError() {
        EventBus.events.filter { appEvent ->
            appEvent is AppEvent.DataLoadError<*>
        }.collectLatest {
            retryMechanism.wrapWithRetry(
                { loginViewModel.checkLogin() },
                {
                    ceh.getDialogForDataLoadError(
                        context = this@MainActivity,
                        result = it.data as ResultSupreme<*>,
                        retryAction = { loginViewModel.checkLogin() }
                    )?.run { ceh.dismissAllAndShow(this) }
                }
            )
        }
    }

    private fun validatedAuthObject(appEvent: AppEvent) {
        val data = appEvent.data as String
        if (data.isNotBlank()) {
            loginViewModel.markInvalidAuthObject(data)
        }
    }

    private suspend fun observeTooManyRequests() {
        EventBus.events.filter { appEvent ->
            appEvent is AppEvent.TooManyRequests
        }.collectLatest {
            binding.sessionErrorLayout.visibility = View.VISIBLE
            binding.retrySessionButton.setOnClickListener {
                binding.sessionErrorLayout.visibility = View.GONE
                loginViewModel.startLoginFlow(listOf(LoginSourceGPlay::class.java.simpleName))
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

    private fun startInstallationOfPurchasedApp(
        viewModel: MainActivityViewModel,
        packageName: String
    ) {
        lifecycleScope.launch {
            val fusedDownload = viewModel.updateAwaitingForPurchasedApp(packageName)
            if (fusedDownload != null) {
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

    private fun showLoginScreen(navController: NavController) {
        navController.popBackStack()
        navController.navigate(R.id.signInFragment)
    }
}
