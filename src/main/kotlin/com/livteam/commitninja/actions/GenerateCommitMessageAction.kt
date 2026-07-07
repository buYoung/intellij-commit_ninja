package com.livteam.commitninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsDataKeys
import com.livteam.commitninja.MyBundle
import com.livteam.commitninja.generation.CommitMessageGenerationResult
import com.livteam.commitninja.generation.GenerationDiagnostic
import com.livteam.commitninja.generation.GenerationFailureType
import com.livteam.commitninja.generation.CommitMessageGenerationService
import com.livteam.commitninja.notifications.CommitGenerationNotifications
import com.livteam.commitninja.settings.CommitGenerationSettings
import com.livteam.commitninja.ui.CommitMessageApplier
import com.livteam.commitninja.vcs.CheckedCommitChangesProvider
import java.util.Collections
import java.util.WeakHashMap

class GenerateCommitMessageAction : DumbAwareAction(
    MyBundle["action.generateCommitMessage.text"],
    MyBundle["action.generateCommitMessage.description"],
    AllIcons.Actions.Commit,
) {
    private val changesProvider = CheckedCommitChangesProvider()
    private val applier = CommitMessageApplier()

    override fun update(event: AnActionEvent) {
        val project = event.project
        val commitMessageControl = event.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
        val isConfigured = CommitGenerationSettings.getInstance().isConfigured()
        val hasCheckedChanges = changesProvider.hasCheckedChanges(event)
        val isGenerating = commitMessageControl != null && InProgress.isGenerating(commitMessageControl)
        event.presentation.isVisible = commitMessageControl != null
        event.presentation.isEnabled = project != null && commitMessageControl != null && isConfigured && hasCheckedChanges && !isGenerating
        event.presentation.text = MyBundle["action.generateCommitMessage.text"]
        event.presentation.description = MyBundle["action.generateCommitMessage.description"]
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val commitMessageControl = event.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) ?: return
        if (!CommitGenerationSettings.getInstance().isConfigured()) {
            CommitGenerationNotifications.notifyFailure(project, GenerationFailureType.SETTINGS_MISSING)
            return
        }
        val changes = changesProvider.collect(event)
        if (changes.isEmpty()) {
            CommitGenerationNotifications.notifyFailure(project, GenerationFailureType.NO_CHECKED_CHANGES)
            return
        }
        if (!InProgress.markStarted(commitMessageControl)) return
        object : Task.Backgroundable(project, MyBundle["progress.generateCommitMessage"], true) {
            private var generationResult: CommitMessageGenerationResult = CommitMessageGenerationResult.Failure(
                GenerationDiagnostic(GenerationFailureType.CANCELLED, "Generation did not complete."),
            )

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.checkCanceled()
                val branchName = changesProvider.currentBranchName(project)
                generationResult = project.service<CommitMessageGenerationService>()
                    .requestFromSettings(changes, branchName)
                    ?: CommitMessageGenerationResult.Failure(
                        GenerationDiagnostic(GenerationFailureType.SETTINGS_MISSING, "Settings are incomplete."),
                    )
            }

            override fun onSuccess() {
                applier.apply(project, commitMessageControl, generationResult)
            }

            override fun onCancel() {
                CommitGenerationNotifications.notifyFailure(project, GenerationFailureType.CANCELLED)
            }

            override fun onFinished() {
                InProgress.markFinished(commitMessageControl)
            }
        }.queue()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private object InProgress {
        private val controls = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

        @Synchronized
        fun isGenerating(control: Any): Boolean = controls.contains(control)

        @Synchronized
        fun markStarted(control: Any): Boolean = controls.add(control)

        @Synchronized
        fun markFinished(control: Any) {
            controls.remove(control)
        }
    }
}
