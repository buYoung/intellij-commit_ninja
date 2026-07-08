package com.livteam.commitninja.generation

import junit.framework.TestCase

class CommitMessageOutputParserTest : TestCase() {
    fun testAcceptsHeaderWithoutSummaryAndNumberedKoreanBody() {
        val output = """
            fix(ACP 모델 목록)
            
            1. 프로필별로 다른 방식의 모델 목록을 직접 읽도록 바꿔서 설정 화면에서 올바른 선택지를 보여준다.
        """.trimIndent()

        assertEquals(output, CommitMessageOutputParser.parse(output))
    }

    fun testAcceptsConventionalCommitHeaderWithSummary() {
        val output = """
            fix(scope): summary
            
            1. Keep existing Conventional Commit headers valid.
        """.trimIndent()

        assertEquals(output, CommitMessageOutputParser.parse(output))
    }

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

    fun testExtractsFinalCommitHeaderAttachedToPreviousProseSentence() {
        val output = """
            변경 내용을 보면 커밋 프롬프트 설정의 Git diff 지침과 출력 조건에 초점을 맞춰야 합니다.refactor(커밋 프롬프트 설정): Git diff 지침 정리

            1. Git diff 기반 커밋 메시지 생성 조건을 명확히 한다.
            2. 분석문 없이 최종 커밋 메시지만 출력하도록 프롬프트를 정리한다.
        """.trimIndent()

        assertEquals(
            """
            refactor(커밋 프롬프트 설정): Git diff 지침 정리

            1. Git diff 기반 커밋 메시지 생성 조건을 명확히 한다.
            2. 분석문 없이 최종 커밋 메시지만 출력하도록 프롬프트를 정리한다.
            """.trimIndent(),
            CommitMessageOutputParser.parse(output),
        )
    }

    fun testDoesNotTreatInlineConventionalCommitExampleAsFinalHeader() {
        val output = """
            The final answer could use refactor(커밋 프롬프트 설정): Git diff 지침 정리 as a header.
            It should include a numbered body, but this is still prose.
        """.trimIndent()

        assertNull(CommitMessageOutputParser.parse(output))
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
