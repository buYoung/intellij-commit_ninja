package com.livteam.commitninja.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JButton
import javax.xml.parsers.DocumentBuilderFactory

class CommitOpencodeConfigurableTest : BasePlatformTestCase() {
    fun testOpencodeSettingsPageContainsOpenConfigButton() {
        val configurable = CommitOpencodeConfigurable()
        val component = configurable.createComponent()

        assertTrue(descendantsOfType(component, JButton::class.java).any { it.text == "Open opencode.jsonc" })
        configurable.disposeUIResources()
    }

    fun testOpencodeConfigPathUsesUserHome() {
        val configPath = CommitOpencodeConfigurable.resolveOpencodeConfigPath("/Users/example")

        assertEquals("/Users/example/.config/opencode/opencode.jsonc", configPath.toString())
    }

    fun testPluginXmlRegistersOpencodeAsCommitNinjaChildConfigurable() {
        val pluginXml = Paths.get("src/main/resources/META-INF/plugin.xml")
        assertTrue("plugin.xml must exist at $pluginXml", Files.isRegularFile(pluginXml))
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(pluginXml.toFile())
        val configurables = document.getElementsByTagName("applicationConfigurable")
        var foundOpencodeConfigurable = false

        for (index in 0 until configurables.length) {
            val attributes = configurables.item(index).attributes
            if (attributes.getNamedItem("instance")?.nodeValue == "com.livteam.commitninja.settings.CommitOpencodeConfigurable") {
                foundOpencodeConfigurable = true
                assertEquals("com.livteam.commitninja.settings.opencode", attributes.getNamedItem("id")?.nodeValue)
                assertEquals("com.livteam.commitninja.settings", attributes.getNamedItem("parentId")?.nodeValue)
                assertEquals("Opencode", attributes.getNamedItem("displayName")?.nodeValue)
            }
        }

        assertTrue("Opencode child configurable must be registered in plugin.xml.", foundOpencodeConfigurable)
    }

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
