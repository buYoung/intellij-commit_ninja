package com.livteam.commitninja.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.AppExecutorUtil
import com.livteam.commitninja.MyBundle
import com.livteam.commitninja.acp.AgentModelOptionsLoader
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent

class CommitGenerationConfigurable : SearchableConfigurable {
    constructor() : this(
        modelOptionsLoader = { profile, command, arguments, workingDirectory ->
            AgentModelOptionsLoader.load(profile, command, arguments, workingDirectory)
        },
        backgroundExecutor = AppExecutorUtil.getAppExecutorService(),
        uiDispatcher = { action ->
            ApplicationManager.getApplication().invokeLater(action, ModalityState.any())
        },
    )

    internal constructor(
        modelOptionsLoader: (AgentProfile, String, List<String>, String?) -> Result<List<String>>,
        backgroundExecutor: Executor,
        uiDispatcher: ((Runnable) -> Unit),
    ) {
        this.modelOptionsLoader = modelOptionsLoader
        this.backgroundExecutor = backgroundExecutor
        this.uiDispatcher = uiDispatcher
    }

    private val modelOptionsLoader: (AgentProfile, String, List<String>, String?) -> Result<List<String>>
    private val backgroundExecutor: Executor
    private val uiDispatcher: (Runnable) -> Unit
    private val modelLoadGeneration = AtomicInteger()
    private var isResetting = false
    private var component: JComponent? = null
    private val profileComboBox = ComboBox(AgentProfile.entries.toTypedArray())
    private val modelComboBoxModel = DefaultComboBoxModel<String>()
    private val modelComboBox = ComboBox(modelComboBoxModel)
    private val commandOverrideField = JBTextField()
    private val argumentsOverrideField = JBTextField()
    private val defaultCommandLabel = JBLabel()
    private val modelStatusLabel = JBLabel()
    private val loadModelsButton = JButton(MyBundle["settings.agent.loadModels"])
    private val confirmBeforeReplaceCheckBox = JBCheckBox(MyBundle["settings.replacement.confirmBeforeReplace"])

    init {
        modelComboBox.isEditable = false
        profileComboBox.addActionListener {
            refreshDefaultCommandText()
            if (!isResetting) {
                clearModelOptions()
                modelStatusLabel.text = MyBundle["settings.agent.modelStatus.default"]
                loadModelOptions()
            }
        }
        loadModelsButton.addActionListener { loadModelOptions() }
    }

    override fun getDisplayName(): String = MyBundle["settings.displayName"]

    override fun getId(): String = "com.livteam.commitninja.settings"

    override fun createComponent(): JComponent {
        component = panel {
            group(MyBundle["settings.agent.group"]) {
                row(MyBundle["settings.agent.profile"]) {
                    cell(profileComboBox).align(Align.FILL)
                }
                row(MyBundle["settings.agent.model"]) {
                    cell(modelComboBox).align(Align.FILL)
                    cell(loadModelsButton)
                }
                row {
                    cell(modelStatusLabel).align(Align.FILL)
                }
            }
            group(MyBundle["settings.agent.advanced"]) {
                row {
                    cell(defaultCommandLabel).align(Align.FILL)
                }
                row(MyBundle["settings.agent.commandOverride"]) {
                    cell(commandOverrideField).align(Align.FILL)
                }
                row(MyBundle["settings.agent.argumentsOverride"]) {
                    cell(argumentsOverrideField).align(Align.FILL)
                }
                row {
                    comment(MyBundle["settings.agent.overrideComment"])
                }
            }
            group(MyBundle["settings.replacement.group"]) {
                row {
                    cell(confirmBeforeReplaceCheckBox)
                }
            }
        }
        reset()
        return component!!
    }

    override fun isModified(): Boolean {
        val settings = CommitGenerationSettings.getInstance()
        val state = settings.state
        val selectedProfile = selectedProfile()
        return state.profileName != selectedProfile.name ||
            normalizedCommandOverride(selectedProfile, state.command.orEmpty()) != normalizedCommandOverride(
                selectedProfile,
                commandOverrideField.text,
            ) ||
            normalizedArgumentsOverride(selectedProfile, state.arguments.orEmpty()) != normalizedArgumentsOverride(
                selectedProfile,
                argumentsOverrideField.text,
            ) ||
            state.model.orEmpty() != selectedModel() ||
            state.confirmBeforeReplace != confirmBeforeReplaceCheckBox.isSelected
    }

    override fun apply() {
        val settings = CommitGenerationSettings.getInstance()
        val selectedProfile = selectedProfile()
        settings.state.profileName = selectedProfile.name
        settings.state.command = normalizedCommandOverride(selectedProfile, commandOverrideField.text)
        settings.state.arguments = normalizedArgumentsOverride(selectedProfile, argumentsOverrideField.text)
        settings.state.model = selectedModel()
        settings.state.confirmBeforeReplace = confirmBeforeReplaceCheckBox.isSelected
    }

    override fun reset() {
        val settings = CommitGenerationSettings.getInstance()
        val state = settings.state
        val profile = AgentProfile.fromStoredName(state.profileName)
        isResetting = true
        try {
            profileComboBox.selectedItem = profile
            commandOverrideField.text = normalizedCommandOverride(profile, state.command.orEmpty())
            argumentsOverrideField.text = normalizedArgumentsOverride(profile, state.arguments.orEmpty())
            setModelOptions(emptyList(), state.model.orEmpty(), allowCustomSelection = true)
            confirmBeforeReplaceCheckBox.isSelected = state.confirmBeforeReplace
            refreshDefaultCommandText()
            modelStatusLabel.text = MyBundle["settings.agent.modelStatus.default"]
        } finally {
            isResetting = false
        }
        loadModelOptions()
    }

    override fun disposeUIResources() {
        component = null
    }

    private fun selectedProfile(): AgentProfile =
        profileComboBox.selectedItem as? AgentProfile ?: AgentProfile.NONE

    private fun selectedModel(): String {
        val selected = modelComboBox.selectedItem?.toString().orEmpty()
        return selected.takeUnless { it == MODEL_AGENT_DEFAULT }.orEmpty()
    }

    private fun refreshDefaultCommandText() {
        val profile = selectedProfile()
        defaultCommandLabel.text = if (profile == AgentProfile.NONE) {
            MyBundle["settings.agent.defaultCommand.none"]
        } else {
            MyBundle["settings.agent.defaultCommand", profile.defaultCommand, profile.defaultArguments.ifBlank { "-" }]
        }
    }

    private fun clearModelOptions() {
        modelLoadGeneration.incrementAndGet()
        modelComboBoxModel.removeAllElements()
        modelComboBoxModel.addElement(MODEL_AGENT_DEFAULT)
        modelComboBox.selectedItem = MODEL_AGENT_DEFAULT
    }

    private fun setModelOptions(models: List<String>, selectedModel: String, allowCustomSelection: Boolean) {
        modelComboBoxModel.removeAllElements()
        modelComboBoxModel.addElement(MODEL_AGENT_DEFAULT)
        models.filter(String::isNotBlank).distinct().forEach(modelComboBoxModel::addElement)
        if (allowCustomSelection && selectedModel.isNotBlank() && modelComboBoxModel.getIndexOf(selectedModel) < 0) {
            modelComboBoxModel.addElement(selectedModel)
        }
        modelComboBox.selectedItem = selectedModel
            .takeIf { it.isNotBlank() && modelComboBoxModel.getIndexOf(it) >= 0 }
            ?: MODEL_AGENT_DEFAULT
    }

    private fun loadModelOptions() {
        val profile = selectedProfile()
        if (profile == AgentProfile.NONE) {
            modelStatusLabel.text = MyBundle["settings.agent.modelStatus.profileRequired"]
            return
        }
        val command = commandOverrideField.text.trim().ifBlank { profile.defaultCommand }
        if (command.isBlank()) {
            modelStatusLabel.text = MyBundle["settings.agent.modelStatus.commandRequired"]
            return
        }
        val arguments = AgentCommandLine.splitArguments(
            argumentsOverrideField.text.trim().ifBlank { profile.defaultArguments },
        )
        val selectedModelBeforeLoad = selectedModel()
        val requestGeneration = modelLoadGeneration.incrementAndGet()
        loadModelsButton.isEnabled = false
        modelStatusLabel.text = MyBundle["settings.agent.modelStatus.loading"]
        backgroundExecutor.execute {
            val result = modelOptionsLoader(profile, command, arguments, null)
            uiDispatcher(
                Runnable {
                    if (
                        requestGeneration != modelLoadGeneration.get() ||
                        profile != selectedProfile() ||
                        command != commandOverrideField.text.trim().ifBlank { profile.defaultCommand } ||
                        arguments != AgentCommandLine.splitArguments(
                            argumentsOverrideField.text.trim().ifBlank { profile.defaultArguments },
                        )
                    ) {
                        return@Runnable
                    }
                    loadModelsButton.isEnabled = true
                    result.fold(
                        onSuccess = { models ->
                            setModelOptions(models, selectedModelBeforeLoad, allowCustomSelection = false)
                            modelStatusLabel.text = if (models.isEmpty()) {
                                MyBundle["settings.agent.modelStatus.empty"]
                            } else {
                                MyBundle["settings.agent.modelStatus.loaded", models.size]
                            }
                        },
                        onFailure = { exception ->
                            modelStatusLabel.text = MyBundle[
                                "settings.agent.modelStatus.failed",
                                exception.message ?: exception.javaClass.simpleName,
                            ]
                        },
                    )
                },
            )
        }
    }

    private fun normalizedCommandOverride(profile: AgentProfile, value: String): String =
        value.trim().takeUnless { it.isBlank() || it == profile.defaultCommand }.orEmpty()

    private fun normalizedArgumentsOverride(profile: AgentProfile, value: String): String =
        value.trim().takeUnless { it.isBlank() || it == profile.defaultArguments }.orEmpty()

    private companion object {
        const val MODEL_AGENT_DEFAULT = "Agent default"
    }
}
