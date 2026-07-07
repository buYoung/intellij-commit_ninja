package com.livteam.commitninja.settings

object AgentCommandLine {
    fun splitArguments(arguments: String): List<String> {
        if (arguments.isBlank()) return emptyList()
        return Regex("""[^\s"']+|"([^"]*)"|'([^']*)'""")
            .findAll(arguments)
            .map { match ->
                match.groups[1]?.value ?: match.groups[2]?.value ?: match.value
            }
            .toList()
    }
}
