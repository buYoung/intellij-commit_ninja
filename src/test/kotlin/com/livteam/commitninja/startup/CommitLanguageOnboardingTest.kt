package com.livteam.commitninja.startup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitLanguageOnboardingTest {
    @Test
    fun `language onboarding is required only before a region is stored`() {
        assertTrue(CommitLanguageOnboarding.isSelectionRequired(null))
        assertTrue(CommitLanguageOnboarding.isSelectionRequired(""))
        assertFalse(CommitLanguageOnboarding.isSelectionRequired("NONE"))
        assertFalse(CommitLanguageOnboarding.isSelectionRequired("REPUBLIC_OF_KOREA"))
    }
}
