package com.livteam.commitninja.ui

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.livteam.commitninja.MyBundle
import javax.swing.Action
import javax.swing.JComponent

class GeneratedCommitMessageDiffDialog(
    private val project: Project,
    private val currentMessage: String,
    private val generatedMessage: String,
) : DialogWrapper(project) {
    private val selection = GeneratedCommitMessageDiffDialogSelection()

    val isApplySelected: Boolean
        get() = selection.isApplySelected

    init {
        title = MyBundle["diff.review.title"]
        init()
    }

    override fun createCenterPanel(): JComponent {
        val contentFactory = DiffContentFactory.getInstance()
        val request = SimpleDiffRequest(
            MyBundle["diff.review.title"],
            contentFactory.create(project, currentMessage),
            contentFactory.create(project, generatedMessage),
            MyBundle["diff.current.label"],
            MyBundle["diff.generated.label"],
        )
        return DiffManager.getInstance()
            .createRequestPanel(project, disposable, null)
            .apply { setRequest(request) }
            .component
    }

    override fun createActions(): Array<Action> {
        okAction.putValue(Action.NAME, MyBundle["diff.apply"])
        return arrayOf(okAction, cancelAction)
    }

    override fun doOKAction() {
        selection.selectApply()
        super.doOKAction()
    }

    override fun doCancelAction() {
        selection.clear()
        super.doCancelAction()
    }
}

internal class GeneratedCommitMessageDiffDialogSelection {
    var isApplySelected: Boolean = false
        private set

    fun selectApply() {
        isApplySelected = true
    }

    fun clear() {
        isApplySelected = false
    }
}
