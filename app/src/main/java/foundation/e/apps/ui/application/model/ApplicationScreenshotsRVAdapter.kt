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

package foundation.e.apps.ui.application.model

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.databinding.ApplicationScreenshotsListItemBinding
import foundation.e.apps.ui.application.ApplicationFragmentDirections

class ApplicationScreenshotsRVAdapter(
    private val origin: Origin
) :
    RecyclerView.Adapter<ApplicationScreenshotsRVAdapter.ViewHolder>() {

    private var oldList = emptyList<String>()

    inner class ViewHolder(val binding: ApplicationScreenshotsListItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ApplicationScreenshotsListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageView = holder.binding.imageView
        when (origin) {
            Origin.CLEANAPK -> {
                imageView.load(CleanApkRetrofit.ASSET_URL + oldList[position])
            }
            Origin.GPLAY -> {
                imageView.load(oldList[position])
            }
            else -> {}
        }
        imageView.setOnClickListener {
            val action =
                ApplicationFragmentDirections.actionApplicationFragmentToScreenshotFragment(
                    oldList.toTypedArray(),
                    position,
                    origin
                )
            it.findNavController().navigate(action)
        }
    }

    override fun getItemCount(): Int {
        return oldList.size
    }

    fun setData(newList: List<String>) {
        val diffUtil = ApplicationScreenshotsDiffUtil(oldList, newList)
        val diffResult = DiffUtil.calculateDiff(diffUtil)
        oldList = newList
        diffResult.dispatchUpdatesTo(this)
    }
}
