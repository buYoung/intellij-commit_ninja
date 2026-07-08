package com.livteam.commitninja.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import javax.swing.text.JTextComponent

class SearchableStringComboBoxTest : BasePlatformTestCase() {
    fun testFilteringAndSelectionRestoreAllOptions() {
        val selector = SearchableStringComboBox(
            options = listOf("Agent default", "ollama-cloud/a", "ollama-cloud/b"),
            isSearchMatchCandidate = { option -> option != "Agent default" },
            restoresAllOptionsForExactQuery = false,
        )
        val editorComponent = selector.comboBox.editor.editorComponent as JTextComponent

        editorComponent.text = "cloud/b"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(listOf("ollama-cloud/b"), selector.visibleOptions())

        selector.comboBox.selectedItem = "ollama-cloud/b"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(listOf("Agent default", "ollama-cloud/a", "ollama-cloud/b"), selector.visibleOptions())
        assertEquals("ollama-cloud/b", selector.selectedValue)
    }

    fun testBlankSelectionDoesNotSelectFirstOption() {
        val selector = SearchableStringComboBox(
            options = listOf("Germany", "United States", "Republic of Korea", "None"),
            allowBlankSelection = true,
        )

        selector.selectedValue = ""

        assertEquals("", selector.selectedValue)
        assertNull(selector.comboBox.selectedItem)
    }

    fun testBlankSelectionSurvivesOptionRebuildAndFilterRestoreWithoutSelectingGermany() {
        val selector = SearchableStringComboBox(
            options = listOf("Germany", "United States", "Republic of Korea", "None"),
            allowBlankSelection = true,
        )
        val editorComponent = selector.comboBox.editor.editorComponent as JTextComponent

        selector.selectedValue = ""
        UIUtil.dispatchAllInvocationEvents()

        selector.setOptions(listOf("Germany", "France", "Republic of Korea", "None"), selectedValue = "")
        UIUtil.dispatchAllInvocationEvents()

        assertEquals("", selector.selectedValue)
        assertNull(selector.comboBox.selectedItem)
        assertFalse("Blank selection must not be forced to Germany after model rebuild.", selector.selectedValue == "Germany")

        editorComponent.text = "Ger"
        UIUtil.dispatchAllInvocationEvents()
        editorComponent.text = ""
        UIUtil.dispatchAllInvocationEvents()

        assertEquals("", selector.selectedValue)
        assertNull(selector.comboBox.selectedItem)
        assertFalse("Blank selection must not be forced to Germany after filter restore.", selector.selectedValue == "Germany")
    }
}
