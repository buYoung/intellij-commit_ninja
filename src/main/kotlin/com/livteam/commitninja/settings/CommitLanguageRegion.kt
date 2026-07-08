package com.livteam.commitninja.settings

enum class CommitLanguageRegion(
    val displayName: String,
    val promptInstruction: String?,
) {
    UNITED_STATES("United States", "Write the commit message in English for United States."),
    MAINLAND_CHINA("Mainland China", "Write the commit message in Simplified Chinese for Mainland China."),
    GERMANY("Germany", "Write the commit message in German for Germany."),
    INDIA("India", "Write the commit message in English for India."),
    UNITED_KINGDOM("United Kingdom", "Write the commit message in English for United Kingdom."),
    FRANCE("France", "Write the commit message in French for France."),
    BRAZIL("Brazil", "Write the commit message in Brazilian Portuguese for Brazil."),
    REPUBLIC_OF_KOREA("Republic of Korea", "Write the commit message in Korean for Republic of Korea."),
    POLAND("Poland", "Write the commit message in Polish for Poland."),
    CANADA("Canada", "Write the commit message in English for Canada."),
    NONE("None", null);

    override fun toString(): String = displayName

    companion object {
        fun fromStoredName(name: String?): CommitLanguageRegion? =
            entries.firstOrNull { it.name == name }
    }
}
