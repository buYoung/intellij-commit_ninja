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

        profileComboBox.selectedItem = AgentProfile.CODEX_ACP

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

        profileComboBox.selectedItem = AgentProfile.CODEX_ACP

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
        profileComboBox.selectedItem = AgentProfile.CODEX_ACP
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

    fun testClaudeAndCodexProfilesAreNotConfiguredWithoutExplicitAcpCommand() {
        val state = CommitGenerationSettings.getInstance().state

        state.profileName = AgentProfile.CLAUDE_AGENT_ACP.name
        state.command = ""
        assertFalse(CommitGenerationSettings.getInstance().isConfigured())

        state.profileName = AgentProfile.CODEX_ACP.name
        state.command = ""
        assertFalse(CommitGenerationSettings.getInstance().isConfigured())
    }

    fun testCodexProfileWithoutExplicitAcpCommandDoesNotLoadModels() {
        val state = CommitGenerationSettings.getInstance().state
        state.profileName = AgentProfile.CODEX_ACP.name
        var loadCount = 0

        val configurable = testConfigurable { _, _, _, _ ->
            loadCount += 1
            Result.success(listOf("gpt-5.4-mini"))
        }
        val component = configurable.createComponent()

        assertEquals(0, loadCount)
        assertEquals(listOf("Agent default"), modelItems(modelComboBox(component)))
        assertEquals("The selected profile has no command.", modelStatusLabel(component).text)

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
            (0 until comboBox.itemCount).any { comboBox.getItemAt(it) is AgentProfile }
        }

    private fun modelComboBox(root: Component): JComboBox<*> =
        descendantsOfType(root, JComboBox::class.java).single { comboBox ->
            (0 until comboBox.itemCount).any { comboBox.getItemAt(it) == "Agent default" }
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
                text.startsWith("The selected profile has no command")
        }

    private fun modelItems(comboBox: JComboBox<*>): List<String> =
        (0 until comboBox.itemCount).map { comboBox.getItemAt(it).toString() }

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
