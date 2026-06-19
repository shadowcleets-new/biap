package com.example.biap.ui.main

import com.example.biap.data.DataRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.biap.data.BusinessIntelligenceData
import com.example.biap.data.Design

class MainScreenViewModelTest {
  @Test
  fun uiState_initiallyLoading() = runTest {
    val viewModel = MainScreenViewModel(FakeMyModelRepository())
    assertEquals(viewModel.uiState.first(), MainScreenUiState.Loading)
  }

  @Test
  fun uiState_onItemSaved_isDisplayed() = runTest {
    val viewModel = MainScreenViewModel(FakeMyModelRepository())
    assertEquals(viewModel.uiState.first(), MainScreenUiState.Loading)
  }
}

private class FakeMyModelRepository : DataRepository {
  override val biData: StateFlow<BusinessIntelligenceData> = MutableStateFlow(BusinessIntelligenceData())
  override fun addOrUpdateDesign(design: Design) {}
  override fun deleteDesign(designId: String) {}
  override fun addGlobalCustomField(field: String) {}
  override fun removeGlobalCustomField(field: String) {}

  override val authManager: com.example.biap.util.AuthManager
    get() = throw UnsupportedOperationException("AuthManager not supported in tests")
  override val isSyncing: StateFlow<Boolean> = MutableStateFlow(false)
  override fun syncWithGoogleDrive() {}
  override fun signOut(onComplete: () -> Unit) { onComplete() }
}
