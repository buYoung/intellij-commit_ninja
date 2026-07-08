package com.livteam.commitninja.actions

import com.livteam.commitninja.settings.SettingsConfigurationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitMessageActionEnablementTest {
    @Test
    fun `enabled when project commit control settings are ready and generation is idle`() {
        val state = CommitMessageActionEnablement.evaluate(readyInput())

        assertTrue(state.isVisible)
        assertTrue(state.isEnabled)
        assertNull(state.disabledReason)
    }

    @Test
    fun `disabled when project is missing`() {
        val state = CommitMessageActionEnablement.evaluate(readyInput(hasProject = false))

        assertFalse(state.isEnabled)
        assertEquals(CommitMessageActionDisabledReason.PROJECT_MISSING, state.disabledReason)
    }

    @Test
    fun `disabled when project is disposed`() {
        val state = CommitMessageActionEnablement.evaluate(readyInput(isProjectDisposed = true))

        assertFalse(state.isEnabled)
        assertEquals(CommitMessageActionDisabledReason.PROJECT_DISPOSED, state.disabledReason)
    }

    @Test
    fun `hidden and disabled when commit message control is missing`() {
        val state = CommitMessageActionEnablement.evaluate(readyInput(hasCommitMessageControl = false))

        assertFalse(state.isVisible)
        assertFalse(state.isEnabled)
        assertEquals(CommitMessageActionDisabledReason.COMMIT_MESSAGE_CONTROL_MISSING, state.disabledReason)
    }

    @Test
    fun `disabled when settings are missing`() {
        val state = CommitMessageActionEnablement.evaluate(readyInput(isConfigured = false))

        assertFalse(state.isEnabled)
        assertEquals(CommitMessageActionDisabledReason.SETTINGS_MISSING, state.disabledReason)
    }

    @Test
    fun `disabled while generation is in progress`() {
        val state = CommitMessageActionEnablement.evaluate(readyInput(isGenerating = true))

        assertFalse(state.isEnabled)
        assertEquals(CommitMessageActionDisabledReason.GENERATION_IN_PROGRESS, state.disabledReason)
    }

    @Test
    fun `checked change count is diagnostic and does not disable action`() {
        val state = CommitMessageActionEnablement.evaluate(readyInput(checkedChangeCount = 0))

        assertTrue(state.isEnabled)
        assertNull(state.disabledReason)
    }

    @Test
    fun `update diagnostics do not collect checked changes`() {
        val checkedChangeCount = CommitMessageActionCheckedChangeDiagnostics.forUpdate()

        assertNull(checkedChangeCount)
    }

    @Test
    fun `invocation diagnostics use collected checked change count`() {
        val checkedChangeCount = CommitMessageActionCheckedChangeDiagnostics.forInvocation(collectedChangeCount = 14)

        assertEquals(14, checkedChangeCount)
    }

    @Test
    fun `enabled for codex acp when selected model and default generation command make settings complete`() {
        val state = CommitMessageActionEnablement.evaluate(
            readyInput(
                settingsProfileName = "CODEX_ACP",
                settingsHasGenerationCommand = true,
                settingsHasModelLoadCommand = true,
                settingsHasSelectedModel = true,
            ),
        )

        assertTrue(state.isVisible)
        assertTrue(state.isEnabled)
        assertNull(state.disabledReason)
    }

    @Test
    fun `diagnostic message includes visible button state fields`() {
        val input = readyInput(
            hasCommitWorkflowUi = false,
            isConfigured = false,
            settingsConfigurationReason = SettingsConfigurationReason.GENERATION_COMMAND_MISSING,
            settingsProfileName = "CODEX_ACP",
            settingsHasGenerationCommand = false,
            settingsHasModelLoadCommand = true,
            settingsHasSelectedModel = true,
            checkedChangeCount = 3,
        )
        val state = CommitMessageActionEnablement.evaluate(input)

        val message = CommitMessageActionDiagnostics.formatState(
            entryPoint = "GenerateCommitMessageAction.actionPerformed",
            input = input,
            state = state,
        )

        assertTrue(message.startsWith("Commit Ninja diagnostic:"))
        assertTrue(message.contains("entryPoint=GenerateCommitMessageAction.actionPerformed"))
        assertTrue(message.contains("projectPresent=true"))
        assertTrue(message.contains("projectDisposed=false"))
        assertTrue(message.contains("settingsConfigured=false"))
        assertTrue(message.contains("settingsConfigurationReason=GENERATION_COMMAND_MISSING"))
        assertTrue(message.contains("settingsProfile=CODEX_ACP"))
        assertTrue(message.contains("settingsHasGenerationCommand=false"))
        assertTrue(message.contains("settingsHasModelLoadCommand=true"))
        assertTrue(message.contains("settingsHasSelectedModel=true"))
        assertTrue(message.contains("checkedChangeCount=3"))
        assertTrue(message.contains("commitWorkflowUiMissing=true"))
        assertTrue(message.contains("commitMessageControlMissing=false"))
        assertTrue(message.contains("visible=true"))
        assertTrue(message.contains("enabled=false"))
        assertTrue(message.contains("disabledReason=SETTINGS_MISSING"))
    }

    @Test
    fun `diagnostic message uses NONE when action is enabled`() {
        val input = readyInput()
        val state = CommitMessageActionEnablement.evaluate(input)

        val message = CommitMessageActionDiagnostics.formatState(
            entryPoint = "GenerateCommitMessageAction.update",
            input = input,
            state = state,
        )

        assertTrue(message.contains("enabled=true"))
        assertTrue(message.contains("settingsConfigurationReason=NONE"))
        assertTrue(message.contains("settingsProfile=OPENCODE"))
        assertTrue(message.contains("disabledReason=NONE"))
    }

    @Test
    fun `diagnostic message marks commit workflow ui unknown when update skips ui lookup`() {
        val input = readyInput(hasCommitWorkflowUi = null, checkedChangeCount = null)
        val state = CommitMessageActionEnablement.evaluate(input)

        val message = CommitMessageActionDiagnostics.formatState(
            entryPoint = "GenerateCommitMessageAction.update",
            input = input,
            state = state,
        )

        assertTrue(message.contains("checkedChangeCount=null"))
        assertTrue(message.contains("commitWorkflowUiMissing=UNKNOWN"))
    }

    @Test
    fun `diagnostic throttle emits changed messages immediately and coalesces repeats`() {
        val throttle = CommitMessageActionDiagnosticThrottle(minimumIntervalMs = 1_000)
        val firstMessage = "Commit Ninja diagnostic: actionState enabled=false"
        val changedMessage = "Commit Ninja diagnostic: actionState enabled=true"

        assertTrue(throttle.shouldLog(firstMessage, nowMs = 10_000))
        assertFalse(throttle.shouldLog(firstMessage, nowMs = 10_100))
        assertTrue(throttle.shouldLog(changedMessage, nowMs = 10_200))
        assertFalse(throttle.shouldLog(changedMessage, nowMs = 10_300))
        assertTrue(throttle.shouldLog(changedMessage, nowMs = 11_200))
    }

    private fun readyInput(
        hasProject: Boolean = true,
        isProjectDisposed: Boolean = false,
        hasCommitMessageControl: Boolean = true,
        hasCommitWorkflowUi: Boolean? = true,
        isConfigured: Boolean = true,
        settingsConfigurationReason: SettingsConfigurationReason? = null,
        settingsProfileName: String = "OPENCODE",
        settingsHasGenerationCommand: Boolean = true,
        settingsHasModelLoadCommand: Boolean = true,
        settingsHasSelectedModel: Boolean = false,
        isGenerating: Boolean = false,
        checkedChangeCount: Int? = 1,
    ): CommitMessageActionEnablementInput =
        CommitMessageActionEnablementInput(
            hasProject = hasProject,
            isProjectDisposed = isProjectDisposed,
            hasCommitMessageControl = hasCommitMessageControl,
            hasCommitWorkflowUi = hasCommitWorkflowUi,
            isConfigured = isConfigured,
            settingsConfigurationReason = settingsConfigurationReason,
            settingsProfileName = settingsProfileName,
            settingsHasGenerationCommand = settingsHasGenerationCommand,
            settingsHasModelLoadCommand = settingsHasModelLoadCommand,
            settingsHasSelectedModel = settingsHasSelectedModel,
            isGenerating = isGenerating,
            checkedChangeCount = checkedChangeCount,
        )
}
