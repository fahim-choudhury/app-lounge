/*
 *  Copyright (C) 2022  ECORP
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.utils

import android.graphics.Color
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import foundation.e.apps.R
import foundation.e.apps.data.enums.Status

fun MaterialButton.disableInstallButton(status: Status? = null) {
    toggleEnableMaterialButton(false, status)
}

fun MaterialButton.enableInstallButton(status: Status? = null) {
    toggleEnableMaterialButton(true, status)
}

private fun MaterialButton.toggleEnableMaterialButton(isEnabled: Boolean, status: Status?) {
    this.isEnabled = isEnabled
    strokeColor = getStrokeColor(isEnabled)
    setButtonTextColor(isEnabled, status)
    backgroundTintList =
        getBackgroundTintList(status)
}

private fun MaterialButton.getBackgroundTintList(status: Status?) =
    if (status == Status.INSTALLED || status == Status.UPDATABLE) {
        ContextCompat.getColorStateList(this.context, R.color.colorAccent)
    } else
        ContextCompat.getColorStateList(this.context, android.R.color.transparent)

private fun MaterialButton.getStrokeColor(
    isEnabled: Boolean,
) = if (isEnabled) {
    ContextCompat.getColorStateList(this.context, R.color.colorAccent)
} else {
    ContextCompat.getColorStateList(this.context, R.color.light_grey)
}

private fun MaterialButton.setButtonTextColor(isEnabled: Boolean, status: Status?) =
    if (isEnabled && (status == Status.INSTALLED || status == Status.UPDATABLE)) {
        setTextColor(Color.WHITE)
    } else if (isEnabled) {
        setTextColor(context.getColor(R.color.colorAccent))
    } else {
        setTextColor(context.getColor(R.color.light_grey))
    }
