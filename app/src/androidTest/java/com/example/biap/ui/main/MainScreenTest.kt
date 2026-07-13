package com.example.biap.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.biap.data.BusinessIntelligenceData
import com.example.biap.data.Design
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.biap.ui.main.MainScreenContent]. */
class MainScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent {
            MainScreenContent(
                data = FAKE_BI_DATA,
                query = "",
                onItemClick = {}
            )
        }
    }

    @Test
    fun firstItem_exists() {
        FAKE_BI_DATA.designs.forEach { composeTestRule.onNodeWithText(it.name).assertExists() }
    }
}

private val FAKE_BI_DATA = BusinessIntelligenceData(
    designs = listOf(
        Design(id = "1", name = "Sample1", category = "Category1"),
        Design(id = "2", name = "Sample2", category = "Category2"),
        Design(id = "3", name = "Sample3", category = "Category3")
    )
)
