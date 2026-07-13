package com.example.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyBackupDecoderTest {

  @Test
  fun decode_currentSchema_passesThrough() {
    val json = """
      {"designs":[{"id":"a","name":"Kurti A","category":"Kurti",
        "sizeInventories":[{"size":"M","stock":2,"price":150000}],
        "customFields":{},"karigars":[],"updatedAt":5}],
       "globalCustomFields":["Top - Fabric"],
       "globalCustomFieldsMetadata":{"Top - Fabric":5},
       "deletedDesignIds":[],"deletedDesigns":{},"deletedCustomFields":{}}
    """.trimIndent()
    val result = LegacyBackupDecoder.decode(json)
    assertNotNull(result)
    assertEquals(150000L, result!!.designs[0].sizeInventories[0].price)
    assertEquals(5L, result.designs[0].updatedAt)
  }

  @Test
  fun decode_legacyDoublePrices_convertsRupeesToPaise() {
    val json = """
      {"designs":[{"id":"a","name":"Old Design","category":"Lehenga",
        "sizeInventories":[{"size":"L","stock":1,"price":1500.5}]}],
       "globalCustomFields":["Top - Fabric"]}
    """.trimIndent()
    val result = LegacyBackupDecoder.decode(json)
    assertNotNull(result)
    assertEquals(150050L, result!!.designs[0].sizeInventories[0].price)
    assertEquals(0L, result.designs[0].updatedAt)
    assertEquals(setOf("Top - Fabric"), result.globalCustomFields)
  }

  @Test
  fun decode_garbage_returnsNull() {
    assertNull(LegacyBackupDecoder.decode("this is not json at all {{{"))
  }
}
