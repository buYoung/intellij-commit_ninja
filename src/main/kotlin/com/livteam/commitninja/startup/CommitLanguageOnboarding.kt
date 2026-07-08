package com.livteam.commitninja.startup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.livteam.commitninja.settings.CommitGenerationSettings
import com.livteam.commitninja.settings.CommitLanguageOnboardingDialog

object CommitLanguageOnboarding {
    fun isSelectionRequired(storedRegionName: String?): Boolean =
        storedRegionName.isNullOrBlank()

    fun scheduleIfRequired(project: Project) {
        val application = ApplicationManager.getApplication()
        if (application.isUnitTestMode || application.isHeadlessEnvironment) return

        val settings = CommitGenerationSettings.getInstance()
        if (!isSelectionRequired(settings.state.languageRegionName)) return

        application.invokeLater {
            if (project.isDisposed || !isSelectionRequired(settings.state.languageRegionName)) return@invokeLater
            val dialog = CommitLanguageOnboardingDialog(project)
            if (dialog.showAndGet()) {
                settings.state.languageRegionName = dialog.selectedRegion.name
            }
        }
    }
}
