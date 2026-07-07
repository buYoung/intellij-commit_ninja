package com.livteam.commitninja.notifications

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.livteam.commitninja.MyBundle
import com.livteam.commitninja.generation.GenerationFailureType
import com.livteam.commitninja.settings.CommitGenerationConfigurable

object CommitGenerationNotifications {
    private const val GROUP_ID = "Commit Ninja"

    fun notifyFailure(project: Project, type: GenerationFailureType) {
        val titleKey = when (type) {
            GenerationFailureType.SETTINGS_MISSING -> "notification.settingsMissing.title"
            GenerationFailureType.NO_CHECKED_CHANGES -> "notification.noCheckedChanges.title"
            GenerationFailureType.LAUNCH_FAILED -> "notification.launchFailed.title"
            GenerationFailureType.PROTOCOL_FAILED -> "notification.protocolFailed.title"
            GenerationFailureType.TIMEOUT -> "notification.timeout.title"
            GenerationFailureType.CANCELLED -> "notification.cancelled.title"
            GenerationFailureType.PARSE_FAILED -> "notification.parseFailed.title"
            GenerationFailureType.APPLY_FAILED -> "notification.applyFailed.title"
        }
        val contentKey = when (type) {
            GenerationFailureType.SETTINGS_MISSING -> "notification.settingsMissing.content"
            GenerationFailureType.NO_CHECKED_CHANGES -> "notification.noCheckedChanges.content"
            else -> "notification.genericFailure.content"
        }
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(MyBundle[titleKey], MyBundle[contentKey], NotificationType.WARNING)
        if (type == GenerationFailureType.SETTINGS_MISSING) {
            notification.addAction(
                com.intellij.notification.NotificationAction.createSimple(MyBundle["notification.openSettings"]) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, CommitGenerationConfigurable::class.java)
                },
            )
        }
        notification.notify(project)
    }
}
