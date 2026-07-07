package com.livteam.commitninja.ui

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
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
                LOG.warn("Generated commit message was not applied: type=${result.diagnostic.type}, diagnostic=${result.diagnostic.message}")
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
        }.onFailure { exception ->
            LOG.warn("Failed to apply generated commit message", exception)
        }.getOrDefault(false)
        if (!didReplace) {
            LOG.warn("Generated commit message apply failed: setText returned false")
            CommitGenerationNotifications.notifyFailure(project, GenerationFailureType.APPLY_FAILED)
        } else {
            LOG.info("Generated commit message applied: messageChars=${message.length}")
        }
    }

    private companion object {
        val LOG = Logger.getInstance(CommitMessageApplier::class.java)
    }
}
