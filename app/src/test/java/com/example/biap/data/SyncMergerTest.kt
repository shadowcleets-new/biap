package com.example.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergerTest {

  private fun design(id: String, name: String, updatedAt: Long) =
    Design(id = id, name = name, category = "Kurti", updatedAt = updatedAt)

  @Test
  fun merge_newerLocalDesignWins() {
    val local = BusinessIntelligenceData(designs = listOf(design("a", "Local Newer", 200L)))
    val remote = BusinessIntelligenceData(designs = listOf(design("a", "Remote Older", 100L)))
    val merged = SyncMerger.merge(local, remote)
    assertEquals(1, merged.designs.size)
    assertEquals("Local Newer", merged.designs[0].name)
  }

  @Test
  fun merge_newerRemoteDesignWins() {
    val local = BusinessIntelligenceData(designs = listOf(design("a", "Local Older", 100L)))
    val remote = BusinessIntelligenceData(designs = listOf(design("a", "Remote Newer", 200L)))
    val merged = SyncMerger.merge(local, remote)
    assertEquals("Remote Newer", merged.designs[0].name)
  }

  @Test
  fun merge_tombstoneDeletesOlderDesign() {
    val local = BusinessIntelligenceData(designs = listOf(design("a", "Edited Before Delete", 100L)))
    val remote = BusinessIntelligenceData(
      deletedDesignIds = setOf("a"),
      deletedDesigns = mapOf("a" to 150L)
    )
    val merged = SyncMerger.merge(local, remote)
    assertTrue(merged.designs.isEmpty())
    assertEquals(150L, merged.deletedDesigns["a"])
  }

  @Test
  fun merge_recreationAfterDeletePrunesTombstone() {
    val local = BusinessIntelligenceData(designs = listOf(design("a", "Recreated", 300L)))
    val remote = BusinessIntelligenceData(
      deletedDesignIds = setOf("a"),
      deletedDesigns = mapOf("a" to 200L)
    )
    val merged = SyncMerger.merge(local, remote)
    assertEquals(1, merged.designs.size)
    assertTrue(merged.deletedDesigns.isEmpty())
  }

  @Test
  fun merge_customFieldTombstoneRemovesOlderField() {
    val local = BusinessIntelligenceData(
      globalCustomFields = setOf("Old Field"),
      globalCustomFieldsMetadata = mapOf("Old Field" to 100L)
    )
    val remote = BusinessIntelligenceData(deletedCustomFields = mapOf("Old Field" to 200L))
    val merged = SyncMerger.merge(local, remote)
    assertTrue(merged.globalCustomFields.isEmpty())
    assertEquals(200L, merged.deletedCustomFields["Old Field"])
  }
}
