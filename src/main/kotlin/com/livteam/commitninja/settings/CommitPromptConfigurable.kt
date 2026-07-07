package com.livteam.commitninja.settings

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.livteam.commitninja.MyBundle
import javax.swing.JComponent

class CommitPromptConfigurable : SearchableConfigurable {
    private var component: JComponent? = null
    private val promptEditor = EditorTextField(
        EditorFactory.getInstance().createDocument(""),
        null,
        FileTypeManager.getInstance().getFileTypeByExtension("md"),
        false,
        false,
    ).apply {
        setPreferredSize(java.awt.Dimension(760, 360))
    }

    override fun getDisplayName(): String = MyBundle["settings.prompt.displayName"]

    override fun getId(): String = "com.livteam.commitninja.settings.prompt"

    override fun createComponent(): JComponent {
        component = panel {
            group(MyBundle["settings.prompt.group"]) {
                row {
                    comment(MyBundle["settings.prompt.description"])
                }
                row {
                    cell(promptEditor).align(Align.FILL)
                }.resizableRow()
            }
        }
        reset()
        return component!!
    }

    override fun isModified(): Boolean {
        val settings = CommitGenerationSettings.getInstance()
        settings.ensurePromptInitialized()
        return settings.state.userPrompt.orEmpty() != promptEditor.text.trim()
    }

    override fun apply() {
        val settings = CommitGenerationSettings.getInstance()
        settings.state.userPrompt = promptEditor.text.trim()
        settings.state.isPromptInitialized = true
    }

    override fun reset() {
        val settings = CommitGenerationSettings.getInstance()
        settings.ensurePromptInitialized()
        promptEditor.text = settings.state.userPrompt.orEmpty()
        promptEditor.setCaretPosition(0)
    }

    override fun disposeUIResources() {
        component = null
    }
}
