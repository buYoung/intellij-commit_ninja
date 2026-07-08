package com.livteam.commitninja.settings

enum class CommitLanguageRegion(
    val displayName: String,
    val languageName: String?,
) {
    UNITED_STATES("United States", "English"),
    MAINLAND_CHINA("Mainland China", "Simplified Chinese"),
    GERMANY("Germany", "German"),
    INDIA("India", "English"),
    UNITED_KINGDOM("United Kingdom", "English"),
    FRANCE("France", "French"),
    BRAZIL("Brazil", "Brazilian Portuguese"),
    REPUBLIC_OF_KOREA("Republic of Korea", "Korean"),
    POLAND("Poland", "Polish"),
    CANADA("Canada", "English"),
    NONE("None", null);

    val promptInstruction: String?
        get() = languageName?.let { language ->
            "Write the commit message in $language for $displayName."
        }

    override fun toString(): String = displayName

    companion object {
        fun fromStoredName(name: String?): CommitLanguageRegion? =
            entries.firstOrNull { it.name == name }
    }
}
