package com.livteam.commitninja.acp

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import com.livteam.commitninja.generation.CommitMessageGenerationRequest
import com.livteam.commitninja.generation.CommitMessageGenerationResult
import com.livteam.commitninja.generation.CommitMessageOutputParser
import com.livteam.commitninja.generation.GenerationDiagnostic
import com.livteam.commitninja.generation.GenerationFailureType
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AcpClient(private val project: Project) {
    private val gson = Gson()

    fun generate(request: CommitMessageGenerationRequest, prompt: String): CommitMessageGenerationResult {
        val process = try {
            val command = listOf(request.command) + request.arguments
            ProcessBuilder(command)
                .directory(request.workingDirectory?.let(::File))
                .start()
        } catch (exception: Exception) {
            return failure(GenerationFailureType.LAUNCH_FAILED, exception.message ?: "Could not launch ACP agent.")
        }

        val executor = AppExecutorUtil.getAppExecutorService()
        executor.submit {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { /* Drain stderr without surfacing diff-adjacent content. */ }
            }
        }
        return try {
            executor.submit<CommitMessageGenerationResult> { runProtocol(process, request, prompt) }.get(60, TimeUnit.SECONDS)
        } catch (exception: TimeoutException) {
            process.destroyForcibly()
            failure(GenerationFailureType.TIMEOUT, "ACP agent did not finish before the timeout.")
        } catch (exception: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            failure(GenerationFailureType.CANCELLED, "Commit message generation was cancelled.")
        } catch (exception: Exception) {
            process.destroyForcibly()
            failure(GenerationFailureType.PROTOCOL_FAILED, exception.cause?.message ?: exception.message.orEmpty())
        } finally {
            process.destroy()
        }
    }

    private fun runProtocol(
        process: Process,
        request: CommitMessageGenerationRequest,
        prompt: String,
    ): CommitMessageGenerationResult {
        val input = BufferedInputStream(process.inputStream)
        val output = BufferedOutputStream(process.outputStream)
        send(
            output,
            1,
            "initialize",
            mapOf(
                "protocolVersion" to 1,
                "clientInfo" to mapOf("name" to "Commit Ninja"),
                "clientCapabilities" to emptyMap<String, Any>(),
            ),
        )
        val initializeResponse = readUntilResponse(input, 1)
        val sessionParams = mutableMapOf<String, Any?>(
            "cwd" to (request.workingDirectory ?: project.basePath ?: ""),
            "mcpServers" to emptyList<Any>(),
        )
        send(output, 2, "session/new", sessionParams)
        val sessionResponse = readUntilResponse(input, 2)
        val sessionId = sessionResponse["result"]?.asJsonObject?.get("sessionId")?.asString
            ?: sessionResponse["result"]?.asJsonObject?.get("id")?.asString
            ?: "commit-ninja"
        val modelConfigOption = request.model
            ?.takeIf { it.isNotBlank() }
            ?.let { findModelConfigOption(initializeResponse) ?: findModelConfigOption(sessionResponse) }
        if (modelConfigOption != null) {
            send(
                output,
                3,
                "session/set_config_option",
                mapOf(
                    "sessionId" to sessionId,
                    "configId" to modelConfigOption,
                    "value" to request.model,
                ),
            )
            readUntilResponse(input, 3)
        }
        send(
            output,
            4,
            "session/prompt",
            mapOf(
                "sessionId" to sessionId,
                "prompt" to listOf(mapOf("type" to "text", "text" to prompt)),
                "attachments" to emptyList<Any>(),
            ),
        )
        val transcript = StringBuilder(MAX_ACP_TRANSCRIPT_CHARS.coerceAtMost(16_384))
        while (process.isAlive) {
            val message = readMessage(input) ?: break
            collectText(message, transcript)
            if (message["id"]?.asInt == 4) {
                collectText(message["result"]?.asJsonObject, transcript)
                break
            }
        }
        process.destroy()
        val parsed = CommitMessageOutputParser.parse(transcript.toString())
        return if (parsed == null) {
            failure(GenerationFailureType.PARSE_FAILED, "ACP response did not contain a usable commit message.")
        } else {
            CommitMessageGenerationResult.Success(parsed)
        }
    }

    private fun send(output: BufferedOutputStream, id: Int, method: String, params: Any?) {
        val json = gson.toJson(mapOf("jsonrpc" to "2.0", "id" to id, "method" to method, "params" to params))
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        output.write("Content-Length: ${bytes.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private fun readUntilResponse(input: BufferedInputStream, id: Int): JsonObject {
        while (true) {
            val message = readMessage(input) ?: error("ACP agent closed the stream.")
            if (message["id"]?.asInt == id) {
                if (message.has("error")) error("ACP error: ${message["error"]}")
                return message
            }
        }
    }

    private fun readMessage(input: BufferedInputStream): JsonObject? {
        val header = readAsciiLine(input) ?: return null
        if (header.startsWith("{")) {
            return JsonParser.parseString(header).asJsonObject
        }
        var contentLength: Int? = null
        var line = header
        while (line.isNotEmpty()) {
            val separator = line.indexOf(':')
            if (separator > 0 && line.substring(0, separator).equals("Content-Length", ignoreCase = true)) {
                contentLength = line.substring(separator + 1).trim().toIntOrNull()
            }
            line = readAsciiLine(input) ?: return null
        }
        val length = contentLength ?: return null
        val bytes = input.readNBytes(length)
        return JsonParser.parseString(String(bytes, StandardCharsets.UTF_8)).asJsonObject
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val next = input.read()
            if (next == -1) return if (bytes.isEmpty()) null else String(bytes.toByteArray(), StandardCharsets.US_ASCII)
            if (next == '\n'.code) {
                if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.removeAt(bytes.lastIndex)
                return String(bytes.toByteArray(), StandardCharsets.US_ASCII)
            }
            bytes.add(next.toByte())
        }
    }

    private fun collectText(json: JsonObject?, transcript: StringBuilder) {
        if (json == null || transcript.length >= MAX_ACP_TRANSCRIPT_CHARS) return
        for ((key, value) in json.entrySet()) {
            if ((key == "text" || key == "content" || key == "message") && value.isJsonPrimitive) {
                appendTranscriptText(transcript, value.asString)
            } else if (value.isJsonObject) {
                collectText(value.asJsonObject, transcript)
            } else if (value.isJsonArray) {
                value.asJsonArray.filter { it.isJsonObject }.forEach { collectText(it.asJsonObject, transcript) }
            }
        }
    }

    private fun appendTranscriptText(transcript: StringBuilder, text: String) {
        val remainingChars = MAX_ACP_TRANSCRIPT_CHARS - transcript.length
        if (remainingChars <= 0) return
        val line = "$text\n"
        transcript.append(line.take(remainingChars))
    }

    private fun findModelConfigOption(message: JsonObject): String? =
        findConfigOptions(message)
            .asSequence()
            .filter { option ->
                val id = option["id"]?.asString.orEmpty()
                val name = option["name"]?.asString.orEmpty()
                val title = option["title"]?.asString.orEmpty()
                val description = option["description"]?.asString.orEmpty()
                listOf(id, name, title, description).any { it.contains("model", ignoreCase = true) }
            }
            .mapNotNull { option -> option["id"]?.asString ?: option["name"]?.asString }
            .firstOrNull()

    private fun findConfigOptions(element: JsonElement?): List<JsonObject> {
        if (element == null || element.isJsonNull) return emptyList()
        if (element.isJsonObject) {
            val json = element.asJsonObject
            val directOptions = json["configOptions"]
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.filter { it.isJsonObject }
                ?.map { it.asJsonObject }
                .orEmpty()
            return directOptions + json.entrySet().flatMap { findConfigOptions(it.value) }
        }
        if (element.isJsonArray) {
            return element.asJsonArray.flatMap { findConfigOptions(it) }
        }
        return emptyList()
    }

    private fun failure(type: GenerationFailureType, message: String): CommitMessageGenerationResult.Failure =
        CommitMessageGenerationResult.Failure(GenerationDiagnostic(type, message))

    private companion object {
        const val MAX_ACP_TRANSCRIPT_CHARS = 120_000
    }
}
