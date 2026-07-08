package com.livteam.commitninja.generation

object CommitMessageOutputParser {
    private const val CONVENTIONAL_COMMIT_TYPES = "feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert"
    private val candidatePrefix = Regex("^\\s*(candidate|option|message)\\s*\\d*\\s*[:.)-]\\s*", RegexOption.IGNORE_CASE)
    private val explanatoryPrefix = Regex("^\\s*(here is|here's|commit message|generated commit message)\\b.*", RegexOption.IGNORE_CASE)
    private val conventionalCommitHeader = Regex(
        "^(?:(?:$CONVENTIONAL_COMMIT_TYPES)(\\([^\\r\\n()]+\\))?!?:\\s+\\S.*|" +
            "(?:$CONVENTIONAL_COMMIT_TYPES)\\([^\\r\\n()]+\\)!?)$",
    )
    private val embeddedConventionalCommitHeader = Regex(
        "(?<=[.!?。？！])((?:(?:$CONVENTIONAL_COMMIT_TYPES)(\\([^\\r\\n()]+\\))?!?:\\s+\\S.*|" +
            "(?:$CONVENTIONAL_COMMIT_TYPES)\\([^\\r\\n()]+\\)!?))$",
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

        val commitStart = candidateLines
            .mapIndexedNotNull { lineIndex, line -> line.commitStartOrNull(lineIndex) }
            .lastOrNull()
            ?: return null

        val parsedLines = listOf(commitStart.headerLine) + candidateLines.drop(commitStart.lineIndex + 1)
        val parsedMessage = parsedLines
            .joinToString("\n")
            .trim()

        if (parsedMessage.isBlank()) return null
        if (parsedMessage.contains("\n---\n") || multipleCandidateMarker.containsMatchIn(output)) return null
        if (parsedMessage.length > 4000) return null
        return parsedMessage
    }

    private fun String.commitStartOrNull(lineIndex: Int): CommitStart? {
        val trimmedLine = trim()
        if (conventionalCommitHeader.matches(trimmedLine)) {
            return CommitStart(lineIndex, trimmedLine)
        }
        val embeddedHeader = embeddedConventionalCommitHeader.find(trimmedLine)?.groupValues?.get(1)
            ?: return null
        return CommitStart(lineIndex, embeddedHeader)
    }

    private data class CommitStart(
        val lineIndex: Int,
        val headerLine: String,
    )
}
