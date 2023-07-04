/*
 *  Copyright (C) 2022  ECORP
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.ui.applicationlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.navigation.findNavController
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.Shimmer.Direction.LEFT_TO_RIGHT
import com.facebook.shimmer.ShimmerDrawable
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import foundation.e.apps.R
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.data.enums.Status
import foundation.e.apps.data.enums.User
import foundation.e.apps.data.fused.FusedAPIInterface
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.databinding.ApplicationListItemBinding
import foundation.e.apps.install.pkg.InstallerService
import foundation.e.apps.ui.AppInfoFetchViewModel
import foundation.e.apps.ui.MainActivityViewModel
import foundation.e.apps.ui.PrivacyInfoViewModel
import foundation.e.apps.ui.search.SearchFragmentDirections
import foundation.e.apps.ui.updates.UpdatesFragmentDirections
import foundation.e.apps.utils.disableInstallButton
import foundation.e.apps.utils.enableInstallButton
import timber.log.Timber
import javax.inject.Singleton

@Singleton
class ApplicationListRVAdapter(
    private val fusedAPIInterface: FusedAPIInterface,
    private val privacyInfoViewModel: PrivacyInfoViewModel,
    private val appInfoFetchViewModel: AppInfoFetchViewModel,
    private val mainActivityViewModel: MainActivityViewModel,
    private val currentDestinationId: Int,
    private var lifecycleOwner: LifecycleOwner?,
    private var paidAppHandler: ((FusedApp) -> Unit)? = null
) : ListAdapter<FusedApp, ApplicationListRVAdapter.ViewHolder>(ApplicationDiffUtil()) {

    private var optionalCategory = ""

    private val shimmer = Shimmer.ColorHighlightBuilder()
        .setDuration(500)
        .setBaseAlpha(0.7f)
        .setDirection(LEFT_TO_RIGHT)
        .setHighlightAlpha(0.6f)
        .setAutoStart(true)
        .build()

    var onPlaceHolderShow: (() -> Unit)? = null

    inner class ViewHolder(val binding: ApplicationListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var isPurchasedLiveData: LiveData<Boolean> = MutableLiveData()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ApplicationListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val view = holder.itemView
        val searchApp = getItem(position)
        val shimmerDrawable = ShimmerDrawable().apply { setShimmer(shimmer) }

        /*
         * A placeholder entry is one where we only show a loading progress bar,
         * instead of an app entry.
         * It is usually done to signify more apps are being loaded at the end of the list.
         *
         * We hide all view elements other than the circular progress bar.
         *
         * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5131 [2]
         */
        if (searchApp.isPlaceHolder) {
            val progressBar = holder.binding.placeholderProgressBar
            holder.binding.root.children.forEach {
                it.visibility = if (it != progressBar) View.INVISIBLE
                else View.VISIBLE
            }
            onPlaceHolderShow?.invoke()
            // Do not process anything else for this entry
            return
        }

        holder.binding.apply {
            if (searchApp.privacyScore == -1) {
                hidePrivacyScore()
            }
            applicationList.setOnClickListener {
                handleAppItemClick(searchApp, view)
            }
            updateAppInfo(searchApp)
            updateRating(searchApp)
            updatePrivacyScore(searchApp, view)
            updateSourceTag(searchApp)
            setAppIcon(searchApp, shimmerDrawable)
            removeIsPurchasedObserver(holder)

            setInstallButtonDimensions(view)

            if (appInfoFetchViewModel.isAppInBlockedList(searchApp)) {
                setupShowMoreButton()
            } else {
                mainActivityViewModel.verifyUiFilter(searchApp) {
                    setupInstallButton(searchApp, view, holder)
                }
            }

            showCalculatedPrivacyScoreData(searchApp, view)
        }
    }

    private fun ApplicationListItemBinding.setInstallButtonDimensions(item: View) {
        item.post {
            val maxAllowedWidth = item.measuredWidth / 2
            installButton.apply {
                if (width > maxAllowedWidth)
                    width = maxAllowedWidth
            }
        }
    }

    private fun ApplicationListItemBinding.setAppIcon(
        searchApp: FusedApp,
        shimmerDrawable: ShimmerDrawable
    ) {
        when (searchApp.origin) {
            Origin.GPLAY -> {
                appIcon.load(searchApp.icon_image_path) {
                    placeholder(shimmerDrawable)
                }
            }
            Origin.CLEANAPK -> {
                appIcon.load(CleanApkRetrofit.ASSET_URL + searchApp.icon_image_path) {
                    placeholder(shimmerDrawable)
                }
            }
            else -> Timber.wtf("${searchApp.package_name} is from an unknown origin")
        }
    }

    private fun ApplicationListItemBinding.updateAppInfo(searchApp: FusedApp) {
        appTitle.text = searchApp.name
        appInfoFetchViewModel.getAuthorName(searchApp).observe(lifecycleOwner!!) {
            appAuthor.text = it
        }
    }

    private fun ApplicationListItemBinding.updateRating(searchApp: FusedApp) {
        if (searchApp.ratings.usageQualityScore != -1.0) {
            appRating.text = searchApp.ratings.usageQualityScore.toString()
        } else {
            appRating.text = root.context.getString(R.string.not_available)
        }
    }

    private fun ApplicationListItemBinding.updatePrivacyScore(
        searchApp: FusedApp,
        view: View
    ) {
        if (searchApp.ratings.privacyScore != -1.0) {
            appPrivacyScore.text = view.context.getString(
                R.string.privacy_rating_out_of,
                searchApp.ratings.privacyScore.toInt().toString()
            )
        }
    }

    private fun ApplicationListItemBinding.updateSourceTag(searchApp: FusedApp) {
        if (searchApp.source.isEmpty()) {
            sourceTag.visibility = View.INVISIBLE
        } else {
            sourceTag.visibility = View.VISIBLE
        }
        sourceTag.text = searchApp.source
    }

    private fun handleAppItemClick(
        searchApp: FusedApp,
        view: View
    ) {
        val catText = searchApp.category.ifBlank { optionalCategory }
        val action = when (currentDestinationId) {
            R.id.applicationListFragment -> {
                ApplicationListFragmentDirections.actionApplicationListFragmentToApplicationFragment(
                    searchApp.package_name,
                    searchApp._id,
                    searchApp.origin,
                    catText,
                    searchApp.isGplayReplaced
                )
            }
            R.id.searchFragment -> {
                SearchFragmentDirections.actionSearchFragmentToApplicationFragment(
                    searchApp.package_name,
                    searchApp._id,
                    searchApp.origin,
                    catText,
                    searchApp.isGplayReplaced
                )
            }
            R.id.updatesFragment -> {
                UpdatesFragmentDirections.actionUpdatesFragmentToApplicationFragment(
                    searchApp.package_name,
                    searchApp._id,
                    searchApp.origin,
                    catText,
                    searchApp.isGplayReplaced
                )
            }
            else -> null
        }
        action?.let { direction -> view.findNavController().navigate(direction) }
    }

    private fun removeIsPurchasedObserver(holder: ViewHolder) {
        lifecycleOwner?.let {
            holder.isPurchasedLiveData.removeObservers(it)
        }
    }

    private fun ApplicationListItemBinding.setupInstallButton(
        searchApp: FusedApp,
        view: View,
        holder: ViewHolder
    ) {
        installButton.visibility = View.VISIBLE
        showMore.visibility = View.INVISIBLE
        when (searchApp.status) {
            Status.INSTALLED -> {
                handleInstalled(searchApp)
            }
            Status.UPDATABLE -> {
                handleUpdatable(searchApp)
            }
            Status.UNAVAILABLE -> {
                handleUnavailable(searchApp, holder)
            }
            Status.QUEUED, Status.AWAITING, Status.DOWNLOADING, Status.DOWNLOADED -> {
                handleDownloading(searchApp)
            }
            Status.INSTALLING -> {
                handleInstalling()
            }
            Status.BLOCKED -> {
                handleBlocked(view)
            }
            Status.INSTALLATION_ISSUE -> {
                handleInstallationIssue(view, searchApp)
            }
            else -> {
                Timber.w("ApplicationListRVAdapter: Unknown status")
            }
        }
    }

    private fun ApplicationListItemBinding.setupShowMoreButton() {
        installButton.visibility = View.INVISIBLE
        showMore.visibility = View.VISIBLE
        progressBarInstall.visibility = View.GONE
    }

    private fun ApplicationListItemBinding.handleInstallationIssue(
        view: View,
        searchApp: FusedApp,
    ) {
        progressBarInstall.visibility = View.GONE
        if (lifecycleOwner == null) {
            return
        }

        appInfoFetchViewModel.isAppFaulty(searchApp).observe(lifecycleOwner!!) {
            updateInstallButton(it, view, searchApp)
        }
    }

    private fun ApplicationListItemBinding.updateInstallButton(
        faultyAppResult: Pair<Boolean, String>,
        view: View,
        searchApp: FusedApp
    ) {
        installButton.apply {
            if (faultyAppResult.first) disableInstallButton() else enableInstallButton()
            text = getInstallationIssueText(faultyAppResult, view)
            backgroundTintList =
                ContextCompat.getColorStateList(view.context, android.R.color.transparent)
            setOnClickListener {
                installApplication(searchApp, appIcon)
            }
        }
    }

    private fun MaterialButton.getInstallationIssueText(
        faultyAppResult: Pair<Boolean, String>,
        view: View
    ) =
        if (faultyAppResult.second.contentEquals(InstallerService.INSTALL_FAILED_UPDATE_INCOMPATIBLE))
            view.context.getText(R.string.update)
        else
            view.context.getString(R.string.retry)

    private fun ApplicationListItemBinding.handleBlocked(view: View) {
        installButton.apply {
            isEnabled = true
            setOnClickListener {
                val errorMsg = when (mainActivityViewModel.getUser()) {
                    User.ANONYMOUS,
                    User.NO_GOOGLE,
                    User.UNAVAILABLE -> view.context.getString(R.string.install_blocked_anonymous)
                    User.GOOGLE -> view.context.getString(R.string.install_blocked_google)
                }
                if (errorMsg.isNotBlank()) {
                    Snackbar.make(view, errorMsg, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun ApplicationListItemBinding.showCalculatedPrivacyScoreData(
        searchApp: FusedApp,
        view: View
    ) {
        if (searchApp.privacyScore > -1) {
            showPrivacyScoreOnAvailableData(searchApp, view)
        } else {
            showPrivacyScoreAfterFetching(searchApp, view)
        }
    }

    private fun ApplicationListItemBinding.showPrivacyScoreOnAvailableData(
        searchApp: FusedApp,
        view: View
    ) {
        showPrivacyScore()
        appPrivacyScore.text = view.context.getString(
            R.string.privacy_rating_out_of,
            searchApp.privacyScore.toString()
        )
    }

    private fun ApplicationListItemBinding.showPrivacyScoreAfterFetching(
        searchApp: FusedApp,
        view: View
    ) {
        if (lifecycleOwner == null) {
            return
        }
        privacyInfoViewModel.getAppPrivacyInfoLiveData(searchApp).observe(lifecycleOwner!!) {
            showPrivacyScore()
            val calculatedScore = privacyInfoViewModel.getPrivacyScore(searchApp)
            searchApp.privacyScore = calculatedScore
            if (it.isSuccess() && calculatedScore != -1) {
                appPrivacyScore.text = view.context.getString(
                    R.string.privacy_rating_out_of,
                    searchApp.privacyScore.toString()
                )
            } else {
                appPrivacyScore.text = view.context.getString(R.string.not_available)
            }
        }
    }

    private fun ApplicationListItemBinding.hidePrivacyScore() {
        progressBar.visibility = View.VISIBLE
        appPrivacyScore.visibility = View.GONE
    }

    private fun ApplicationListItemBinding.showPrivacyScore() {
        progressBar.visibility = View.GONE
        appPrivacyScore.visibility = View.VISIBLE
    }

    private fun ApplicationListItemBinding.handleInstalling() {
        installButton.apply {
            disableInstallButton()
            text = context.getText(R.string.installing)
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun ApplicationListItemBinding.handleDownloading(
        searchApp: FusedApp,
    ) {
        installButton.apply {
            enableInstallButton()
            text = context.getString(R.string.cancel)
            setOnClickListener {
                cancelDownload(searchApp)
            }
            progressBarInstall.visibility = View.GONE
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun ApplicationListItemBinding.handleUnavailable(
        searchApp: FusedApp,
        holder: ViewHolder,
    ) {
        installButton.apply {
            updateUIByPaymentType(searchApp, this, this@handleUnavailable, holder)
            setOnClickListener {
                if (mainActivityViewModel.checkUnsupportedApplication(searchApp, context)) {
                    return@setOnClickListener
                }
                if (searchApp.isFree || searchApp.isPurchased) {
                    disableInstallButton()
                    text = context.getText(R.string.cancel)
                    installApplication(searchApp, appIcon)
                } else {
                    paidAppHandler?.invoke(searchApp)
                }
            }
        }
    }

    private fun updateUIByPaymentType(
        searchApp: FusedApp,
        materialButton: MaterialButton,
        applicationListItemBinding: ApplicationListItemBinding,
        holder: ViewHolder
    ) {
        when {
            mainActivityViewModel.checkUnsupportedApplication(searchApp) -> {
                materialButton.enableInstallButton()
                materialButton.text = materialButton.context.getString(R.string.not_available)
                applicationListItemBinding.progressBarInstall.visibility = View.GONE
            }
            searchApp.isFree -> {
                materialButton.enableInstallButton()
                materialButton.text = materialButton.context.getString(R.string.install)
                materialButton.strokeColor =
                    ContextCompat.getColorStateList(holder.itemView.context, R.color.light_grey)
                applicationListItemBinding.progressBarInstall.visibility = View.GONE
            }
            else -> {
                materialButton.disableInstallButton()
                materialButton.text = ""
                applicationListItemBinding.progressBarInstall.visibility = View.VISIBLE
                holder.isPurchasedLiveData = appInfoFetchViewModel.isAppPurchased(searchApp)
                if (lifecycleOwner == null) {
                    return
                }
                holder.isPurchasedLiveData.observe(lifecycleOwner!!) {
                    materialButton.enableInstallButton()
                    applicationListItemBinding.progressBarInstall.visibility = View.GONE
                    materialButton.text =
                        if (it) materialButton.context.getString(R.string.install) else searchApp.price
                }
            }
        }
    }

    private fun ApplicationListItemBinding.handleUpdatable(
        searchApp: FusedApp
    ) {
        installButton.apply {
            enableInstallButton(Status.UPDATABLE)
            text = if (mainActivityViewModel.checkUnsupportedApplication(searchApp))
                context.getString(R.string.not_available)
            else context.getString(R.string.update)
            setOnClickListener {
                if (mainActivityViewModel.checkUnsupportedApplication(searchApp, context)) {
                    return@setOnClickListener
                }
                installApplication(searchApp, appIcon)
            }
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun ApplicationListItemBinding.handleInstalled(
        searchApp: FusedApp,
    ) {
        installButton.apply {
            enableInstallButton(Status.INSTALLED)
            text = context.getString(R.string.open)
            setOnClickListener {
                if (searchApp.is_pwa) {
                    mainActivityViewModel.launchPwa(searchApp)
                } else {
                    context.startActivity(
                        mainActivityViewModel.getLaunchIntentForPackageName(
                            searchApp.package_name
                        )
                    )
                }
            }
        }
        progressBarInstall.visibility = View.GONE
    }

    fun setData(newList: List<FusedApp>, optionalCategory: String? = null) {
        optionalCategory?.let {
            this.optionalCategory = it
        }
        currentList.forEach {
            newList.find { item -> item._id == it._id }?.let { foundItem ->
                foundItem.privacyScore = it.privacyScore
            }
        }
        this.submitList(newList.map { it.copy() })
    }

    private fun installApplication(searchApp: FusedApp, appIcon: ImageView) {
        fusedAPIInterface.getApplication(searchApp, appIcon)
    }

    private fun cancelDownload(searchApp: FusedApp) {
        fusedAPIInterface.cancelDownload(searchApp)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        lifecycleOwner = null
        paidAppHandler = null
    }
}
