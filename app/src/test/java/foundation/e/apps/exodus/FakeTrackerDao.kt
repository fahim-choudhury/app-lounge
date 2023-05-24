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
