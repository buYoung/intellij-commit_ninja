package com.livteam.commitninja.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Container
import java.util.ArrayDeque
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.text.JTextComponent

class CommitGenerationConfigurableTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            val state = CommitGenerationSettings.getInstance().state
            state.profileName = AgentProfile.NONE.name
            state.command = ""
            state.arguments = ""
            state.model = ""
            state.languageRegionName = ""
            state.confirmBeforeReplace = true
        } finally {
            super.tearDown()
        }
    }

    fun testResetAutomaticallyLoadsSelectedProfileModels() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.OPENCODE.name

        val configurable = testConfigurable { profile, _, _, _ ->
            assertEquals(AgentProfile.OPENCODE, profile)
            Result.success(listOf("ollama-cloud/자동-모델"))
        }
        val component = configurable.createComponent()
        val modelComboBox = modelComboBox(component)

        assertTrue(modelItems(modelComboBox).contains("ollama-cloud/자동-모델"))

        configurable.disposeUIResources()
    }

    fun testManualLoadButtonUpdatesModelsWhileConfigurableIsActive() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.OPENCODE.name
        var loadCount = 0

        val configurable = testConfigurable { _, _, _, _ ->
            loadCount += 1
            Result.success(listOf(if (loadCount == 1) "ollama-cloud/초기-모델" else "ollama-cloud/수동-모델"))
        }
        val component = configurable.createComponent()
        val modelComboBox = modelComboBox(component)
        val loadButton = descendantsOfType(component, JButton::class.java)
            .single { it.text == "Load models" }

        loadButton.doClick()

        assertTrue(modelItems(modelComboBox).contains("ollama-cloud/수동-모델"))

        configurable.disposeUIResources()
    }

    fun testSwitchingProfileClearsStaleModelOptions() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.OPENCODE.name
        state.model = "ollama-cloud/deepseek-v4-pro"
        state.command = "custom-codex-acp"

        val configurable = testConfigurable { profile, _, _, _ ->
            Result.success(
                when (profile) {
                    AgentProfile.CODEX_ACP -> listOf("gpt-5.4-mini")
                    else -> listOf("ollama-cloud/deepseek-v4-pro")
                },
            )
        }
        val component = configurable.createComponent()
        val profileComboBox = profileComboBox(component)
        val modelComboBox = modelComboBox(component)

        selectProfile(profileComboBox, AgentProfile.CODEX_ACP)

        assertFalse(modelItems(modelComboBox).contains("ollama-cloud/deepseek-v4-pro"))
        assertEquals(listOf("Agent default", "gpt-5.4-mini"), modelItems(modelComboBox))
        assertEquals("Agent default", modelComboBox.selectedItem)

        configurable.disposeUIResources()
    }

    fun testModelSelectorIsSearchableEditableComboBox() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.CODEX_ACP.name
        state.command = "custom-codex-acp"

        val configurable = testConfigurable { profile, _, _, _ ->
            assertEquals(AgentProfile.CODEX_ACP, profile)
            Result.success(listOf("gpt-5.5", "gpt-5.4", "gpt-5.4-mini"))
        }
        val component = configurable.createComponent()
        val modelComboBox = modelComboBox(component)

        assertTrue("Model selector must be editable so users can type to search/select.", modelComboBox.isEditable)
        val editorComponent = modelComboBox.editor.editorComponent
        assertTrue("Editable model selector should expose a text editor component.", editorComponent is JTextComponent)
        (editorComponent as JTextComponent).text = "gpt-5.4"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(listOf("gpt-5.4", "gpt-5.4-mini"), modelItems(modelComboBox))
        assertEquals("gpt-5.4", editorComponent.text)

        configurable.disposeUIResources()
    }

    fun testLanguageSelectorIsSearchableEditableComboBox() {
        val configurable = testConfigurable { _, _, _, _ -> Result.success(emptyList()) }
        val component = configurable.createComponent()
        val languageComboBox = languageComboBox(component)

        assertTrue("Language selector must be editable so users can type to search/select.", languageComboBox.isEditable)
        val editorComponent = languageComboBox.editor.editorComponent
        assertTrue("Editable language selector should expose a text editor component.", editorComponent is JTextComponent)
        (editorComponent as JTextComponent).text = "Korea"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(listOf("Republic of Korea"), comboBoxItems(languageComboBox))
        assertEquals("Korea", editorComponent.text)

        configurable.disposeUIResources()
    }

    fun testLanguageSelectorShowsAllRegionsAfterResetWithNoneSelected() {
        val state = CommitGenerationSettings.getInstance().state
        state.languageRegionName = CommitLanguageRegion.NONE.name
        val configurable = testConfigurable { _, _, _, _ -> Result.success(emptyList()) }
        val component = configurable.createComponent()
        val languageComboBox = languageComboBox(component)

        UIUtil.dispatchAllInvocationEvents()

        assertEquals(languageRegionDisplayNames(), comboBoxItems(languageComboBox))
        assertEquals("None", languageComboBox.editor.item?.toString())

        configurable.disposeUIResources()
    }

    fun testLanguageSelectorKeepsBlankStoredValueBlank() {
        val state = CommitGenerationSettings.getInstance().state
        state.languageRegionName = ""
        val configurable = testConfigurable { _, _, _, _ -> Result.success(emptyList()) }
        val component = configurable.createComponent()
        val languageComboBox = languageComboBox(component)

        UIUtil.dispatchAllInvocationEvents()

        assertEquals("", languageComboBox.editor.item?.toString().orEmpty())
        assertNull(languageComboBox.selectedItem)
        assertFalse("Blank language must not be forced to Germany after reset events.", languageComboBox.editor.item?.toString() == "Germany")

        configurable.apply()

        assertEquals("", CommitGenerationSettings.getInstance().state.languageRegionName.orEmpty())
        assertFalse(CommitGenerationSettings.getInstance().state.languageRegionName == CommitLanguageRegion.UNITED_STATES.name)
        assertFalse(CommitGenerationSettings.getInstance().state.languageRegionName == CommitLanguageRegion.GERMANY.name)

        configurable.disposeUIResources()
    }

    fun testLanguageSelectorKeepsBlankValueBlankAfterFilterEventsAndApply() {
        val state = CommitGenerationSettings.getInstance().state
        state.languageRegionName = ""
        val configurable = testConfigurable { _, _, _, _ -> Result.success(emptyList()) }
        val component = configurable.createComponent()
        val languageComboBox = languageComboBox(component)
        val editorComponent = languageComboBox.editor.editorComponent as JTextComponent

        editorComponent.text = "Ger"
        UIUtil.dispatchAllInvocationEvents()
        editorComponent.text = ""
        UIUtil.dispatchAllInvocationEvents()

        assertEquals("", languageComboBox.editor.item?.toString().orEmpty())
        assertNull(languageComboBox.selectedItem)

        configurable.apply()

        assertEquals("", CommitGenerationSettings.getInstance().state.languageRegionName.orEmpty())
        assertFalse("Blank language must not be forced to Germany after filter events.", CommitGenerationSettings.getInstance().state.languageRegionName == CommitLanguageRegion.GERMANY.name)

        configurable.disposeUIResources()
    }

    fun testLanguageSelectorRestoresAllRegionsAfterSelectingNoneFromFilteredList() {
        val configurable = testConfigurable { _, _, _, _ -> Result.success(emptyList()) }
        val component = configurable.createComponent()
        val languageComboBox = languageComboBox(component)
        val editorComponent = languageComboBox.editor.editorComponent as JTextComponent

        editorComponent.text = "None"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(languageRegionDisplayNames(), comboBoxItems(languageComboBox))

        languageComboBox.selectedItem = "None"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(languageRegionDisplayNames(), comboBoxItems(languageComboBox))
        assertEquals("None", editorComponent.text)

        configurable.disposeUIResources()
    }

    fun testLanguageSelectorRestoresAllRegionsAfterSelectingCountryFromFilteredList() {
        val configurable = testConfigurable { _, _, _, _ -> Result.success(emptyList()) }
        val component = configurable.createComponent()
        val languageComboBox = languageComboBox(component)
        val editorComponent = languageComboBox.editor.editorComponent as JTextComponent

        editorComponent.text = "Korea"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(listOf("Republic of Korea"), comboBoxItems(languageComboBox))

        languageComboBox.selectedItem = "Republic of Korea"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(languageRegionDisplayNames(), comboBoxItems(languageComboBox))
        assertEquals("Republic of Korea", editorComponent.text)

        configurable.disposeUIResources()
    }

    fun testSettingsUiHasDedicatedLanguageSection() {
        val configurable = testConfigurable { _, _, _, _ -> Result.success(emptyList()) }
        val component = configurable.createComponent()

        assertTrue(descendantLabelTexts(component).contains("Language"))
        assertTrue(descendantLabelTexts(component).contains("Country or region:"))
        assertFalse(descendantLabelTexts(component).contains("Commit language:"))

        configurable.disposeUIResources()
    }

    fun testLanguageSelectionIsAppliedToSettingsState() {
        val configurable = testConfigurable { _, _, _, _ -> Result.success(emptyList()) }
        val component = configurable.createComponent()
        val languageComboBox = languageComboBox(component)

        languageComboBox.selectedItem = "Republic of Korea"
        configurable.apply()

        assertEquals(CommitLanguageRegion.REPUBLIC_OF_KOREA.name, CommitGenerationSettings.getInstance().state.languageRegionName)

        configurable.disposeUIResources()
    }

    fun testModelSelectorFiltersLargeLoadedListAndHidesNonMatches() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.CODEX_ACP.name
        state.command = "custom-codex-acp"

        val loadedModels = (1..40).map { modelNumber -> "local-model-$modelNumber" } + listOf("gpt-5.5", "gpt-5.4", "gpt-5.4-mini")
        val configurable = testConfigurable { _, _, _, _ -> Result.success(loadedModels) }
        val component = configurable.createComponent()
        val modelComboBox = modelComboBox(component)
        val editorComponent = modelComboBox.editor.editorComponent as JTextComponent

        editorComponent.text = "5.4-mini"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(listOf("gpt-5.4-mini"), modelItems(modelComboBox))
        assertFalse(modelItems(modelComboBox).contains("gpt-5.5"))
        assertFalse(modelItems(modelComboBox).contains("local-model-4"))

        configurable.disposeUIResources()
    }

    fun testModelSelectorRestoresLoadedListAfterSelectingFilteredItem() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.CODEX_ACP.name
        state.command = "custom-codex-acp"

        val configurable = testConfigurable { _, _, _, _ ->
            Result.success(listOf("gpt-5.5", "gpt-5.4", "gpt-5.4-mini"))
        }
        val component = configurable.createComponent()
        val modelComboBox = modelComboBox(component)
        val editorComponent = modelComboBox.editor.editorComponent as JTextComponent

        editorComponent.text = "5.4-mini"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(listOf("gpt-5.4-mini"), modelItems(modelComboBox))

        modelComboBox.selectedItem = "gpt-5.4-mini"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(listOf("Agent default", "gpt-5.5", "gpt-5.4", "gpt-5.4-mini"), modelItems(modelComboBox))
        assertEquals("gpt-5.4-mini", editorComponent.text)
        configurable.apply()
        assertEquals("gpt-5.4-mini", CommitGenerationSettings.getInstance().state.model)

        configurable.disposeUIResources()
    }

    fun testOllamaCloudModelSelectorKeepsAllLoadedModelsAfterSelection() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.OPENCODE.name
        val loadedModels = listOf(
            "ollama-cloud/deepseek-v4-pro",
            "ollama-cloud/deepseek-v4-flash",
            "ollama-cloud/qwen3-coder-plus",
            "local/llama3.2",
        )
        val configurable = testConfigurable { _, _, _, _ -> Result.success(loadedModels) }
        val component = configurable.createComponent()
        val modelComboBox = modelComboBox(component)
        val editorComponent = modelComboBox.editor.editorComponent as JTextComponent

        editorComponent.text = "ollama-cloud"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(loadedModels.take(3), modelItems(modelComboBox))

        modelComboBox.selectedItem = "ollama-cloud/qwen3-coder-plus"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(listOf("Agent default") + loadedModels, modelItems(modelComboBox))
        assertEquals("ollama-cloud/qwen3-coder-plus", editorComponent.text)

        configurable.disposeUIResources()
    }

    fun testMainSettingsPageDoesNotContainOpencodeConfigButton() {
        val configurable = testConfigurable { _, _, _, _ -> Result.success(emptyList()) }
        val component = configurable.createComponent()

        assertTrue(descendantsOfType(component, JButton::class.java).none { it.text == "Open opencode.jsonc" })
        configurable.disposeUIResources()
    }

    fun testCodexSettingsPathLoadsModelsWhenStoredCommandIsExplicitAcpCommand() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.CODEX_ACP.name
        state.command = "/opt/commit-ninja/bin/custom-codex-acp"

        val configurable = testConfigurable { profile, command, _, _ ->
            assertEquals(AgentProfile.CODEX_ACP, profile)
            assertEquals("/opt/commit-ninja/bin/custom-codex-acp", command)
            if (command == "/opt/commit-ninja/bin/custom-codex-acp") {
                Result.success(listOf("gpt-5.5", "gpt-5.4", "gpt-5.4-mini"))
            } else {
                Result.failure(IllegalStateException("unexpected ACP command: $command"))
            }
        }
        val component = configurable.createComponent()
        val modelComboBox = modelComboBox(component)
        val editorComponent = modelComboBox.editor.editorComponent as JTextComponent

        assertEquals(listOf("Agent default", "gpt-5.5", "gpt-5.4", "gpt-5.4-mini"), modelItems(modelComboBox))
        assertEquals("Loaded 3 model choices from the ACP agent.", modelStatusLabel(component).text)

        editorComponent.text = "5.4"
        UIUtil.dispatchAllInvocationEvents()

        assertEquals(listOf("gpt-5.4", "gpt-5.4-mini"), modelItems(modelComboBox))

        configurable.disposeUIResources()
    }

    fun testSwitchingProfileClearsStaleSelectedModelAndAppliesDefault() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.OPENCODE.name
        state.model = "ollama-cloud/deepseek-v4-pro"
        state.command = "custom-codex-acp"

        val configurable = testConfigurable { profile, _, _, _ ->
            Result.success(
                when (profile) {
                    AgentProfile.CODEX_ACP -> listOf("gpt-5.5", "gpt-5.4", "gpt-5.4-mini")
                    else -> listOf("ollama-cloud/deepseek-v4-pro")
                },
            )
        }
        val component = configurable.createComponent()
        val profileComboBox = profileComboBox(component)
        val modelComboBox = modelComboBox(component)
        val editorComponent = modelComboBox.editor.editorComponent as JTextComponent

        assertEquals("ollama-cloud/deepseek-v4-pro", editorComponent.text)

        selectProfile(profileComboBox, AgentProfile.CODEX_ACP)

        assertEquals(listOf("Agent default", "gpt-5.5", "gpt-5.4", "gpt-5.4-mini"), modelItems(modelComboBox))
        assertEquals("Agent default", modelComboBox.selectedItem)
        assertEquals("Agent default", editorComponent.text)

        configurable.disposeUIResources()
    }

    fun testStaleBackgroundModelLoadDoesNotOverwriteNewerProfileOptionsOrSelection() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.OPENCODE.name
        state.model = "ollama-cloud/deepseek-v4-pro"
        state.command = "custom-codex-acp"
        val queuedExecutor = QueuedExecutor()

        val configurable = CommitGenerationConfigurable(
            modelOptionsLoader = { profile, _, _, _ ->
                Result.success(
                    when (profile) {
                        AgentProfile.OPENCODE -> listOf("ollama-cloud/deepseek-v4-pro")
                        AgentProfile.CODEX_ACP -> listOf("gpt-5.5", "gpt-5.4", "gpt-5.4-mini")
                        else -> emptyList()
                    },
                )
            },
            backgroundExecutor = queuedExecutor,
            uiDispatcher = Runnable::run,
        )
        val component = configurable.createComponent()
        val profileComboBox = profileComboBox(component)
        val modelComboBox = modelComboBox(component)

        assertEquals(1, queuedExecutor.pendingCount)
        selectProfile(profileComboBox, AgentProfile.CODEX_ACP)
        assertEquals(2, queuedExecutor.pendingCount)

        queuedExecutor.runFirst()

        assertEquals(listOf("Agent default"), modelItems(modelComboBox))
        assertEquals("Loading models from the ACP agent...", modelStatusLabel(component).text)

        queuedExecutor.runFirst()

        assertEquals(listOf("Agent default", "gpt-5.5", "gpt-5.4", "gpt-5.4-mini"), modelItems(modelComboBox))
        assertEquals("Agent default", modelComboBox.selectedItem)
        assertEquals("Loaded 3 model choices from the ACP agent.", modelStatusLabel(component).text)

        configurable.disposeUIResources()
    }

    fun testClaudeModelLoadFailureKeepsExistingSelectorState() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.CLAUDE_AGENT_ACP.name
        state.model = "sonnet"
        state.command = "claude-acp"
        var loadCount = 0

        val configurable = testConfigurable { _, _, _, _ ->
            loadCount += 1
            if (loadCount == 1) Result.success(listOf("default", "sonnet")) else Result.failure(IllegalStateException("explicit Claude ACP command not available"))
        }
        val component = configurable.createComponent()
        val modelComboBox = modelComboBox(component)
        val loadButton = descendantsOfType(component, JButton::class.java)
            .single { it.text == "Load models" }

        loadButton.doClick()

        assertEquals(listOf("Agent default", "default", "sonnet"), modelItems(modelComboBox))
        assertEquals("sonnet", modelComboBox.selectedItem)

        configurable.disposeUIResources()
    }

    fun testClaudeAndCodexProfilesAreConfiguredWithSelectedModelAndDefaultGenerationCommand() {
        val state = CommitGenerationSettings.getInstance().state

        state.profileName = AgentProfile.CLAUDE_AGENT_ACP.name
        state.command = ""
        state.model = "sonnet"
        var diagnostic = CommitGenerationSettings.getInstance().configurationDiagnostic()
        assertTrue(diagnostic.isConfigured)
        assertNull(diagnostic.reason)
        assertTrue(diagnostic.hasGenerationCommand)
        assertEquals("npx", CommitGenerationSettings.getInstance().resolvedCommand)
        assertEquals("-y @zed-industries/claude-agent-acp", CommitGenerationSettings.getInstance().resolvedArguments)

        state.profileName = AgentProfile.CODEX_ACP.name
        state.command = ""
        state.model = "gpt-5.4-mini"
        diagnostic = CommitGenerationSettings.getInstance().configurationDiagnostic()
        assertTrue(diagnostic.isConfigured)
        assertNull(diagnostic.reason)
        assertTrue(diagnostic.hasGenerationCommand)
        assertEquals("npx", CommitGenerationSettings.getInstance().resolvedCommand)
        assertEquals("-y @zed-industries/codex-acp", CommitGenerationSettings.getInstance().resolvedArguments)

        state.profileName = AgentProfile.JUNIE_ACP.name
        state.command = ""
        state.arguments = ""
        state.model = ""
        diagnostic = CommitGenerationSettings.getInstance().configurationDiagnostic()
        assertTrue(diagnostic.isConfigured)
        assertNull(diagnostic.reason)
        assertTrue(diagnostic.hasGenerationCommand)
        assertTrue(diagnostic.hasModelLoadCommand)
        assertEquals("junie", CommitGenerationSettings.getInstance().resolvedCommand)
        assertEquals("--acp true", CommitGenerationSettings.getInstance().resolvedArguments)
    }

    fun testExplicitCommandProvidesModelLoadCommandForProfileWithoutDefaultModelCommand() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.CLAUDE_AGENT_ACP.name
        state.command = "claude-acp"

        val diagnostic = CommitGenerationSettings.getInstance().configurationDiagnostic()

        assertTrue(diagnostic.hasGenerationCommand)
        assertTrue(diagnostic.hasModelLoadCommand)
    }

    fun testCodexProfileWithoutExplicitAcpCommandLoadsModelsWithDefaultModelCommand() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.CODEX_ACP.name
        val loadRequests = mutableListOf<Pair<String, List<String>>>()

        val configurable = testConfigurable { profile, command, arguments, _ ->
            assertEquals(AgentProfile.CODEX_ACP, profile)
            loadRequests += command to arguments
            Result.success(listOf("gpt-5.4-mini"))
        }
        val component = configurable.createComponent()

        assertEquals(listOf("codex" to listOf("debug", "models", "--bundled")), loadRequests)
        assertEquals(listOf("Agent default", "gpt-5.4-mini"), modelItems(modelComboBox(component)))
        assertEquals(
            "Loaded 1 model choices from the ACP agent.",
            modelStatusLabel(component).text,
        )

        configurable.disposeUIResources()
    }

    fun testCodexModelSelectionMarksSettingsConfiguredWithDefaultGenerationCommand() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.CODEX_ACP.name

        val configurable = testConfigurable { profile, _, _, _ ->
            assertEquals(AgentProfile.CODEX_ACP, profile)
            Result.success(listOf("gpt-5.4-mini"))
        }
        val component = configurable.createComponent()
        val modelComboBox = modelComboBox(component)

        modelComboBox.selectedItem = "gpt-5.4-mini"
        configurable.apply()

        val diagnostic = CommitGenerationSettings.getInstance().configurationDiagnostic()
        assertTrue(diagnostic.isConfigured)
        assertNull(diagnostic.reason)
        assertTrue(diagnostic.hasGenerationCommand)
        assertTrue(diagnostic.hasModelLoadCommand)
        assertTrue(diagnostic.hasSelectedModel)
        assertEquals("npx", CommitGenerationSettings.getInstance().resolvedCommand)
        assertEquals("-y @zed-industries/codex-acp", CommitGenerationSettings.getInstance().resolvedArguments)
        assertEquals("gpt-5.4-mini", CommitGenerationSettings.getInstance().state.model)

        configurable.disposeUIResources()
    }

    fun testJunieProfileLoadsWithoutModelChoicesInSettingsUi() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.JUNIE_ACP.name
        val loadRequests = mutableListOf<Triple<AgentProfile, String, List<String>>>()

        val configurable = testConfigurable { profile, command, arguments, _ ->
            loadRequests += Triple(profile, command, arguments)
            Result.success(emptyList())
        }
        val component = configurable.createComponent()

        assertEquals(listOf(Triple(AgentProfile.JUNIE_ACP, "junie", emptyList<String>())), loadRequests)
        assertEquals(listOf("Agent default"), comboBoxItems(modelComboBox(component)))
        assertEquals("The ACP agent did not report model choices. Agent default will be used.", modelStatusLabel(component).text)

        configurable.disposeUIResources()
    }

    private fun testConfigurable(
        loader: (AgentProfile, String, List<String>, String?) -> Result<List<String>>,
    ): CommitGenerationConfigurable =
        CommitGenerationConfigurable(
            modelOptionsLoader = loader,
            backgroundExecutor = Runnable::run,
            uiDispatcher = Runnable::run,
        )

    private fun profileComboBox(root: Component): JComboBox<*> =
        descendantsOfType(root, JComboBox::class.java).single { comboBox ->
            comboBoxItems(comboBox).containsAll(AgentProfile.entries.map { it.displayName })
        }

    private fun selectProfile(comboBox: JComboBox<*>, profile: AgentProfile) {
        comboBox.selectedItem = (0 until comboBox.itemCount)
            .map { comboBox.getItemAt(it) }
            .single { it.toString() == profile.displayName }
    }

    private fun modelComboBox(root: Component): JComboBox<*> =
        descendantsOfType(root, JComboBox::class.java).single { comboBox ->
            (0 until comboBox.itemCount).any { comboBox.getItemAt(it) == "Agent default" }
        }

    private fun languageComboBox(root: Component): JComboBox<*> =
        descendantsOfType(root, JComboBox::class.java).single { comboBox ->
            (0 until comboBox.itemCount).any { comboBox.getItemAt(it) == "Republic of Korea" } ||
                (comboBox.editor.item?.toString() in CommitLanguageRegion.entries.map { it.displayName })
        }

    private fun modelStatusLabel(root: Component): JLabel =
        descendantsOfType(root, JLabel::class.java).single { label ->
            val text = label.text.orEmpty()
            text.startsWith("Choose Agent default") ||
                text.startsWith("Loading models") ||
                text.startsWith("Loaded ") ||
                text.startsWith("Could not load models") ||
                text.startsWith("The ACP agent did not report") ||
                text.startsWith("Select an ACP agent profile") ||
                text.startsWith("The selected profile has no command") ||
                text.startsWith("Loaded 1 model choices. Commit generation still needs")
        }

    private fun modelItems(comboBox: JComboBox<*>): List<String> =
        comboBoxItems(comboBox)

    private fun comboBoxItems(comboBox: JComboBox<*>): List<String> =
        (0 until comboBox.itemCount).map { comboBox.getItemAt(it).toString() }

    private fun languageRegionDisplayNames(): List<String> =
        CommitLanguageRegion.entries.map { it.displayName }

    private fun descendantLabelTexts(root: Component): List<String> =
        descendantsOfType(root, JLabel::class.java).mapNotNull { label -> label.text }

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

    private class QueuedExecutor : java.util.concurrent.Executor {
        private val tasks = ArrayDeque<Runnable>()

        val pendingCount: Int
            get() = tasks.size

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runFirst() {
            tasks.removeFirst().run()
        }
    }
}
