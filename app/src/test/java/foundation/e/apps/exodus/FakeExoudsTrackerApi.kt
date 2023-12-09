// Copyright MURENA SAS 2023
// SPDX-FileCopyrightText: 2023 MURENA SAS <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.exodus

import foundation.e.apps.data.exodus.ExodusTrackerApi
import foundation.e.apps.data.exodus.Report
import foundation.e.apps.data.exodus.Tracker
import foundation.e.apps.data.exodus.Trackers
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

    override suspend fun getTrackerInfoOfApp(
        appHandle: String,
        versionCode: Int
    ): Response<List<Report>> {
        if (appHandle.isEmpty()) {
            return Response.error(404, null)
        }
        val reportOne = Report(System.currentTimeMillis(), "12-12-12", "1.2.3", "123", "google", listOf(1, 2, 3), listOf())
        val reportTwo = Report(System.currentTimeMillis(), "12-12-12", "1.2.3", "123", "fdroid", listOf(1, 2, 3), listOf())
        return Response.success(listOf(reportOne, reportTwo))
    }
}
