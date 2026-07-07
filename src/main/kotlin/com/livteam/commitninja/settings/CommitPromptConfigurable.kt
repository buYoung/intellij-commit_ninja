package com.livteam.commitninja.settings

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.livteam.commitninja.MyBundle
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

class CommitPromptConfigurable : SearchableConfigurable {
    private var component: JComponent? = null
    private val project = ProjectManager.getInstance().openProjects.firstOrNull()
        ?: ProjectManager.getInstance().defaultProject
    private val promptEditor = CommitPromptMarkdownEditorField(
        EditorFactory.getInstance().createDocument(""),
        project,
        FileTypeManager.getInstance().getFileTypeByExtension("md"),
        false,
        false,
    ).apply {
        minimumSize = Dimension(0, 360)
    }

    override fun getDisplayName(): String = MyBundle["settings.prompt.displayName"]

    override fun getId(): String = "com.livteam.commitninja.settings.prompt"

    override fun createComponent(): JComponent {
        component = panel {
            group(MyBundle["settings.prompt.group"]) {
                row {
                    comment(MyBundle["settings.prompt.description"])
                }
                row {
                    cell(promptEditor).align(Align.FILL)
                }.resizableRow()
            }
        }
        reset()
        return component!!
    }

    override fun isModified(): Boolean {
        val settings = CommitGenerationSettings.getInstance()
        settings.ensurePromptInitialized()
        return settings.state.userPrompt.orEmpty() != promptEditor.text.trim()
    }

    override fun apply() {
        val settings = CommitGenerationSettings.getInstance()
        settings.state.userPrompt = promptEditor.text.trim()
        settings.state.isPromptInitialized = true
    }

    override fun reset() {
        val settings = CommitGenerationSettings.getInstance()
        settings.ensurePromptInitialized()
        promptEditor.text = settings.state.userPrompt.orEmpty()
        promptEditor.setCaretPosition(0)
    }

    override fun disposeUIResources() {
        component = null
    }
}

internal class CommitPromptMarkdownEditorField(
    document: Document,
    private val editorProject: Project,
    fileType: FileType,
    isViewer: Boolean,
    oneLineMode: Boolean,
) : EditorTextField(document, editorProject, fileType, isViewer, oneLineMode) {
    override fun createEditor(): EditorEx {
        val editor = super.createEditor()
        editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(editorProject, fileType)
        editor.isOneLineMode = false
        editor.scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        editor.scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        editor.setBackgroundColor(editor.colorsScheme.defaultBackground)
        editor.contentComponent.background = editor.colorsScheme.defaultBackground
        editor.settings.applyPromptEditorSettings()
        return editor
    }

    internal fun createEditorForInspection(): EditorEx = createEditor()

    private fun EditorSettings.applyPromptEditorSettings() {
        isLineNumbersShown = true
        isLineMarkerAreaShown = true
        isFoldingOutlineShown = true
        isRightMarginShown = true
        isUseSoftWraps = true
        additionalLinesCount = 2
        additionalColumnsCount = 2
    }
}
