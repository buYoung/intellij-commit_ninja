package com.livteam.commitninja.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.livteam.commitninja.generation.CommitMessageGenerationResult
import com.livteam.commitninja.settings.CommitGenerationSettings

class CommitMessageApplierTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            CommitGenerationSettings.getInstance().state.confirmBeforeReplace = true
        } finally {
            super.tearDown()
        }
    }

    fun testAppliesGeneratedMessageToCommitMessageControlWhenConfirmationIsDisabled() {
        val state = CommitGenerationSettings.getInstance().state
        state.confirmBeforeReplace = false
        val control = TestCommitMessageControl("fix: old message")

        CommitMessageApplier().apply(
            project,
            control,
            CommitMessageGenerationResult.Success("feat: generated message"),
        )

        assertEquals("feat: generated message", control.getCommitMessage())
    }

    private class TestCommitMessageControl(private var commitMessage: String) {
        fun getCommitMessage(): String = commitMessage

        fun setCommitMessage(commitMessage: String) {
            this.commitMessage = commitMessage
        }
    }
}
