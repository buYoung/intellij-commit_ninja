# [feat] Add commit generation settings

## Work Type
feat

## Current State (As-Is)
- As of `d365af7` on `main`, `src/main/resources/META-INF/plugin.xml` only declares `com.intellij.modules.platform`, a sample tool window, and a sample startup activity; it has no plugin-owned settings registration.
- As of `d365af7` on `main`, `build.gradle.kts` targets IntelliJ IDEA `2025.2.6.2` and has no VCS, settings, or ACP-related dependency declarations beyond the template platform setup.
- `docs/FDD/acp-commit-message-generation.md` is the product contract for MVP settings: do not reuse JetBrains AI Assistant custom ACP config, split agent settings from the prompt-only sub-settings, keep confirm-before-replace default-on, and omit an environment-variable editing UI.
- `docs/default_commit_message_prompt.md` contains the default commit prompt text that should seed the plugin's user-editable prompt setting.
- `src/main/resources/messages/MyBundle.properties` only contains template sample strings (`projectService`, `randomLabel`, `shuffle`).

## Desired Outcome (To-Be)
- The plugin exposes a dedicated settings surface for ACP commit message generation without reading or depending on JetBrains AI Assistant custom ACP configuration.
- Agent settings store the selected MVP profile (`opencode`, Claude Agent ACP adapter, or Codex ACP adapter), command, arguments, and model preference.
- A prompt-only sub-settings surface stores the user commit prompt separately from agent launch settings.
- The default user commit prompt is initialized from the intent of `docs/default_commit_message_prompt.md` and remains user-editable after first use.
- The runtime default prompt is packaged or copied into runtime-accessible plugin resources so the plugin does not depend on reading the repository `docs/` directory after installation.
- The confirm-before-replace option exists outside the prompt-only sub-settings and defaults to enabled.
- The settings model can answer whether generation is configured enough for the commit message action to be enabled.

## Scope
### In Scope
- Add plugin-owned persistent settings for agent profile, command, arguments, model preference, user commit prompt, and confirm-before-replace.
- Add an IDE settings configurable with an agent-settings area and a prompt-only sub-settings area.
- Provide MVP defaults for `opencode`, Claude Agent ACP adapter, and Codex ACP adapter profiles without auto-installing any CLI or adapter.
- Add resource-bundle strings for settings labels, validation messages, action enablement text, and failure notifications introduced by this settings slice.
- Replace or bypass template sample service usage when it conflicts with the new settings service shape.
### Out of Scope
- [hard] Do not support JetBrains AI Assistant custom ACP config files.
- [hard] Do not add environment-variable editing UI in MVP.
- [hard] Do not store tokens, API keys, or other secrets in plugin settings.
- [deferred] Automatic ACP agent discovery, adapter installation, and ACP registry integration.
- [deferred] Project-specific prompt overrides or branch-specific prompt switching.
- [deferred] VCS platform dependency and Git plugin dependency verification — child brief 03 owns action-placement dependencies.

## Constraints
- Keep settings ownership plugin-local; JetBrains AI Assistant installation state must not affect this feature.
- Treat the contents of `docs/default_commit_message_prompt.md` as the source for the runtime default prompt, but ensure the implemented default is available to the plugin at runtime.
- Do not leave the installed plugin dependent on a repository-local `docs/default_commit_message_prompt.md` path; convert that source text into a packaged default resource or equivalent runtime default.
- Do not add new test files unless the requester explicitly asks for tests; use existing compile/build verification and manual IDE verification for this brief.

## Related Files / Entry Points
- `docs/FDD/acp-commit-message-generation.md` — use sections 9.4 through 9.8 and 12 as the settings contract.
- `docs/default_commit_message_prompt.md` — source text for the default prompt.
- `src/main/resources/META-INF/plugin.xml` — register the settings configurable if the chosen IntelliJ API requires plugin XML registration.
- `src/main/resources/messages/MyBundle.properties` — add user-facing settings and validation strings.
- `src/main/kotlin/com/livteam/commitninja/settings/CommitGenerationSettings.kt` (proposed) — persistent state for agent, model, prompt, and replacement behavior.
- `src/main/kotlin/com/livteam/commitninja/settings/CommitGenerationConfigurable.kt` (proposed) — IDE settings UI entry point.

## Side Effect Checkpoints
- [ ] Existing plugin id `com.livteam.commitninja` remains unchanged in `plugin.xml`.
- [ ] Existing resource-bundle access through `MyBundle` still resolves strings after new keys are added.
- [ ] Settings validation reports incomplete agent configuration without launching any local CLI.
- [ ] The confirm-before-replace default is enabled for fresh settings state.
- [ ] The prompt-only sub-settings do not expose command, argument, model, or environment controls.

## Acceptance Criteria
- [ ] A fresh IDE profile shows plugin-owned settings for agent selection, command, arguments, model preference, user commit prompt, and confirm-before-replace.
- [ ] The default prompt text matches the behavior specified by `docs/default_commit_message_prompt.md`: commit message only, no markdown fence, Korean numbered body, and fallback text for empty or unreadable patches.
- [ ] A fresh installed plugin can initialize the default prompt without requiring the repository `docs/` directory to exist.
- [ ] Selecting no agent or leaving required command data incomplete makes the settings service report "not configured" for action enablement.
- [ ] Confirm-before-replace is enabled by default and can be turned off from settings.
- [ ] `./gradlew compileKotlin -q` succeeds after the settings slice lands.

## Open Questions
- None — user-owned settings decisions for MVP are already fixed in the FDD; implementation choices are bounded above.
