package com.livteam.commitninja.acp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.readText

class AcpClientSdkUsageTest {
    @Test
    fun `ACP generation client is backed by official Kotlin SDK protocol classes`() {
        val source = Path.of("src/main/kotlin/com/livteam/commitninja/acp/AcpClient.kt").readText()

        assertTrue(source.contains("com.agentclientprotocol.client.Client"))
        assertTrue(source.contains("com.agentclientprotocol.protocol.Protocol"))
        assertTrue(source.contains("com.agentclientprotocol.transport.StdioTransport"))
        assertFalse(source.contains("Content-Length"))
        assertFalse(source.contains("JsonParser"))
        assertFalse(source.contains("fun send("))
        assertFalse(source.contains("fun readMessage("))
    }

    @Test
    fun `ACP model options loader uses profile specific command discovery`() {
        val source = Path.of("src/main/kotlin/com/livteam/commitninja/acp/AcpModelOptionsLoader.kt").readText()

        assertTrue(source.contains("loadOpencodeModels"))
        assertTrue(source.contains("loadCodexBundledModels"))
        assertTrue(source.contains("loadClaudeBuiltInModels"))
        assertTrue(source.contains("\"models\""))
        assertTrue(source.contains("\"debug\", \"models\", \"--bundled\""))
        assertFalse(source.contains("com.agentclientprotocol.client.Client"))
        assertFalse(source.contains("com.agentclientprotocol.protocol.Protocol"))
        assertFalse(source.contains("com.agentclientprotocol.transport.StdioTransport"))
        assertFalse(source.contains("Content-Length"))
        assertFalse(source.contains("fun send("))
        assertFalse(source.contains("fun readMessage("))
        assertFalse(source.contains("enum class Transport"))
        assertFalse(source.contains("AcpModelOptionsLoader.Transport"))
    }
}
