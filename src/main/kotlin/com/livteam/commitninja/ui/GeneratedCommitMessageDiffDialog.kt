package com.livteam.commitninja.ui

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.livteam.commitninja.MyBundle
import javax.swing.Action
import javax.swing.JComponent

class GeneratedCommitMessageDiffDialog(
    private val project: Project,
    private val currentMessage: String,
    private val generatedMessage: String,
) : DialogWrapper(project), Disposable {
    var isApplySelected: Boolean = false
        private set

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
            .createRequestPanel(project, this, null)
            .apply { setRequest(request) }
            .component
    }

    override fun createActions(): Array<Action> {
        okAction.putValue(Action.NAME, MyBundle["diff.apply"])
        return arrayOf(okAction, cancelAction)
    }

    override fun doOKAction() {
        isApplySelected = true
        super.doOKAction()
    }

    override fun dispose() = Unit
}
