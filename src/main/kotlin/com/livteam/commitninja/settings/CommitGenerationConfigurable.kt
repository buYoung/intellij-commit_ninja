package com.livteam.commitninja.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
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
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

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
    private var isFilteringModelOptions = false
    private var isRestoringCommittedModelSelection = false
    private var committedModelFilterTextToIgnore: String? = null
    private var pendingModelFilterText: String? = null
    private var availableModelOptions = listOf(MODEL_AGENT_DEFAULT)
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
        modelComboBox.isEditable = true
        val modelEditor = modelComboBox.editor.editorComponent as? JTextComponent
        modelEditor?.document?.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent) = scheduleModelFilter(modelEditor.text)
                override fun removeUpdate(event: DocumentEvent) = scheduleModelFilter(modelEditor.text)
                override fun changedUpdate(event: DocumentEvent) = scheduleModelFilter(modelEditor.text)
            },
        )
        profileComboBox.addActionListener {
            refreshDefaultCommandText()
            if (!isResetting) {
                LOG.info("ACP profile changed in settings: profile=${selectedProfile().name}; clearing stale model selection")
                clearModelOptions()
                modelStatusLabel.text = MyBundle["settings.agent.modelStatus.default"]
                loadModelOptions()
            }
        }
        modelComboBox.addActionListener {
            restoreModelOptionsAfterCommittedSelection()
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
        val selectedModel = selectedModel()
        LOG.info("Applying ACP generation settings: profile=${selectedProfile.name}, model=${selectedModel.ifBlank { MODEL_AGENT_DEFAULT }}")
        settings.state.profileName = selectedProfile.name
        settings.state.command = normalizedCommandOverride(selectedProfile, commandOverrideField.text)
        settings.state.arguments = normalizedArgumentsOverride(selectedProfile, argumentsOverrideField.text)
        settings.state.model = selectedModel
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
        val selected = (modelComboBox.editor.item ?: modelComboBox.selectedItem)
            ?.toString()
            .orEmpty()
            .trim()
        return selected
            .takeIf { it.isNotBlank() && it in availableModelOptions && it != MODEL_AGENT_DEFAULT }
            .orEmpty()
    }

    private fun refreshDefaultCommandText() {
        val profile = selectedProfile()
        defaultCommandLabel.text = if (profile == AgentProfile.NONE) {
            MyBundle["settings.agent.defaultCommand.none"]
        } else if (profile.defaultCommand.isBlank()) {
            MyBundle["settings.agent.defaultCommand.customRequired"]
        } else {
            MyBundle["settings.agent.defaultCommand", profile.defaultCommand, profile.defaultArguments.ifBlank { "-" }]
        }
    }

    private fun clearModelOptions() {
        modelLoadGeneration.incrementAndGet()
        availableModelOptions = listOf(MODEL_AGENT_DEFAULT)
        modelComboBox.editor.item = MODEL_AGENT_DEFAULT
        filterModelOptions("")
        modelComboBox.selectedItem = MODEL_AGENT_DEFAULT
        modelComboBox.editor.item = MODEL_AGENT_DEFAULT
    }

    private fun setModelOptions(models: List<String>, selectedModel: String, allowCustomSelection: Boolean) {
        availableModelOptions = buildList {
            add(MODEL_AGENT_DEFAULT)
            models.filter(String::isNotBlank).distinct().forEach(::add)
            if (allowCustomSelection && selectedModel.isNotBlank() && selectedModel !in this) {
                add(selectedModel)
            }
        }
        filterModelOptions("")
        val selectedModelOption = selectedModel
            .takeIf { it.isNotBlank() && it in availableModelOptions }
            ?: MODEL_AGENT_DEFAULT
        if (selectedModel.isNotBlank() && selectedModelOption == MODEL_AGENT_DEFAULT) {
            LOG.info("Cleared stale ACP selected model for profile=${selectedProfile().name}: model=$selectedModel")
        }
        modelComboBox.selectedItem = selectedModelOption
        modelComboBox.editor.item = selectedModelOption
    }

    private fun scheduleModelFilter(query: String) {
        if (isFilteringModelOptions) return
        val trimmedQuery = query.trim()
        if (
            committedModelFilterTextToIgnore == trimmedQuery &&
            trimmedQuery in availableModelOptions
        ) {
            committedModelFilterTextToIgnore = null
            isRestoringCommittedModelSelection = true
            try {
                modelComboBox.selectedItem = trimmedQuery
                modelComboBoxModel.selectedItem = trimmedQuery
                modelComboBox.editor.item = trimmedQuery
            } finally {
                isRestoringCommittedModelSelection = false
            }
            return
        }
        pendingModelFilterText = query
        SwingUtilities.invokeLater {
            val filterText = pendingModelFilterText ?: return@invokeLater
            pendingModelFilterText = null
            filterModelOptions(filterText)
        }
    }

    private fun filterModelOptions(query: String) {
        if (isFilteringModelOptions) return
        isFilteringModelOptions = true
        try {
            val trimmedQuery = query.trim()
            val visibleModelOptions = if (trimmedQuery.isBlank()) {
                availableModelOptions
            } else {
                availableModelOptions.filter { modelOption ->
                    modelOption != MODEL_AGENT_DEFAULT && modelOption.contains(trimmedQuery, ignoreCase = true)
                }
            }
            val editorText = (modelComboBox.editor.editorComponent as? JTextComponent)?.text.orEmpty()
            val selectedItem = modelComboBox.selectedItem
            modelComboBoxModel.removeAllElements()
            visibleModelOptions.forEach(modelComboBoxModel::addElement)
            if (editorText.isNotBlank()) {
                modelComboBox.editor.item = editorText
            } else if (selectedItem != null && selectedItem in visibleModelOptions) {
                modelComboBox.selectedItem = selectedItem
            }
        } finally {
            isFilteringModelOptions = false
        }
    }

    private fun restoreModelOptionsAfterCommittedSelection() {
        if (isResetting || isFilteringModelOptions || isRestoringCommittedModelSelection) return
        val selectedModelOption = modelComboBox.selectedItem?.toString()?.trim().orEmpty()
        if (selectedModelOption.isBlank() || selectedModelOption !in availableModelOptions) return
        if (modelItems() == availableModelOptions) {
            LOG.info("ACP model selected in settings: profile=${selectedProfile().name}, model=$selectedModelOption")
            return
        }
        pendingModelFilterText = null
        committedModelFilterTextToIgnore = selectedModelOption
        isRestoringCommittedModelSelection = true
        try {
            LOG.info("ACP model selected from filtered settings list: profile=${selectedProfile().name}, model=$selectedModelOption; restoring loaded model list")
            filterModelOptions("")
            modelComboBox.selectedItem = selectedModelOption
            modelComboBox.editor.item = selectedModelOption
            SwingUtilities.invokeLater {
                if (selectedModelOption in availableModelOptions && modelItems() == availableModelOptions) {
                    isRestoringCommittedModelSelection = true
                    try {
                        modelComboBox.selectedItem = selectedModelOption
                        modelComboBoxModel.selectedItem = selectedModelOption
                        modelComboBox.editor.item = selectedModelOption
                    } finally {
                        isRestoringCommittedModelSelection = false
                    }
                }
            }
        } finally {
            isRestoringCommittedModelSelection = false
        }
    }

    private fun loadModelOptions() {
        val profile = selectedProfile()
        if (profile == AgentProfile.NONE) {
            loadModelsButton.isEnabled = true
            modelStatusLabel.text = MyBundle["settings.agent.modelStatus.profileRequired"]
            return
        }
        val command = modelLoadCommand(profile)
        if (command.isBlank()) {
            loadModelsButton.isEnabled = true
            modelStatusLabel.text = MyBundle["settings.agent.modelStatus.commandRequired"]
            return
        }
        val arguments = modelLoadArguments(profile)
        val selectedModelBeforeLoad = selectedModel()
        val requestGeneration = modelLoadGeneration.incrementAndGet()
        loadModelsButton.isEnabled = false
        modelStatusLabel.text = MyBundle["settings.agent.modelStatus.loading"]
        LOG.info(
            "Loading ACP model options from settings: profile=${profile.name}, command=$command, arguments=${arguments.joinToString(" ")}, selectedModelBeforeLoad=${selectedModelBeforeLoad.ifBlank { MODEL_AGENT_DEFAULT }}",
        )
        backgroundExecutor.execute {
            val result = modelOptionsLoader(profile, command, arguments, null)
            uiDispatcher(
                Runnable {
                    if (
                        requestGeneration != modelLoadGeneration.get() ||
                        profile != selectedProfile() ||
                        command != modelLoadCommand(profile) ||
                        arguments != modelLoadArguments(profile)
                    ) {
                        return@Runnable
                    }
                    loadModelsButton.isEnabled = true
                    result.fold(
                        onSuccess = { models ->
                            setModelOptions(models, selectedModelBeforeLoad, allowCustomSelection = false)
                            LOG.info("Loaded ACP model options in settings: profile=${profile.name}, count=${models.size}")
                            modelStatusLabel.text = if (models.isEmpty()) {
                                MyBundle["settings.agent.modelStatus.empty"]
                            } else if (generationCommand(profile).isBlank()) {
                                MyBundle["settings.agent.modelStatus.loadedGenerationCommandRequired", models.size]
                            } else {
                                MyBundle["settings.agent.modelStatus.loaded", models.size]
                            }
                        },
                        onFailure = { exception ->
                            LOG.warn(
                                "Failed to load ACP model options in settings: profile=${profile.name}, command=$command, arguments=${arguments.joinToString(" ")}",
                                exception,
                            )
                            modelStatusLabel.text = MyBundle[
                                "settings.agent.modelStatus.failed",
                                exception.message ?: exception.javaClass.simpleName,
                            ]
                            setModelOptions(modelItems(), selectedModelBeforeLoad, allowCustomSelection = true)
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

    private fun generationCommand(profile: AgentProfile): String =
        commandOverrideField.text.trim().ifBlank { profile.defaultCommand }

    private fun modelLoadCommand(profile: AgentProfile): String =
        commandOverrideField.text.trim().ifBlank { profile.defaultModelCommand }

    private fun modelLoadArguments(profile: AgentProfile): List<String> =
        AgentCommandLine.splitArguments(
            argumentsOverrideField.text.trim().ifBlank { profile.defaultModelArguments },
        )

    private fun modelItems(): List<String> =
        availableModelOptions
            .filterNot { it == MODEL_AGENT_DEFAULT }

    private companion object {
        val LOG = Logger.getInstance(CommitGenerationConfigurable::class.java)
        const val MODEL_AGENT_DEFAULT = "Agent default"
    }
}
