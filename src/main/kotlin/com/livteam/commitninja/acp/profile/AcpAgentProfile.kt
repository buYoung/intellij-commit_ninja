package com.livteam.commitninja.acp.profile

data class AcpAgentProfile(
    val id: String,
    val displayName: String,
    val generationCommand: String,
    val generationArguments: String,
    val modelCommand: String = generationCommand,
    val modelArguments: String = "",
    val modelProvider: AcpModelProvider = AcpModelProvider.None,
)

sealed interface AcpModelProvider {
    val canListModels: Boolean

    data object None : AcpModelProvider {
        override val canListModels: Boolean = false
    }

    data class BuiltIn(val models: List<String>) : AcpModelProvider {
        override val canListModels: Boolean = true
    }

    data class Command(
        val load: (command: String, workingDirectory: String?) -> Result<List<String>>,
    ) : AcpModelProvider {
        override val canListModels: Boolean = true
    }
}
