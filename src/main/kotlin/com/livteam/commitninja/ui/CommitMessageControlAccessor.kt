package com.livteam.commitninja.ui

class CommitMessageControlAccessor(private val control: Any) {
    fun getText(): String {
        val method = control.javaClass.methods.firstOrNull {
            it.parameterCount == 0 && (it.name == "getCommitMessage" || it.name == "getComment" || it.name == "getText")
        } ?: return ""
        return method.invoke(control)?.toString().orEmpty()
    }

    fun setText(text: String): Boolean {
        val method = control.javaClass.methods.firstOrNull {
            it.parameterCount == 1 &&
                it.parameterTypes[0] == String::class.java &&
                (it.name == "setCommitMessage" || it.name == "setComment" || it.name == "setText")
        } ?: return false
        method.invoke(control, text)
        return true
    }
}
