package com.livteam.commitninja.acp

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object AcpModelOptionsLoader {
    private val gson = Gson()

    fun load(
        command: String,
        arguments: List<String>,
        workingDirectory: String?,
        transport: Transport = Transport.CONTENT_LENGTH,
    ): Result<List<String>> {
        val process = try {
            ProcessBuilder(listOf(command) + arguments)
                .directory(workingDirectory?.let(::File))
                .start()
        } catch (exception: Exception) {
            return Result.failure(exception)
        }

        val executor = AppExecutorUtil.getAppExecutorService()
        return try {
            executor.submit {
                process.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { /* Drain stderr without surfacing user environment details. */ }
                }
            }
            val options = executor.submit<List<String>> {
                runProtocol(process, workingDirectory, transport)
            }.get(15, TimeUnit.SECONDS)
            Result.success(options)
        } catch (exception: TimeoutException) {
            process.destroyForcibly()
            Result.failure(exception)
        } catch (exception: Exception) {
            Result.failure(exception.cause ?: exception)
        } finally {
            process.destroy()
            process.waitFor(1, TimeUnit.SECONDS)
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun runProtocol(process: Process, workingDirectory: String?, transport: Transport): List<String> {
        val input = BufferedInputStream(process.inputStream)
        val output = BufferedOutputStream(process.outputStream)
        send(
            output,
            1,
            "initialize",
            mapOf(
                "protocolVersion" to 1,
                "clientInfo" to mapOf("name" to "Commit Ninja", "version" to "0.0.1"),
                "clientCapabilities" to emptyMap<String, Any>(),
            ),
            transport,
        )
        val initializeResponse = readUntilResponse(input, 1)
        send(
            output,
            2,
            "session/new",
            mapOf(
                "cwd" to (workingDirectory ?: System.getProperty("user.home").orEmpty()),
                "mcpServers" to emptyList<Any>(),
            ),
            transport,
        )
        val sessionResponse = readUntilResponse(input, 2)
        return (findConfigOptions(initializeResponse) + findConfigOptions(sessionResponse))
            .asSequence()
            .filter(::isModelOption)
            .flatMap { extractOptionValues(it).asSequence() }
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
    }

    private fun send(output: BufferedOutputStream, id: Int, method: String, params: Any?, transport: Transport) {
        val json = gson.toJson(mapOf("jsonrpc" to "2.0", "id" to id, "method" to method, "params" to params))
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        if (transport == Transport.CONTENT_LENGTH) {
            output.write("Content-Length: ${bytes.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        }
        output.write(bytes)
        if (transport == Transport.NEWLINE_JSON) {
            output.write('\n'.code)
        }
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
        val headerBytes = readLineBytes(input) ?: return null
        if (headerBytes.firstOrNull() == '{'.code.toByte()) {
            return JsonParser.parseString(String(headerBytes, StandardCharsets.UTF_8)).asJsonObject
        }
        val header = String(headerBytes, StandardCharsets.US_ASCII)
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
        val bytes = readLineBytes(input) ?: return null
        return String(bytes, StandardCharsets.US_ASCII)
    }

    private fun readLineBytes(input: BufferedInputStream): ByteArray? {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val next = input.read()
            if (next == -1) return if (bytes.isEmpty()) null else bytes.toByteArray()
            if (next == '\n'.code) {
                if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.removeAt(bytes.lastIndex)
                return bytes.toByteArray()
            }
            bytes.add(next.toByte())
        }
    }

    private fun isModelOption(option: JsonObject): Boolean {
        val id = option["id"]?.asString.orEmpty()
        val name = option["name"]?.asString.orEmpty()
        val title = option["title"]?.asString.orEmpty()
        val description = option["description"]?.asString.orEmpty()
        return listOf(id, name, title, description).any { it.contains("model", ignoreCase = true) }
    }

    private fun extractOptionValues(option: JsonObject): List<String> {
        val arrayKeys = listOf("options", "values", "enum")
        return arrayKeys.flatMap { key ->
            option[key]
                ?.takeIf(JsonElement::isJsonArray)
                ?.asJsonArray
                ?.mapNotNull(::extractOptionValue)
                .orEmpty()
        }
    }

    private fun extractOptionValue(element: JsonElement): String? {
        if (element.isJsonPrimitive) return element.asString
        if (!element.isJsonObject) return null
        val json = element.asJsonObject
        return json["value"]?.asString
            ?: json["id"]?.asString
            ?: json["name"]?.asString
            ?: json["label"]?.asString
            ?: json["title"]?.asString
    }

    private fun findConfigOptions(element: JsonElement?): List<JsonObject> {
        if (element == null || element.isJsonNull) return emptyList()
        if (element.isJsonObject) {
            val json = element.asJsonObject
            val directOptions = json["configOptions"]
                ?.takeIf(JsonElement::isJsonArray)
                ?.asJsonArray
                ?.filter(JsonElement::isJsonObject)
                ?.map(JsonElement::getAsJsonObject)
                .orEmpty()
            return directOptions + json.entrySet().flatMap { findConfigOptions(it.value) }
        }
        if (element.isJsonArray) {
            return element.asJsonArray.flatMap { findConfigOptions(it) }
        }
        return emptyList()
    }

    enum class Transport {
        CONTENT_LENGTH,
        NEWLINE_JSON,
    }
}
