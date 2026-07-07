package com.livteam.commitninja.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.ClientCapabilities
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigOptionCategory
import com.agentclientprotocol.model.SessionConfigSelectOptions
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

object AcpModelOptionsLoader {
    fun load(
        command: String,
        arguments: List<String>,
        workingDirectory: String?,
    ): Result<List<String>> {
        LOG.info(
            "Starting ACP config model load through Kotlin SDK: command=${formatCommand(command, arguments)}, workingDirectory=${workingDirectory.orEmpty()}",
        )
        val process = try {
            ProcessBuilder(listOf(command) + arguments)
                .directory(workingDirectory?.let(::File))
                .start()
        } catch (exception: Exception) {
            LOG.warn("Could not start ACP config model load: command=${formatCommand(command, arguments)}", exception)
            return Result.failure(exception)
        }

        val executor = AppExecutorUtil.getAppExecutorService()
        val stderrFuture = executor.submit<String> {
            process.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.joinToString("\n").take(MAX_DIAGNOSTIC_CHARS)
            }
        }
        return try {
            val options = executor.submit<List<String>> {
                runBlocking {
                    runSdkProtocolUntilLoadedOrExited(process, workingDirectory)
                }
            }.get(15, TimeUnit.SECONDS)
            LOG.info("Loaded ACP config model options: command=${formatCommand(command, arguments)}, count=${options.size}")
            Result.success(options)
        } catch (exception: TimeoutException) {
            process.destroyForcibly()
            val diagnostic = "ACP config model load timed out: command=${formatCommand(command, arguments)}, stderr=${stderrFuture.diagnosticStderr()}"
            LOG.warn(
                diagnostic,
            )
            Result.failure(IllegalStateException(diagnostic, exception))
        } catch (exception: Exception) {
            val cause = exception.cause ?: exception
            val diagnostic = buildString {
                append("ACP config model load failed: command=")
                append(formatCommand(command, arguments))
                append(", exitCode=")
                append(exitCodeIfAvailable(process))
                val stderr = stderrFuture.diagnosticStderr()
                if (stderr.isNotBlank()) {
                    append(", stderr=")
                    append(stderr)
                }
                append(". ")
                append(cause.message ?: cause.javaClass.simpleName)
            }
            LOG.warn(
                diagnostic,
                cause,
            )
            Result.failure(IllegalStateException(diagnostic, cause))
        } finally {
            process.destroy()
            process.waitFor(1, TimeUnit.SECONDS)
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private suspend fun runSdkProtocolUntilLoadedOrExited(process: Process, workingDirectory: String?): List<String> {
        val result = CompletableDeferred<List<String>>()
        val protocolScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        protocolScope.launch {
            runCatching { runSdkProtocol(process, workingDirectory) }
                .onSuccess { result.complete(it) }
                .onFailure { result.completeExceptionally(it) }
        }
        protocolScope.launch(Dispatchers.IO) {
            process.waitFor()
            delay(PROCESS_EXIT_GRACE_MILLIS)
            if (!result.isCompleted) {
                result.completeExceptionally(
                    IllegalStateException("ACP agent exited before model options were loaded: exitCode=${exitCodeIfAvailable(process)}"),
                )
            }
        }
        return try {
            result.await()
        } finally {
            protocolScope.cancel()
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun runSdkProtocol(process: Process, workingDirectory: String?): List<String> {
        val protocolScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val outputWriter = process.outputStream.bufferedWriter(StandardCharsets.UTF_8)
        val transport = StdioTransport(
            parentScope = protocolScope,
            ioDispatcher = Dispatchers.IO,
            input = flow {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { line -> emit(line) }
                }
            },
            output = { line ->
                withContext(Dispatchers.IO) {
                    outputWriter.write(line)
                    outputWriter.newLine()
                    outputWriter.flush()
                }
            },
            name = "CommitNinjaAcpModelOptionsTransport",
        )
        val protocol = Protocol(protocolScope, transport)
        val client = Client(protocol)

        return try {
            protocol.start()
            client.initialize(
                ClientInfo(
                    capabilities = ClientCapabilities(),
                    implementation = Implementation(name = "Commit Ninja", version = "0.1.0"),
                ),
            )
            val session = client.newSession(
                SessionCreationParameters(
                    cwd = workingDirectory ?: System.getProperty("user.home").orEmpty(),
                    mcpServers = emptyList(),
                ),
            ) { _, _ -> ModelOptionsClientSessionOperations() }
            extractModelOptions(session)
        } finally {
            transport.close()
            protocolScope.cancel()
        }
    }

    @OptIn(UnstableApi::class)
    private fun extractModelOptions(session: ClientSession): List<String> {
        val availableModels = if (session.modelsSupported) {
            session.availableModels.map { model -> model.modelId.value }
        } else {
            emptyList()
        }
        val configModels = if (session.configOptionsSupported) {
            session.configOptions.value
                .asSequence()
                .filter(::isModelOption)
                .flatMap { option -> extractOptionValues(option).asSequence() }
                .toList()
        } else {
            emptyList()
        }
        return (availableModels + configModels)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    private class ModelOptionsClientSessionOperations : ClientSessionOperations {
        override suspend fun requestPermissions(
            toolCall: SessionUpdate.ToolCallUpdate,
            permissions: List<PermissionOption>,
            _meta: JsonElement?,
        ): RequestPermissionResponse = RequestPermissionResponse(RequestPermissionOutcome.Cancelled)

        override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) {
            LOG.debug("ACP model option session notification: $notification")
        }
    }

    @OptIn(UnstableApi::class)
    private fun isModelOption(option: SessionConfigOption): Boolean {
        if (option.category == SessionConfigOptionCategory.MODEL) return true
        return listOf(option.id.value, option.name, option.description.orEmpty())
            .any { value -> value.contains("model", ignoreCase = true) }
    }

    private fun extractOptionValues(option: SessionConfigOption): List<String> {
        if (option !is SessionConfigOption.Select) return emptyList()
        return when (val options = option.options) {
            is SessionConfigSelectOptions.Flat -> options.options.map { selectOption -> selectOption.value.value }
            is SessionConfigSelectOptions.Grouped -> options.groups.flatMap { group ->
                group.options.map { selectOption -> selectOption.value.value }
            }
        }
    }

    private fun java.util.concurrent.Future<String>.diagnosticStderr(): String =
        runCatching { get(1, TimeUnit.SECONDS).trim().take(MAX_DIAGNOSTIC_CHARS) }.getOrDefault("")

    private fun exitCodeIfAvailable(process: Process): String =
        runCatching {
            if (process.isAlive) "running" else process.exitValue().toString()
        }.getOrDefault("unknown")

    private fun formatCommand(command: String, arguments: List<String>): String =
        (listOf(command) + arguments).joinToString(" ")

    private val LOG = Logger.getInstance(AcpModelOptionsLoader::class.java)
    private const val MAX_DIAGNOSTIC_CHARS = 1_000
    private const val PROCESS_EXIT_GRACE_MILLIS = 250L
}
