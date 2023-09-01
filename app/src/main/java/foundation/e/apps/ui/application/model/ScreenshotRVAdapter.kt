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

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import coil.load
import foundation.e.apps.R
import foundation.e.apps.data.cleanapk.CleanApkRetrofit
import foundation.e.apps.data.enums.Origin
import foundation.e.apps.databinding.ScreenshotListItemBinding

class ScreenshotRVAdapter(private val list: List<String>, private val origin: Origin) :
    RecyclerView.Adapter<ScreenshotRVAdapter.ViewHolder>() {

    private lateinit var circularProgressDrawable: CircularProgressDrawable

    inner class ViewHolder(val binding: ScreenshotListItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Setup progress drawable for coil placeholder
        circularProgressDrawable = CircularProgressDrawable(parent.context)
        circularProgressDrawable.strokeWidth = 10f
        circularProgressDrawable.centerRadius = 50f
        circularProgressDrawable.colorFilter = PorterDuffColorFilter(
            parent.context.getColor(R.color.colorAccent),
            PorterDuff.Mode.SRC_IN
        )

        return ViewHolder(
            ScreenshotListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageView = holder.binding.imageView
        when (origin) {
            Origin.CLEANAPK -> {
                imageView.load(CleanApkRetrofit.ASSET_URL + list[position]) {
                    placeholder(circularProgressDrawable)
                }
            }
            Origin.GPLAY -> {
                imageView.load(list[position]) {
                    placeholder(circularProgressDrawable)
                }
            }

            else -> {}
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }
}
