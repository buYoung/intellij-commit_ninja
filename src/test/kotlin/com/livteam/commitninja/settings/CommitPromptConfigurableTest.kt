package com.livteam.commitninja.settings

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container

class CommitPromptConfigurableTest : BasePlatformTestCase() {
    fun testPromptSettingsUseMarkdownAwareEditor() {
        val configurable = CommitPromptConfigurable()

        val component = configurable.createComponent()
        val editors = descendantsOfType(component, EditorTextField::class.java)
        val textAreas = descendantsOfType(component, JBTextArea::class.java)

        assertTrue("Prompt settings should render an editor component.", editors.isNotEmpty())
        assertTrue("Prompt settings should not use a plain text area.", textAreas.isEmpty())
        assertSame(FileTypeManager.getInstance().getFileTypeByExtension("md"), editors.single().fileType)

        configurable.disposeUIResources()
    }

    private fun <T> descendantsOfType(root: Component, expectedClass: Class<T>): List<T> {
        val matches = mutableListOf<T>()
        fun visit(component: Component) {
            if (expectedClass.isInstance(component)) {
                matches += expectedClass.cast(component)
            }
            if (component is Container) {
                component.components.forEach(::visit)
            }
        }
        visit(root)
        return matches
    }
}
