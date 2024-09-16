/*
 * Copyright (C) 2021-2024 MURENA SAS
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
 *
 */

package foundation.e.apps.ui

import android.content.Intent
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
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
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.exceptions.ApiException
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.BuildConfig
import foundation.e.apps.R
import foundation.e.apps.contract.ParentalControlContract.COLUMN_LOGIN_TYPE
import foundation.e.apps.data.Constants
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.install.models.AppInstall
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.LoginViewModel
import foundation.e.apps.data.login.PlayStoreAuthenticator
import foundation.e.apps.data.login.exceptions.GPlayValidationException
import foundation.e.apps.databinding.ActivityMainBinding
import foundation.e.apps.install.updates.UpdatesNotifier
import foundation.e.apps.ui.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.ui.purchase.AppPurchaseFragmentDirections
import foundation.e.apps.ui.settings.SettingsFragment
import foundation.e.apps.ui.setup.signin.SignInViewModel
import foundation.e.apps.utils.SystemInfoProvider
import foundation.e.apps.utils.eventBus.AppEvent
import foundation.e.apps.utils.eventBus.EventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var signInViewModel: SignInViewModel
    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainActivityViewModel

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val SESSION_DIALOG_TAG = "session_dialog"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setupBackPressHandlingForTiramisuAndAbove()

        setContentView(binding.root)

        val (bottomNavigationView, navController) = setupBootomNav()

        setupViewModels()

        setupNavigations(navController)

        if (intent.hasExtra(UpdatesNotifier.UPDATES_NOTIFICATION_CLICK_EXTRA)) {
            bottomNavigationView.selectedItemId = R.id.updatesFragment
        }

        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            viewModel.createNotificationChannels()
        }


        viewModel.setupConnectivityManager(this.applicationContext)

        observeInternetConnections()

        observeAuthObjects(navController)

        setupDestinationChangedListener(navController, bottomNavigationView)

        observePurchaseAppPage()

        observeErrorMessage()

        observeErrorMessageString()

        observeIsAppPurchased()

        observePurchaseDeclined()

        if (viewModel.internetConnection.value != true) {
            showNoInternet()
        }

        viewModel.updateAppWarningList()
        viewModel.updateContentRatings()
        viewModel.fetchUpdatableSystemAppsList()

        observeEvents()

        checkGPlayLoginRequest(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        checkGPlayLoginRequest(intent)
    }

    private fun checkGPlayLoginRequest(intent: Intent?) {
        viewModel.gPlayLoginRequested =
            intent?.getBooleanExtra(Constants.REQUEST_GPLAY_LOGIN, false) ?: false

        if (!viewModel.gPlayLoginRequested) return
        if (!viewModel.getTocStatus()) return
        if (viewModel.getUser() !in listOf(User.GOOGLE, User.ANONYMOUS)) {
            loginViewModel.logout()
        }
    }

    private fun refreshSession() {
        loginViewModel.startLoginFlow(listOf(PlayStoreAuthenticator::class.java.simpleName))
    }

    // In Android 12 (API level 32) and lower, onBackPressed is always called,
    // regardless of any registered instances of OnBackPressedCallback.
    // https://developer.android.com/guide/navigation/navigation-custom-back#onbackpressed
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isInitialScreen()) {
            resetIgnoreStatusForSessionRefresh()
            finish()
        }
        super.onBackPressed()
    }

    private fun isInitialScreen(): Boolean {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment) as NavHostFragment

        val navController = navHostFragment.navController

        return navController.currentDestination?.id == navController.graph.startDestinationId
    }

    private fun resetIgnoreStatusForSessionRefresh() {
        viewModel.shouldIgnoreSessionError = false
    }

    @Suppress("DEPRECATION")
    private fun setupBackPressHandlingForTiramisuAndAbove() {
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(PRIORITY_DEFAULT) {
                resetIgnoreStatusForSessionRefresh()
                finish()
            }
        }
    }

    private fun setupNavigations(navController: NavController) {
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
    }

    private fun observeEvents() {
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
                    observeErrorEvent()
                }

                launch {
                    observeErrorDialogEvent()
                }

                launch {
                    observeAppPurchaseFragment()
                }

                launch {
                    observeNoInternetEvent()
                }

                launch {
                    observeAgeLimitRestrictionEvent()
                }

                launch {
                    observeSuccessfulLogin()
                }
            }
        }
    }

    private suspend fun observeAgeLimitRestrictionEvent() {
        EventBus.events.filter {
            it is AppEvent.AgeLimitRestrictionEvent
        }.collectLatest {
            ApplicationDialogFragment(
                getString(R.string.restricted_app, it.data as String),
                getString(R.string.age_rate_limit_message, it.data as String),
                positiveButtonText = getString(R.string.ok),
            ).show(supportFragmentManager, TAG)
        }
    }

    private fun observePurchaseDeclined() {
        viewModel.purchaseDeclined.observe(this) {
            if (it.isNotEmpty()) {
                lifecycleScope.launch {
                    viewModel.updateUnavailableForPurchaseDeclined(it)
                }
            }
        }
    }

    private fun observeIsAppPurchased() {
        viewModel.isAppPurchased.observe(this) {
            if (it.isNotEmpty()) {
                startInstallationOfPurchasedApp(viewModel, it)
            }
        }
    }

    private fun observeErrorMessageString() {
        viewModel.errorMessageStringResource.observe(this) {
            showSnackbarMessage(getString(it))
        }
    }

    private fun observeErrorMessage() {
        viewModel.errorMessage.observe(this) {
            when (it) {
                is ApiException.AppNotPurchased -> showSnackbarMessage(getString(R.string.message_app_available_later))
                else -> showSnackbarMessage(
                    it.localizedMessage ?: getString(R.string.unknown_error)
                )
            }
        }
    }

    private fun observePurchaseAppPage() {
        viewModel.purchaseAppLiveData.observe(this) {
            goToAppPurchaseFragment(it)
        }
    }

    private fun observeInternetConnections() {
        viewModel.internetConnection.distinctUntilChanged().observe(this) { isInternetAvailable ->
            Timber.d("Observe internetConnection: $isInternetAvailable")
            if (isInternetAvailable) {
                binding.noInternet.visibility = View.GONE
                binding.fragment.visibility = View.VISIBLE
            }
        }
    }

    private fun setupDestinationChangedListener(
        navController: NavController,
        bottomNavigationView: BottomNavigationView
    ) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (viewModel.internetConnection.value == false) {
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
    }

    private fun observeAuthObjects(navController: NavController) {
        loginViewModel.authObjects.distinctUntilChanged().observe(this) {
            when {
                it == null -> return@observe
                it.isEmpty() -> {
                    // No auth type defined means user has not logged in yet
                    // Pop back stack to prevent showing TOSFragment on pressing back button.
                    navController.popBackStack()
                    navController.navigate(R.id.signInFragment)
                    if (viewModel.gPlayLoginRequested) viewModel.closeAfterLogin = true
                    return@observe
                }

                else -> {}
            }

            val gPlayAuthObject = it.find { it is AuthObject.GPlayAuth }

            gPlayAuthObject?.result?.run {
                if (isSuccess()) {
                    viewModel.gPlayAuthData = data as AuthData
                } else if (exception is GPlayValidationException) {
                    val email = otherPayload.toString()
                    viewModel.uploadFaultyTokenToEcloud(
                        email,
                        SystemInfoProvider.getAppBuildInfo()
                    )
                } else if (exception != null) {
                    Timber.e(exception, "Login failed! message: ${exception?.localizedMessage}")
                }
            }

            if (viewModel.closeAfterLogin && it.isNotEmpty() && it.all { it.result.isSuccess() }) {
                finishAndRemoveTask()
            }
        }
    }

    private suspend fun observeSuccessfulLogin() {
        EventBus.events.filter {
            it is AppEvent.SuccessfulLogin
        }.collectLatest {
            broadcastGPlayLogin(it.data as User)
        }
    }

    private fun broadcastGPlayLogin(user: User) {
        Timber.d("Sending broadcast with login type - $user")
        val intent = Intent(Constants.ACTION_PARENTAL_CONTROL_APP_LOUNGE_LOGIN).apply {
            setPackage(BuildConfig.PACKAGE_NAME_PARENTAL_CONTROL)
            putExtra(COLUMN_LOGIN_TYPE, user.name)
        }
        sendBroadcast(intent)
    }

    private fun setupViewModels() {
        viewModel = ViewModelProvider(this)[MainActivityViewModel::class.java]
        signInViewModel = ViewModelProvider(this)[SignInViewModel::class.java]
        loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]
    }

    private fun setupBootomNav(): Pair<BottomNavigationView, NavController> {
        val bottomNavigationView = binding.bottomNavigationView
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment) as NavHostFragment
        val navController = navHostFragment.navController
        bottomNavigationView.setupWithNavController(navController)
        setupBottomNavItemSelectedListener(bottomNavigationView, navHostFragment, navController)
        return Pair(bottomNavigationView, navController)
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
            goToAppPurchaseFragment(it.data as AppInstall)
        }
    }

    private fun goToAppPurchaseFragment(it: AppInstall) {
        val action =
            AppPurchaseFragmentDirections.actionGlobalAppPurchaseFragment(it.packageName)
        findNavController(R.id.fragment).navigate(action)
    }

    private suspend fun observeErrorEvent() {
        EventBus.events.filter { appEvent ->
            appEvent is AppEvent.ErrorMessageEvent
        }.collectLatest {
            showSnackbarMessage(getString(it.data as Int))
        }
    }

    private suspend fun observeErrorDialogEvent() {
        EventBus.events.filter { appEvent ->
            appEvent is AppEvent.ErrorMessageDialogEvent
        }.collectLatest {
            ApplicationDialogFragment(
                title = getString(R.string.unknown_error),
                message = getString(it.data as Int)
            ).show(supportFragmentManager, TAG)
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
            if (BuildConfig.DEBUG) {
                Toast.makeText(this, "Refreshing token...", Toast.LENGTH_SHORT).show()
            }
            validatedAuthObject(it)
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
            handleRefreshSessionEvent()
        }
    }

    private fun handleRefreshSessionEvent() {
        val shouldShowDialog = !viewModel.shouldIgnoreSessionError
        val isDialogShowing = supportFragmentManager.findFragmentByTag(SESSION_DIALOG_TAG) != null
        if (shouldShowDialog && !isDialogShowing) {
            showRefreshSessionDialog()
        }
    }

    private fun showRefreshSessionDialog() {
        ApplicationDialogFragment(
            title = getString(R.string.account_unavailable),
            message = getString(R.string.too_many_requests_desc),
            drawableResId = R.drawable.ic_warning,
            positiveButtonText = getString(R.string.refresh_session),
            positiveButtonAction = {
                refreshSession()
            },
            cancelButtonText = getString(R.string.ignore).uppercase(),
            cancelButtonAction = {
                onIgnoreSessionClick()
            },
            cancelable = true,
        ).show(supportFragmentManager, SESSION_DIALOG_TAG)
    }

    private fun onIgnoreSessionClick() {
        viewModel.shouldIgnoreSessionError = true
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
        lifecycleScope.launch(Dispatchers.IO) {
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
}
