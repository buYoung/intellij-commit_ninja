package com.livteam.commitninja.settings

object DefaultCommitPrompt {
    fun load(): String {
        val stream = javaClass.classLoader.getResourceAsStream("default_commit_message_prompt.md")
        return stream?.bufferedReader()?.use { it.readText() }?.trim().orEmpty()
    }
}
