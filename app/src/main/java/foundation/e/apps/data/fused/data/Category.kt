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

package foundation.e.apps.data.fused.data

import foundation.e.apps.data.enums.AppTag
import java.util.UUID

data class Category(
    val id: String = UUID.randomUUID().toString(),
    val title: String = String(),
    val browseUrl: String = String(),
    val imageUrl: String = String(),
    var drawable: Int = -1,
    /*
     * Change tag to standard AppTag class.
     * Issue: https://gitlab.e.foundation/e/backlog/-/issues/5364
     */
    var tag: AppTag = AppTag.GPlay()
)
