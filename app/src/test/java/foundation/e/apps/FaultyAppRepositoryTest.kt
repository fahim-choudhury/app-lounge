/*
 *  Copyright (C) 2022  ECORP
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package foundation.e.apps

import foundation.e.apps.api.faultyApps.FaultyApp
import foundation.e.apps.api.faultyApps.FaultyAppDao
import foundation.e.apps.api.faultyApps.FaultyAppRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class FaultyAppRepositoryTest {
    private lateinit var faultyAppRepository: FaultyAppRepository
    private val fakeFaultyAppDao = FakeFaultyAppDao()

    @Before
    fun setup() {
        faultyAppRepository = FaultyAppRepository(fakeFaultyAppDao)
    }

    @Test
    fun addFaultyApp_CheckSize() = runTest {
        faultyAppRepository.addFaultyApp("foundation.e.apps", "")
        assertEquals("testAddFaultyApp", 1, fakeFaultyAppDao.faultyAppList.size)
    }

    @Test
    fun getAllFaultyApps_ReturnsAppList() = runTest {
        fakeFaultyAppDao.faultyAppList.add(FaultyApp("foundation.e.apps", ""))
        fakeFaultyAppDao.faultyAppList.add(FaultyApp("foundation.e.edrive", ""))
        fakeFaultyAppDao.faultyAppList.add(FaultyApp("foundation.e.privacycentral", ""))
        val faultyAppList = faultyAppRepository.getAllFaultyApps()
        assertTrue("testGetAllFaultyApps", faultyAppList[0].packageName.contentEquals("foundation.e.apps"))
    }

    @Test
    fun deleteFaultyApps_CheckSize() = runTest {
        fakeFaultyAppDao.faultyAppList.add(FaultyApp("foundation.e.apps", ""))
        fakeFaultyAppDao.faultyAppList.add(FaultyApp("foundation.e.edrive", ""))
        fakeFaultyAppDao.faultyAppList.add(FaultyApp("foundation.e.privacycentral", ""))
        faultyAppRepository.deleteFaultyAppByPackageName("foundation.e.apps")
        assertTrue("testDeleteFaultyApps", fakeFaultyAppDao.faultyAppList.size == 2)
    }

    class FakeFaultyAppDao : FaultyAppDao {
        val faultyAppList: MutableList<FaultyApp> = mutableListOf()

        override suspend fun addFaultyApp(faultyApp: FaultyApp): Long {
            faultyAppList.add(faultyApp)
            return -1
        }

        override suspend fun getFaultyApps(): List<FaultyApp> {
            return faultyAppList
        }

        override suspend fun deleteFaultyAppByPackageName(packageName: String): Int {
            val isSuccess = faultyAppList.removeIf {
                it.packageName.contentEquals(packageName)
            }
            return if (isSuccess) 1 else -1
        }
    }
}
