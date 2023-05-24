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

package foundation.e.apps.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.CheckBoxPreference
import foundation.e.apps.R

class RadioButtonPreference : CheckBoxPreference {

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        setView()
    }

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        setView()
    }

    private fun setView() {
        widgetLayoutResource = R.layout.preference_widget_radiobutton
    }

    override fun onClick() {
        if (this.isChecked)
            return

        super.onClick()
    }
}
