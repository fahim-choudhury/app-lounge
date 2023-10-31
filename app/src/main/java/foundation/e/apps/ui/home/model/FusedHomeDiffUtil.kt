/*
 * Apps  Quickly and easily install Android apps onto your device!
 * Copyright (C) 2022  E FOUNDATION
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

import androidx.recyclerview.widget.DiffUtil
import foundation.e.apps.data.application.data.Home

class FusedHomeDiffUtil : DiffUtil.ItemCallback<Home>() {
    override fun areItemsTheSame(oldItem: Home, newItem: Home): Boolean {
        return oldItem.list == newItem.list
    }

    override fun areContentsTheSame(oldItem: Home, newItem: Home): Boolean {
        return oldItem.title.contentEquals(newItem.title) &&
            oldItem.list == newItem.list
    }
}
