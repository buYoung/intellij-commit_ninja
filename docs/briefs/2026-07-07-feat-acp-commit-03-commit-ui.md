# [feat] Add commit message action and checked scope

## Work Type
feat

## Current State (As-Is)
- As of `d365af7` on `main`, `src/main/resources/META-INF/plugin.xml` has no `<actions>` block and no registration for `Vcs.MessageActionGroup`.
- As of `d365af7` on `main`, `plugin.xml` only depends on `com.intellij.modules.platform`; the FDD says VCS platform dependency is required for commit message action placement and commit message control access.
- The current sample `MyToolWindowFactory` creates an unrelated tool window and does not participate in the Commit Tool Window or commit message editor.
- `docs/FDD/acp-commit-message-generation.md` identifies `Vcs.MessageActionGroup`, `VcsDataKeys.COMMIT_MESSAGE_CONTROL`, checked commit items, disabled-until-configured behavior, and generation-in-progress disabling as the platform UI contract.
- The repo does not yet contain code for collecting checked commit files or constructing the commit-generation input from the commit UI.

## Desired Outcome (To-Be)
- The Commit Tool Window message editor shows a 16x16 commit-message generation icon in the existing message action area.
- The action is enabled only when a commit message control is present, a usable agent configuration exists, checked changes exist, and no generation is already running for that editor context.
- Pressing the action collects only the currently checked commit items as the generation scope and excludes unchecked files.
- The action builds a generation request from checked-change context, branch name when available, and the user commit prompt, then calls the ACP generation service from child brief 02.
- The action hands the commit message control plus the typed generation result/diagnostic to the message-application flow owned by child brief 04; it does not implement diff review or editor replacement inside the action class.
- The action uses an accessible action name and tooltip and does not reuse JetBrains AI Assistant internal icons or implementation.

## Scope
### In Scope
- Add an `AnAction` registered into `Vcs.MessageActionGroup` for commit message generation.
- Add the required VCS platform dependency declarations in Gradle and `plugin.xml`.
- Implement checked commit item discovery for the Commit Tool Window context and turn it into generation input.
- Wire action enablement to commit message control presence, settings completeness from child brief 01, checked-change presence, and in-progress state.
- Keep the sample tool window from becoming part of the commit-generation UX; remove its registration only if it blocks or confuses the MVP plugin surface.
### Out of Scope
- [hard] Do not inject Swing buttons by traversing Commit Tool Window components directly.
- [hard] Do not reuse JetBrains AI Assistant internal assets, actions, or private implementation.
- [hard] Do not read or reuse JetBrains AI Assistant custom ACP configuration.
- [hard] Do not include unchecked files in the generation prompt.
- [hard] Do not implement generated-message replacement or diff review in this child; child brief 04 owns commit message editor mutation.
- [deferred] Perfect support for partial-line or per-hunk commit selection beyond the MVP checked-file scope.
- [deferred] Toolbar placement outside the commit message editor, such as `ChangesView.CommitToolbar`.

## Constraints
- Use platform action registration rather than direct UI component mutation.
- Consume configured-state, profile, model, and prompt data through plugin-owned settings from child brief 01, including opencode, Claude Agent ACP adapter, and Codex ACP adapter profile names.
- Treat child brief 01 and child brief 02 as required predecessors for full runtime behavior; this child may compile against their public service contracts but should not duplicate their implementation.
- Follow the parent brief's execution order when picking this child independently; if child brief 01 or 02 public services are not implemented yet, stop and complete the predecessor first instead of adding placeholder interfaces or duplicating their responsibilities.
- The user commit prompt comes from prompt-specific sub-settings; this child should not read `docs/default_commit_message_prompt.md` directly at action time.
- Confirm during implementation whether Git plugin dependency is necessary; add it only if checked-change or branch APIs require Git-specific classes.
- Keep action execution non-blocking; long ACP generation must not run on the UI thread.
- Do not add new test files unless the requester explicitly asks for tests; use existing compile/build verification and manual IDE verification for this brief.

## Related Files / Entry Points
- `docs/FDD/acp-commit-message-generation.md` — use sections 9.1, 9.4, 9.9, 9.11, 14.2, and Appendix Code Map as the UI-action contract.
- `src/main/resources/META-INF/plugin.xml` — add VCS dependency and register the action under the platform commit message action group.
- `build.gradle.kts` — add IntelliJ platform plugin dependency declarations needed for VCS APIs.
- `src/main/resources/messages/MyBundle.properties` — add action text, tooltip, disabled reason, and progress text keys.
- `src/main/kotlin/com/livteam/commitninja/actions/GenerateCommitMessageAction.kt` (proposed) — commit message action entry point.
- `src/main/kotlin/com/livteam/commitninja/vcs/CheckedCommitChangesProvider.kt` (proposed) — checked-change collection and prompt context extraction.
- `src/main/resources/icons/commitMessageGenerate.svg` (proposed) — plugin-owned 16x16 icon asset if an existing bundled icon is not suitable.

## Side Effect Checkpoints
- [ ] The action appears only in commit message contexts that provide `VcsDataKeys.COMMIT_MESSAGE_CONTROL`.
- [ ] Incomplete agent settings make the icon disabled, not executable with a later failure.
- [ ] Empty checked-change scope reports "no checked changes" without calling the ACP service.
- [ ] Generation-in-progress state prevents duplicate ACP requests from the same commit message editor context.
- [ ] Existing Commit Tool Window commit behavior, change list checkboxes, and commit inspections remain owned by the IDE.
- [ ] Successful generation is routed to the child brief 04 apply flow rather than being written directly from the action implementation.
- [ ] The action-to-apply handoff passes enough data for child brief 04 to choose immediate insert, immediate replace, diff review, or notification without re-collecting checked changes.
- [ ] If predecessor services are absent, this child does not create competing settings, ACP, or apply implementations outside the agreed public contracts.

## Acceptance Criteria
- [ ] The generated plugin shows a commit-message generation icon in the commit message editor action area through `Vcs.MessageActionGroup`.
- [ ] With no configured agent, the icon is visible but disabled in the commit message editor context.
- [ ] With a configured agent and checked changes, pressing the icon invokes the generation pipeline with only checked files in the request.
- [ ] Unchecked files are absent from the prompt context sent to the generation service.
- [ ] End-to-end message filling is completed only when this action is combined with child brief 04's apply/diff behavior.
- [ ] This child is not started before the settings configured-state API and ACP generation service API exist.
- [ ] `./gradlew compileKotlin -q` succeeds after the UI-action slice lands.

## Open Questions
- None — Git dependency is a technical verification item for the implementation agent, not a user-owned product decision.
