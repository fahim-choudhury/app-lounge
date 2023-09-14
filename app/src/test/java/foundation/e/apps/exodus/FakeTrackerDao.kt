// Copyright MURENA SAS 2023
// SPDX-FileCopyrightText: 2023 MURENA SAS <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps.exodus

import foundation.e.apps.data.exodus.Tracker
import foundation.e.apps.data.exodus.TrackerDao

class FakeTrackerDao : TrackerDao {

    val trackers = mutableListOf<Tracker>(
        Tracker(1, "Tracker A", "It;s tracer A", "", "", "", ""),
        Tracker(2, "Tracker B", "It;s tracer B", "", "", "", ""),
        Tracker(3, "Tracker C", "It;s tracer C", "", "", "", "")
    )

    override suspend fun saveTrackers(trackerList: List<Tracker>): List<Long> {
        trackers.addAll(trackerList)
        return trackerList.map { it.id }
    }

    override suspend fun getTrackers(): List<Tracker> {
        return trackers
    }
}
