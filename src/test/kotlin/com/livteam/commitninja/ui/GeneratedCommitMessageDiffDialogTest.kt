package com.livteam.commitninja.ui

import junit.framework.TestCase

class GeneratedCommitMessageDiffDialogTest : TestCase() {
    fun testApplySelectionIsSetOnlyAfterApply() {
        val selection = GeneratedCommitMessageDiffDialogSelection()

        assertFalse(selection.isApplySelected)

        selection.selectApply()

        assertTrue(selection.isApplySelected)
    }

    fun testCancelOrCloseClearsApplySelection() {
        val selection = GeneratedCommitMessageDiffDialogSelection()

        selection.selectApply()
        selection.clear()

        assertFalse(selection.isApplySelected)
    }

    fun testDialogKeepsDialogWrapperDisposeFlow() {
        val declaresDispose = GeneratedCommitMessageDiffDialog::class.java.declaredMethods.any { it.name == "dispose" }

        assertFalse(declaresDispose)
    }
}
