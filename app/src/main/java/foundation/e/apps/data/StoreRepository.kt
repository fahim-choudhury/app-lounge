// Copyright MURENA SAS 2023
// SPDX-FileCopyrightText: 2023 E FOUNDATION <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.data

interface StoreRepository {
    suspend fun getHomeScreenData(): Any
    suspend fun getAppDetails(packageNameOrId: String): Any?
}
