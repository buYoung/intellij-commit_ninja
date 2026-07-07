# Brief Set: ACP commit message MVP

## Purpose
- Deliver the MVP path for generating commit messages from checked Commit Tool Window items through a user-configured local ACP code-agent CLI.
- Keep execution coordinated across settings, ACP protocol work, Commit Tool Window action placement, and safe message replacement so the MVP reaches the AI Assistant-like flow documented in `docs/FDD/acp-commit-message-generation.md`.

## Child Briefs
- [ ] `docs/briefs/2026-07-07-feat-acp-commit-01-settings.md` — Add commit generation settings; exists because action enablement, agent launch, prompt policy, and replacement behavior need plugin-owned persistent settings before runtime flows can be reliable.
- [ ] `docs/briefs/2026-07-07-feat-acp-commit-02-agent.md` — Implement ACP commit generation pipeline; exists because local stdio ACP launch, session flow, and output parsing are independent from Commit Tool Window placement.
- [ ] `docs/briefs/2026-07-07-feat-acp-commit-03-commit-ui.md` — Add commit message action and checked scope; exists because the AI Assistant-like icon placement and checked-file input boundary are a separate IntelliJ VCS UI integration surface.
- [ ] `docs/briefs/2026-07-07-feat-acp-commit-04-message-apply.md` — Apply generated messages through diff review; exists because generated-message replacement, diff review, undo grouping, and notifications must preserve the user's current commit message.

## Execution Order
- Wave 1: `docs/briefs/2026-07-07-feat-acp-commit-01-settings.md` and `docs/briefs/2026-07-07-feat-acp-commit-02-agent.md` can start in parallel after both agents read the FDD; the ACP pipeline should consume the settings contract without editing settings UI files directly.
- Wave 2: `docs/briefs/2026-07-07-feat-acp-commit-03-commit-ui.md` starts after the settings configured-state API exists and after the ACP service has a callable generation entry point, even if the full protocol implementation is still being finalized.
- Wave 3: `docs/briefs/2026-07-07-feat-acp-commit-04-message-apply.md` starts after the action can pass a commit message control and a generated result into the apply flow.

## Dependencies
- `docs/briefs/2026-07-07-feat-acp-commit-03-commit-ui.md` depends on `docs/briefs/2026-07-07-feat-acp-commit-01-settings.md` because the icon must disable itself when agent settings are incomplete.
- `docs/briefs/2026-07-07-feat-acp-commit-03-commit-ui.md` depends on `docs/briefs/2026-07-07-feat-acp-commit-02-agent.md` because pressing the icon must call a generation service rather than embed ACP protocol logic in the action.
- `docs/briefs/2026-07-07-feat-acp-commit-04-message-apply.md` depends on `docs/briefs/2026-07-07-feat-acp-commit-01-settings.md` because confirm-before-replace controls immediate replacement versus diff review.
- `docs/briefs/2026-07-07-feat-acp-commit-04-message-apply.md` depends on `docs/briefs/2026-07-07-feat-acp-commit-03-commit-ui.md` because it needs the commit message control and action result path.
- `docs/briefs/2026-07-07-feat-acp-commit-01-settings.md` and `docs/briefs/2026-07-07-feat-acp-commit-02-agent.md` have no dependency on each other beyond agreeing on the settings data contract.

## Parallelization
- `docs/briefs/2026-07-07-feat-acp-commit-01-settings.md` and `docs/briefs/2026-07-07-feat-acp-commit-02-agent.md` can run in parallel if only one child edits shared files at a time.
- `docs/briefs/2026-07-07-feat-acp-commit-03-commit-ui.md` must not run in parallel with another child editing `src/main/resources/META-INF/plugin.xml` or `build.gradle.kts`.
- `docs/briefs/2026-07-07-feat-acp-commit-04-message-apply.md` can run in parallel with late ACP internals only after the generation result contract is stable.

## Conflict Hotspots
- `src/main/resources/META-INF/plugin.xml` — settings registration, VCS dependency, and action registration can all touch this file.
- `build.gradle.kts` — VCS platform dependencies and any protocol/serialization dependency changes must be coordinated.
- `src/main/resources/messages/MyBundle.properties` — settings, action, diff review, and notification labels share the resource bundle.
- `src/main/kotlin/com/livteam/commitninja/services/MyProjectService.kt` — template service may be removed, replaced, or left unused; only one child should decide that cleanup path.

## Shared Constraints
- Follow `docs/FDD/acp-commit-message-generation.md` as the product contract for the whole MVP.
- Do not read, support, or reuse JetBrains AI Assistant custom ACP settings.
- Preserve the named MVP agent targets: opencode direct ACP, Claude Agent ACP adapter, and Codex ACP adapter.
- Keep `docs/default_commit_message_prompt.md` as the source for the default user commit prompt.
- Store the user commit prompt in prompt-specific sub-settings, not in the agent launch settings area.
- Keep confirm-before-replace enabled by default for fresh settings state.
- Use only currently checked Commit Tool Window items as generation scope; unchecked files are excluded.
- Keep file-write and terminal-run ACP capabilities out of MVP.
- Keep direct editing inside the generated-message diff window out of MVP.
- Do not add new test files unless the requester explicitly asks for tests; use existing compile/build verification and manual IDE verification.
- Preserve plugin id `com.livteam.commitninja`.

## Global Acceptance Criteria
- [ ] A user can configure opencode, Claude Agent ACP adapter, or Codex ACP adapter profile data, model preference, user commit prompt, and confirm-before-replace setting from plugin-owned settings.
- [ ] Confirm-before-replace is enabled by default and uses diff review before replacing an existing non-empty message.
- [ ] The default user commit prompt is sourced from `docs/default_commit_message_prompt.md` and the editable user prompt lives under prompt-specific sub-settings.
- [ ] In the Commit Tool Window, the commit message editor shows the generation icon in the message action area and disables it when configuration is incomplete.
- [ ] Pressing the icon with checked changes sends only checked changes plus the user commit prompt to the local ACP generation service.
- [ ] Successful generation inserts a clean commit message into an empty message editor, or follows the configured replacement/diff review path when text already exists.
- [ ] Failure, timeout, cancellation, missing settings, missing checked changes, and parse failure preserve the existing commit message and surface IDE notifications.
- [ ] The MVP compiles with `./gradlew compileKotlin -q`.

## Open Questions
- None — remaining Git dependency verification is implementation-owned and is already captured in child brief 03.
