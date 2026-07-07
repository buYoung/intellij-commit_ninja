# [feat] Implement ACP commit generation pipeline

## Work Type
feat

## Current State (As-Is)
- As of `d365af7` on `main`, there is no ACP client, JSON-RPC transport, agent process launcher, generation request model, or result parser in `src/main/kotlin/com/livteam/commitninja`.
- `docs/FDD/acp-commit-message-generation.md` defines the MVP ACP path as local stdio subprocess transport, ACP initialize/capability negotiation, session creation, one prompt turn, and final commit-message extraction.
- `docs/FDD/acp-commit-message-generation.md` excludes file-write and terminal-run capabilities for MVP; the IDE must collect checked diff/context and pass it in the prompt instead.
- The current `MyProjectService` is a template service that only returns random numbers and should not be treated as the ACP service contract.
- `build.gradle.kts` has no extra serialization, JSON-RPC, coroutine, or process-management dependency declarations beyond the template setup.

## Desired Outcome (To-Be)
- The plugin can launch a configured local ACP agent over stdio and run one commit-message generation request.
- The generation request combines checked-change context, branch name when available, model preference, and the user commit prompt into a single ACP prompt turn.
- The ACP client negotiates initialization and session setup, applies model preference through ACP session config options when the agent exposes them, and falls back to configured model text or agent defaults when it does not.
- The ACP pipeline consumes only plugin-owned settings and never reads JetBrains AI Assistant custom ACP configuration.
- Profile handling preserves the MVP distinction that opencode uses direct ACP while Claude and Codex use their ACP adapter command paths unless those profiles are later updated in plugin-owned settings.
- This child owns only the ACP agent pipeline; plugin settings, commit UI action placement, and commit message editor mutation are owned by child briefs 01, 03, and 04.
- The ACP generation request receives the resolved user commit prompt string from settings/request construction; this child does not read `docs/default_commit_message_prompt.md` directly.
- The generation pipeline returns a structured success, cancellation, timeout, launch failure, protocol failure, or parse failure result without changing the commit message UI directly.
- The output parser preserves a commit title plus body while removing wrappers such as markdown fences, explanatory notes, or multiple-candidate formatting.

## Scope
### In Scope
- Add request, response, diagnostic, and result models for commit-message generation.
- Add a local stdio ACP process launcher and JSON-RPC message flow for initialize, session creation, prompt turn, updates, completion, cancellation, and timeout.
- Add an ACP generation service callable from the commit action workflow.
- Support MVP profile defaults for opencode direct ACP, Claude Agent ACP adapter, and Codex ACP adapter by consuming the settings from child brief 01.
- Accept checked-change prompt context as the only diff/source-code input boundary; do not broaden the request by reading unchecked files in the ACP layer.
- Implement final output extraction that either returns a clean commit message or a parse-failure result.
### Out of Scope
- [hard] Do not provide file-write or terminal-run capability handlers to the agent in MVP.
- [hard] Do not call provider-specific cloud APIs directly.
- [hard] Do not read or reuse JetBrains AI Assistant custom ACP configuration for command, model, prompt, or profile data.
- [hard] Do not implement settings UI, action placement, checked-change collection UI, or commit message editor replacement in this child.
- [hard] Do not verify or add VCS/Git plugin dependencies in this child unless ACP service compilation itself requires them; child brief 03 owns commit UI dependency verification.
- [hard] Do not implement remote ACP transport in MVP.
- [deferred] Agent auto-installation, registry lookup, and dynamic adapter discovery.
- [deferred] Commit message quality scoring, lint integration, and multiple generated candidates.

## Constraints
- Treat the local agent command as a trust boundary; never log checked diff content, secrets, or full stderr output into user-visible diagnostics.
- Preserve the profile command boundary: opencode may use a direct ACP command, while Claude and Codex use configured ACP adapter commands unless the plugin-owned profile data says otherwise.
- Keep profile templates editable: opencode may default to its direct ACP command, while Claude and Codex adapter command/argument shapes must remain configurable instead of being assumed identical.
- Treat the user commit prompt as an input string supplied by the settings/action layer, not as a file this service reads from `docs/`.
- Prefer IntelliJ Platform or Kotlin/JDK APIs already available in the project before adding external dependencies; if a new dependency is required for JSON handling or subprocess protocol work, keep it narrow and explain it in the implementation summary.
- Keep the ACP pipeline UI-agnostic; commit message control writes belong to child brief 04.
- Do not add new test files unless the requester explicitly asks for tests; use existing compile/build verification and small scratch-only protocol probes if needed.

## Related Files / Entry Points
- `docs/FDD/acp-commit-message-generation.md` — use sections 8.1, 8.2, 9.2 through 9.7, 11.1 through 11.4, and 15 as the behavior contract.
- `src/main/kotlin/com/livteam/commitninja/services/MyProjectService.kt` — template service to replace, remove, or leave unused when adding the real generation service.
- `build.gradle.kts` — inspect before adding any protocol or serialization dependency.
- `src/main/kotlin/com/livteam/commitninja/acp/AcpClient.kt` (proposed) — ACP JSON-RPC stdio client.
- `src/main/kotlin/com/livteam/commitninja/acp/AcpAgentProcess.kt` (proposed) — local process lifecycle and stdio transport.
- `src/main/kotlin/com/livteam/commitninja/generation/CommitMessageGenerationService.kt` (proposed) — generation orchestration entry point for UI actions.
- `src/main/kotlin/com/livteam/commitninja/generation/CommitMessageOutputParser.kt` (proposed) — final-message extraction policy.

## Side Effect Checkpoints
- [ ] Agent launch failures are classified separately from ACP protocol failures.
- [ ] Timeouts and cancellations do not return partial text as a successful commit message.
- [ ] Model preference is sent only when supported by agent config options or represented as a safe prompt/session preference.
- [ ] The generation request contains only checked-change context supplied by the commit UI layer, not unchecked workspace files.
- [ ] Checked diff/context is only passed as prompt input and is not written to plugin logs.
- [ ] The service can be called repeatedly without reusing a stale ACP session after failure or cancellation.

## Acceptance Criteria
- [ ] Given a configured local ACP command and checked-change prompt input, the service produces either a clean commit-message string or a typed failure result.
- [ ] opencode, Claude Agent ACP adapter, and Codex ACP adapter profiles can be represented without assuming the same executable or argument shape for all three.
- [ ] The service API makes it clear that the prompt text is supplied by the caller and that default prompt initialization belongs to child brief 01.
- [ ] The ACP service does not consult JetBrains AI Assistant custom ACP settings.
- [ ] A response containing markdown fences or explanatory text is reduced to the commit message text only; an ambiguous response returns parse failure instead of inserting raw output.
- [ ] Launch failure, protocol failure, timeout, cancellation, and parse failure are distinguishable in the returned diagnostic type.
- [ ] The ACP pipeline exposes no API that lets the agent write workspace files or run IDE-mediated terminal commands.
- [ ] `./gradlew compileKotlin -q` succeeds after the ACP slice lands.

## Open Questions
- None — MVP ACP behavior and excluded capabilities are fixed in the FDD; implementation-specific protocol details should be verified during coding.
