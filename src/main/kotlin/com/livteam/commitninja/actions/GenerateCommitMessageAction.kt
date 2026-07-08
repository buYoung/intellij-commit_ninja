package com.livteam.commitninja.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
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
import com.livteam.commitninja.settings.CommitChangeCollectionSettings
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
    private val updateDiagnosticThrottle = CommitMessageActionDiagnosticThrottle(UPDATE_DIAGNOSTIC_INTERVAL_MS)

    override fun update(event: AnActionEvent) {
        val snapshot = event.enablementSnapshot(
            entryPoint = "GenerateCommitMessageAction.update",
            hasCommitWorkflowUi = null,
            checkedChangeCount = CommitMessageActionCheckedChangeDiagnostics.forUpdate(),
        )
        event.presentation.isVisible = snapshot.state.isVisible
        event.presentation.isEnabled = snapshot.state.isEnabled
        event.presentation.text = MyBundle["action.generateCommitMessage.text"]
        event.presentation.description = MyBundle["action.generateCommitMessage.description"]
        logEnablement(snapshot, isThrottled = true)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val snapshot = event.enablementSnapshot(
            entryPoint = "GenerateCommitMessageAction.actionPerformed",
            hasCommitWorkflowUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI) != null,
            checkedChangeCount = null,
        )
        logEnablement(snapshot, isThrottled = false)
        val project = event.project ?: return
        if (project.isDisposed) return
        val commitMessageControl = event.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) ?: return
        val settingsDiagnostic = CommitGenerationSettings.getInstance().configurationDiagnostic()
        if (!settingsDiagnostic.isConfigured) {
            CommitGenerationNotifications.notifyFailure(project, GenerationFailureType.SETTINGS_MISSING)
            return
        }
        val maxCommitListSize = CommitChangeCollectionSettings.getInstance().resolvedMaxCommitListSize
        val preflightCheckedChangeCount = changesProvider.checkedChangeCount(event)
        if (maxCommitListSize != null &&
            preflightCheckedChangeCount != null &&
            preflightCheckedChangeCount > maxCommitListSize
        ) {
            LOG.warn(
                "Commit Ninja diagnostic: executionSkipped entryPoint=GenerateCommitMessageAction.actionPerformed " +
                    "reason=COMMIT_LIST_TOO_LARGE checkedChangeCount=$preflightCheckedChangeCount " +
                    "maxCommitListSize=$maxCommitListSize",
            )
            CommitGenerationNotifications.notifyFailure(project, GenerationFailureType.COMMIT_LIST_TOO_LARGE)
            return
        }
        val changes = changesProvider.collect(event)
        val checkedChangeCount = CommitMessageActionCheckedChangeDiagnostics.forInvocation(changes.size)
        LOG.warn(
            "Commit Ninja diagnostic: executionPreflight entryPoint=GenerateCommitMessageAction.actionPerformed " +
                "collectedChangeCount=${changes.size} checkedChangeCount=$checkedChangeCount " +
                "settingsConfigured=${snapshot.input.isConfigured} " +
                "settingsConfigurationReason=${snapshot.input.settingsConfigurationReason ?: "NONE"} " +
                "commitWorkflowUiMissing=${snapshot.input.hasCommitWorkflowUi?.not() ?: "UNKNOWN"} " +
                "commitMessageControlMissing=${!snapshot.input.hasCommitMessageControl}",
        )
        if (changes.isEmpty()) {
            CommitGenerationNotifications.notifyFailure(project, GenerationFailureType.NO_CHECKED_CHANGES)
            return
        }
        if (!InProgress.markStarted(commitMessageControl)) {
            LOG.warn(
                "Commit Ninja diagnostic: executionSkipped entryPoint=GenerateCommitMessageAction.actionPerformed " +
                    "reason=GENERATION_IN_PROGRESS checkedChangeCount=$checkedChangeCount",
            )
            return
        }
        LOG.warn(
            "Commit Ninja diagnostic: executionStarting entryPoint=GenerateCommitMessageAction.actionPerformed " +
                "collectedChangeCount=${changes.size} checkedChangeCount=$checkedChangeCount",
        )
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

    private fun AnActionEvent.enablementSnapshot(
        entryPoint: String,
        hasCommitWorkflowUi: Boolean?,
        checkedChangeCount: Int?,
    ): EnablementSnapshot {
        val eventProject = project
        val commitMessageControl = getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
        val settingsDiagnostic = CommitGenerationSettings.getInstance().configurationDiagnostic()
        val input = CommitMessageActionEnablementInput(
            hasProject = eventProject != null,
            isProjectDisposed = eventProject?.isDisposed == true,
            hasCommitMessageControl = commitMessageControl != null,
            hasCommitWorkflowUi = hasCommitWorkflowUi,
            isConfigured = settingsDiagnostic.isConfigured,
            settingsConfigurationReason = settingsDiagnostic.reason,
            settingsProfileName = settingsDiagnostic.profileId,
            settingsHasGenerationCommand = settingsDiagnostic.hasGenerationCommand,
            settingsHasModelLoadCommand = settingsDiagnostic.hasModelLoadCommand,
            settingsHasSelectedModel = settingsDiagnostic.hasSelectedModel,
            isGenerating = commitMessageControl != null && InProgress.isGenerating(commitMessageControl),
            checkedChangeCount = checkedChangeCount,
        )
        return EnablementSnapshot(
            entryPoint = entryPoint,
            input = input,
            state = CommitMessageActionEnablement.evaluate(input),
        )
    }

    private fun logEnablement(snapshot: EnablementSnapshot, isThrottled: Boolean) {
        val message = CommitMessageActionDiagnostics.formatState(
            entryPoint = snapshot.entryPoint,
            input = snapshot.input,
            state = snapshot.state,
        )
        if (!isThrottled || updateDiagnosticThrottle.shouldLog(message, System.currentTimeMillis())) {
            LOG.warn(message)
        }
    }

    private data class EnablementSnapshot(
        val entryPoint: String,
        val input: CommitMessageActionEnablementInput,
        val state: CommitMessageActionEnablementState,
    )

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

    private companion object {
        val LOG: Logger = Logger.getInstance(GenerateCommitMessageAction::class.java)
        const val UPDATE_DIAGNOSTIC_INTERVAL_MS = 30_000L
    }
}
