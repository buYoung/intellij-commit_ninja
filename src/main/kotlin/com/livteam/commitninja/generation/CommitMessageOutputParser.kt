package com.livteam.commitninja.generation

object CommitMessageOutputParser {
    private val candidatePrefix = Regex("^\\s*(candidate|option|message)\\s*\\d*\\s*[:.)-]\\s*", RegexOption.IGNORE_CASE)
    private val explanatoryPrefix = Regex("^\\s*(here is|here's|commit message|generated commit message)\\b.*", RegexOption.IGNORE_CASE)

    fun parse(output: String): String? {
        val unfenced = output.trim()
            .removeSurrounding("```")
            .lineSequence()
            .filterNot { it.trim().equals("```") }
            .filterNot { it.trim().matches(Regex("^```[a-zA-Z0-9_-]*$")) }
            .filterNot { explanatoryPrefix.matches(it) }
            .map { it.replace(candidatePrefix, "") }
            .joinToString("\n")
            .trim()

        if (unfenced.isBlank()) return null
        if (unfenced.contains("\n---\n") || Regex("(?im)^\\s*(option|candidate)\\s+2\\b").containsMatchIn(output)) return null
        if (unfenced.length > 4000) return null
        return unfenced
    }
}
