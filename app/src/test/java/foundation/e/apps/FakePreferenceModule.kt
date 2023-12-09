// Copyright (C) 2022  ECORP
// SPDX-FileCopyrightText: 2023 ECORP <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps

import android.content.Context
import foundation.e.apps.data.preference.PreferenceManagerModule

class FakePreferenceModule(context: Context) : PreferenceManagerModule(context) {
    var isPWASelectedFake = false
    var isOpenSourceelectedFake = false
    var isGplaySelectedFake = false
    var shouldUpdateFromOtherStores = true

    override fun isPWASelected(): Boolean {
        return isPWASelectedFake
    }

    override fun isOpenSourceSelected(): Boolean {
        return isOpenSourceelectedFake
    }

    override fun isGplaySelected(): Boolean {
        return isGplaySelectedFake
    }

    override fun preferredApplicationType(): String {
        return when {
            isOpenSourceelectedFake -> "open"
            isPWASelectedFake -> "pwa"
            else -> "any"
        }
    }

    override fun shouldUpdateAppsFromOtherStores(): Boolean {
        return shouldUpdateFromOtherStores
    }
}
