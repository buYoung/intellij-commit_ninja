package com.livteam.commitninja.diagnostics

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CommitNinjaDiagnosticFiles {
    fun logDebugText(logger: Logger, label: String, content: String) {
        if (!logger.isDebugEnabled) return
        val totalChunks = content.length.ceilDiv(LOG_CHUNK_CHARS).coerceAtLeast(1)
        logger.debug("Commit Ninja diagnostic text start: label=$label, chars=${content.length}, chunks=$totalChunks")
        if (content.isEmpty()) {
            logger.debug("Commit Ninja diagnostic text chunk: label=$label, chunk=1/1\n")
        } else {
            content.chunked(LOG_CHUNK_CHARS).forEachIndexed { index, chunk ->
                logger.debug("Commit Ninja diagnostic text chunk: label=$label, chunk=${index + 1}/$totalChunks\n$chunk")
            }
        }
        logger.debug("Commit Ninja diagnostic text end: label=$label")
    }

    fun writeText(logger: Logger, fileNamePrefix: String, fileExtension: String, content: String): Path? {
        return try {
            val directory = Paths.get(PathManager.getLogPath(), DIAGNOSTIC_DIRECTORY)
            Files.createDirectories(directory)
            val fileName = "${fileNamePrefix}-${TIMESTAMP_FORMATTER.format(LocalDateTime.now())}.${fileExtension.trimStart('.')}"
            val path = directory.resolve(fileName)
            Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)
            path
        } catch (exception: Exception) {
            logger.warn(
                "Failed to write Commit Ninja diagnostic file: prefix=$fileNamePrefix, contentChars=${content.length}",
                exception,
            )
            null
        }
    }

    const val DIAGNOSTIC_DIRECTORY = "commit-ninja"

    private const val LOG_CHUNK_CHARS = 8_000
    private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

    private fun Int.ceilDiv(divisor: Int): Int = (this + divisor - 1) / divisor
}
