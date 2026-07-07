---
doc-type: Feature Design Doc
profile: full
feature-name: acp-commit-message-generation
status: active
created: 2026-07-07
last-verified: 2026-07-07
verified-against: a6d6a0e
tags: [commit-message, acp, jetbrains, local-agent, opencode, claude-agent, codex]
related: []
purpose: Source of design decisions, not implementation actions
agent-readable: true
not:
  - task list
  - PR checklist
  - file-level change guide
---

# ACP Commit Message Generation Feature Design Doc

## 1. Document Intent

이 문서는 IntelliJ 기반 플러그인에서 ACP 기반 로컬 코딩 에이전트를 사용해 커밋 메시지를 생성하는 기능의 의사결정 원천이다.

문서는 사용자 경험, 기능 범위, 도메인 개념, 정책 결정, 플랫폼 제약, 실패 처리, 보안 및 권한 원칙을 정의한다. 구현 순서, 파일별 작업, 하위 작업 분해는 별도 구현 계획에서 다룬다.

---

## 2. Background / Problem

IntelliJ 계열 IDE에는 커밋 도구 창에서 변경 항목을 선택하고 커밋 메시지를 입력하는 표준 흐름이 있다. 사용자는 이미 JetBrains AI Assistant의 커밋 메시지 생성 흐름에 익숙하며, 기대하는 흐름은 “체크된 변경 목록을 기준으로, 사용자 커밋 프롬프트를 반영해, 커밋 메시지 입력칸에서 바로 생성”하는 것이다.

현재 플러그인의 목표는 특정 클라우드 LLM API에 직접 묶이지 않고, 사용자가 로컬에서 이미 사용 중인 코딩 에이전트 CLI를 ACP 에이전트로 호출하는 것이다. 이 방식은 에이전트 선택권을 사용자에게 주고, opencode, Claude Agent, Codex 같은 도구별 인증 및 모델 설정을 각 도구의 기존 생태계에 위임할 수 있다.

이 기능이 없으면 사용자는 커밋 전 변경 사항을 별도로 복사하거나 터미널 에이전트에 diff를 넘긴 뒤 결과를 다시 IDE 커밋 메시지 입력칸에 붙여 넣어야 한다. 이 흐름은 선택된 변경 목록과 실제 커밋 메시지 생성 입력이 어긋날 수 있고, 부분 커밋이나 여러 변경 목록을 다루는 IDE 워크플로와도 잘 맞지 않는다.

---

## 3. Feature Definition

```text
ACP Commit Message Generation is an IntelliJ commit workflow feature that generates a commit message from the currently checked commit items by asking a user-selected local ACP coding agent.
```

### This feature is

- IntelliJ 커밋 도구 창의 체크 상태를 커밋 메시지 생성 기준으로 삼는 기능이다.
- 사용자가 설정한 커밋 프롬프트와 체크된 변경 내용을 조합해 로컬 ACP 에이전트에 요청하는 기능이다.
- 생성 결과를 커밋 메시지 입력칸에 반영하는 IDE 내 생산성 기능이다.
- opencode, Claude Agent 어댑터, Codex 어댑터를 초기 지원 대상으로 삼는 로컬 에이전트 연동 기능이다.

### This feature is not

- 범용 AI 채팅 도구가 아니다.
- 코드를 자동 수정하거나 커밋을 자동 실행하는 기능이 아니다.
- 특정 LLM 공급자의 직접 API 클라이언트가 아니다.
- Git 전용 커밋 엔진을 새로 구현하는 기능이 아니다.

---

## 4. Goals & Non-Goals

### Goals

- JetBrains AI Assistant의 커밋 메시지 생성 경험과 유사한 흐름을 제공한다.
- 체크된 변경 항목을 생성 입력의 기준으로 삼아, 사용자가 실제 커밋하려는 내용과 메시지 생성 범위를 일치시킨다.
- 사용자가 선택한 로컬 ACP 에이전트를 통해 커밋 메시지를 생성한다.
- 사용자 커밋 프롬프트를 설정값으로 저장하고 매 생성 시 반영한다.
- MVP에서 opencode, Claude Agent 어댑터, Codex 어댑터를 구분 가능한 에이전트 프로필로 다룬다.
- 에이전트 권한은 커밋 메시지 생성에 필요한 최소 수준으로 제한한다.

### Non-Goals

- MVP에서 에이전트가 파일을 수정하거나 터미널 명령을 실행하도록 허용하지 않는다.
- MVP에서 모든 ACP 에이전트 생태계를 자동 탐지하거나 자동 설치하지 않는다.
- MVP에서 커밋 메시지 품질 평가, 다중 후보 랭킹, 자동 커밋 실행을 제공하지 않는다.
- MVP에서 원격 ACP 에이전트를 정식 지원하지 않는다.
- JetBrains AI Assistant 자체를 대체하거나 통합 인증을 공유하는 것을 목표로 하지 않는다.

---

## 5. User Model & Core Concepts

### User Model

사용자는 이 기능을 “커밋 메시지 입력칸 옆의 생성 버튼”으로 이해한다. 사용자는 체크박스로 선택한 변경 목록이 곧 생성 범위라고 기대한다.

Users think of this feature as:

- 커밋하기 직전에 누르는 메시지 생성 버튼
- 현재 체크된 변경분을 설명하는 커밋 메시지 초안 작성 도구
- 사용자가 선호하는 로컬 코딩 에이전트를 IDE 커밋 흐름에 연결하는 기능

Users should not need to understand:

- ACP의 JSON-RPC 메시지 세부 구조
- 에이전트 세션 식별자나 요청 식별자
- IDE 내부의 변경 목록 모델
- 각 에이전트 어댑터의 내부 구현 방식

### Core Concepts

| Concept | Meaning |
| ------- | ------- |
| 체크된 변경 항목 | 커밋 도구 창에서 현재 커밋 대상에 포함된 변경 항목 |
| 사용자 커밋 프롬프트 | 커밋 메시지 스타일, 언어, 형식, 주의사항을 설명하는 사용자 설정 문장 |
| 생성 아이콘 | 커밋 메시지 입력칸의 기존 메시지 액션 영역에 표시되는 커밋 메시지 생성 액션 |
| ACP 클라이언트 | IDE 플러그인 안에서 로컬 에이전트와 ACP로 통신하는 쪽 |
| ACP 에이전트 | opencode 또는 Claude/Codex 어댑터처럼 ACP를 구현한 로컬 하위 프로세스 |
| 에이전트 프로필 | 실행 명령, 인자, 환경, 모델 관련 선택을 묶은 사용자 설정 |
| 생성 결과 | 커밋 메시지 입력칸에 넣을 최종 텍스트 |

---

## 6. Relationship to Existing Features

| Existing Feature | Relationship |
| ---------------- | ------------ |
| IntelliJ 커밋 도구 창 | 확장된다. 기존 체크 상태와 커밋 메시지 입력 흐름을 유지하면서 생성 기능을 추가한다. |
| JetBrains AI Assistant 커밋 메시지 생성 | 사용자 경험 기준점이다. 구현이나 인증을 공유한다는 의미는 아니다. |
| JetBrains AI Assistant custom ACP 설정 | 재사용하지 않는다. 이 기능은 플러그인 독립 설정을 사용한다. |
| IntelliJ 변경 목록 및 부분 커밋 모델 | 입력 범위의 원천이다. MVP는 체크된 변경 항목을 기준으로 하고, 부분 라인 선택은 검증 후 확장한다. |
| IDE 설정 화면 | 확장된다. 에이전트 선택, 모델 관련 선택, 사용자 커밋 프롬프트를 저장한다. |
| 로컬 코딩 에이전트 CLI | 재사용된다. 인증, 모델 공급자, 기본 규칙은 각 에이전트의 기존 설정을 우선 존중한다. |

---

## 7. Primary User Flows

### 7.1 Main Flow

```text
사용자가 커밋 도구 창에서 커밋할 변경 항목을 체크한다.
  -> 사용자가 커밋 메시지 입력칸의 생성 아이콘을 누른다.
  -> 시스템이 체크된 변경 항목과 사용자 커밋 프롬프트를 기준으로 로컬 ACP 에이전트에 요청한다.
  -> 기존 커밋 메시지가 비어 있으면 시스템이 생성된 커밋 메시지를 바로 입력칸에 넣는다.
  -> 기존 커밋 메시지가 있고 확인 후 교체 설정이 켜져 있으면 시스템이 diff window에서 기존 메시지와 생성 메시지를 비교해 보여준다.
  -> 사용자가 diff window에서 생성 메시지 적용을 선택하면 시스템이 생성된 커밋 메시지를 커밋 메시지 입력칸에 넣는다.
  -> 사용자는 메시지를 검토하고 필요하면 수정한 뒤 기존 커밋 흐름을 계속 진행한다.
```

### 7.2 Settings Flow

```text
사용자가 플러그인 설정을 연다.
  -> 에이전트 설정에서 사용할 에이전트, 실행 명령, 실행 인자, 모델 관련 선택을 지정한다.
  -> 프롬프트 전용 하위 설정에서 사용자 커밋 프롬프트를 작성한다.
  -> 교체 확인 설정에서 diff window 검토 여부를 지정한다.
  -> 시스템은 이후 커밋 메시지 생성 요청에 해당 설정을 적용한다.
```

### 7.3 Failure / Partial Success Flow

```text
사용자가 생성 아이콘을 누른다.
  -> 시스템이 체크된 변경 항목, 에이전트 설정, 프로세스 실행 가능 여부를 확인한다.
  -> 필요한 조건이 충족되지 않으면 커밋 메시지 입력칸을 덮어쓰지 않고 IDE notification으로 이유를 표시한다.
  -> 설정 문제인 경우 notification에서 설정 화면으로 이동할 수 있다.
  -> 생성이 중간에 취소되거나 실패하면 기존 커밋 메시지를 보존한다.
```

---

## 8. Design

### 8.1 Behavior

생성 요청은 항상 현재 커밋 도구 창에서 체크된 변경 항목을 기준으로 한다. 체크되지 않은 변경 항목은 프롬프트의 diff/context에 포함하지 않는다.

시스템은 사용자 커밋 프롬프트, 체크된 파일의 요약, 필요한 diff 정보를 하나의 ACP 프롬프트로 구성한다. 프롬프트는 “커밋 메시지만 반환”하도록 에이전트에 명확히 요구한다.

로컬 에이전트 연결은 ACP v1의 기본 흐름을 따른다. 시스템은 에이전트 프로세스를 시작하고, 버전과 기능을 협상한 뒤, 프로젝트 작업 디렉터리에 대한 세션을 만들고, 커밋 메시지 생성을 하나의 prompt turn으로 요청한다.

에이전트가 스트리밍한 중간 메시지는 진행 상태로 사용할 수 있지만, 커밋 메시지 입력칸에는 최종 생성 결과만 반영한다. 기존 커밋 메시지가 비어 있으면 시스템은 생성 결과를 즉시 반영한다. 기존 커밋 메시지가 이미 입력되어 있고 “확인 후 교체” 설정이 켜져 있으면 시스템은 생성 결과를 바로 덮어쓰지 않고 diff window에서 기존 메시지와 생성 메시지를 비교해 보여준 뒤 사용자가 적용할 때만 교체한다. 해당 설정이 꺼져 있으면 생성 결과를 즉시 반영한다.

생성 진입점은 커밋 메시지 입력칸의 기존 메시지 액션 영역에 아이콘 액션으로 제공한다. 사용자는 AI Assistant의 커밋 메시지 생성 경험처럼 커밋 메시지 입력 맥락 안에서 바로 생성 기능을 발견하고 실행할 수 있어야 한다.

생성 액션은 커밋 메시지 컨텍스트에서만 표시되거나 활성화되어야 한다. 커밋 메시지 컨텍스트가 없는 메뉴, 툴바, 검색 결과에서 잘못 실행되지 않도록 IDE가 제공하는 커밋 메시지 자료 문맥을 확인한다. 사용할 에이전트 설정이 완료되지 않은 경우 생성 아이콘은 표시되더라도 비활성화되어야 한다.

생성 중에는 생성 아이콘을 비활성화하고 진행 상태를 표시한다. 사용자가 같은 커밋 메시지 입력칸에서 중복 생성 요청을 시작할 수 없어야 한다.

### 8.2 Conceptual Data Model

| Entity | Meaning |
| ------ | ------- |
| Commit Generation Request | 체크된 변경 항목, 사용자 커밋 프롬프트, 에이전트 선택, 모델 관련 선택을 묶은 생성 요청 |
| Agent Profile | ACP 에이전트를 실행하고 세션을 구성하는 사용자 설정 |
| Agent Session | 하나의 로컬 ACP 연결 안에서 생성 요청을 처리하는 대화 단위 |
| Generation Result | 커밋 메시지 입력칸에 반영 가능한 최종 텍스트와 결과 상태 |
| Generation Diagnostic | 실패 원인, 에이전트 로그, 취소 여부 같은 사용자 또는 진단용 정보 |

| Field | Meaning |
| ----- | ------- |
| Agent selection | 사용할 ACP 에이전트 프로필 |
| Command | ACP 에이전트를 실행할 명령 |
| Arguments | ACP 에이전트 실행 인자 |
| Environment | 에이전트 실행 시 전달할 비밀이 아닌 환경 설정 |
| Model preference | 에이전트가 지원할 경우 사용할 모델 선택 |
| User commit prompt | 커밋 메시지 정책과 선호 스타일 |
| Default prompt source | 기본 사용자 커밋 프롬프트를 제공하는 문서 |
| Confirm before replacing existing message | 기존 커밋 메시지가 있을 때 diff window에서 생성 결과 적용 여부를 확인할지 여부 |
| Output format policy | 생성 결과에서 제목과 본문을 보존하고 설명문이나 코드 블록을 제거하는 규칙 |
| Working directory strategy | 프로젝트 루트 또는 Git 루트 중 ACP 세션 기준 디렉터리 |
| Timeout policy | 에이전트 시작과 생성 응답에 대한 제한 시간 |

### 8.3 Failure Handling

시스템은 다음 실패 범주를 구분한다.

- 체크된 변경 항목 없음: 생성 요청을 보내지 않고 사용자에게 커밋 대상 선택이 필요함을 알린다.
- 에이전트 미설정: 생성 아이콘을 비활성화하고 설정 화면으로 이동할 수 있는 안내를 제공한다.
- 에이전트 실행 실패: 명령 경로, 인자, 권한, 환경 문제를 진단 가능한 메시지로 표시한다.
- ACP 초기화 실패: 프로토콜 버전 또는 capability 협상 실패로 다룬다.
- 생성 시간 초과: 기존 커밋 메시지를 보존하고 재시도 가능 상태로 남긴다.
- 사용자 취소: 에이전트에 취소를 요청하고, 완료되지 않은 결과를 커밋 메시지 입력칸에 반영하지 않는다.
- 결과 파싱 실패: 원문을 무조건 삽입하지 않고 사용자가 검토 가능한 실패 상태로 처리한다.

---

## 9. Policy Decisions

### 9.1 Commit Scope Policy

Decision:

- 커밋 메시지 생성 범위는 AI Assistant 경험과 동일하게 커밋 도구 창에서 현재 체크된 파일이다.
- 체크되지 않은 파일은 생성 입력에서 제외한다.
- MVP는 부분 라인 선택이나 파일 내부 일부 hunks 선택보다 체크된 파일 단위 범위를 우선한다.

Rationale:

- 사용자는 JetBrains AI Assistant 흐름과 동일하게 체크 상태가 생성 기준이 된다고 기대한다.
- 실제 커밋 대상과 메시지 생성 대상이 다르면 잘못된 메시지가 만들어질 위험이 크다.
- 사용자가 명시한 제품 기준은 “체크된 파일만”이며, 파일 내부 부분 선택은 MVP의 핵심 기대가 아니다.

### 9.2 Local ACP Agent Policy

Decision:

- MVP는 로컬 ACP 에이전트를 하위 프로세스로 실행하는 stdio transport를 기본으로 한다.
- 원격 ACP transport는 MVP 범위에서 제외한다.

Rationale:

- ACP v1에서 로컬 stdio는 현재 안정적이고 문서화된 기본 경로다.
- 사용자는 이미 로컬에서 인증 및 설정이 완료된 code-agent CLI를 쓰고자 한다.
- 원격 transport는 보안, 인증, 네트워크 장애, 세션 수명 정책이 추가로 필요하다.

### 9.3 Agent Capability Policy

Decision:

- MVP에서 IDE 클라이언트는 파일 쓰기와 터미널 실행 capability를 기본 제공하지 않는다.
- 체크된 diff/context는 IDE가 직접 수집해 프롬프트에 포함한다.
- 에이전트가 파일이나 터미널 접근을 요청하더라도 커밋 메시지 생성에는 필요하지 않은 권한으로 간주한다.

Rationale:

- 커밋 메시지 생성은 읽기 중심 작업이며, 에이전트가 워크스페이스를 변경할 필요가 없다.
- 권한을 최소화하면 사용자 신뢰와 안전성이 높아진다.
- IDE가 선택 범위를 직접 결정해야 커밋 도구 창의 체크 상태와 생성 입력을 일치시킬 수 있다.

### 9.4 Agent Support Policy

Decision:

- opencode는 직접 ACP 명령을 사용하는 기본 프로필로 지원한다.
- Claude는 Claude Agent ACP 어댑터를 기준으로 지원한다.
- Codex는 Codex ACP 어댑터를 기준으로 지원한다.
- 각 에이전트의 직접 ACP 지원 여부가 바뀌면 프로필의 실행 명령만 갱신할 수 있어야 한다.
- 사용할 에이전트 설정이 완료되지 않은 경우 생성 아이콘은 비활성화한다.

Rationale:

- opencode는 공식적으로 ACP subprocess 실행을 문서화했다.
- Claude Code와 Codex CLI의 직접 ACP 서버 모드는 확인되지 않았고, 현재 확실한 경로는 어댑터다.
- 프로필 기반 설계는 에이전트별 실행 방식 차이를 사용자 설정으로 흡수한다.
- 설정이 없는 상태에서 액션을 실행 가능하게 두면 사용자는 생성 실패를 기능 오류로 해석할 가능성이 높다.

### 9.5 Model Selection Policy

Decision:

- 모델 선택은 가능하면 ACP session config options에서 에이전트가 제공한 `model` 범주를 우선한다.
- 에이전트가 모델 선택지를 제공하지 않는 경우 사용자 입력 문자열 또는 에이전트 기본값을 사용한다.

Rationale:

- ACP는 모델, 모드, 사고 수준 같은 세션 설정을 config options로 노출하는 방식을 권장한다.
- 에이전트마다 모델 이름과 공급자 표기가 다르므로 플러그인이 모든 모델 목록을 하드코딩하면 빠르게 낡는다.
- 사용자 로컬 CLI 설정을 존중하면 인증 및 공급자별 정책 충돌이 줄어든다.

### 9.6 Configuration Ownership Policy

Decision:

- JetBrains AI Assistant의 custom ACP 설정 파일은 이 기능에서 지원하거나 재사용하지 않는다.
- 에이전트 프로필, 모델 선택, 사용자 커밋 프롬프트, 확인 후 교체 설정은 플러그인 독립 설정으로 관리한다.
- MVP 설정 화면은 에이전트 설정과 프롬프트 전용 하위 설정을 분리한다.
- 에이전트 설정은 에이전트 선택, 실행 명령, 실행 인자, 모델 선택을 포함한다.
- 프롬프트 전용 하위 설정은 사용자 커밋 프롬프트만 다룬다.
- 확인 후 교체 설정은 별도 옵션으로 제공한다.
- 환경 변수 편집 UI는 MVP에서 제공하지 않는다.

Rationale:

- JetBrains AI Assistant의 custom ACP 설정은 AI Chat용 사용자 정의 ACP 에이전트 설정이며, 이 기능의 커밋 메시지 생성 UX와 직접 동일하지 않다.
- 플러그인 독립 설정을 사용하면 커밋 메시지 생성에 필요한 최소 설정만 제공할 수 있다.
- AI Assistant 설정 형식 변화나 설치 여부에 기능이 종속되지 않는다.
- 프롬프트를 전용 하위 설정으로 분리하면 사용자가 커밋 메시지 정책을 에이전트 실행 설정과 혼동하지 않는다.

### 9.7 Prompt Policy

Decision:

- 기본 사용자 커밋 프롬프트는 `docs/default_commit_message_prompt.md`를 기준으로 한다.
- 사용자 커밋 프롬프트는 생성 요청마다 항상 포함한다.
- 시스템은 사용자 프롬프트와 별도로 “커밋 메시지만 반환” 같은 최소 출력 규칙을 덧붙일 수 있다.
- 생성 결과는 제목과 본문을 그대로 허용한다.
- 출력이 코드 블록, 설명문, 다중 후보를 포함하면 실제 커밋 메시지 텍스트만 남기도록 최종 커밋 메시지 추출 규칙을 적용한다.

Rationale:

- 사용자 프롬프트는 팀의 커밋 스타일과 언어 규칙을 반영하는 핵심 설정이다.
- 기본 프롬프트 문서를 별도로 두면 제품 기본값과 사용자가 편집하는 프롬프트 정책을 명확히 구분할 수 있다.
- 커밋 메시지 입력칸에는 설명문이 아니라 실제 커밋 메시지가 들어가야 한다.
- 에이전트별 응답 형식 차이를 줄이려면 출력 규칙과 추출 정책이 필요하다.
- 좋은 커밋 메시지는 한 줄 제목만이 아니라 본문을 포함할 수 있으므로 본문을 기본적으로 보존한다.

### 9.8 Existing Message Preservation Policy

Decision:

- 생성 실패, 취소, 파싱 실패 시 기존 커밋 메시지를 보존한다.
- 이미 사용자가 작성한 커밋 메시지가 있을 때 생성 결과를 적용하기 전 확인할지 여부를 설정에서 켜고 끌 수 있다.
- “확인 후 교체” 설정의 기본값은 켜짐이다.
- 기존 커밋 메시지가 비어 있으면 diff window를 띄우지 않고 생성 결과를 즉시 반영한다.
- “확인 후 교체” 설정이 켜져 있으면 diff window에서 기존 메시지와 생성 메시지를 비교한 뒤, 사용자가 적용을 선택할 때만 커밋 메시지 입력칸에 반영한다.
- diff window는 MVP에서 생성 메시지 적용 단일 액션을 제공한다.
- 사용자가 diff window 안에서 생성 메시지를 직접 편집하는 기능은 MVP에서 제공하지 않는다.
- “확인 후 교체” 설정이 꺼져 있으면 생성 성공 시 결과를 즉시 커밋 메시지 입력칸에 반영한다.

Rationale:

- 커밋 메시지 입력칸은 사용자의 현재 작업 상태다.
- 실패한 AI 생성이 사용자의 수동 입력을 잃게 만들면 신뢰가 크게 떨어진다.
- 사용자는 빠른 교체와 안전한 확인 중 선호하는 흐름을 선택할 수 있어야 한다.
- 기본값은 기존 사용자 입력 보호를 우선한다.
- diff window는 기존 메시지와 생성 메시지의 차이를 사용자가 실제로 검토할 수 있게 하므로 단순 확인 dialog보다 이 기능의 목적에 맞다.
- 기존 메시지가 비어 있는 상태에서 빈 내용과 생성 결과를 diff로 비교하는 것은 불필요한 마찰이다.
- MVP의 diff window는 적용 여부 결정에 집중하고, 편집은 적용 후 커밋 메시지 입력칸에서 하게 하는 편이 단순하다.

### 9.9 Commit Message Action Placement Policy

Decision:

- 생성 액션은 커밋 메시지 입력칸의 기존 메시지 액션 영역에 16x16 아이콘 액션으로 배치한다.
- 플랫폼의 커밋 메시지 액션 그룹에 등록하는 방식을 사용한다.
- AI Assistant의 내부 아이콘 자산이나 내부 구현은 재사용하지 않는다.
- 액션은 텍스트 버튼이 아니라 아이콘과 접근 가능한 이름, tooltip을 가진 IDE 액션으로 제공한다.
- 액션은 커밋 메시지 컨트롤이 있는 자료 문맥에서만 표시되거나 활성화된다.
- 생성 중에는 액션을 비활성화하고 진행 상태를 표시한다.

Rationale:

- 사용자는 커밋 메시지를 작성하는 위치에서 생성 기능을 찾을 가능성이 가장 높다.
- IntelliJ 커밋 메시지 컴포넌트는 메시지 액션 그룹 기반 툴바를 사용하므로, 별도 Swing 주입보다 플랫폼 액션 모델과 맞는 배치가 적합하다.
- AI Assistant와 유사한 경험은 위치와 흐름으로 재현하고, 자산 및 내부 API 의존성은 피한다.
- 같은 패턴을 쓰는 참조 플러그인 조사 결과, 커밋 메시지 입력칸 옆 액션 영역은 커밋 메시지 액션 그룹 등록으로 노출된다.
- 생성 중 액션을 비활성화하면 중복 요청과 결과 경합을 막을 수 있다.

### 9.10 Commit Message Update Policy

Decision:

- 커밋 메시지 입력칸에 생성 결과를 반영할 때는 IDE의 커밋 메시지 컨트롤을 통해 값을 설정한다.
- 문서 변경은 하나의 명령 단위로 처리해 사용자가 되돌리기 동작을 예측할 수 있게 한다.
- ACP 생성 중 미리보기 형태로 커밋 메시지 입력칸을 반복 변경하지 않는다.
- diff window에서 사용자가 적용을 선택하기 전까지는 커밋 메시지 입력칸을 변경하지 않는다.

Rationale:

- 커밋 메시지 컨트롤을 통하면 IDE 커밋 UI의 상태 반영 경로와 맞을 가능성이 높다.
- 명령 단위 변경은 커밋 메시지 교체가 사용자에게 하나의 편집 작업으로 보이게 한다.
- 생성 도중 반복 쓰기와 취소 시 되돌리기 방식은 사용자의 기존 되돌리기 스택과 충돌할 수 있다.
- diff window 검토 흐름은 생성 결과의 품질과 의도를 확인한 뒤 반영하게 하므로 커밋 메시지 보존 정책과 일관된다.

### 9.11 Platform Dependency Policy

Decision:

- 커밋 메시지 입력칸 액션과 커밋 메시지 컨트롤 접근에 필요한 VCS 플랫폼 의존성은 필수 의존성으로 선언한다.
- Git 전용 기능을 쓰지 않는 한 Git 플러그인 의존성은 추가 검증 전까지 필수로 간주하지 않는다.

Rationale:

- `Vcs.MessageActionGroup`과 커밋 메시지 자료 문맥은 이 기능의 핵심 진입점과 상태 접근 경로다.
- 의존성이 선택 사항이면 액션 등록과 커밋 메시지 접근이 IDE 구성에 따라 실패할 수 있다.
- 기능 정체성은 Git 전용이 아니라 IDE 커밋 UI 연동이므로 최소 필수 VCS 의존성을 우선한다.

### 9.12 Failure Feedback Policy

Decision:

- 생성 실패, 설정 누락, 시간 초과, 파싱 실패는 커밋 메시지 입력칸을 변경하지 않고 IDE notification으로 표시한다.
- 설정 문제 notification은 설정 화면으로 이동하는 액션을 포함한다.
- 실패 notification은 사용자가 다음 행동을 판단할 수 있는 원인을 짧게 설명한다.

Rationale:

- 실패 상황에서 커밋 메시지 입력칸을 변경하지 않는 것이 사용자 입력 보존 정책과 일관된다.
- 설정 문제는 사용자가 바로 수정할 수 있어야 한다.
- 커밋 메시지 입력 영역 안에 긴 오류 설명을 넣으면 실제 커밋 메시지 작성 흐름을 방해한다.

---

## 10. Alternatives Considered

### Alternative: Direct LLM API Integration

Description:

- 플러그인이 OpenAI, Anthropic 또는 다른 공급자의 API를 직접 호출해 커밋 메시지를 생성한다.

Why not chosen:

- 사용자는 로컬에서 이미 사용 중인 code-agent CLI를 ACP로 활용하고 싶어 한다.
- 직접 API 통합은 공급자별 인증, 모델 목록, 가격, 권한, 네트워크 정책을 플러그인이 직접 떠안게 만든다.
- ACP를 쓰면 에이전트 생태계와 IDE 기능을 느슨하게 결합할 수 있다.

### Alternative: Non-ACP CLI Invocation

Description:

- 각 CLI의 비대화형 명령을 직접 호출하고 stdout의 최종 텍스트만 읽는다.

Why not chosen:

- 도구별 명령, 옵션, 스트리밍, 취소, 인증 방식이 모두 달라져 통합 비용이 커진다.
- ACP의 세션, 업데이트, 권한 요청, config options 같은 공통 UX 요소를 활용할 수 없다.
- 사용자 목표가 ACP 기반 연동이므로 기능 정체성과 맞지 않는다.

### Alternative: Reuse JetBrains AI Chat Agent Selector Only

Description:

- 플러그인이 별도 ACP 클라이언트를 갖지 않고 JetBrains AI Chat의 ACP 설정과 선택기를 그대로 사용한다.

Why not chosen:

- 커밋 메시지 생성은 커밋 도구 창의 체크 상태와 메시지 입력칸에 직접 연결되어야 한다.
- JetBrains AI Chat의 설정 재사용 가능성은 추가 확인이 필요하며, 플러그인 독립성과 배포 가능성에 영향을 줄 수 있다.
- MVP는 플러그인이 필요한 최소 ACP 클라이언트 동작을 직접 소유하는 방향이 더 명확하다.

### Alternative: Grant Full Filesystem and Terminal Capabilities

Description:

- 에이전트가 IDE를 통해 파일 읽기, 파일 쓰기, 터미널 실행을 요청할 수 있게 한다.

Why not chosen:

- 커밋 메시지 생성에는 변경분 context만 필요하며, 쓰기와 터미널 실행은 과도한 권한이다.
- 잘못된 권한 부여는 보안, 개인정보, 사용자 신뢰 리스크를 키운다.
- 필요한 context는 IDE가 직접 수집해 프롬프트에 포함할 수 있다.

---

## 11. Cross-cutting Concerns

### 11.1 Security

- 로컬 에이전트는 사용자 머신에서 실행되는 하위 프로세스이므로 실행 명령과 환경 변수는 신뢰 경계로 취급한다.
- MVP 기본 capability는 최소 권한 원칙을 따른다.
- 파일 쓰기와 터미널 실행은 커밋 메시지 생성에 필요하지 않으므로 기본 비활성화한다.
- 에이전트 실행 실패나 stderr 로그에는 민감한 인증 정보가 노출되지 않도록 표시 범위를 제한해야 한다.

### 11.2 Privacy

- 체크된 변경분에는 소스 코드, 비밀값, 고객 정보가 포함될 수 있다.
- 사용자가 선택한 로컬 에이전트와 그 모델 공급자 정책에 따라 diff 내용이 외부 서비스로 전송될 수 있음을 설정 또는 최초 사용 시 명확히 알려야 한다.
- 플러그인은 토큰이나 API 키를 평문 설정에 저장하지 않는다.
- 생성 요청에는 체크된 변경 항목과 필요한 주변 정보만 포함한다.

### 11.3 Permissions

- 사용자는 어떤 ACP 에이전트 프로필을 사용할지 명시적으로 선택한다.
- MVP에서 IDE 클라이언트는 쓰기 및 터미널 capability를 기본적으로 제공하지 않는다.
- 에이전트별 인증은 기존 CLI 로그인 또는 안전한 비밀 저장소를 통해 관리한다.
- Not applicable: 별도 서버 측 역할 기반 권한 모델은 이 로컬 IDE 기능의 범위가 아니다.

### 11.4 Observability

- 생성 요청의 상태는 사용자에게 진행 중, 완료, 실패, 취소로 구분되어 보여야 한다.
- 실패 시 에이전트 실행 실패, ACP 협상 실패, 시간 초과, 결과 파싱 실패를 구분할 수 있어야 한다.
- 진단 정보는 문제 해결에 충분해야 하지만 diff 내용이나 비밀값을 불필요하게 로그에 남기지 않아야 한다.

### 11.5 Accessibility

- 생성 기능은 마우스 클릭뿐 아니라 키보드 접근 가능한 액션으로 사용할 수 있어야 한다.
- 진행 중 상태와 실패 상태는 색상만으로 전달하지 않는다.
- 생성 아이콘은 접근 가능한 이름과 설명을 가져야 한다.

### 11.6 Internationalization

- 사용자-facing 문구는 리소스 번들을 통해 관리한다.
- 사용자 커밋 프롬프트는 커밋 메시지 언어와 스타일을 결정할 수 있어야 한다.
- 기본 프롬프트는 영어만 강제하지 않고 팀 또는 사용자 언어 정책을 반영할 수 있어야 한다.

---

## 12. Scope

### In Scope for MVP research-backed design (2026-07-07)

- 커밋 도구 창에서 체크된 변경 항목을 기준으로 커밋 메시지를 생성한다.
- 사용자 커밋 프롬프트를 설정값으로 사용한다.
- 기본 사용자 커밋 프롬프트는 `docs/default_commit_message_prompt.md`를 사용한다.
- 사용자 커밋 프롬프트는 프롬프트 전용 하위 설정에서 관리한다.
- 커밋 메시지 입력칸의 기존 메시지 액션 영역에 생성 아이콘을 제공한다.
- 에이전트 설정이 완료되지 않은 경우 생성 아이콘을 비활성화한다.
- 생성 중에는 생성 아이콘을 비활성화하고 진행 상태를 표시한다.
- 로컬 stdio ACP 에이전트를 실행하고 ACP 세션으로 생성 요청을 보낸다.
- opencode, Claude Agent ACP 어댑터, Codex ACP 어댑터를 초기 대상 프로필로 다룬다.
- 모델 선택은 에이전트가 제공한 config options 또는 사용자 입력 모델 문자열을 통해 표현한다.
- JetBrains AI Assistant custom ACP 설정을 재사용하지 않고 플러그인 독립 설정을 사용한다.
- 생성 결과는 제목과 본문을 보존하되 설명문과 코드 블록을 제거한다.
- 기존 커밋 메시지가 비어 있으면 생성 성공 시 즉시 반영하고, 기존 커밋 메시지가 있으면 설정에 따라 즉시 반영하거나 diff window 검토 후 반영한다.
- diff window에서는 생성 메시지 적용 단일 액션을 제공하고 직접 편집은 제공하지 않는다.
- 생성 실패, 취소, 시간 초과 시 기존 커밋 메시지를 보존한다.
- 생성 실패, 설정 누락, 시간 초과, 파싱 실패는 IDE notification으로 표시한다.
- 커밋 메시지 입력칸 액션과 메시지 컨트롤 접근에 필요한 VCS 플랫폼 의존성을 필수로 둔다.

### Out of Scope for MVP research-backed design (2026-07-07)

- 원격 ACP 에이전트 정식 지원
- 에이전트 자동 설치 및 ACP 레지스트리 통합
- 커밋 자동 실행
- 커밋 메시지 다중 후보 비교 UI
- diff window 안에서 생성 메시지를 직접 편집하는 기능
- 환경 변수 편집 UI
- 생성 품질 점수화 또는 자동 평가
- 파일 쓰기 및 터미널 실행 capability 제공
- 모든 IDE 제품과 버전에서 동일한 커밋 내부 API 안정성 보장
- 부분 라인 커밋 또는 파일 내부 일부 hunk 선택의 완전한 반영 보장

---

## 13. Risks & Open Questions

### Risks

- IntelliJ 커밋 UI 내부 API 일부가 구현 모듈에 있어 공개 안정 API가 아닐 수 있다.
- 파일 내부 일부 hunk 선택을 정확히 반영하지 못하면 실제 커밋 내용보다 넓은 파일 단위 context가 에이전트에 전달될 수 있다.
- Claude와 Codex는 직접 ACP 서버 모드가 아니라 어댑터 경로가 현재 확실한 방식이므로 설치 및 실행 경험이 에이전트마다 다르다.
- 로컬 에이전트가 최소 capability 조합에서 정상 동작하지 않을 수 있다.
- 에이전트 출력 형식이 일정하지 않으면 커밋 메시지 추출 규칙이 필요 이상으로 복잡해질 수 있다.

### Open Questions

- Git 플러그인 의존성이 필요 없는지 구현 전 확인이 필요하다.

---

## 14. Platform Design

### 14.1 Common Design

공통 설계는 JetBrains IDE 플랫폼 위에서 동작하는 플러그인 기능이다. 로컬 ACP 에이전트와의 통신은 운영체제와 무관하게 stdio 기반 하위 프로세스 모델을 사용한다.

설정은 IDE 사용자 설정과 프로젝트 맥락을 분리해 생각한다. 에이전트 실행 명령, 기본 모델, 사용자 커밋 프롬프트는 사용자별 설정 성격이 강하다. 작업 디렉터리 기준과 프로젝트별 프롬프트 확장은 프로젝트별 설정 후보가 될 수 있다.

### 14.2 JetBrains IDE Platform

커밋 메시지 생성은 IDE의 기존 커밋 도구 창과 커밋 메시지 입력 흐름에 결합된다. 기능은 기존 커밋 실행, 변경 목록 표시, 커밋 검사 흐름을 대체하지 않는다.

커밋 메시지 입력칸은 플랫폼의 커밋 메시지 컴포넌트와 메시지 액션 툴바를 사용한다. 생성 액션은 이 툴바에 추가되는 IDE 액션으로 동작해야 하며, 커밋 도구 창의 Swing 컴포넌트를 직접 탐색해 임의로 버튼을 삽입하는 방식은 피한다.

참조 플러그인 조사 결과, 커밋 메시지 입력칸 주변 액션은 플랫폼 액션 그룹인 `Vcs.MessageActionGroup`에 등록해 노출할 수 있다. 액션 실행 시에는 자료 문맥에서 `VcsDataKeys.COMMIT_MESSAGE_CONTROL`을 통해 커밋 메시지 컨트롤을 얻고, 해당 컨트롤에 생성 결과를 반영하는 방식이 적합하다.

기존 커밋 메시지가 있는 상태의 확인 후 교체 UX는 단순 확인 dialog가 아니라 diff window를 사용한다. diff window는 현재 메시지를 기준 버전으로, 생성 메시지를 새 버전으로 보여주며, 사용자가 적용을 선택하기 전까지 커밋 메시지 입력칸을 변경하지 않는다.

VCS 플랫폼 의존성은 필수다. 커밋 메시지 액션 그룹과 커밋 메시지 자료 문맥은 기능의 핵심 경로이므로 선택 의존성으로 두지 않는다.

긴 작업은 UI 스레드를 막지 않아야 하며, 생성 진행 중에도 IDE는 응답 가능해야 한다. 생성 완료 후 커밋 메시지 입력칸 갱신은 UI 스레드에서 수행되어야 한다.

### 14.3 Local Operating System

로컬 에이전트 실행 명령은 운영체제별 경로와 실행 권한 차이를 가진다. 사용자는 절대 경로 또는 PATH 기반 명령을 설정할 수 있어야 한다.

환경 변수 기반 인증은 운영체제와 셸 설정에 따라 IDE 프로세스에 전달되지 않을 수 있다. 따라서 에이전트가 기존 로그인 상태를 사용할 수 있는지, 별도 환경 변수가 필요한지 사용자에게 진단 가능해야 한다.

---

## 15. Result Semantics

| State | Meaning | User-visible? |
| ----- | ------- | ------------- |
| Ready | 생성 요청을 시작할 수 있는 상태 | Yes |
| Disabled Until Configured | 사용할 에이전트 설정이 없어 생성 아이콘이 비활성화된 상태 | Yes |
| Generating | 에이전트가 커밋 메시지를 생성 중인 상태 | Yes |
| Awaiting Diff Review | 기존 메시지와 생성 메시지를 diff window에서 비교 중인 상태 | Yes |
| Completed | 생성 결과가 커밋 메시지 입력칸에 반영 가능한 상태 | Yes |
| Cancelled | 사용자가 생성 요청을 중단한 상태 | Yes |
| Failed With Notification | 기존 메시지를 보존한 채 IDE notification으로 실패 원인을 표시한 상태 | Yes |
| No Checked Changes | 커밋 대상으로 체크된 변경 항목이 없는 상태 | Yes |
| Agent Not Configured | 사용할 ACP 에이전트 프로필이 없는 상태 | Yes |
| Agent Launch Failed | 로컬 에이전트 프로세스를 시작하지 못한 상태 | Yes |
| Protocol Failed | ACP 초기화, 세션 생성, prompt turn 처리 중 실패한 상태 | Yes |
| Timed Out | 제한 시간 안에 생성이 완료되지 않은 상태 | Yes |
| Parse Failed | 에이전트 응답에서 커밋 메시지를 신뢰성 있게 추출하지 못한 상태 | Yes |

---

## 16. Future Extensions

- ACP 레지스트리를 활용한 에이전트 탐색 및 설치 지원
- 커밋 메시지 다중 후보 생성 및 선택 UI
- 팀 규칙 기반 커밋 메시지 템플릿 프리셋
- 부분 라인 커밋 context의 정확한 반영
- 생성 결과 품질 검사 또는 커밋 메시지 lint 연동
- 원격 ACP 에이전트 지원
- 변경 목록별 프롬프트 자동 전환

---

## Appendix

### Research Sources

| Topic | Source |
| ----- | ------ |
| ACP 소개 및 로컬/원격 개요 | https://agentclientprotocol.com/get-started/introduction |
| ACP 아키텍처와 stdio 하위 프로세스 모델 | https://agentclientprotocol.com/get-started/architecture |
| ACP v1 메시지 흐름 | https://agentclientprotocol.com/protocol/v1/overview |
| ACP 초기화 및 capability 협상 | https://agentclientprotocol.com/protocol/v1/initialization |
| ACP prompt turn | https://agentclientprotocol.com/protocol/v1/prompt-turn |
| ACP transport | https://agentclientprotocol.com/protocol/v1/transports |
| ACP session config options | https://agentclientprotocol.com/protocol/v1/session-config-options |
| JetBrains AI Assistant custom ACP 설정 조사 | https://www.jetbrains.com/help/ai-assistant/acp.html |
| opencode ACP 지원 | https://opencode.ai/docs/acp/ |
| Claude Agent ACP 어댑터 | https://github.com/agentclientprotocol/claude-agent-acp |
| Claude Agent SDK | https://code.claude.com/docs/en/agent-sdk/overview |
| Codex CLI | https://developers.openai.com/codex/cli |
| Codex ACP 어댑터 | https://github.com/zed-industries/codex-acp |
| 커밋 메시지 액션 배치 참조 | `docs/etc/gitmoji-plus-commit-button-master` 서브에이전트 조사 결과 |
| 기본 커밋 메시지 프롬프트 | `docs/default_commit_message_prompt.md` |

### Code Map (non-normative)

> 이 표는 `verified-against: a6d6a0e` 기준 탐색용 앵커다. 설계 계약이 아니며 이후 구현에서 바뀔 수 있다.

| Concept / Flow | Where it lived (as of `verified-against`) |
| -------------- | ----------------------------------------- |
| 플러그인 등록 지점 | `src/main/resources/META-INF/plugin.xml` |
| 커밋 메시지 액션 그룹 후보 | IntelliJ Platform `Vcs.MessageActionGroup` |
| 커밋 메시지 컨트롤 자료 문맥 후보 | IntelliJ Platform `VcsDataKeys.COMMIT_MESSAGE_CONTROL` |
| 커밋 메시지 값 반영 후보 | IntelliJ Platform commit message control |
| 커밋 도구 창 액션 그룹 후보 | IntelliJ Platform `ChangesView.CommitToolbar` |
| 기본 사용자 커밋 프롬프트 | `docs/default_commit_message_prompt.md` |
| 현재 샘플 도구 창 | `src/main/kotlin/com/livteam/commitninja/toolWindow/MyToolWindowFactory.kt` |
| 현재 샘플 프로젝트 서비스 | `src/main/kotlin/com/livteam/commitninja/services/MyProjectService.kt` |
| 현재 샘플 시작 활동 | `src/main/kotlin/com/livteam/commitninja/startup/MyProjectActivity.kt` |
| 리소스 번들 접근 | `src/main/kotlin/com/livteam/commitninja/MyBundle.kt` |
| 리소스 번들 문구 | `src/main/resources/messages/MyBundle.properties` |

---

## Revision History

| Date | Type | Summary |
| ---- | ---- | ------- |
| 2026-07-07 | updated | 체크 범위를 “체크된 파일만”으로 명확화하고, 기존 커밋 메시지 확인 후 교체 설정을 추가했다. |
| 2026-07-07 | updated | JetBrains AI Assistant custom ACP 설정 재사용을 제외하고, 확인 후 교체 기본값을 켜짐으로 확정했다. |
| 2026-07-07 | updated | 커밋 메시지 입력칸의 기존 메시지 액션 영역에 생성 아이콘을 배치하는 정책을 추가했다. |
| 2026-07-07 | updated | 참조 플러그인 조사 결과를 반영해 `Vcs.MessageActionGroup`, 커밋 메시지 자료 문맥, 커밋 메시지 값 반영 정책을 보강했다. |
| 2026-07-07 | updated | 확인 후 교체 UX를 단순 확인 dialog가 아니라 diff window 검토 후 적용하는 방식으로 확정했다. |
| 2026-07-07 | updated | 에이전트 미설정 시 아이콘 비활성화, 제목+본문 보존 출력 정책, 빈 메시지 즉시 반영, VCS 플랫폼 필수 의존성을 확정했다. |
| 2026-07-07 | updated | 기본 프롬프트 파일, 프롬프트 전용 하위 설정, diff window 단일 적용 액션, 생성 중 비활성화, notification 실패 표시를 확정했다. |
