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

package foundation.e.apps.home.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.findNavController
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerDrawable
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import foundation.e.apps.AppInfoFetchViewModel
import foundation.e.apps.MainActivityViewModel
import foundation.e.apps.R
import foundation.e.apps.api.cleanapk.CleanAPKInterface
import foundation.e.apps.api.fused.FusedAPIInterface
import foundation.e.apps.api.fused.data.FusedApp
import foundation.e.apps.databinding.HomeChildListItemBinding
import foundation.e.apps.home.HomeFragmentDirections
import foundation.e.apps.utils.disableInstallButton
import foundation.e.apps.utils.enableInstallButton
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.User

class HomeChildRVAdapter(
    private var fusedAPIInterface: FusedAPIInterface?,
    private val appInfoFetchViewModel: AppInfoFetchViewModel,
    private val mainActivityViewModel: MainActivityViewModel,
    private var lifecycleOwner: LifecycleOwner?,
    private var paidAppHandler: ((FusedApp) -> Unit)? = null
) : ListAdapter<FusedApp, HomeChildRVAdapter.ViewHolder>(HomeChildFusedAppDiffUtil()) {

    private val shimmer = Shimmer.ColorHighlightBuilder()
        .setDuration(500)
        .setBaseAlpha(0.7f)
        .setDirection(Shimmer.Direction.LEFT_TO_RIGHT)
        .setHighlightAlpha(0.6f)
        .setAutoStart(true)
        .build()

    inner class ViewHolder(val binding: HomeChildListItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewHolder = ViewHolder(
            HomeChildListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val view = holder.itemView
        val homeApp = getItem(position)
        val shimmerDrawable = ShimmerDrawable().apply { setShimmer(shimmer) }

        holder.binding.apply {
            if (homeApp.origin == Origin.CLEANAPK) {
                appIcon.load(CleanAPKInterface.ASSET_URL + homeApp.icon_image_path) {
                    placeholder(shimmerDrawable)
                }
            } else {
                appIcon.load(homeApp.icon_image_path) {
                    placeholder(shimmerDrawable)
                }
            }
            appName.text = homeApp.name
            homeLayout.setOnClickListener {
                val action = HomeFragmentDirections.actionHomeFragmentToApplicationFragment(
                    homeApp._id,
                    homeApp.package_name,
                    homeApp.origin,
                    homeApp.category
                )
                holder.itemView.findNavController().navigate(action)
            }

            when (homeApp.status) {
                Status.INSTALLED -> {
                    handleInstalled(homeApp)
                }
                Status.UPDATABLE -> {
                    handleUpdatable(homeApp)
                }
                Status.UNAVAILABLE -> {
                    handleUnavailable(homeApp, holder)
                }
                Status.QUEUED, Status.AWAITING, Status.DOWNLOADING, Status.DOWNLOADED -> {
                    handleQueued(homeApp)
                }
                Status.INSTALLING -> {
                    handleInstalling()
                }
                Status.BLOCKED -> {
                    handleBlocked()
                }
                Status.INSTALLATION_ISSUE -> {
                    handleInstallationIssue(homeApp)
                }
            }
        }
    }

    private fun HomeChildListItemBinding.handleInstallationIssue(
        homeApp: FusedApp
    ) {
        installButton.apply {
            enableInstallButton()
            text = context.getString(R.string.retry)
            setOnClickListener {
                installApplication(homeApp, appIcon)
            }
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun HomeChildListItemBinding.handleBlocked() {
        val view = this.root
        installButton.enableInstallButton()
        installButton.setOnClickListener {
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
        progressBarInstall.visibility = View.GONE
    }

    private fun HomeChildListItemBinding.handleInstalling() {
        installButton.apply {
            disableInstallButton()
            text = context.getString(R.string.installing)
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun HomeChildListItemBinding.handleQueued(
        homeApp: FusedApp
    ) {
        installButton.apply {
            enableInstallButton()
            text = context.getString(R.string.cancel)
            setTextColor(context.getColor(R.color.colorAccent))
            backgroundTintList = ContextCompat.getColorStateList(
                context,
                android.R.color.transparent
            )
            strokeColor =
                ContextCompat.getColorStateList(context, R.color.colorAccent)

            setOnClickListener {
                cancelDownload(homeApp)
            }
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun HomeChildListItemBinding.handleUnavailable(
        homeApp: FusedApp,
        holder: ViewHolder,
    ) {
        installButton.apply {
            updateUIByPaymentType(homeApp, this, holder.binding)
            setOnClickListener {
                if (mainActivityViewModel.checkUnsupportedApplication(homeApp, context)) {
                    return@setOnClickListener
                }
                if (homeApp.isFree) {
                    disableInstallButton()
                    text = context.getString(R.string.cancel)
                    installApplication(homeApp, appIcon)
                } else {
                    paidAppHandler?.invoke(homeApp)
                }
            }
        }
    }

    private fun HomeChildListItemBinding.handleUpdatable(
        homeApp: FusedApp
    ) {
        installButton.apply {
            enableInstallButton(Status.UPDATABLE)
            text = if (mainActivityViewModel.checkUnsupportedApplication(homeApp))
                context.getString(R.string.not_available)
            else context.getString(R.string.update)
            setOnClickListener {
                if (mainActivityViewModel.checkUnsupportedApplication(homeApp, context)) {
                    return@setOnClickListener
                }
                installApplication(homeApp, appIcon)
            }
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun HomeChildListItemBinding.handleInstalled(
        homeApp: FusedApp
    ) {
        installButton.apply {
            enableInstallButton(Status.INSTALLED)
            text = context.getString(R.string.open)
            setOnClickListener {
                if (homeApp.is_pwa) {
                    mainActivityViewModel.launchPwa(homeApp)
                } else {
                    context.startActivity(mainActivityViewModel.getLaunchIntentForPackageName(homeApp.package_name))
                }
            }
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun updateUIByPaymentType(
        homeApp: FusedApp,
        materialButton: MaterialButton,
        homeChildListItemBinding: HomeChildListItemBinding
    ) {
        when {
            mainActivityViewModel.checkUnsupportedApplication(homeApp) -> {
                materialButton.enableInstallButton()
                materialButton.text = materialButton.context.getString(R.string.not_available)
            }
            homeApp.isFree -> {
                materialButton.enableInstallButton()
                materialButton.text = materialButton.context.getString(R.string.install)
                homeChildListItemBinding.progressBarInstall.visibility = View.GONE
            }
            else -> {
                materialButton.disableInstallButton()
                materialButton.text = ""
                homeChildListItemBinding.progressBarInstall.visibility = View.VISIBLE
                lifecycleOwner?.let {
                    appInfoFetchViewModel.isAppPurchased(homeApp).observe(it) {
                        homeChildListItemBinding.progressBarInstall.visibility = View.GONE
                        materialButton.enableInstallButton()
                        materialButton.text =
                            if (it) materialButton.context.getString(R.string.install) else homeApp.price
                    }
                }
            }
        }
    }

    fun setData(newList: List<FusedApp>) {
        this.submitList(newList.map { it.copy() })
    }

    private fun installApplication(homeApp: FusedApp, appIcon: ImageView) {
        fusedAPIInterface?.getApplication(homeApp, appIcon)
    }

    private fun cancelDownload(homeApp: FusedApp) {
        fusedAPIInterface?.cancelDownload(homeApp)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        lifecycleOwner = null
        paidAppHandler = null
        fusedAPIInterface = null
    }
}
