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
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import foundation.e.apps.ui.AppInfoFetchViewModel
import foundation.e.apps.ui.MainActivityViewModel
import foundation.e.apps.data.fused.FusedAPIInterface
import foundation.e.apps.data.fused.data.FusedApp
import foundation.e.apps.data.fused.data.FusedHome
import foundation.e.apps.databinding.HomeParentListItemBinding

class HomeParentRVAdapter(
    private val fusedAPIInterface: FusedAPIInterface,
    private val mainActivityViewModel: MainActivityViewModel,
    private val appInfoFetchViewModel: AppInfoFetchViewModel,
    private var lifecycleOwner: LifecycleOwner?,
    private val paidAppHandler: ((FusedApp) -> Unit)? = null
) : ListAdapter<FusedHome, HomeParentRVAdapter.ViewHolder>(FusedHomeDiffUtil()) {

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
        val homeChildRVAdapter =
            HomeChildRVAdapter(
                fusedAPIInterface,
                appInfoFetchViewModel,
                mainActivityViewModel,
                lifecycleOwner,
                paidAppHandler
            )
        homeChildRVAdapter.setData(fusedHome.list)

        holder.binding.titleTV.text = fusedHome.title

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

    private fun observeAppInstall(
        fusedHome: FusedHome,
        homeChildRVAdapter: RecyclerView.Adapter<*>?
    ) {
        lifecycleOwner?.let {
            mainActivityViewModel.downloadList.observe(it) {
                mainActivityViewModel.updateStatusOfFusedApps(fusedHome.list, it)
                (homeChildRVAdapter as HomeChildRVAdapter).setData(fusedHome.list)
            }
        }
    }

    fun setData(newList: List<FusedHome>) {
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
