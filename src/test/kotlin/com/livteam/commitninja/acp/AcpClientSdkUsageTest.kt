package com.livteam.commitninja.acp

import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
    fun `ACP generation timeout is at least three minutes`() {
        val source = Path.of("src/main/kotlin/com/livteam/commitninja/acp/AcpClient.kt").readText()

        assertTrue(source.contains("const val GENERATION_TIMEOUT_MILLIS = 180_000L"))
    }

    @Test
    fun `ACP permission policy allows read only tool permission`() {
        val decision = AcpPermissionPolicy.decide(
            toolCall = toolCall(ToolKind.READ),
            permissions = listOf(
                permissionOption("allow-read-always", PermissionOptionKind.ALLOW_ALWAYS),
                permissionOption("allow-read-once", PermissionOptionKind.ALLOW_ONCE),
            ),
        )

        assertEquals("selected", decision.outcome)
        assertEquals(
            RequestPermissionOutcome.Selected(PermissionOptionId("allow-read-once")),
            decision.response.outcome,
        )
    }

    @Test
    fun `ACP permission policy allows search and fetch tool permissions`() {
        listOf(ToolKind.SEARCH, ToolKind.FETCH).forEach { toolKind ->
            val optionId = "allow-${toolKind.name.lowercase()}-once"
            val decision = AcpPermissionPolicy.decide(
                toolCall = toolCall(toolKind),
                permissions = listOf(permissionOption(optionId, PermissionOptionKind.ALLOW_ONCE)),
            )

            assertEquals("selected", decision.outcome)
            assertEquals(
                RequestPermissionOutcome.Selected(PermissionOptionId(optionId)),
                decision.response.outcome,
            )
        }
    }

    @Test
    fun `ACP permission policy allows always option when it is the only allow option`() {
        val decision = AcpPermissionPolicy.decide(
            toolCall = toolCall(ToolKind.SEARCH),
            permissions = listOf(permissionOption("allow-search-always", PermissionOptionKind.ALLOW_ALWAYS)),
        )

        assertEquals("selected", decision.outcome)
        assertEquals(
            RequestPermissionOutcome.Selected(PermissionOptionId("allow-search-always")),
            decision.response.outcome,
        )
    }

    @Test
    fun `ACP permission policy cancels read only tool permissions without allow options`() {
        listOf(ToolKind.READ, ToolKind.SEARCH, ToolKind.FETCH).forEach { toolKind ->
            val decision = AcpPermissionPolicy.decide(
                toolCall = toolCall(toolKind),
                permissions = listOf(
                    permissionOption("reject-once", PermissionOptionKind.REJECT_ONCE),
                    permissionOption("reject-always", PermissionOptionKind.REJECT_ALWAYS),
                ),
            )

            assertEquals("cancelled", decision.outcome)
            assertEquals(RequestPermissionOutcome.Cancelled, decision.response.outcome)
        }
    }

    @Test
    fun `ACP permission policy cancels write shell and unknown tool permissions`() {
        listOf(ToolKind.EDIT, ToolKind.EXECUTE, ToolKind.OTHER, null).forEach { toolKind ->
            val decision = AcpPermissionPolicy.decide(
                toolCall = toolCall(toolKind),
                permissions = listOf(permissionOption("allow-once", PermissionOptionKind.ALLOW_ONCE)),
            )

            assertEquals("cancelled", decision.outcome)
            assertEquals(RequestPermissionOutcome.Cancelled, decision.response.outcome)
        }
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

    private fun toolCall(kind: ToolKind?): SessionUpdate.ToolCallUpdate =
        SessionUpdate.ToolCallUpdate(
            toolCallId = ToolCallId("tool-call"),
            title = "Read selected files",
            kind = kind,
        )

    private fun permissionOption(
        optionId: String,
        kind: PermissionOptionKind,
    ): PermissionOption =
        PermissionOption(
            optionId = PermissionOptionId(optionId),
            name = optionId,
            kind = kind,
        )
}
