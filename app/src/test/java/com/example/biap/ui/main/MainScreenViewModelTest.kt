package com.example.biap.ui.main

import com.example.biap.data.BusinessIntelligenceData
import com.example.biap.data.DataRepository
import com.example.biap.data.Design
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_initiallyLoading() = runTest {
    val viewModel = MainScreenViewModel(FakeMyModelRepository())
    assertEquals(MainScreenUiState.Loading, viewModel.uiState.first())
  }

  @Test
  fun uiState_searchQuery_filtersByName() = runTest {
    val data = BusinessIntelligenceData(
      designs = listOf(
        Design(id = "1", name = "Rose Kurti", category = "Kurti", updatedAt = 1L),
        Design(id = "2", name = "Blue Anarkali", category = "Anarkali", updatedAt = 1L)
      )
    )
    val viewModel = MainScreenViewModel(FakeMyModelRepository(data))
    viewModel.updateSearchQuery("rose")
    val success = viewModel.uiState.first { it is MainScreenUiState.Success } as MainScreenUiState.Success
    assertEquals(1, success.data.designs.size)
    assertEquals("Rose Kurti", success.data.designs[0].name)
  }
}

private class FakeMyModelRepository(
  initialData: BusinessIntelligenceData = BusinessIntelligenceData()
) : DataRepository {
  override val biData: StateFlow<BusinessIntelligenceData> = MutableStateFlow(initialData)
  override fun addOrUpdateDesign(design: Design) {}
  override fun deleteDesign(designId: String) {}
  override fun addGlobalCustomField(field: String) {}
  override fun removeGlobalCustomField(field: String) {}

  override val authManager: com.example.biap.util.AuthManager
    get() = throw UnsupportedOperationException("AuthManager not supported in tests")
  override val isSyncing: StateFlow<Boolean> = MutableStateFlow(false)
  override val syncStatus: StateFlow<com.example.biap.data.SyncStatus> = MutableStateFlow(com.example.biap.data.SyncStatus.Idle)
  override fun syncWithGoogleDrive() {}
  override fun resetSyncStatus() {}
  override fun signOut(onComplete: () -> Unit) { onComplete() }
  override suspend fun hasGuestData(): Boolean = false
  override suspend fun mergeGuestData(): Boolean = false
  override suspend fun clearGuestData(): Boolean = true
}
