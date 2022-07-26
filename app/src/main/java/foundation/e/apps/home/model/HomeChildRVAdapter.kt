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

import android.graphics.Color
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
import foundation.e.apps.utils.enums.Origin
import foundation.e.apps.utils.enums.Status
import foundation.e.apps.utils.enums.User
import timber.log.Timber

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
                    homeApp.origin
                )
                holder.itemView.findNavController().navigate(action)
            }

            when (homeApp.status) {
                Status.INSTALLED -> {
                    handleInstalled(view, homeApp)
                }
                Status.UPDATABLE -> {
                    handleUpdatable(view, homeApp)
                }
                Status.UNAVAILABLE -> {
                    handleUnavailable(homeApp, holder, view)
                }
                Status.QUEUED, Status.AWAITING, Status.DOWNLOADING, Status.DOWNLOADED -> {
                    handleQueued(view, homeApp)
                }
                Status.INSTALLING, Status.UNINSTALLING -> {
                    handleInstalling(view)
                }
                Status.BLOCKED -> {
                    handleBlocked(view)
                }
                Status.INSTALLATION_ISSUE -> {
                    handleInstallationIssue(view, homeApp)
                }
            }
        }
    }

    private fun HomeChildListItemBinding.handleInstallationIssue(
        view: View,
        homeApp: FusedApp
    ) {
        installButton.apply {
            text = view.context.getString(R.string.retry)
            setTextColor(context.getColor(R.color.colorAccent))
            backgroundTintList = ContextCompat.getColorStateList(
                view.context,
                android.R.color.transparent
            )
            strokeColor =
                ContextCompat.getColorStateList(view.context, R.color.colorAccent)
            setOnClickListener {
                installApplication(homeApp, appIcon)
            }
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun HomeChildListItemBinding.handleBlocked(view: View) {
        installButton.setOnClickListener {
            val errorMsg = when (mainActivityViewModel.getUser()) {
                User.ANONYMOUS,
                User.UNAVAILABLE -> view.context.getString(R.string.install_blocked_anonymous)
                User.GOOGLE -> view.context.getString(R.string.install_blocked_google)
            }
            if (errorMsg.isNotBlank()) {
                Snackbar.make(view, errorMsg, Snackbar.LENGTH_SHORT).show()
            }
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun HomeChildListItemBinding.handleInstalling(view: View) {
        installButton.apply {
            isEnabled = false
            setTextColor(context.getColor(R.color.light_grey))
            text = context.getString(R.string.installing)
            backgroundTintList = ContextCompat.getColorStateList(
                view.context,
                android.R.color.transparent
            )
            strokeColor =
                ContextCompat.getColorStateList(view.context, R.color.light_grey)
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun HomeChildListItemBinding.handleQueued(
        view: View,
        homeApp: FusedApp
    ) {
        installButton.apply {
            text = context.getString(R.string.cancel)
            setTextColor(context.getColor(R.color.colorAccent))
            backgroundTintList = ContextCompat.getColorStateList(
                view.context,
                android.R.color.transparent
            )
            strokeColor =
                ContextCompat.getColorStateList(view.context, R.color.colorAccent)

            setOnClickListener {
                cancelDownload(homeApp)
            }
        }
        progressBarInstall.visibility = View.GONE
    }

    private fun HomeChildListItemBinding.handleUnavailable(
        homeApp: FusedApp,
        holder: ViewHolder,
        view: View
    ) {
        installButton.apply {
            updateUIByPaymentType(homeApp, this, holder.binding)
            setTextColor(context.getColor(R.color.colorAccent))
            backgroundTintList = ContextCompat.getColorStateList(
                view.context,
                android.R.color.transparent
            )
            strokeColor =
                ContextCompat.getColorStateList(view.context, R.color.colorAccent)
            setOnClickListener {
                if (mainActivityViewModel.checkUnsupportedApplication(homeApp, context)) {
                    return@setOnClickListener
                }
                if (homeApp.isFree) {
                    installApplication(homeApp, appIcon)
                } else {
                    paidAppHandler?.invoke(homeApp)
                }
            }
        }
    }

    private fun HomeChildListItemBinding.handleUpdatable(
        view: View,
        homeApp: FusedApp
    ) {
        installButton.apply {
            text = if (mainActivityViewModel.checkUnsupportedApplication(homeApp))
                context.getString(R.string.not_available)
            else context.getString(R.string.update)
            setTextColor(Color.WHITE)
            backgroundTintList =
                ContextCompat.getColorStateList(view.context, R.color.colorAccent)
            strokeColor =
                ContextCompat.getColorStateList(view.context, R.color.colorAccent)
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
        view: View,
        homeApp: FusedApp
    ) {
        installButton.apply {
            isEnabled = true
            text = context.getString(R.string.open)
            setTextColor(Color.WHITE)
            backgroundTintList =
                ContextCompat.getColorStateList(view.context, R.color.colorAccent)
            strokeColor =
                ContextCompat.getColorStateList(view.context, R.color.colorAccent)
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
                materialButton.isEnabled = true
                materialButton.text = materialButton.context.getString(R.string.not_available)
            }
            homeApp.isFree -> {
                materialButton.isEnabled = true
                materialButton.text = materialButton.context.getString(R.string.install)
                homeChildListItemBinding.progressBarInstall.visibility = View.GONE
            }
            else -> {
                materialButton.isEnabled = false
                materialButton.text = ""
                homeChildListItemBinding.progressBarInstall.visibility = View.VISIBLE
                lifecycleOwner?.let {
                    appInfoFetchViewModel.isAppPurchased(homeApp).observe(it) {
                        materialButton.isEnabled = true
                        homeChildListItemBinding.progressBarInstall.visibility = View.GONE
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
