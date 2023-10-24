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

package foundation.e.apps.ui.categories.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import foundation.e.apps.data.fused.data.Category
import foundation.e.apps.databinding.CategoriesListItemBinding
import foundation.e.apps.ui.categories.CategoriesFragmentDirections

class CategoriesRVAdapter :
    RecyclerView.Adapter<CategoriesRVAdapter.ViewHolder>() {

    private var oldList = listOf<Category>()

    inner class ViewHolder(val binding: CategoriesListItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            CategoriesListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.apply {
            categoryLayout.setOnClickListener {
                val direction =
                    CategoriesFragmentDirections.actionCategoriesFragmentToApplicationListFragment(
                        oldList[position].id,
                        oldList[position].title,
                        oldList[position].tag.getOperationalTag(),
                        oldList[position].browseUrl
                    )
                holder.itemView.findNavController().navigate(direction)
            }
            loadCategoryIcon(position)
            categoryTitle.text = oldList[position].title
            updateTag(position)
        }
    }

    private fun CategoriesListItemBinding.loadCategoryIcon(position: Int) {
        if (oldList[position].drawable != -1) {
            categoryIcon.load(oldList[position].drawable)
        } else {
            categoryIcon.load(oldList[position].imageUrl)
        }
    }

    private fun CategoriesListItemBinding.updateTag(position: Int) {
        val tag = oldList[position].tag
        if (tag.displayTag.isNotBlank()) {
            categoryTag.visibility = View.VISIBLE
            categoryTag.text = tag.displayTag
        } else {
            categoryTag.visibility = View.INVISIBLE
            categoryTag.text = ""
        }
    }

    override fun getItemCount(): Int {
        return oldList.size
    }

    fun setData(newList: List<Category>) {
        val diffUtil = CategoriesDiffUtil(oldList, newList)
        val diffResult = DiffUtil.calculateDiff(diffUtil)
        oldList = newList
        diffResult.dispatchUpdatesTo(this)
    }
}
