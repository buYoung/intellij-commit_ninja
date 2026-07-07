package com.livteam.commitninja.generation

object CommitMessageOutputParser {
    private val candidatePrefix = Regex("^\\s*(candidate|option|message)\\s*\\d*\\s*[:.)-]\\s*", RegexOption.IGNORE_CASE)
    private val explanatoryPrefix = Regex("^\\s*(here is|here's|commit message|generated commit message)\\b.*", RegexOption.IGNORE_CASE)
    private val conventionalCommitHeader = Regex(
        "^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\\([^\\r\\n()]+\\))?!?:\\s+\\S.*$",
    )
    private val multipleCandidateMarker = Regex("(?im)^\\s*(option|candidate)\\s+2\\b")

    fun parse(output: String): String? {
        val candidateLines = output.trim()
            .removeSurrounding("```")
            .lineSequence()
            .filterNot { it.trim().equals("```") }
            .filterNot { it.trim().matches(Regex("^```[a-zA-Z0-9_-]*$")) }
            .filterNot { explanatoryPrefix.matches(it) }
            .map { it.replace(candidatePrefix, "") }
            .toList()

        val commitStartLine = candidateLines.indexOfLast { conventionalCommitHeader.matches(it.trim()) }
        if (commitStartLine < 0) return null

        val parsedMessage = candidateLines
            .drop(commitStartLine)
            .joinToString("\n")
            .trim()

        if (parsedMessage.isBlank()) return null
        if (parsedMessage.contains("\n---\n") || multipleCandidateMarker.containsMatchIn(output)) return null
        if (parsedMessage.length > 4000) return null
        return parsedMessage
    }
}
