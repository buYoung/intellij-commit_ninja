# [feat] Apply generated messages through diff review

## Work Type
feat

## Current State (As-Is)
- As of `d365af7` on `main`, there is no code that reads or writes the commit message editor, opens a generated-message diff review, or reports ACP generation failures to the user.
- `docs/FDD/acp-commit-message-generation.md` requires commit message writes through the IDE commit message control, with one undoable command and no repeated preview writes while ACP generation is running.
- `docs/FDD/acp-commit-message-generation.md` requires immediate insertion when the existing commit message is empty, optional immediate replacement when confirm-before-replace is off, and diff-window review when an existing message is present and confirm-before-replace is on.
- `docs/FDD/acp-commit-message-generation.md` requires diff window review, not a simple confirmation dialog, and excludes direct editing inside the diff window for MVP.
- Failure states in the FDD include no checked changes, agent not configured, launch failure, protocol failure, timeout, cancellation, and parse failure.

## Desired Outcome (To-Be)
- Successful generated messages are applied to the Commit Tool Window message editor without losing user-entered text unexpectedly.
- This brief delivers only the message-apply slice of the parent MVP; generation icon wiring, checked-change collection, and ACP adapter execution are verified by sibling child briefs and by the parent global acceptance criteria.
- If the existing commit message is empty, the generated message is inserted immediately.
- If the existing commit message is non-empty and confirm-before-replace is enabled, a diff window compares the current message with the generated message and applies it only after the user chooses the single generated-message apply action.
- If confirm-before-replace is disabled, a successful generated message replaces the existing message immediately.
- Failures, cancellations, timeouts, parse failures, missing checked changes, and missing settings preserve the current commit message and show concise IDE notifications.
- Missing agent configuration is normally prevented by the disabled action from child brief 03; this slice only preserves the message and reports configuration problems if it is reached through a stale or indirect call path.
- This child is message-apply only: settings, prompt loading, agent-profile execution, checked-change collection, and ACP adapter differences are normalized by child briefs 01, 02, and 03 before this slice receives a typed result.
- This child can implement the apply-side API before child brief 03 is wired; the action slice later calls this API with the commit message control and typed generation result.

## Scope
### In Scope
- Implement commit message read/write through the platform commit message control obtained by the action flow from child brief 03.
- Apply generated text as one command so undo treats replacement as one user-visible edit.
- Implement the generated-message diff review window with a single apply action and no direct editing inside the diff review.
- Add notification flows for settings missing, no checked changes, launch failure, protocol failure, timeout, cancellation, and parse failure.
- Add a settings-opening notification action for configuration problems.
- Preserve the checked-files-only boundary in any no-checked-changes or stale-request handling; do not broaden context inside the apply layer.
- Coordinate with child brief 03 for VCS platform and Git plugin dependency questions instead of adding independent dependency declarations from the apply layer.
- Consume the typed generation result/diagnostic from child brief 02 and the commit message control from child brief 03; do not branch on opencode versus Claude versus Codex inside the apply layer.
### Out of Scope
- [hard] Do not mutate the commit message editor with streaming partial ACP output.
- [hard] Do not overwrite an existing non-empty message before the user applies the diff when confirm-before-replace is enabled.
- [hard] Do not offer direct editing inside the diff window in MVP.
- [hard] Do not implement generation icon placement or disabled-until-configured behavior in this child; child brief 03 owns that UI action scope.
- [hard] Do not implement opencode, Claude Agent ACP adapter, or Codex ACP adapter execution differences in this child; child brief 02 owns adapter/profile execution and returns normalized results.
- [deferred] Multiple generated candidates or side-by-side candidate ranking.
- [deferred] Commit message linting or quality scoring after generation.

## Constraints
- Keep the current commit message unchanged for every non-success result.
- Do not read or reuse JetBrains AI Assistant custom ACP settings when routing users to configuration; navigate only to plugin-owned settings.
- Treat missing settings as a disabled-action responsibility first and a defensive notification path second.
- Treat checked-change scope as already collected by child brief 03; this child must not add unchecked files while preparing notifications or diff review.
- Treat the user commit prompt as already resolved upstream; this child must not read `docs/default_commit_message_prompt.md` or prompt-specific settings directly except for confirm-before-replace.
- Define the apply-side handoff contract even if child brief 03 is not complete yet; do not implement the action-side wiring in this child.
- Do not add or change VCS/Git dependencies in this child unless the diff/apply API itself requires it; if that happens, update the conflict hotspot with child brief 03.
- Treat parent global acceptance as the end-to-end MVP tracker; this child's acceptance criteria intentionally stop at apply API, diff review, editor mutation, and notifications.
- Run UI updates on the UI thread and keep ACP work off the UI thread.
- Keep user-facing text in `MyBundle.properties`.
- Do not add new test files unless the requester explicitly asks for tests; use existing compile/build verification and manual IDE verification for this brief.

## Related Files / Entry Points
- `docs/FDD/acp-commit-message-generation.md` — use sections 7.1, 7.3, 8.1, 9.8, 9.10, 9.12, 14.2, and 15 as the apply-flow contract.
- `src/main/resources/messages/MyBundle.properties` — add diff review labels, apply action text, and notification messages.
- `src/main/kotlin/com/livteam/commitninja/actions/GenerateCommitMessageAction.kt` (proposed) — call into apply/review behavior after generation returns.
- `src/main/kotlin/com/livteam/commitninja/ui/CommitMessageApplier.kt` (proposed) — one-command commit message replacement through the commit message control.
- `src/main/kotlin/com/livteam/commitninja/ui/GeneratedCommitMessageDiff.kt` (proposed) — diff window comparison and single apply action.
- `src/main/kotlin/com/livteam/commitninja/notifications/CommitGenerationNotifications.kt` (proposed) — failure and settings navigation notifications.

## Side Effect Checkpoints
- [ ] Existing non-empty commit message text remains unchanged after launch failure, protocol failure, timeout, cancellation, parse failure, or diff review cancellation.
- [ ] Applying a generated message from diff review creates one undoable edit in the commit message editor.
- [ ] Empty existing message path does not open an unnecessary diff window.
- [ ] Settings problem notification offers a path to the plugin settings surface from child brief 01.
- [ ] Missing agent configuration keeps the commit message unchanged and is consistent with the disabled-icon behavior owned by child brief 03.
- [ ] No-checked-changes handling preserves the checked-files-only generation boundary.
- [ ] The apply layer treats agent-specific output differences as already normalized by the typed generation result from child brief 02.
- [ ] Diff/apply APIs do not require a separate Git plugin dependency; if they do, the dependency decision is coordinated with child brief 03.
- [ ] The apply-side API is usable by child brief 03 without child brief 04 owning action registration or checked-change collection.
- [ ] Notification text never includes full checked diff content or secrets.

## Acceptance Criteria
- [ ] Empty commit message plus successful generation inserts the generated title and body directly into the commit message editor.
- [ ] Non-empty commit message plus confirm-before-replace enabled opens a diff window and applies the generated text only after the user chooses the generated-message apply action.
- [ ] Non-empty commit message plus confirm-before-replace disabled replaces the message immediately after successful generation.
- [ ] Failure, timeout, cancellation, and parse failure paths preserve the previous commit message and show an IDE notification.
- [ ] The apply flow can be invoked from child brief 03 with commit message control plus typed generation result/diagnostic, without rereading prompt source or checked changes.
- [ ] `./gradlew compileKotlin -q` succeeds after the message-apply slice lands.

## Open Questions
- None — replacement behavior, diff review, and failure preservation are fixed by the FDD.
