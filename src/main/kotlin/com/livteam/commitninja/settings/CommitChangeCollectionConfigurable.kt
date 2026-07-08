package com.livteam.commitninja.settings

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.livteam.commitninja.MyBundle
import java.awt.Dimension
import javax.swing.JComponent

class CommitChangeCollectionConfigurable : SearchableConfigurable {
    private var component: JComponent? = null
    private val patchExcludedFilePatternsArea = JBTextArea().apply {
        lineWrap = false
        minimumSize = Dimension(0, 220)
    }
    private val maxCommitListSizeField = JBTextField()

    override fun getDisplayName(): String = MyBundle["settings.changeCollection.displayName"]

    override fun getId(): String = "com.livteam.commitninja.settings.changeCollection"

    override fun createComponent(): JComponent {
        component = panel {
            group(MyBundle["settings.changeCollection.group"]) {
                row {
                    comment(MyBundle["settings.changeCollection.description"])
                }
                row(MyBundle["settings.changeCollection.maxCommitListSize"]) {
                    cell(maxCommitListSizeField)
                        .align(Align.FILL)
                        .comment(MyBundle["settings.changeCollection.maxCommitListSize.comment"])
                }
                row {
                    label(MyBundle["settings.changeCollection.patchExcludedFilePatterns"])
                        .comment(MyBundle["settings.changeCollection.patchExcludedFilePatterns.comment"])
                }
                row {
                    cell(JBScrollPane(patchExcludedFilePatternsArea))
                        .align(Align.FILL)
                }.resizableRow()
            }
        }
        reset()
        return component!!
    }

    override fun isModified(): Boolean {
        val settings = CommitChangeCollectionSettings.getInstance()
        return settings.state.patchExcludedFilePatterns.orEmpty() != patchExcludedFilePatternsArea.text.trim() ||
            settings.state.maxCommitListSize != parsedMaxCommitListSizeOrNull()
    }

    override fun apply() {
        CommitChangeCollectionSettings.invalidPatchExcludedFilePattern(patchExcludedFilePatternsArea.text.trim())?.let {
            throw ConfigurationException(MyBundle["settings.changeCollection.patchExcludedFilePatterns.invalid", it])
        }
        val settings = CommitChangeCollectionSettings.getInstance()
        settings.state.patchExcludedFilePatterns = patchExcludedFilePatternsArea.text.trim()
        settings.state.maxCommitListSize = parseMaxCommitListSize()
    }

    override fun reset() {
        val settings = CommitChangeCollectionSettings.getInstance()
        patchExcludedFilePatternsArea.text = settings.state.patchExcludedFilePatterns.orEmpty()
        patchExcludedFilePatternsArea.caretPosition = 0
        maxCommitListSizeField.text = settings.state.maxCommitListSize.toString()
    }

    override fun disposeUIResources() {
        component = null
    }

    private fun parsedMaxCommitListSizeOrNull(): Int? =
        maxCommitListSizeField.text.trim().toIntOrNull()

    private fun parseMaxCommitListSize(): Int {
        val value = parsedMaxCommitListSizeOrNull()
            ?: throw ConfigurationException(MyBundle["settings.changeCollection.maxCommitListSize.invalid"])
        if (value < 0) {
            throw ConfigurationException(MyBundle["settings.changeCollection.maxCommitListSize.invalid"])
        }
        return value
    }
}
