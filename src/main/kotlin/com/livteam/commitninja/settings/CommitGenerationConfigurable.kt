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
import com.livteam.commitninja.acp.profile.AcpAgentProfile
import com.livteam.commitninja.acp.profile.AcpBuiltInProfiles
import com.livteam.commitninja.acp.profile.AcpProfileRegistry
import com.livteam.commitninja.acp.profile.LegacyProfileIds
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
        marker = Unit,
    )

    internal constructor(
        modelOptionsLoader: (AgentProfile, String, List<String>, String?) -> Result<List<String>>,
        backgroundExecutor: Executor,
        uiDispatcher: ((Runnable) -> Unit),
    ) : this(
        modelOptionsLoader = { profile, command, arguments, workingDirectory ->
            modelOptionsLoader(AgentProfile.fromStoredName(profile.id), command, arguments, workingDirectory)
        },
        backgroundExecutor = backgroundExecutor,
        uiDispatcher = uiDispatcher,
        marker = Unit,
    )

    private constructor(
        modelOptionsLoader: (AcpAgentProfile, String, List<String>, String?) -> Result<List<String>>,
        backgroundExecutor: Executor,
        uiDispatcher: ((Runnable) -> Unit),
        marker: Unit,
    ) {
        this.modelOptionsLoader = modelOptionsLoader
        this.backgroundExecutor = backgroundExecutor
        this.uiDispatcher = uiDispatcher
    }

    private val modelOptionsLoader: (AcpAgentProfile, String, List<String>, String?) -> Result<List<String>>
    private val backgroundExecutor: Executor
    private val uiDispatcher: (Runnable) -> Unit
    private val modelLoadGeneration = AtomicInteger()
    private var isResetting = false
    private var component: JComponent? = null
    private val profileComboBox = ComboBox<ProfileSelectionItem>(DefaultComboBoxModel(knownProfileItems().toTypedArray()))
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
    private val confirmBeforeReplaceCheckBox = JBCheckBox(MyBundle["settings.replacement.confirmBeforeReplace"])

    init {
        profileComboBox.addActionListener {
            refreshDefaultCommandText()
            if (!isResetting) {
                LOG.info("ACP profile changed in settings: profile=${selectedProfileId()}; clearing stale model selection")
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
        return LegacyProfileIds.toStableId(state.profileName).orEmpty() != selectedProfileId() ||
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
        LOG.info("Applying ACP generation settings: profile=${selectedProfileId()}, model=${selectedModel.ifBlank { MODEL_AGENT_DEFAULT }}")
        settings.state.profileName = selectedProfileId()
        settings.state.command = normalizedCommandOverride(selectedProfile, commandOverrideField.text)
        settings.state.arguments = normalizedArgumentsOverride(selectedProfile, argumentsOverrideField.text)
        settings.state.model = selectedModel
        settings.state.languageRegionName = selectedLanguageRegionName()
        settings.state.confirmBeforeReplace = confirmBeforeReplaceCheckBox.isSelected
    }

    override fun reset() {
        val settings = CommitGenerationSettings.getInstance()
        val state = settings.state
        val profileItem = profileItemForStoredId(state.profileName)
        val profile = profileItem.profile
        isResetting = true
        try {
            profileComboBox.model = DefaultComboBoxModel(profileItemsFor(profileItem).toTypedArray())
            profileComboBox.selectedItem = profileItem
            commandOverrideField.text = normalizedCommandOverride(profile, state.command.orEmpty())
            argumentsOverrideField.text = normalizedArgumentsOverride(profile, state.arguments.orEmpty())
            setModelOptions(emptyList(), state.model.orEmpty(), allowCustomSelection = true)
            languageSelector.selectedValue = CommitLanguageRegion.fromStoredName(state.languageRegionName)?.displayName.orEmpty()
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

    private fun selectedProfileItem(): ProfileSelectionItem =
        profileComboBox.selectedItem as? ProfileSelectionItem ?: ProfileSelectionItem.Known(AcpBuiltInProfiles.NONE)

    private fun selectedProfile(): AcpAgentProfile? =
        selectedProfileItem().profile

    private fun selectedProfileId(): String =
        selectedProfileItem().id

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
        defaultCommandLabel.text = if (profile == null || profile.id == AcpBuiltInProfiles.NONE.id) {
            MyBundle["settings.agent.defaultCommand.none"]
        } else if (profile.generationCommand.isBlank()) {
            MyBundle["settings.agent.defaultCommand.customRequired"]
        } else {
            MyBundle["settings.agent.defaultCommand", profile.generationCommand, profile.generationArguments.ifBlank { "-" }]
        }
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
            LOG.info("Cleared stale ACP selected model for profile=${selectedProfileId()}: model=$selectedModel")
        }
        modelSelector.setOptions(modelOptions, selectedModelOption, allowCustomSelection)
    }

    private fun loadModelOptions() {
        val profile = selectedProfile()
        if (profile == null || profile.id == AcpBuiltInProfiles.NONE.id) {
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
            "Loading ACP model options from settings: profile=${profile.id}, command=$command, arguments=${arguments.joinToString(" ")}, selectedModelBeforeLoad=${selectedModelBeforeLoad.ifBlank { MODEL_AGENT_DEFAULT }}",
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
                            LOG.info("Loaded ACP model options in settings: profile=${profile.id}, count=${models.size}")
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
                                "Failed to load ACP model options in settings: profile=${profile.id}, command=$command, arguments=${arguments.joinToString(" ")}",
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

    private fun normalizedCommandOverride(profile: AcpAgentProfile?, value: String): String =
        value.trim().takeUnless { it.isBlank() || it == profile?.generationCommand.orEmpty() }.orEmpty()

    private fun normalizedArgumentsOverride(profile: AcpAgentProfile?, value: String): String =
        value.trim().takeUnless { it.isBlank() || it == profile?.generationArguments.orEmpty() }.orEmpty()

    private fun generationCommand(profile: AcpAgentProfile): String =
        commandOverrideField.text.trim().ifBlank { profile.generationCommand }

    private fun modelLoadCommand(profile: AcpAgentProfile): String =
        commandOverrideField.text.trim().ifBlank { profile.modelCommand }

    private fun modelLoadArguments(profile: AcpAgentProfile): List<String> =
        AgentCommandLine.splitArguments(
            argumentsOverrideField.text.trim().ifBlank { profile.modelArguments },
        )

    private fun modelItems(): List<String> =
        modelSelector.options()
            .filterNot { it == MODEL_AGENT_DEFAULT }

    internal companion object {
        private val LOG = Logger.getInstance(CommitGenerationConfigurable::class.java)
        const val MODEL_AGENT_DEFAULT = "Agent default"

        private fun knownProfileItems(): List<ProfileSelectionItem> =
            AcpProfileRegistry.profiles.map(ProfileSelectionItem::Known)

        private fun profileItemForStoredId(storedId: String?): ProfileSelectionItem {
            val stableId = LegacyProfileIds.toStableId(storedId).orEmpty()
            val profile = AcpProfileRegistry.findById(stableId)
            return if (profile != null) {
                ProfileSelectionItem.Known(profile)
            } else {
                ProfileSelectionItem.Missing(stableId)
            }
        }

        private fun profileItemsFor(selectedItem: ProfileSelectionItem): List<ProfileSelectionItem> =
            buildList {
                addAll(knownProfileItems())
                if (selectedItem is ProfileSelectionItem.Missing) add(selectedItem)
            }
    }
}

private sealed class ProfileSelectionItem {
    abstract val id: String
    abstract val profile: AcpAgentProfile?

    data class Known(override val profile: AcpAgentProfile) : ProfileSelectionItem() {
        override val id: String = profile.id
        override fun toString(): String = profile.displayName
    }

    data class Missing(override val id: String) : ProfileSelectionItem() {
        override val profile: AcpAgentProfile? = null
        override fun toString(): String = "Missing profile: $id"
    }
}
