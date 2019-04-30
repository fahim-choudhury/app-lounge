/*
    Copyright (C) 2019  e Foundation

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps.utils

import android.content.Context
import foundation.e.apps.application.model.Application
import foundation.e.apps.applicationmanager.ApplicationManager
import foundation.e.apps.application.model.data.BasicData
import foundation.e.apps.application.model.data.FullData

class ApplicationParser {
    companion object {
        fun parseToApps(applicationManager: ApplicationManager, context: Context, apps: Array<FullData>): ArrayList<Application> {
            val result = ArrayList<Application>()
            apps.forEach {
                val application = applicationManager.findOrCreateApp(it.packageName)
                application.update(it, context)
                result.add(application)
            }
            return result
        }

        fun parseToApps(applicationManager: ApplicationManager, context: Context, apps: Array<BasicData>): ArrayList<Application> {
            val result = ArrayList<Application>()
            apps.forEach {
                val application = applicationManager.findOrCreateApp(it.packageName)
                application.update(it, context)
                result.add(application)
            }
            return result
        }
    }
}