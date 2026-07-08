# ACP Client Profiles

This guide describes how same-repository contributors add a built-in ACP client profile. Runtime contribution through IntelliJ extension points and a separate Gradle module are deferred; do not add either for a basic profile.

## Minimal Path

1. Add a profile definition near `src/main/kotlin/com/livteam/commitninja/acp/profile/AcpBuiltInProfiles.kt`.
2. Add the profile to `AcpProfileRegistry.profiles`.
3. Add a legacy enum-name mapping in `LegacyProfileIds` only when the profile has already been persisted under an old enum name.
4. Keep `AgentProfile` as a compatibility bridge while it exists. New generation request code should use `profileId` and `profileDisplayName`, not the enum.

A simple profile definition needs:

- `id`: stable persisted id, lower-case kebab-case, never renamed after release.
- `displayName`: UI label.
- `generationCommand` and `generationArguments`: the ACP adapter launched for commit generation.
- `modelCommand` and `modelArguments`: the command displayed and passed through the model loading facade.
- `modelProvider`: the provider that actually lists models.

## Model Provider Choice

| Provider | Use when | Notes |
| --- | --- | --- |
| `AcpModelProvider.None` | The client has no model-list support or should keep the agent-default behavior. | This is used by `none` and `junie-acp`. |
| `AcpModelProvider.BuiltIn` | The available models are static and should not run a process. | This is used by `claude-agent-acp`. |
| `AcpModelProvider.Command` | The models come from a client command and parser. | This is used by `opencode` and `codex-acp`. Add parser support only when the existing helpers do not fit. |

The current production model loader preserves the original helper behavior: Opencode still executes hard-coded `models`, and Codex still executes hard-coded `debug models --bundled`. UI-computed model arguments can still flow through the facade for diagnostics and compatibility, but command providers must not start honoring them unless that behavior is explicitly changed.

## Compatibility Checklist

- Existing saved enum names must continue to resolve through `LegacyProfileIds`.
- Unknown saved profile ids must remain preserved by settings reset and load flows until the user explicitly applies a different selection.
- Applying a known profile should write the stable id, not the legacy enum name.
- Command and arguments override normalization should store blanks when values match the profile defaults.
- Generation requests should carry stable profile identity through `CommitMessageGenerationRequest.profileId` and `profileDisplayName`.
- `hasModelLoadCommand` means command-string availability for compatibility. Provider model-list capability is `AcpModelProvider.canListModels`.

## Optional Parser Work

Add parser or command-runner work only when the new client cannot use the existing model helpers in `AcpModelOptionsLoader`.

- Keep command execution diagnostics, timeout, retry, and stderr handling compatible.
- Keep parser behavior close to the client output format.
- Route the provider through `AcpModelProvider.Command` instead of adding profile-specific dispatch to `AgentModelOptionsLoader`.

## Optional Child Settings

Do not add a child settings page for a basic ACP profile. Add one only when the client has profile-specific configuration that cannot live in the generic generation settings.

When a child settings page is necessary:

- Follow the existing `CommitOpencodeConfigurable` pattern.
- Add bundle keys to `src/main/resources/messages/MyBundle.properties` only for new UI copy.
- Register the configurable in `src/main/resources/META-INF/plugin.xml`.

Test additions or changes require implementation-stage permission. Without that permission, use the existing compile and test commands plus manual compatibility checks.
