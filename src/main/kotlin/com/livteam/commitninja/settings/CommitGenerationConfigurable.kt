package com.livteam.commitninja.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.livteam.commitninja.MyBundle
import java.awt.BorderLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

class CommitGenerationConfigurable : Configurable {
    private var panel: JPanel? = null
    private val profileComboBox = JComboBox(AgentProfile.entries.toTypedArray())
    private val commandField = JBTextField()
    private val argumentsField = JBTextField()
    private val modelField = JBTextField()
    private val promptArea = JBTextArea(14, 80)
    private val confirmBeforeReplaceCheckBox = JBCheckBox(MyBundle["settings.replacement.confirmBeforeReplace"])

    override fun getDisplayName(): String = MyBundle["settings.displayName"]

    override fun createComponent(): JComponent {
        val settings = CommitGenerationSettings.getInstance()
        settings.ensurePromptInitialized()
        profileComboBox.addActionListener {
            val profile = profileComboBox.selectedItem as? AgentProfile ?: AgentProfile.NONE
            if (commandField.text.isBlank()) commandField.text = profile.defaultCommand
            if (argumentsField.text.isBlank()) argumentsField.text = profile.defaultArguments
        }
        val form = FormBuilder.createFormBuilder()
            .addComponent(JBLabel(MyBundle["settings.agent.group"]))
            .addLabeledComponent(MyBundle["settings.agent.profile"], profileComboBox)
            .addLabeledComponent(MyBundle["settings.agent.command"], commandField)
            .addLabeledComponent(MyBundle["settings.agent.arguments"], argumentsField)
            .addLabeledComponent(MyBundle["settings.agent.model"], modelField)
            .addSeparator()
            .addComponent(JBLabel(MyBundle["settings.prompt.group"]))
            .addComponentFillVertically(JScrollPane(promptArea), 0)
            .addSeparator()
            .addComponent(JBLabel(MyBundle["settings.replacement.group"]))
            .addComponent(confirmBeforeReplaceCheckBox)
            .panel
        panel = JPanel(BorderLayout()).also { it.add(form, BorderLayout.CENTER) }
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val state = CommitGenerationSettings.getInstance().state
        val selectedProfile = profileComboBox.selectedItem as? AgentProfile ?: AgentProfile.NONE
        return state.profileName != selectedProfile.name ||
            state.command.orEmpty() != commandField.text ||
            state.arguments.orEmpty() != argumentsField.text ||
            state.model.orEmpty() != modelField.text ||
            state.userPrompt.orEmpty() != promptArea.text ||
            state.confirmBeforeReplace != confirmBeforeReplaceCheckBox.isSelected
    }

    override fun apply() {
        val settings = CommitGenerationSettings.getInstance()
        val selectedProfile = profileComboBox.selectedItem as? AgentProfile ?: AgentProfile.NONE
        settings.state.profileName = selectedProfile.name
        settings.state.command = commandField.text.trim()
        settings.state.arguments = argumentsField.text.trim()
        settings.state.model = modelField.text.trim()
        settings.state.userPrompt = promptArea.text.trim()
        settings.state.confirmBeforeReplace = confirmBeforeReplaceCheckBox.isSelected
        settings.state.isPromptInitialized = true
    }

    override fun reset() {
        val settings = CommitGenerationSettings.getInstance()
        settings.ensurePromptInitialized()
        val state = settings.state
        profileComboBox.selectedItem = AgentProfile.fromStoredName(state.profileName)
        commandField.text = state.command.orEmpty()
        argumentsField.text = state.arguments.orEmpty()
        modelField.text = state.model.orEmpty()
        promptArea.text = state.userPrompt.orEmpty()
        confirmBeforeReplaceCheckBox.isSelected = state.confirmBeforeReplace
    }

    override fun disposeUIResources() {
        panel = null
    }
}
