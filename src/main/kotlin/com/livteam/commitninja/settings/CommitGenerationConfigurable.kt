package com.livteam.commitninja.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.IdeBorderFactory
import com.intellij.util.concurrency.AppExecutorUtil
import com.livteam.commitninja.MyBundle
import com.livteam.commitninja.acp.AgentModelOptionsLoader
import java.awt.BorderLayout
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

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
    private val modelSelector = SearchableStringComboBox(
        options = listOf(MODEL_AGENT_DEFAULT),
        isSearchMatchCandidate = { option -> option != MODEL_AGENT_DEFAULT },
        restoresAllOptionsForExactQuery = false,
    )
    private val languageSelector = SearchableStringComboBox(
        options = CommitLanguageRegion.entries.map { it.displayName },
        allowBlankSelection = true,
    )
    private val commandOverrideField = JBTextField()
    private val argumentsOverrideField = JBTextField()
    private val defaultCommandLabel = JBLabel()
    private val modelStatusLabel = JBLabel()
    private val loadModelsButton = JButton(MyBundle["settings.agent.loadModels"])
    private val openOpencodeConfigButton = JButton(MyBundle["settings.opencode.openConfig"])
    private val opencodeSettingsPanel = JPanel(BorderLayout()).apply {
        border = IdeBorderFactory.createTitledBorder(MyBundle["settings.opencode.group"])
        add(openOpencodeConfigButton, BorderLayout.WEST)
    }
    private val confirmBeforeReplaceCheckBox = JBCheckBox(MyBundle["settings.replacement.confirmBeforeReplace"])

    init {
        profileComboBox.addActionListener {
            refreshDefaultCommandText()
            updateOpencodeSettingsVisibility()
            if (!isResetting) {
                LOG.info("ACP profile changed in settings: profile=${selectedProfile().name}; clearing stale model selection")
                clearModelOptions()
                modelStatusLabel.text = MyBundle["settings.agent.modelStatus.default"]
                loadModelOptions()
            }
        }
        loadModelsButton.addActionListener { loadModelOptions() }
        openOpencodeConfigButton.addActionListener { openOpencodeConfigFile() }
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
                    cell(modelSelector.comboBox).align(Align.FILL)
                    cell(loadModelsButton)
                }
                row {
                    cell(modelStatusLabel).align(Align.FILL)
                }
            }
            group(MyBundle["settings.language.group"]) {
                row(MyBundle["settings.language.region"]) {
                    cell(languageSelector.comboBox).align(Align.FILL)
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
            row {
                cell(opencodeSettingsPanel).align(Align.FILL)
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
            state.languageRegionName.orEmpty() != selectedLanguageRegionName() ||
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
        settings.state.languageRegionName = selectedLanguageRegionName()
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
            languageSelector.selectedValue = CommitLanguageRegion.fromStoredName(state.languageRegionName)?.displayName.orEmpty()
            confirmBeforeReplaceCheckBox.isSelected = state.confirmBeforeReplace
            refreshDefaultCommandText()
            updateOpencodeSettingsVisibility()
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
        val selected = modelSelector.selectedValue
        return selected
            .takeIf { it.isNotBlank() && it in modelSelector.options() && it != MODEL_AGENT_DEFAULT }
            .orEmpty()
    }

    private fun selectedLanguageRegionName(): String =
        CommitLanguageRegion.entries.firstOrNull { it.displayName == languageSelector.selectedValue }?.name.orEmpty()

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

    private fun updateOpencodeSettingsVisibility() {
        val isOpencode = selectedProfile() == AgentProfile.OPENCODE
        opencodeSettingsPanel.isVisible = isOpencode
        openOpencodeConfigButton.isVisible = isOpencode
    }

    private fun openOpencodeConfigFile() {
        val configPath = resolveOpencodeConfigPath()
        if (!Files.isRegularFile(configPath)) {
            Messages.showInfoMessage(
                MyBundle["settings.opencode.configMissing", configPath.toString()],
                MyBundle["settings.opencode.configMissing.title"],
            )
            return
        }
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(configPath)
        val project = ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
        if (virtualFile == null || project == null) {
            Messages.showInfoMessage(
                MyBundle["settings.opencode.configCannotOpen", configPath.toString()],
                MyBundle["settings.opencode.configCannotOpen.title"],
            )
            return
        }
        OpenFileDescriptor(project, virtualFile).navigate(true)
    }

    private fun clearModelOptions() {
        modelLoadGeneration.incrementAndGet()
        modelSelector.setOptions(listOf(MODEL_AGENT_DEFAULT), selectedValue = MODEL_AGENT_DEFAULT)
    }

    private fun setModelOptions(models: List<String>, selectedModel: String, allowCustomSelection: Boolean) {
        val modelOptions = buildList {
            add(MODEL_AGENT_DEFAULT)
            models.filter(String::isNotBlank).distinct().forEach(::add)
        }
        val selectedModelOption = selectedModel
            .takeIf { it.isNotBlank() && (it in modelOptions || allowCustomSelection) }
            ?: MODEL_AGENT_DEFAULT
        if (selectedModel.isNotBlank() && selectedModelOption == MODEL_AGENT_DEFAULT) {
            LOG.info("Cleared stale ACP selected model for profile=${selectedProfile().name}: model=$selectedModel")
        }
        modelSelector.setOptions(modelOptions, selectedModelOption, allowCustomSelection)
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
        modelSelector.options()
            .filterNot { it == MODEL_AGENT_DEFAULT }

    internal companion object {
        private val LOG = Logger.getInstance(CommitGenerationConfigurable::class.java)
        const val MODEL_AGENT_DEFAULT = "Agent default"

        fun resolveOpencodeConfigPath(userHome: String = System.getProperty("user.home")): Path =
            Paths.get(userHome).resolve(".config").resolve("opencode").resolve("opencode.jsonc")
    }
}
