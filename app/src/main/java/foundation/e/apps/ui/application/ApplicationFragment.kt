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

package foundation.e.apps.ui.application

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.format.Formatter
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aurora.gplayapi.data.models.ContentRating
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import dagger.hilt.android.AndroidEntryPoint
import foundation.e.apps.MainActivity
import foundation.e.apps.R
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.shareUri
import foundation.e.apps.data.application.utils.CategoryStringFormatter
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.ResultStatus
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.enums.isInitialized
import foundation.e.apps.data.login.AuthObject
import foundation.e.apps.data.login.exceptions.GPlayLoginException
import foundation.e.apps.databinding.FragmentApplicationBinding
import foundation.e.apps.di.CommonUtilsModule.LIST_OF_NULL
import foundation.e.apps.domain.ValidateAppAgeLimitUseCase.Companion.KEY_ANTI_FEATURES_NSFW
import foundation.e.apps.install.download.data.DownloadProgress
import foundation.e.apps.install.pkg.AppLoungePackageManager
import foundation.e.apps.install.pkg.PWAManager
import foundation.e.apps.ui.AppInfoFetchViewModel
import foundation.e.apps.ui.MainActivityViewModel
import foundation.e.apps.ui.PrivacyInfoViewModel
import foundation.e.apps.ui.application.ShareButtonVisibilityState.Hidden
import foundation.e.apps.ui.application.ShareButtonVisibilityState.Visible
import foundation.e.apps.ui.application.model.ApplicationScreenshotsRVAdapter
import foundation.e.apps.ui.application.subFrags.ApplicationDialogFragment
import foundation.e.apps.ui.parentFragment.TimeoutFragment
import foundation.e.apps.utils.isValid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject


@AndroidEntryPoint
class ApplicationFragment : TimeoutFragment(R.layout.fragment_application) {

    private val args: ApplicationFragmentArgs by navArgs()
    private val TAG = ApplicationFragment::class.java.simpleName
    private var _binding: FragmentApplicationBinding? = null

    private val binding get() = _binding!!

    /*
     * We have no way to pass an argument for a specific deeplink to signify it is an f-droid link.
     * Hence we check the intent from the activity.
     * This boolean is later used to lock the origin to Origin.CLEANAPK,
     * and call a different method to fetch from cleanapk.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5509
     */
    private val isFdroidDeepLink: Boolean by lazy {
        activity?.intent?.data?.host?.equals("f-droid.org") ?: false
    }

    /*
     * We will use this variable in all cases instead of directly calling args.origin.
     *
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5509
     */
    private val origin by lazy {
        if (isFdroidDeepLink) {
            Origin.CLEANAPK
        } else {
            args.origin
        }
    }

    private var isDetailsLoaded = false

    private lateinit var screenshotsRVAdapter: ApplicationScreenshotsRVAdapter

    @Inject
    lateinit var appLoungePackageManager: AppLoungePackageManager

    @Inject
    lateinit var pwaManager: PWAManager

    private val applicationViewModel: ApplicationViewModel by viewModels()
    private val privacyInfoViewModel: PrivacyInfoViewModel by viewModels()
    private val appInfoFetchViewModel: AppInfoFetchViewModel by viewModels()
    override val mainActivityViewModel: MainActivityViewModel by activityViewModels()

    private var applicationIcon: ImageView? = null

    private var shouldReloadPrivacyInfo = false

    companion object {
        private const val PRIVACY_SCORE_SOURCE_CODE_URL =
            "https://gitlab.e.foundation/e/os/apps/-/blob/main/app/src/main/java/foundation/e/apps/data/exodus/repositories/PrivacyScoreRepositoryImpl.kt"
        private const val EXODUS_URL = "https://exodus-privacy.eu.org"
        private const val EXODUS_REPORT_URL = "https://reports.exodus-privacy.eu.org/"
        private const val PRIVACY_GUIDELINE_URL = "https://doc.e.foundation/privacy_score"
        private const val REQUEST_EXODUS_REPORT_URL =
            "https://reports.exodus-privacy.eu.org/en/analysis/submit#"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentApplicationBinding.bind(view)

        setupListening()

        authObjects.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            loadDataWhenNetworkAvailable(it)
        }

        applicationViewModel.exceptionsLiveData.observe(viewLifecycleOwner) {
            handleExceptionsCommon(it)
        }

        setupToolbar(view)

        setupScreenshotRVAdapter()

        binding.applicationLayout.visibility = View.INVISIBLE

        applicationViewModel.applicationLiveData.observe(viewLifecycleOwner) { resultPair ->
            Timber.d("ApplicationLiveData: ${resultPair.first.contentRating}")
            updateUi(resultPair)
        }

        applicationViewModel.errorMessageLiveData.observe(viewLifecycleOwner) {
            (requireActivity() as MainActivity).showSnackbarMessage(getString(it))
        }
    }

    private fun updateUi(
        resultPair: Pair<Application, ResultStatus>,
    ) {
        if (resultPair.second != ResultStatus.OK) {
            return
        }

        /*
         * Previously fusedApp only had instance of FusedApp.
         * As such previously all reference was simply using "it", the default variable in
         * the scope. But now "it" is Pair(FusedApp, ResultStatus), not an instance of FusedApp.
         *
         * Avoid Git diffs by using a variable named "it".
         *
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5413
         */
        val it = resultPair.first

        togglePrivacyInfoVisibility(false)

        isDetailsLoaded = true
        if (applicationViewModel.appStatus.value == null) {
            applicationViewModel.appStatus.value = it.status
        }

        if (it.other_images_path.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
        }
        screenshotsRVAdapter.setData(it.other_images_path)

        // Title widgets
        updateAppTitlePanel(it)

        binding.downloadInclude.appSize.text = it.appSize

        // Ratings widgets
        updateAppRating(it)

        updateAppDescriptionText(it)

        // Information widgets
        updateAppInformation(it)

        // Privacy widgets
        updatePrivacyPanel()

        showWarningMessage(it)

        fetchAppTracker(it)
        observeDownloadList()
        observeDownloadStatus(binding.root)
        stopLoadingUI()

        collectState()
    }

    private fun collectState() {
        collectShareVisibilityState()
        collectAppContentRatingState()
    }

    private fun showWarningMessage(it: Application) {
        if (appInfoFetchViewModel.isAppInBlockedList(it)) {
            binding.snackbarLayout.visibility = View.VISIBLE
        } else if (args.isGplayReplaced && !applicationViewModel.isOpenSourceSelected()) {
            binding.duplicateAppCardview.visibility = View.VISIBLE
            binding.duplicateAppCardview.setOnClickListener {
                ApplicationDialogFragment(
                    title = getString(R.string.open_source_apps),
                    message = getString(R.string.duplicate_app_from_sources)
                ).show(childFragmentManager, TAG)
            }
        }
    }

    private fun observeDownloadList() {
        mainActivityViewModel.downloadList.removeObservers(viewLifecycleOwner)
        mainActivityViewModel.downloadList.observe(viewLifecycleOwner) { list ->
            applicationViewModel.updateApplicationStatus(list)
        }
    }

    private fun updateAppDescriptionText(it: Application) {
        binding.appDescription.text =
            Html.fromHtml(it.description, Html.FROM_HTML_MODE_COMPACT)

        binding.appDescriptionMore.setOnClickListener { view ->
            val action =
                ApplicationFragmentDirections.actionApplicationFragmentToDescriptionFragment(it.description)
            view.findNavController().navigate(action)
        }
    }

    private fun updatePrivacyPanel() {
        binding.privacyInclude.apply {
            appPermissions.setOnClickListener { _ ->
                ApplicationDialogFragment(
                    drawableResId = R.drawable.ic_perm,
                    title = getString(R.string.permissions),
                    message = getPermissionListString()
                ).show(childFragmentManager, TAG)
            }
            appTrackers.setOnClickListener {
                val fusedApp = applicationViewModel.getFusedApp()
                var trackers =
                    buildTrackersString(fusedApp)

                ApplicationDialogFragment(
                    drawableResId = R.drawable.ic_tracker,
                    title = getString(R.string.trackers_title),
                    message = trackers
                ).show(childFragmentManager, TAG)
            }
        }
    }

    private fun buildTrackersString(application: Application?): String {
        var trackers =
            privacyInfoViewModel.getTrackerListText(application)

        if (application?.trackers == LIST_OF_NULL) {
            trackers = getString(R.string.tracker_information_not_found)
        } else if (trackers.isNotEmpty()) {
            trackers += "<br /> <br />" + getString(
                R.string.privacy_computed_using_text,
                generateExodusUrl()
            )
        } else {
            trackers = getString(R.string.no_tracker_found)
        }

        return trackers
    }

    private fun updateAppInformation(
        it: Application,
    ) {
        binding.infoInclude.apply {
            appUpdatedOn.text = getString(
                R.string.updated_on,
                if (origin == Origin.CLEANAPK) it.updatedOn else it.last_modified
            )
            val notAvailable = getString(R.string.not_available)
            appRequires.text = getString(R.string.min_android_version, notAvailable)
            appVersion.text = getString(
                R.string.version,
                if (it.latest_version_number == "-1") notAvailable else it.latest_version_number
            )
            appLicense.text = getString(
                R.string.license,
                if (it.licence.isBlank() or (it.licence == "unknown")) notAvailable else it.licence
            )
            appPackageName.text = getString(R.string.package_name, it.package_name)
        }
    }

    private fun updateAppRating(it: Application) {
        binding.ratingsInclude.apply {
            if (it.ratings.usageQualityScore != -1.0) {
                val rating =
                    applicationViewModel.handleRatingFormat(it.ratings.usageQualityScore)
                appRating.text =
                    getString(
                        R.string.rating_out_of, rating
                    )

                appRating.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_star_blank),
                    null,
                    getRatingDrawable(rating),
                    null
                )
                appRating.compoundDrawablePadding = 15
            }
            appRatingLayout.setOnClickListener {
                ApplicationDialogFragment(
                    drawableResId = R.drawable.ic_star_blank,
                    title = getString(R.string.rating),
                    message = getString(R.string.rating_description)
                ).show(childFragmentManager, TAG)
            }

            appPrivacyScoreLayout.setOnClickListener {
                if (privacyInfoViewModel.shouldRequestExodusReport(applicationViewModel.getFusedApp())) {
                    showRequestExodusReportDialog()
                    return@setOnClickListener
                }

                showPrivacyScoreCalculationLoginDialog()
            }
        }
    }

    private fun showRequestExodusReportDialog() {
        ApplicationDialogFragment(
            drawableResId = R.drawable.ic_lock,
            title = getString(R.string.request_exodus_report),
            message = getRequestExodusReportDialogDetailsText(),
            positiveButtonText = getString(R.string.ok),
            positiveButtonAction = {
                shouldReloadPrivacyInfo = true
                openRequestExodusReportUrl()
            },
            cancelButtonText = getString(R.string.cancel)
        ).show(childFragmentManager, TAG)
    }

    private fun getRequestExodusReportDialogDetailsText() = getString(
        R.string.request_exodus_report_confirm_dialog,
        getString(R.string.ok),
        getString(R.string.app_name)
    )

    private fun openRequestExodusReportUrl() {
        val openUrlIntent = Intent(Intent.ACTION_VIEW)
        openUrlIntent.data =
            Uri.parse("${REQUEST_EXODUS_REPORT_URL}${applicationViewModel.getFusedApp()?.package_name}")
        startActivity(openUrlIntent)
    }

    private fun showPrivacyScoreCalculationLoginDialog() {
        ApplicationDialogFragment(
            drawableResId = R.drawable.ic_lock,
            title = getString(R.string.privacy_score),
            message = getString(
                R.string.privacy_description,
                PRIVACY_SCORE_SOURCE_CODE_URL,
                generateExodusUrl(),
                PRIVACY_GUIDELINE_URL
            )
        ).show(childFragmentManager, TAG)
    }

    private fun updateAppTitlePanel(it: Application) {
        binding.titleInclude.apply {
            applicationIcon = appIcon
            appName.text = it.name
            appInfoFetchViewModel.getAuthorName(it).observe(viewLifecycleOwner) {
                appAuthor.text = it
            }

            updateCategoryTitle(it)

            if (it.origin == Origin.CLEANAPK) {
                sourceTag.visibility = View.VISIBLE
                sourceTag.text = it.source
            }
            if (origin == Origin.CLEANAPK) {
                appIcon.load(CleanApkRetrofit.ASSET_URL + it.icon_image_path)
            } else {
                appIcon.load(it.icon_image_path)
            }
        }

        updateAntiFeaturesUi(it)
    }

    private fun updateAntiFeaturesUi(app: Application) {
        val isNsfwApp =
            app.antiFeatures.find { antiFeature -> antiFeature.containsKey(KEY_ANTI_FEATURES_NSFW) } != null

        val isKnownNsfwApp = applicationViewModel.isKnownNsfwApp(app)

        if (!isNsfwApp && !isKnownNsfwApp) return

        binding.titleInclude.antiFeatureInfoLayout.apply {
            isVisible = true
            setOnClickListener {
                ApplicationDialogFragment(
                    title = getString(R.string.nsfw_dialog_title),
                    message = getString(R.string.nsfw_dialog_message),
                    drawableResId = R.drawable.ic_visibility_off
                ).show(childFragmentManager, TAG)
            }
        }
    }

    private fun updateCategoryTitle(app: Application) {
        binding.titleInclude.apply {
            var catText = app.category.ifBlank { args.category }

            catText = when (catText) {
                "game_open_games" -> getString(R.string.games) // F-droid games
                "web_games" -> getString(R.string.games) // PWA games
                else -> catText
            }

            catText = CategoryStringFormatter.format(catText)

            categoryTitle.isVisible = catText.isNotBlank()
            categoryTitle.text = catText
        }
    }

    private fun setupScreenshotRVAdapter() {
        screenshotsRVAdapter = ApplicationScreenshotsRVAdapter(origin)
        binding.recyclerView.apply {
            adapter = screenshotsRVAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupToolbar(view: View) {
        val startDestination = findNavController().graph.startDestinationId
        if (startDestination == R.id.applicationFragment) {
            binding.toolbar.setNavigationOnClickListener {
                val action = ApplicationFragmentDirections.actionApplicationFragmentToHomeFragment()
                view.findNavController().navigate(action)
            }
        } else {
            binding.toolbar.setNavigationOnClickListener {
                view.findNavController().navigateUp()
            }
        }

        binding.actionShare.setOnClickListener { openShareSheet() }
    }

    private fun openShareSheet() {
        val application = applicationViewModel.applicationLiveData.value?.first ?: return
        val shareIntent = AppShareIntent.create(application.name, application.shareUri)
        startActivity(Intent.createChooser(shareIntent, null))
    }

    private fun collectShareVisibilityState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                applicationViewModel.shareButtonVisibilityState.collectLatest { state ->
                    when (state) {
                        Hidden -> binding.actionShare.visibility = View.GONE
                        Visible -> binding.actionShare.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun collectAppContentRatingState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                applicationViewModel.appContentRatingState
                    .collectLatest(::updateContentRatingUi)
            }
        }
    }

    private suspend fun updateContentRatingUi(contentRating: ContentRating) {
        if (contentRating.isValid()) {
            loadContentRating(contentRating)
        } else {
            hideContentRating()
        }
    }

    private suspend fun loadContentRating(contentRating: ContentRating) {
        val drawable = loadContentRatingDrawable(contentRating.artwork.url)
        displayRating(contentRating, drawable)
    }

    private fun hideContentRating() {
        binding.ratingsInclude.appContentRatingLayout.visibility = View.GONE
    }

    private fun displayRating(contentRating: ContentRating, drawable: Drawable?) {
        binding.ratingsInclude.apply {
            appContentRatingTitle.text = contentRating.title

            if (drawable != null) {
                appContentRatingProgress.visibility = View.GONE
                appContentRatingIcon.setImageDrawable(drawable)
            }

            appContentRatingLayout.apply {
                visibility = View.VISIBLE

                setOnClickListener {
                    openContentRatingDialog(
                        contentRating.title,
                        drawable,
                        contentRating.description
                    )
                }
            }
        }
    }

    private suspend fun loadContentRatingDrawable(url: String): Drawable? {
        return withContext(Dispatchers.IO) {
            val imageRequest = ImageRequest.Builder(requireContext()).data(url).build()
            val result = ImageLoader.invoke(requireContext()).execute(imageRequest)
            if (result is SuccessResult) result.drawable else null
        }
    }

    private fun openContentRatingDialog(title: String, iconUrl: Drawable?, recommendation: String) {
        ApplicationDialogFragment(drawable = iconUrl, title = title, message = recommendation)
            .show(childFragmentManager, TAG)
    }

    override fun loadData(authObjectList: List<AuthObject>) {
        if (isDetailsLoaded) return
        /* Show the loading bar. */
        showLoadingUI()
        /* Remove trailing slash (if present) that can become part of the packageName */
        val packageName = args.packageName.run { if (endsWith('/')) dropLast(1) else this }

        applicationViewModel.loadData(
            args.id,
            packageName,
            origin,
            isFdroidDeepLink,
            authObjectList
        ) {
            clearAndRestartGPlayLogin()
            true
        }
    }

    override fun onTimeout(
        exception: Exception,
        predefinedDialog: AlertDialog.Builder
    ): AlertDialog.Builder? {
        return predefinedDialog
    }

    override fun onSignInError(
        exception: GPlayLoginException,
        predefinedDialog: AlertDialog.Builder
    ): AlertDialog.Builder? {
        return predefinedDialog
    }

    override fun onDataLoadError(
        exception: Exception,
        predefinedDialog: AlertDialog.Builder
    ): AlertDialog.Builder? {
        return predefinedDialog
    }

    private fun observeDownloadStatus(view: View) {
        applicationViewModel.appStatus.observe(viewLifecycleOwner) { status ->
            val application = applicationViewModel.getFusedApp() ?: Application()
            mainActivityViewModel.verifyUiFilter(application) {
                if (!application.filterLevel.isInitialized()) {
                    return@verifyUiFilter
                }

                handleInstallStatus(status, view, application)
            }
        }
    }

    private fun handleInstallStatus(
        status: Status?,
        view: View,
        application: Application,
    ) {
        val installButton = binding.downloadInclude.installButton
        val downloadPB = binding.downloadInclude.progressLayout
        val appSize = binding.downloadInclude.appSize

        when (status) {
            Status.INSTALLED -> handleInstalled(
                installButton,
                view,
                application,
                downloadPB,
                appSize
            )

            Status.UPDATABLE -> handleUpdatable(
                installButton,
                view,
                application,
                downloadPB,
                appSize
            )

            Status.UNAVAILABLE -> handleUnavaiable(
                installButton,
                application,
                downloadPB,
                appSize
            )

            Status.QUEUED, Status.AWAITING, Status.DOWNLOADED -> handleQueued(
                installButton,
                application,
                downloadPB,
                appSize
            )

            Status.DOWNLOADING -> handleDownloading(
                installButton,
                application,
                downloadPB,
                appSize
            )

            Status.INSTALLING -> handleInstalling(
                installButton,
                downloadPB,
                appSize
            )

            Status.BLOCKED -> handleBlocked(installButton, view)
            Status.INSTALLATION_ISSUE -> handleInstallingIssue(
                installButton,
                application,
                downloadPB,
                appSize
            )

            else -> {
                Timber.d("Unknown status: $status")
            }
        }
    }

    private fun handleInstallingIssue(
        installButton: MaterialButton,
        application: Application,
        downloadPB: RelativeLayout,
        appSize: MaterialTextView
    ) {
        installButton.apply {
            enableInstallButton(R.string.retry)
            setOnClickListener {
                applicationIcon?.let {
                    mainActivityViewModel.getApplication(application)
                }
            }
        }
        downloadPB.visibility = View.GONE
        appSize.visibility = View.VISIBLE
    }

    private fun handleBlocked(
        installButton: MaterialButton,
        view: View
    ) {
        installButton.setOnClickListener {
            val errorMsg = when (mainActivityViewModel.getUser()) {
                User.ANONYMOUS,
                User.NO_GOOGLE,
                User.UNAVAILABLE -> getString(R.string.install_blocked_anonymous)

                User.GOOGLE -> getString(R.string.install_blocked_google)
            }
            if (errorMsg.isNotBlank()) {
                Snackbar.make(view, errorMsg, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleInstalling(
        installButton: MaterialButton,
        downloadPB: RelativeLayout,
        appSize: MaterialTextView
    ) {
        installButton.disableInstallButton(R.string.installing)
        downloadPB.visibility = View.GONE
        appSize.visibility = View.VISIBLE
    }

    private fun handleDownloading(
        installButton: MaterialButton,
        application: Application,
        downloadPB: RelativeLayout,
        appSize: MaterialTextView
    ) {
        installButton.apply {
            enableInstallButton(R.string.cancel)
            text = getString(R.string.cancel)
            setOnClickListener {
                mainActivityViewModel.cancelDownload(application)
            }
        }
        downloadPB.visibility = View.VISIBLE
        appSize.visibility = View.GONE
        applicationViewModel.downloadProgress.observe(viewLifecycleOwner) {
            lifecycleScope.launch(Dispatchers.Main) {
                updateProgress(it)
            }
        }
    }

    private fun handleQueued(
        installButton: MaterialButton,
        application: Application,
        downloadPB: RelativeLayout,
        appSize: MaterialTextView
    ) {
        downloadPB.visibility = View.GONE
        appSize.visibility = View.VISIBLE
        installButton.apply {
            enableInstallButton(R.string.cancel)
            text = getString(R.string.cancel)
            setOnClickListener {
                mainActivityViewModel.cancelDownload(application)
            }
        }
    }

    private fun handleUnavaiable(
        installButton: MaterialButton,
        application: Application,
        downloadPB: RelativeLayout,
        appSize: MaterialTextView
    ) {
        installButton.apply {
            enableInstallButton(R.string.install)
            text = when {
                mainActivityViewModel.checkUnsupportedApplication(application) ->
                    getString(R.string.not_available)

                application.isFree -> getString(R.string.install)
                else -> application.price
            }
            setOnClickListener {
                if (mainActivityViewModel.checkUnsupportedApplication(application, activity)) {
                    return@setOnClickListener
                }
                applicationIcon?.let {
                    if (application.isFree) {
                        disableInstallButton(R.string.cancel)
                        installApplication(application)
                    } else {
                        if (!mainActivityViewModel.shouldShowPaidAppsSnackBar(application)) {
                            ApplicationDialogFragment(
                                title = getString(R.string.dialog_title_paid_app, application.name),
                                message = getString(
                                    R.string.dialog_paidapp_message,
                                    application.name,
                                    application.price
                                ),
                                positiveButtonText = getString(R.string.dialog_confirm),
                                positiveButtonAction = {
                                    installApplication(application)
                                },
                                cancelButtonText = getString(R.string.dialog_cancel),
                            ).show(childFragmentManager, "ApplicationFragment")
                        }
                    }
                }
            }
        }
        downloadPB.visibility = View.GONE
        appSize.visibility = View.VISIBLE
    }

    private fun MaterialButton.disableInstallButton(buttonStringID: Int) {
        isEnabled = false
        text = context.getString(buttonStringID)
        strokeColor = ContextCompat.getColorStateList(context, R.color.light_grey)
        setTextColor(context.getColor(R.color.light_grey))
        backgroundTintList =
            ContextCompat.getColorStateList(context, android.R.color.transparent)
    }

    private fun MaterialButton.enableInstallButton(buttonStringID: Int) {
        isEnabled = true
        text = context.getString(buttonStringID)
        strokeColor = ContextCompat.getColorStateList(context, R.color.colorAccent)
        setTextColor(context.getColor(R.color.colorAccent))
        backgroundTintList =
            ContextCompat.getColorStateList(context, android.R.color.transparent)
    }

    private fun installApplication(
        application: Application
    ) {
        if (appInfoFetchViewModel.isAppInBlockedList(application)) {
            ApplicationDialogFragment(
                title = getString(R.string.this_app_may_not_work_properly),
                message = getString(R.string.may_not_work_warning_message),
                positiveButtonText = getString(R.string.install_anyway),
                positiveButtonAction = {
                    mainActivityViewModel.getApplication(application)
                }
            ).show(childFragmentManager, "ApplicationFragment")
        } else {
            mainActivityViewModel.getApplication(application)
        }
    }

    private fun handleUpdatable(
        installButton: MaterialButton,
        view: View,
        application: Application,
        downloadPB: RelativeLayout,
        appSize: MaterialTextView
    ) {
        installButton.apply {
            enableInstallButton(R.string.not_available)
            text = if (mainActivityViewModel.checkUnsupportedApplication(application))
                getString(R.string.not_available)
            else getString(R.string.update)
            setTextColor(Color.WHITE)
            backgroundTintList =
                ContextCompat.getColorStateList(view.context, R.color.colorAccent)
            setOnClickListener {
                if (mainActivityViewModel.checkUnsupportedApplication(application, activity)) {
                    return@setOnClickListener
                }
                applicationIcon?.let {
                    mainActivityViewModel.getApplication(application)
                }
            }
        }
        downloadPB.visibility = View.GONE
        appSize.visibility = View.VISIBLE
    }

    private fun handleInstalled(
        installButton: MaterialButton,
        view: View,
        application: Application,
        downloadPB: RelativeLayout,
        appSize: MaterialTextView
    ) {
        downloadPB.visibility = View.GONE
        appSize.visibility = View.VISIBLE
        installButton.apply {
            enableInstallButton(R.string.open)
            setTextColor(Color.WHITE)
            backgroundTintList =
                ContextCompat.getColorStateList(view.context, R.color.colorAccent)
            setOnClickListener {
                if (application.is_pwa) {
                    pwaManager.launchPwa(application)
                } else {
                    val launchIntent =
                        appLoungePackageManager.getLaunchIntent(application.package_name)
                    launchIntent?.run { startActivity(this) }
                }
            }
        }
    }

    private suspend fun updateProgress(
        downloadProgress: DownloadProgress,
    ) {
        val progressResult = applicationViewModel.calculateProgress(downloadProgress)
        if (view == null || progressResult.first < 1) {
            return
        }
        val downloadedSize = "${
            Formatter.formatFileSize(requireContext(), progressResult.second).substringBefore(" MB")
        }/${Formatter.formatFileSize(requireContext(), progressResult.first)}"
        val progressPercentage =
            ((progressResult.second / progressResult.first.toDouble()) * 100f).toInt()
        binding.downloadInclude.appInstallPB.progress = progressPercentage
        binding.downloadInclude.percentage.text =
            String.format(Locale.getDefault(), "%d%%", progressPercentage)
        binding.downloadInclude.downloadedSize.text = downloadedSize
    }

    private fun getPermissionListString(): String {
        var permission =
            applicationViewModel.transformPermsToString()
        if (permission.isEmpty()) {
            permission = getString(
                R.string.no_permission_found
            )
        } else {
            permission += "<br />" + getString(
                R.string.privacy_computed_using_text,
                generateExodusUrl()
            )
        }
        return permission
    }

    private fun generateExodusUrl(): String {
        // if app info not loaded yet, pass the default exodus homePage url
        val fusedApp = applicationViewModel.getFusedApp()
        if (fusedApp == null || fusedApp.permsFromExodus == LIST_OF_NULL) {
            return EXODUS_URL
        }

        val reportId = applicationViewModel.applicationLiveData.value!!.first.reportId
        return "$EXODUS_REPORT_URL${Locale.getDefault().language}/reports/$reportId"
    }

    private fun fetchAppTracker(application: Application) {
        privacyInfoViewModel.getSingularAppPrivacyInfoLiveData(application)
            .observe(viewLifecycleOwner) {
                updatePrivacyScore()
            }
    }

    override fun showLoadingUI() {
        binding.applicationLayout.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
    }

    override fun stopLoadingUI() {
        binding.applicationLayout.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
    }

    private fun updatePrivacyScore() {
        val privacyScore =
            privacyInfoViewModel.getPrivacyScore(applicationViewModel.getFusedApp())
        if (privacyScore != -1) {
            val appPrivacyScore = binding.ratingsInclude.appPrivacyScore
            appPrivacyScore.text = getString(
                R.string.privacy_rating_out_of,
                privacyScore.toString()
            )

            appPrivacyScore.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_lock_blank),
                null,
                getPrivacyDrawable(privacyScore.toString()),
                null
            )
            appPrivacyScore.compoundDrawablePadding = 15
        }
        togglePrivacyInfoVisibility(true)
    }

    private fun togglePrivacyInfoVisibility(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.INVISIBLE
        binding.privacyInclude.run {
            appPermissions.visibility = visibility
            appTrackers.visibility = visibility
            loadingBar.isVisible = !visible
        }
        binding.ratingsInclude.loadingBar.isVisible = !visible

        togglePrivacyScoreVisibility(visible)
    }

    private fun togglePrivacyScoreVisibility(visible: Boolean) {
        var isRequestReportVisible = false
        var privacyScoreVisibility = if (visible) View.VISIBLE else View.INVISIBLE

        if (visible) {
            isRequestReportVisible =
                privacyInfoViewModel.shouldRequestExodusReport(applicationViewModel.getFusedApp())
            privacyScoreVisibility = if (isRequestReportVisible) View.INVISIBLE else View.VISIBLE
        }

        binding.ratingsInclude.appPrivacyScore.visibility = privacyScoreVisibility
        binding.ratingsInclude.requestExodusReport.isVisible = isRequestReportVisible
    }

    override fun onResume() {
        super.onResume()
        observeDownloadList()
        reloadPrivacyInfo()
    }

    private fun reloadPrivacyInfo() {
        if (shouldReloadPrivacyInfo) {
            togglePrivacyInfoVisibility(false)
            privacyInfoViewModel.refreshAppPrivacyInfo(applicationViewModel.getFusedApp())
        }

        shouldReloadPrivacyInfo = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.recyclerView?.adapter = null
        _binding = null
        applicationIcon = null
    }

    private fun getPrivacyDrawable(privacyRating: String): Drawable? {
        val rating = privacyRating.toInt()

        var dotColor = ContextCompat.getColor(requireContext(), R.color.colorGreen)
        if (rating <= 3) {
            dotColor = ContextCompat.getColor(requireContext(), R.color.colorRed)
        } else if (rating <= 6) {
            dotColor = ContextCompat.getColor(requireContext(), R.color.colorYellow)
        }

        return applyDotAccent(dotColor)
    }

    private fun getRatingDrawable(reviewRating: String): Drawable? {
        val rating = reviewRating.toDouble()

        var dotColor = ContextCompat.getColor(requireContext(), R.color.colorGreen)
        if (rating <= 2.0) {
            dotColor = ContextCompat.getColor(requireContext(), R.color.colorRed)
        } else if (rating <= 3.4) {
            dotColor = ContextCompat.getColor(requireContext(), R.color.colorYellow)
        }

        return applyDotAccent(dotColor)
    }

    private fun applyDotAccent(dotColor: Int): Drawable? {
        val circleDrawable =
            ContextCompat.getDrawable(requireContext(), R.drawable.ic_rating_privacy_circle)

        circleDrawable?.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
            dotColor,
            BlendModeCompat.SRC_ATOP
        )

        return circleDrawable
    }
}
