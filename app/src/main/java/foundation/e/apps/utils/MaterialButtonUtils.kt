// Copyright (C) 2022  ECORP
// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

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
