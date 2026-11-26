package com.sc.biap.ui.main

import com.sc.biap.data.BusinessIntelligenceData
import com.sc.biap.data.DataRepository
import com.sc.biap.data.Design
import com.sc.biap.data.SyncStatus
import com.sc.biap.data.UserProfile
import com.sc.biap.ui.design.DesignDetailUiState
import com.sc.biap.ui.design.DesignDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Cold start and account switches start with an empty [BusinessIntelligenceData] before Room
 * opens. Both [MainScreenViewModel] and [DesignDetailViewModel] must hold `Loading` until
 * [DataRepository.isDatabaseReady] turns true rather than flashing "No designs yet" or
 * "Not found".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ColdStartLoadingTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeRepoState {
        val biData = MutableStateFlow(BusinessIntelligenceData())
        val isDatabaseReady = MutableStateFlow(false)
        val isSyncing = MutableStateFlow(false)
        val lastSyncAt = MutableStateFlow(0L)
        val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
        val profile = MutableStateFlow(UserProfile())

        fun asRepository(): DataRepository = Proxy.newProxyInstance(
            DataRepository::class.java.classLoader,
            arrayOf(DataRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getBiData" -> biData
                "isDatabaseReady" -> isDatabaseReady
                "isSyncing", "getIsSyncing" -> isSyncing
                "getLastSyncAt" -> lastSyncAt
                "getSyncStatus" -> syncStatus
                "getProfile" -> profile
                else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
        } as DataRepository
    }

    @Test
    fun `MainScreenViewModel stays Loading until database is ready, even with empty biData`() = runBlocking {
        val fake = FakeRepoState()
        val vm = MainScreenViewModel(fake.asRepository())
        val collector = launch { vm.uiState.collect { } }
        try {
            // Give flowOn(Dispatchers.Default) time to evaluate the initial combine emission.
            delay(100)
            assertEquals(MainScreenUiState.Loading, vm.uiState.value)

            // Genuine empty catalogue once Room finishes opening -> transitions to Success(0).
            fake.isDatabaseReady.value = true
            val loaded = withTimeout(2_000) {
                vm.uiState.first { it is MainScreenUiState.Success }
            } as MainScreenUiState.Success
            assertEquals(0, loaded.totalCount)

            // Account switch resets readiness -> returns to Loading rather than flashing empty.
            fake.isDatabaseReady.value = false
            fake.biData.value = BusinessIntelligenceData()
            val reloading = withTimeout(2_000) {
                vm.uiState.first { it is MainScreenUiState.Loading }
            }
            assertEquals(MainScreenUiState.Loading, reloading)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `DesignDetailViewModel stays Loading until database is ready instead of flashing NotFound`() = runBlocking {
        val fake = FakeRepoState()
        val vm = DesignDetailViewModel("d-1", fake.asRepository())

        delay(50)
        assertEquals(DesignDetailUiState.Loading, vm.uiState.value)

        // Once DB is ready and design is absent -> NotFound.
        fake.isDatabaseReady.value = true
        val notFound = withTimeout(2_000) {
            vm.uiState.first { it is DesignDetailUiState.NotFound }
        }
        assertEquals(DesignDetailUiState.NotFound, notFound)

        // Once design arrives -> Success.
        val sample = Design(id = "d-1", name = "Anarkali", category = "Salwar Kameez")
        fake.biData.value = BusinessIntelligenceData(designs = listOf(sample))
        val found = withTimeout(2_000) {
            vm.uiState.first { it is DesignDetailUiState.Success }
        } as DesignDetailUiState.Success
        assertEquals("Anarkali", found.design.name)
        assertTrue(found.design.id == "d-1")
    }
}
