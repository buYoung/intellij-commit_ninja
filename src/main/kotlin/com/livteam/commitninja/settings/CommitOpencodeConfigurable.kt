package com.livteam.commitninja.settings

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.dsl.builder.panel
import com.livteam.commitninja.MyBundle
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent

class CommitOpencodeConfigurable : SearchableConfigurable {
    private var component: JComponent? = null

    override fun getDisplayName(): String = MyBundle["settings.opencode.displayName"]

    override fun getId(): String = "com.livteam.commitninja.settings.opencode"

    override fun createComponent(): JComponent {
        component = panel {
            group(MyBundle["settings.opencode.group"]) {
                row {
                    button(MyBundle["settings.opencode.openConfig"]) {
                        openOpencodeConfigFile()
                    }
                }
            }
        }
        return component!!
    }

    override fun isModified(): Boolean = false

    override fun apply() = Unit

    override fun reset() = Unit

    override fun disposeUIResources() {
        component = null
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

    internal companion object {
        fun resolveOpencodeConfigPath(userHome: String = System.getProperty("user.home")): Path =
            Paths.get(userHome).resolve(".config").resolve("opencode").resolve("opencode.jsonc")
    }
}
