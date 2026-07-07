package com.livteam.commitninja.ui

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.livteam.commitninja.generation.CommitMessageGenerationResult
import com.livteam.commitninja.generation.GenerationFailureType
import com.livteam.commitninja.notifications.CommitGenerationNotifications
import com.livteam.commitninja.settings.CommitGenerationSettings

class CommitMessageApplier {
    fun apply(project: Project, commitMessageControl: Any, result: CommitMessageGenerationResult) {
        when (result) {
            is CommitMessageGenerationResult.Failure -> {
                CommitGenerationNotifications.notifyFailure(project, result.diagnostic.type)
            }
            is CommitMessageGenerationResult.Success -> {
                val accessor = CommitMessageControlAccessor(commitMessageControl)
                val currentMessage = accessor.getText()
                if (currentMessage.isBlank()) {
                    replace(project, accessor, result.message)
                    return
                }
                if (!CommitGenerationSettings.getInstance().state.confirmBeforeReplace) {
                    replace(project, accessor, result.message)
                    return
                }
                val dialog = GeneratedCommitMessageDiffDialog(project, currentMessage, result.message)
                if (dialog.showAndGet() && dialog.isApplySelected) {
                    replace(project, accessor, result.message)
                }
            }
        }
    }

    private fun replace(project: Project, accessor: CommitMessageControlAccessor, message: String) {
        val didReplace = runCatching {
            var didSetText = false
            WriteCommandAction.runWriteCommandAction(project, "Apply Generated Commit Message", VcsDataKeys.COMMIT_MESSAGE_CONTROL.name, {
                didSetText = accessor.setText(message)
            })
            didSetText
        }.getOrDefault(false)
        if (!didReplace) {
            CommitGenerationNotifications.notifyFailure(project, GenerationFailureType.APPLY_FAILED)
        }
    }
}
