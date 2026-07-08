package com.livteam.commitninja.settings

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import javax.swing.ScrollPaneConstants

class CommitPromptConfigurableTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            resetPromptSettings()
        } finally {
            super.tearDown()
        }
    }

    fun testPromptSettingsUseMarkdownAwareEditor() {
        val configurable = CommitPromptConfigurable()

        val component = configurable.createComponent()
        val editors = descendantsOfType(component, EditorTextField::class.java)
        val textAreas = descendantsOfType(component, JBTextArea::class.java)

        assertTrue("Prompt settings should render an editor component.", editors.isNotEmpty())
        assertTrue("Prompt settings should not use a plain text area.", textAreas.isEmpty())
        val markdownFileType = FileTypeManager.getInstance().getFileTypeByExtension("md")
        val editor = editors.single()

        assertSame(markdownFileType, editor.fileType)
        assertNotSame("Markdown prompt editor must not resolve to plain text.", PlainTextFileType.INSTANCE, editor.fileType)
        assertSame("Prompt editor must be project-aware so Markdown highlighting can be installed.", project, editor.project)
        val markdownEditor = editor as? CommitPromptMarkdownEditorField
        assertNotNull("Prompt editor must use the Markdown-aware editor field.", markdownEditor)
        val createdEditor = markdownEditor!!.createEditorForInspection()
        try {
            assertNotNull(
                "Prompt editor must install a syntax highlighter on the actual editor component.",
                createdEditor.highlighter,
            )
            assertTrue(
                "Prompt editor must show line numbers.",
                createdEditor.settings.isLineNumbersShown,
            )
            assertTrue(
                "Prompt editor must keep the gutter visible for IDE editor affordances.",
                createdEditor.settings.isLineMarkerAreaShown,
            )
            assertTrue(
                "Prompt editor must show the vertical scrollbar.",
                createdEditor.scrollPane.verticalScrollBarPolicy != ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            )
            assertEquals(
                "Prompt editor must rely on soft wraps instead of exposing an unwanted horizontal scrollbar.",
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                createdEditor.scrollPane.horizontalScrollBarPolicy,
            )
            assertEquals(
                "Prompt editor content must follow the active editor color scheme background.",
                createdEditor.colorsScheme.defaultBackground,
                createdEditor.contentComponent.background,
            )
            assertFalse(
                "Prompt editor must be a multiline editor, not a one-line field.",
                createdEditor.isOneLineMode,
            )
            assertSame(
                "Prompt editor must create the actual EditorEx with project context.",
                project,
                createdEditor.project,
            )
            assertTrue(
                "Prompt editor must not keep the previous fixed preferred width.",
                editor.preferredSize.width != 760,
            )
        } finally {
            EditorFactory.getInstance().releaseEditor(createdEditor)
        }

        configurable.disposeUIResources()
    }

    fun testPromptIsManagedSettingAndLoadedFromCurrentPromptSettingsService() {
        val promptSettings = CommitPromptSettings.getInstance()
        promptSettings.setUserPrompt("Use imperative mood.")
        val configurable = CommitPromptConfigurable()

        val component = configurable.createComponent()
        val editor = descendantsOfType(component, EditorTextField::class.java).single()

        assertEquals("Use imperative mood.", editor.text)

        configurable.disposeUIResources()
    }

    fun testSyncDisabledUsesLocalPrompt() {
        val promptSettings = CommitPromptSettings.getInstance()
        promptSettings.setUserPrompt("Local prompt")
        CommitPromptSyncSettings.getInstance().state.userPrompt = "Synced prompt"

        assertFalse(promptSettings.isPromptSyncEnabled)
        assertEquals("Local prompt", promptSettings.resolvedUserPrompt)
    }

    fun testEnablingSyncCopiesCurrentPromptIntoSyncableState() {
        val promptSettings = CommitPromptSettings.getInstance()
        promptSettings.setUserPrompt("Local prompt before sync")

        promptSettings.isPromptSyncEnabled = true

        val syncedState = CommitPromptSyncSettings.getInstance().state
        assertTrue(promptSettings.isPromptSyncEnabled)
        assertEquals("Local prompt before sync", syncedState.userPrompt)
        assertEquals("Local prompt before sync", promptSettings.resolvedUserPrompt)
    }

    fun testDisablingSyncCopiesCurrentSyncedPromptIntoLocalState() {
        val promptSettings = CommitPromptSettings.getInstance()
        promptSettings.setUserPrompt("Local prompt before sync")
        promptSettings.isPromptSyncEnabled = true
        promptSettings.setUserPrompt("Synced prompt before disabling")

        promptSettings.isPromptSyncEnabled = false

        assertFalse(promptSettings.isPromptSyncEnabled)
        assertEquals("Synced prompt before disabling", promptSettings.state.userPrompt)
        assertEquals("Synced prompt before disabling", promptSettings.resolvedUserPrompt)
    }

    fun testOldCommitGenerationSettingsUserPromptMigratesIntoNewPromptState() {
        val legacyState = CommitGenerationSettings.getInstance().state
        legacyState.userPrompt = "Legacy prompt"
        legacyState.isPromptInitialized = true

        val promptSettings = CommitPromptSettings.getInstance()

        assertEquals("Legacy prompt", promptSettings.resolvedUserPrompt)
        assertEquals("Legacy prompt", promptSettings.state.userPrompt)
        assertTrue(promptSettings.state.isPromptInitialized)
    }

    fun testPromptSyncCheckboxAppliesTransition() {
        val promptSettings = CommitPromptSettings.getInstance()
        promptSettings.setUserPrompt("Local prompt")
        val configurable = CommitPromptConfigurable()
        val component = configurable.createComponent()
        val editor = descendantsOfType(component, EditorTextField::class.java).single()
        val syncCheckBox = descendantsOfType(component, JBCheckBox::class.java)
            .single { it.text == "Sync prompt with JetBrains Settings Sync" }

        editor.text = "Edited prompt"
        syncCheckBox.isSelected = true
        assertTrue(configurable.isModified)

        configurable.apply()

        assertTrue(promptSettings.isPromptSyncEnabled)
        assertEquals("Edited prompt", CommitPromptSyncSettings.getInstance().state.userPrompt)

        configurable.disposeUIResources()
    }

    fun testPromptStorageIsSplitBetweenLocalAndSyncableServices() {
        val generationState = CommitGenerationSettings::class.java.getAnnotation(State::class.java)
        val localPromptState = CommitPromptSettings::class.java.getAnnotation(State::class.java)
        val syncPromptState = CommitPromptSyncSettings::class.java.getAnnotation(State::class.java)

        assertEquals(RoamingType.DISABLED, generationState.storages.single().roamingType)
        assertEquals(RoamingType.DISABLED, localPromptState.storages.single().roamingType)
        assertEquals(RoamingType.DEFAULT, syncPromptState.storages.single().roamingType)
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

    private fun resetPromptSettings() {
        CommitPromptSettings.getInstance().loadState(CommitPromptSettings.State())
        CommitPromptSyncSettings.getInstance().loadState(CommitPromptSyncSettings.State())
        val legacyState = CommitGenerationSettings.getInstance().state
        legacyState.userPrompt = ""
        legacyState.isPromptInitialized = false
    }
}
