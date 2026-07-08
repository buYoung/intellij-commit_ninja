package com.livteam.commitninja.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.ClientCapabilities
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.ModelId
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionConfigId
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigOptionCategory
import com.agentclientprotocol.model.SessionConfigOptionValue
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolKind
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import com.livteam.commitninja.generation.CommitMessageGenerationRequest
import com.livteam.commitninja.generation.CommitMessageGenerationResult
import com.livteam.commitninja.generation.CommitMessageOutputParser
import com.livteam.commitninja.generation.GenerationDiagnostic
import com.livteam.commitninja.generation.GenerationFailureType
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement

class AcpClient(private val project: Project) {
    fun generate(request: CommitMessageGenerationRequest, prompt: String): CommitMessageGenerationResult {
        val command = listOf(request.command) + request.arguments
        LOG.info(
            "Starting ACP commit generation through Kotlin SDK: profile=${request.profile.name}, model=${request.model.orEmpty()}, command=${formatCommand(command)}, workingDirectory=${request.workingDirectory.orEmpty()}, branch=${request.branchName.orEmpty()}, checkedChangeCount=${request.changes.size}",
        )
        LOG.debug("ACP commit generation input prompt:\n$prompt")
        val process = try {
            ProcessBuilder(command)
                .directory(request.workingDirectory?.let(::File))
                .start()
        } catch (exception: Exception) {
            LOG.warn(
                "Could not start ACP agent: command=${formatCommand(command)}, workingDirectory=${request.workingDirectory.orEmpty()}",
                exception,
            )
            return failure(
                GenerationFailureType.LAUNCH_FAILED,
                "Could not launch ACP agent: ${formatCommand(command)}. ${exception.message ?: exception.javaClass.simpleName}",
            )
        }

        val executor = AppExecutorUtil.getAppExecutorService()
        val stderrFuture = executor.submit<String> {
            process.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.takeWhile { true }.joinToString("\n").take(MAX_DIAGNOSTIC_CHARS)
            }
        }
        return try {
            runBlocking {
                withTimeout(GENERATION_TIMEOUT_MILLIS) {
                    runSdkProtocol(process, request, prompt)
                }
            }
        } catch (exception: TimeoutException) {
            process.destroyForcibly()
            LOG.warn("ACP commit generation timed out: ${diagnosticMessage("timeout", command, process, stderrFuture)}")
            failure(
                GenerationFailureType.TIMEOUT,
                diagnosticMessage("ACP agent did not finish before the timeout.", command, process, stderrFuture),
            )
        } catch (exception: kotlinx.coroutines.TimeoutCancellationException) {
            val exitedBeforeTimeout = runCatching { !process.isAlive }.getOrDefault(false)
            process.destroyForcibly()
            val failureType = if (exitedBeforeTimeout) GenerationFailureType.PROTOCOL_FAILED else GenerationFailureType.TIMEOUT
            val reason = if (exitedBeforeTimeout) {
                "ACP agent exited before completing the protocol."
            } else {
                "ACP agent did not finish before the timeout."
            }
            LOG.warn("ACP commit generation timed out: ${diagnosticMessage(reason, command, process, stderrFuture)}")
            failure(failureType, diagnosticMessage(reason, command, process, stderrFuture))
        } catch (exception: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            LOG.warn("ACP commit generation interrupted: ${diagnosticMessage("cancelled", command, process, stderrFuture)}")
            failure(
                GenerationFailureType.CANCELLED,
                diagnosticMessage("Commit message generation was cancelled.", command, process, stderrFuture),
            )
        } catch (exception: Exception) {
            process.destroyForcibly()
            LOG.warn(
                "ACP commit generation failed: ${
                    diagnosticMessage(
                        exception.cause?.message ?: exception.message ?: "ACP protocol failed.",
                        command,
                        process,
                        stderrFuture,
                    )
                }",
                exception,
            )
            failure(
                GenerationFailureType.PROTOCOL_FAILED,
                diagnosticMessage(
                    exception.cause?.message ?: exception.message ?: "ACP protocol failed.",
                    command,
                    process,
                    stderrFuture,
                ),
            )
        } finally {
            process.destroy()
            process.waitFor(1, TimeUnit.SECONDS)
            if (process.isAlive) process.destroyForcibly()
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun runSdkProtocol(
        process: Process,
        request: CommitMessageGenerationRequest,
        prompt: String,
    ): CommitMessageGenerationResult {
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
            name = "CommitNinjaAcpStdioTransport",
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
                    cwd = request.workingDirectory ?: project.basePath ?: "",
                    mcpServers = emptyList(),
                ),
            ) { _, _ -> CommitGenerationClientSessionOperations() }

            request.model
                ?.takeIf(String::isNotBlank)
                ?.let { model -> applyModelSelection(session, model) }

            val transcript = StringBuilder(MAX_ACP_TRANSCRIPT_CHARS.coerceAtMost(16_384))
            session.prompt(listOf(ContentBlock.Text(prompt))).collect { event ->
                when (event) {
                    is Event.SessionUpdateEvent -> collectText(event.update, transcript)
                    is Event.PromptResponseEvent -> LOG.debug("ACP prompt finished: stopReason=${event.response.stopReason}")
                }
            }
            val rawOutput = transcript.toString()
            LOG.debug("ACP collected output prompt:\n$rawOutput")
            val parsed = CommitMessageOutputParser.parse(rawOutput)
            if (parsed == null) {
                val diagnostic = parseFailureDiagnostic(rawOutput)
                LOG.warn("Failed to parse ACP commit generation output: $diagnostic")
                failure(GenerationFailureType.PARSE_FAILED, diagnostic)
            } else {
                LOG.info("Parsed ACP commit generation output: messageChars=${parsed.length}")
                LOG.debug("ACP final commit message:\n$parsed")
                CommitMessageGenerationResult.Success(parsed)
            }
        } finally {
            transport.close()
            protocolScope.cancel()
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun applyModelSelection(
        session: com.agentclientprotocol.client.ClientSession,
        model: String,
    ) {
        if (session.modelsSupported) {
            session.setModel(ModelId(model))
            LOG.info("Applied ACP model selection through session model API: model=$model")
            return
        }
        val modelConfigOption = session.configOptions.value.firstOrNull(::isModelConfigOption)
        if (modelConfigOption != null) {
            session.setConfigOption(
                SessionConfigId(modelConfigOption.id.value),
                SessionConfigOptionValue.StringValue(model),
            )
            LOG.info("Applied ACP model selection through session config option: model=$model, configId=${modelConfigOption.id.value}")
        }
    }

    private class CommitGenerationClientSessionOperations : ClientSessionOperations {
        override suspend fun requestPermissions(
            toolCall: SessionUpdate.ToolCallUpdate,
            permissions: List<PermissionOption>,
            _meta: JsonElement?,
        ): RequestPermissionResponse {
            val decision = AcpPermissionPolicy.decide(toolCall, permissions)
            LOG.info(
                "ACP permission decision: outcome=${decision.outcome}, reason=${decision.reason}, " +
                    "toolKind=${toolCall.kind?.name.orEmpty()}, toolTitle=${toolCall.title?.boundedLogValue().orEmpty()}, " +
                    "selectedOption=${decision.selectedOptionLogValue}",
            )
            return decision.response
        }

        override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) {
            LOG.debug("ACP session notification outside prompt: $notification")
        }
    }

    @OptIn(UnstableApi::class)
    private fun isModelConfigOption(option: SessionConfigOption): Boolean {
        if (option.category == SessionConfigOptionCategory.MODEL) return true
        return listOf(option.id.value, option.name, option.description.orEmpty())
            .any { value -> value.contains("model", ignoreCase = true) }
    }

    private fun collectText(update: SessionUpdate, transcript: StringBuilder) {
        when (update) {
            is SessionUpdate.AgentMessageChunk -> collectContentBlock(update.content, transcript)
            is SessionUpdate.AgentThoughtChunk -> collectContentBlock(update.content, transcript)
            is SessionUpdate.ToolCall -> update.content.orEmpty().forEach { appendTranscriptText(transcript, it.toString()) }
            else -> Unit
        }
    }

    private fun collectContentBlock(content: ContentBlock, transcript: StringBuilder) {
        if (content is ContentBlock.Text) {
            appendTranscriptText(transcript, content.text)
        }
    }

    private fun appendTranscriptText(transcript: StringBuilder, text: String) {
        val remainingChars = MAX_ACP_TRANSCRIPT_CHARS - transcript.length
        if (remainingChars <= 0) return
        transcript.append(text.take(remainingChars))
    }

    private fun failure(type: GenerationFailureType, message: String): CommitMessageGenerationResult.Failure =
        CommitMessageGenerationResult.Failure(GenerationDiagnostic(type, message))

    private fun parseFailureDiagnostic(rawOutput: String): String = buildString {
        append("ACP response did not contain a usable commit message.")
        append(" outputChars=")
        append(rawOutput.length)
        append("\noutput prompt:\n")
        append(boundedPreview(rawOutput))
    }

    private fun diagnosticMessage(
        reason: String,
        command: List<String>,
        process: Process,
        stderrFuture: java.util.concurrent.Future<String>,
    ): String {
        val stderr = runCatching {
            stderrFuture.get(1, TimeUnit.SECONDS)
        }.getOrDefault("").trim()
        return buildString {
            append(reason)
            append(" Command: ")
            append(formatCommand(command))
            runCatching {
                if (!process.isAlive) {
                    append(". Exit code: ")
                    append(process.exitValue())
                }
            }
            if (stderr.isNotBlank()) {
                append(". stderr: ")
                append(stderr.take(MAX_DIAGNOSTIC_CHARS))
            }
        }
    }

    private fun boundedPreview(value: String): String {
        val normalized = value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        val preview = normalized.take(MAX_DIAGNOSTIC_CHARS)
        val suffix = if (normalized.length > MAX_DIAGNOSTIC_CHARS) "...<truncated>" else ""
        return preview + suffix
    }

    private fun formatCommand(command: List<String>): String = command.joinToString(" ")

    private companion object {
        val LOG = Logger.getInstance(AcpClient::class.java)
        const val MAX_ACP_TRANSCRIPT_CHARS = 120_000
        const val MAX_DIAGNOSTIC_CHARS = 2_000
        const val GENERATION_TIMEOUT_MILLIS = 180_000L
    }
}

internal object AcpPermissionPolicy {
    private val readOnlyToolKinds = setOf(ToolKind.READ, ToolKind.SEARCH, ToolKind.FETCH)
    private val allowPermissionKinds = setOf(PermissionOptionKind.ALLOW_ONCE, PermissionOptionKind.ALLOW_ALWAYS)

    fun decide(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
    ): AcpPermissionDecision {
        if (toolCall.kind !in readOnlyToolKinds) {
            return AcpPermissionDecision.cancelled("non_read_only_tool")
        }
        val selectedOption = permissions
            .filter { it.kind in allowPermissionKinds }
            .minByOrNull { option ->
                when (option.kind) {
                    PermissionOptionKind.ALLOW_ONCE -> 0
                    PermissionOptionKind.ALLOW_ALWAYS -> 1
                    PermissionOptionKind.REJECT_ONCE -> 2
                    PermissionOptionKind.REJECT_ALWAYS -> 3
                }
            }
            ?: return AcpPermissionDecision.cancelled("no_allow_option")

        return AcpPermissionDecision.selected(selectedOption)
    }
}

internal data class AcpPermissionDecision(
    val response: RequestPermissionResponse,
    val outcome: String,
    val reason: String,
    val selectedOptionLogValue: String,
) {
    companion object {
        fun selected(option: PermissionOption): AcpPermissionDecision =
            AcpPermissionDecision(
                response = RequestPermissionResponse(RequestPermissionOutcome.Selected(option.optionId)),
                outcome = "selected",
                reason = "read_only_tool",
                selectedOptionLogValue = "${option.kind.name}:${option.name.boundedLogValue()}",
            )

        fun cancelled(reason: String): AcpPermissionDecision =
            AcpPermissionDecision(
                response = RequestPermissionResponse(RequestPermissionOutcome.Cancelled),
                outcome = "cancelled",
                reason = reason,
                selectedOptionLogValue = "",
            )
    }
}

private fun String.boundedLogValue(): String = take(120)
