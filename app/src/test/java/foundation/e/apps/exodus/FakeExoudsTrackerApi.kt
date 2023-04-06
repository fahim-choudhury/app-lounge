/*
 * Copyright MURENA SAS 2023
 * Apps  Quickly and easily install Android apps onto your device!
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

package foundation.e.apps.exodus

import foundation.e.apps.api.exodus.ExodusTrackerApi
import foundation.e.apps.api.exodus.Report
import foundation.e.apps.api.exodus.Tracker
import foundation.e.apps.api.exodus.Trackers
import retrofit2.Response

class FakeExoudsTrackerApi : ExodusTrackerApi {
    private val trackers = mutableListOf<Tracker>(
        Tracker(1, "Tracker A", "It;s tracer A", "", "", "", ""),
        Tracker(2, "Tracker B", "It;s tracer B", "", "", "", ""),
        Tracker(3, "Tracker C", "It;s tracer C", "", "", "", "")
    )

    override suspend fun getTrackerList(date: String): Response<Trackers> {
        return Response.success(Trackers(mutableMapOf(Pair("one", trackers[0]), Pair("two", trackers[1]))))
    }

    override suspend fun getTrackerInfoOfApp(appHandle: String,
                                             versionCode: Int): Response<List<Report>> {
        if (appHandle.isEmpty()) {
            return Response.error(404, null)
        }
        val reportOne = Report(System.currentTimeMillis(), "12-12-12", "1.2.3", "123", listOf(1,2,3), listOf())
        val reportTwo = Report(System.currentTimeMillis(), "12-12-12", "1.2.3", "123", listOf(1,2,3), listOf())
        return Response.success(listOf(reportOne, reportTwo))
    }
}