package com.livteam.commitninja.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.livteam.commitninja.MyBundle
import javax.swing.JComponent

class CommitLanguageOnboardingDialog(project: Project?) : DialogWrapper(project, true) {
    private val languageSelector = SearchableStringComboBox(CommitLanguageRegion.entries.map { it.displayName })

    val selectedRegion: CommitLanguageRegion
        get() = CommitLanguageRegion.entries.firstOrNull { it.displayName == languageSelector.selectedValue }
            ?: CommitLanguageRegion.NONE

    init {
        title = MyBundle["language.onboarding.title"]
        languageSelector.selectedValue = CommitLanguageRegion.NONE.displayName
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(MyBundle["language.onboarding.region"]) {
            cell(languageSelector.comboBox).align(Align.FILL)
        }
        row {
            comment(MyBundle["language.onboarding.description"])
        }
    }
}
