package com.livteam.commitninja.generation

import junit.framework.TestCase

class CommitMessageOutputParserTest : TestCase() {
    fun testExtractsFinalConventionalCommitFromReasoningHeavyOutput() {
        val output = """
            The user wants me to generate a commit message for the changes. Let me analyze the changes.
            The
             user wants
             me to generate
             a commit message
            
            feat(ACP 프로토콜): ACP 클라이언트가 JSON-RPC와 streamable HTTP 두 가지 프로토콜을 지원하도록 확장
            
            - JSON-RPC와 streamable HTTP 전송을 모두 처리
            - 모델 설정 흐름과 UTF-8 응답 처리를 개선
        """.trimIndent()

        assertEquals(
            """
            feat(ACP 프로토콜): ACP 클라이언트가 JSON-RPC와 streamable HTTP 두 가지 프로토콜을 지원하도록 확장
            
            - JSON-RPC와 streamable HTTP 전송을 모두 처리
            - 모델 설정 흐름과 UTF-8 응답 처리를 개선
            """.trimIndent(),
            CommitMessageOutputParser.parse(output),
        )
    }

    fun testRejectsFragmentedProseWithoutConventionalCommitHeader() {
        val output = """
            The user wants me to generate a commit message.
            The
             user wants
             me to generate
             a commit message
             from the selected changes.
        """.trimIndent()

        assertNull(CommitMessageOutputParser.parse(output))
    }
}
