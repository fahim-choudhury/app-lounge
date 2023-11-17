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
package foundation.e.apps.ui.applicationlist

import androidx.recyclerview.widget.DiffUtil
import foundation.e.apps.data.application.data.Application
import foundation.e.apps.di.CommonUtilsModule.LIST_OF_NULL

class ApplicationDiffUtil : DiffUtil.ItemCallback<Application>() {
    override fun areItemsTheSame(oldItem: Application, newItem: Application): Boolean {
        return oldItem._id == newItem._id
    }

    override fun areContentsTheSame(oldItem: Application, newItem: Application): Boolean {
        return oldItem == newItem
    }
}
