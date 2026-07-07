package com.livteam.commitninja.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComboBox

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
}
