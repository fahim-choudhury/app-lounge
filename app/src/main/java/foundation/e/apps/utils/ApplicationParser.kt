/*
 * Copyright (C) 2019-2021  E FOUNDATION
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

package foundation.e.apps.utils

import android.content.Context
import foundation.e.apps.application.model.Application
import foundation.e.apps.application.model.data.BasicData
import foundation.e.apps.application.model.data.PwasBasicData
import foundation.e.apps.application.model.data.SearchAppsBasicData
import foundation.e.apps.applicationmanager.ApplicationManager

/**
 * Contains various methods used for parsing into ArrayList containing [Application]
 */
class ApplicationParser {
    companion object {
        /**
         * Pareses the given array into an ArrayList containing [Application]
         * @param applicationManager [ApplicationManager]
         * @param context [Context]
         * @param apps An array of [BasicData]
         * @return ArrayList containing [Application]
         */
        fun parseToApps(
            applicationManager: ApplicationManager,
            context: Context,
            apps: Array<BasicData>
        ): ArrayList<Application> {
            val result = ArrayList<Application>()
            apps.forEach {
                val application = applicationManager.findOrCreateApp(it.packageName)
                application.update(it, context)
                result.add(application)
            }
            return result
        }

        /**
         * Pareses the given array into an ArrayList containing [Application]
         * @param applicationManager [ApplicationManager]
         * @param context [Context]
         * @param apps An array of [PwasBasicData]
         * @return ArrayList containing [Application]
         */
        fun PwaParseToApps(
            applicationManager: ApplicationManager,
            context: Context,
            apps: Array<PwasBasicData>
        ): ArrayList<Application> {
            val result = ArrayList<Application>()
            apps.forEach {
                val application = applicationManager.findOrCreateApp(it.name)
                application.Pwaupdate(it, context)
                result.add(application)
            }
            return result

        }

        /**
         * Pareses the given array into an ArrayList containing [Application]
         * @param applicationManager [ApplicationManager]
         * @param context [Context]
         * @param apps An array of [SearchAppsBasicData]
         * @return ArrayList containing [Application]
         */
        fun SearchAppsparseToApps(
            applicationManager: ApplicationManager,
            context: Context,
            apps: Array<SearchAppsBasicData>
        ): ArrayList<Application> {
            val result = ArrayList<Application>()
            apps.forEach {
                val application = applicationManager.findOrCreateApp(it.packageName)
                application.searchUpdate(it, context)
                result.add(application)
            }
            return result
        }

        /**
         * Pareses the given app into an ArrayList containing [Application]
         * @param applicationManager [ApplicationManager]
         * @param context [Context]
         * @param apps [BasicData]
         * @return ArrayList containing [Application]
         */
        fun parseSystemAppData(
            applicationManager: ApplicationManager,
            context: Context,
            apps: BasicData
        ): ArrayList<Application> {
            val result = ArrayList<Application>()
            val application = applicationManager.findOrCreateApp(Constants.MICROG_PACKAGE)
            application.update(apps, context)
            result.add(application)
            return result
        }

    }
}