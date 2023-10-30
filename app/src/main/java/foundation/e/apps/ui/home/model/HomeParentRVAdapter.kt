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

package foundation.e.apps.ui.home.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import foundation.e.apps.data.application.ApplicationInstaller
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.data.application.data.Home
import foundation.e.apps.databinding.HomeParentListItemBinding
import foundation.e.apps.ui.AppInfoFetchViewModel
import foundation.e.apps.ui.MainActivityViewModel

class HomeParentRVAdapter(
    private val applicationInstaller: ApplicationInstaller,
    private val mainActivityViewModel: MainActivityViewModel,
    private val appInfoFetchViewModel: AppInfoFetchViewModel,
    private var lifecycleOwner: LifecycleOwner?,
    private val paidAppHandler: ((Application) -> Unit)? = null
) : ListAdapter<Home, HomeParentRVAdapter.ViewHolder>(FusedHomeDiffUtil()) {

    private val viewPool = RecyclerView.RecycledViewPool()
    private var isDetachedFromRecyclerView = false

    inner class ViewHolder(val binding: HomeParentListItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            HomeParentListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fusedHome = getItem(position)

        holder.binding.titleTV.text = fusedHome.title
        handleChildShimmerView(fusedHome, holder)

        if (fusedHome.list.isEmpty()) {
            return
        }

        val homeChildRVAdapter =
            HomeChildRVAdapter(
                applicationInstaller,
                appInfoFetchViewModel,
                mainActivityViewModel,
                lifecycleOwner,
                paidAppHandler
            )

        homeChildRVAdapter.setData(fusedHome.list)

        holder.binding.childRV.apply {
            recycledViewPool.setMaxRecycledViews(0, 0)
            adapter = homeChildRVAdapter
            layoutManager =
                LinearLayoutManager(
                    holder.binding.root.context,
                    LinearLayoutManager.HORIZONTAL,
                    false
                )
            setRecycledViewPool(viewPool)
        }

        observeAppInstall(fusedHome, homeChildRVAdapter)
    }

    private fun handleChildShimmerView(home: Home, holder: ViewHolder) {
        if (home.list.isEmpty()) {
            holder.binding.shimmerLayout.visibility = View.VISIBLE
            holder.binding.shimmerLayout.startShimmer()
            holder.binding.childRV.visibility = View.GONE
            return
        }

        holder.binding.shimmerLayout.visibility = View.GONE
        holder.binding.shimmerLayout.stopShimmer()
        holder.binding.childRV.visibility = View.VISIBLE
    }

    private fun observeAppInstall(
        home: Home,
        homeChildRVAdapter: RecyclerView.Adapter<*>?
    ) {
        lifecycleOwner?.let {
            mainActivityViewModel.downloadList.observe(it) {
                mainActivityViewModel.updateStatusOfFusedApps(home.list, it)
                (homeChildRVAdapter as HomeChildRVAdapter).setData(home.list)
            }
        }
    }

    fun setData(newList: List<Home>) {
        submitList(newList.map { it.copy() })
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        isDetachedFromRecyclerView = true
        lifecycleOwner = null
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (isDetachedFromRecyclerView) {
            holder.binding.childRV.adapter = null
        }
    }
}
