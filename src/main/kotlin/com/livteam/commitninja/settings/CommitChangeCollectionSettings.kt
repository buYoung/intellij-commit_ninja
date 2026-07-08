package com.livteam.commitninja.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service
@State(
    name = "CommitNinjaCommitChangeCollectionSettings",
    storages = [Storage(value = "commit-ninja-commit-change-collection.xml", roamingType = RoamingType.DISABLED)],
    category = SettingsCategory.PLUGINS,
)
class CommitChangeCollectionSettings : SimplePersistentStateComponent<CommitChangeCollectionSettings.State>(State()) {
    class State : BaseState() {
        var patchExcludedFilePatterns by string(DEFAULT_PATCH_EXCLUDED_FILE_PATTERNS)
        var maxCommitListSize by property(0)
    }

    val resolvedPatchExcludedFilePatterns: List<PatchExcludedFileRegex>
        get() = parsePatchExcludedFilePatterns(state.patchExcludedFilePatterns.orEmpty())

    val resolvedMaxCommitListSize: Int?
        get() = state.maxCommitListSize.takeIf { it > 0 }

    companion object {
        val DEFAULT_PATCH_EXCLUDED_FILE_PATTERNS = listOf(
            "bun\\.lock",
            "bun\\.lockb",
            "cargo\\.lock",
            "cartfile\\.resolved",
            "composer\\.lock",
            "gemfile\\.lock",
            "go\\.sum",
            "gradle\\.lockfile",
            "mix\\.lock",
            "package-lock\\.json",
            "package\\.resolved",
            "pipfile\\.lock",
            "pnpm-lock\\.yaml",
            "podfile\\.lock",
            "poetry\\.lock",
            "pubspec\\.lock",
            "uv\\.lock",
            "yarn\\.lock",
            ".*\\.lock",
        ).joinToString("\n")

        fun getInstance(): CommitChangeCollectionSettings =
            ApplicationManager.getApplication().getService(CommitChangeCollectionSettings::class.java)

        fun invalidPatchExcludedFilePattern(patterns: String): String? =
            normalizedPatchExcludedFilePatternLines(patterns).firstOrNull { pattern ->
                pattern.toPatchExcludedFileRegexOrNull() == null
            }

        fun parsePatchExcludedFilePatterns(patterns: String): List<PatchExcludedFileRegex> =
            normalizedPatchExcludedFilePatternLines(patterns)
                .mapNotNull { it.toPatchExcludedFileRegexOrNull() }
                .toList()

        private fun normalizedPatchExcludedFilePatternLines(patterns: String): Sequence<String> =
            patterns
                .lineSequence()
                .map { it.trim() }
                .map { if (it == LEGACY_LOCK_FILE_WILDCARD) ".*\\.lock" else it }
                .filter { it.isNotEmpty() }
                .distinct()

        private fun String.toPatchExcludedFileRegexOrNull(): PatchExcludedFileRegex? =
            try {
                PatchExcludedFileRegex(pattern = this, regex = Regex(this, RegexOption.IGNORE_CASE))
            } catch (_: IllegalArgumentException) {
                null
            }

        private const val LEGACY_LOCK_FILE_WILDCARD = "*.lock"
    }

    data class PatchExcludedFileRegex(
        val pattern: String,
        val regex: Regex,
    )
}
