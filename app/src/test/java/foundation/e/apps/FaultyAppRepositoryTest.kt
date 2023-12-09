// Copyright (C) 2022  ECORP
// SPDX-FileCopyrightText: 2023 ECORP <https://gitlab.e.foundation/e/apps/apps/-/wikis/home>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package foundation.e.apps

import foundation.e.apps.data.faultyApps.FaultyApp
import foundation.e.apps.data.faultyApps.FaultyAppDao
import foundation.e.apps.data.faultyApps.FaultyAppRepository
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
