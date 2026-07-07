package com.livteam.commitninja.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.livteam.commitninja.MyBundle
import javax.swing.JComponent
import javax.swing.JScrollPane

class CommitPromptConfigurable : SearchableConfigurable {
    private var component: JComponent? = null
    private val promptArea = JBTextArea(18, 96).apply {
        lineWrap = true
        wrapStyleWord = true
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
                    cell(JScrollPane(promptArea)).align(Align.FILL)
                }.resizableRow()
            }
        }
        reset()
        return component!!
    }

    override fun isModified(): Boolean {
        val settings = CommitGenerationSettings.getInstance()
        settings.ensurePromptInitialized()
        return settings.state.userPrompt.orEmpty() != promptArea.text.trim()
    }

    override fun apply() {
        val settings = CommitGenerationSettings.getInstance()
        settings.state.userPrompt = promptArea.text.trim()
        settings.state.isPromptInitialized = true
    }

    override fun reset() {
        val settings = CommitGenerationSettings.getInstance()
        settings.ensurePromptInitialized()
        promptArea.text = settings.state.userPrompt.orEmpty()
        promptArea.caretPosition = 0
    }

    override fun disposeUIResources() {
        component = null
    }
}
