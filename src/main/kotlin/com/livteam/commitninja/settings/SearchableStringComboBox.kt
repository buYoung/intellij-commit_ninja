package com.livteam.commitninja.settings

import com.intellij.openapi.ui.ComboBox
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

class SearchableStringComboBox(
    options: List<String>,
    private val isSearchMatchCandidate: (String) -> Boolean = { true },
    private val restoresAllOptionsForExactQuery: Boolean = true,
    private val allowBlankSelection: Boolean = false,
) {
    private val comboBoxModel = DefaultComboBoxModel<String>()
    val comboBox = ComboBox(comboBoxModel)
    private var allOptions = emptyList<String>()
    private var isFiltering = false
    private var isRestoringCommittedSelection = false
    private var isBlankSelectionActive = false
    private var pendingFilterText: String? = null

    init {
        comboBox.isEditable = true
        setOptions(options)
        editor?.document?.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent) = scheduleFilter(editor?.text.orEmpty())
                override fun removeUpdate(event: DocumentEvent) = scheduleFilter(editor?.text.orEmpty())
                override fun changedUpdate(event: DocumentEvent) = scheduleFilter(editor?.text.orEmpty())
            },
        )
        comboBox.addActionListener {
            restoreOptionsAfterCommittedSelection()
        }
    }

    var selectedValue: String
        get() = (comboBox.editor.item ?: comboBox.selectedItem)?.toString()?.trim().orEmpty()
        set(value) {
            restoreAllOptions(value)
        }

    fun options(): List<String> = allOptions

    fun visibleOptions(): List<String> =
        (0 until comboBox.itemCount).map { comboBox.getItemAt(it).toString() }

    fun setOptions(
        options: List<String>,
        selectedValue: String = this.selectedValue,
        allowCustomSelection: Boolean = false,
    ) {
        allOptions = buildList {
            options.filter(String::isNotBlank).distinct().forEach(::add)
            if (allowCustomSelection && selectedValue.isNotBlank() && selectedValue !in this) {
                add(selectedValue)
            }
        }
        restoreAllOptions(selectedValue)
    }

    private val editor: JTextComponent?
        get() = comboBox.editor.editorComponent as? JTextComponent

    private fun scheduleFilter(query: String) {
        if (isFiltering || isRestoringCommittedSelection) return
        pendingFilterText = query
        SwingUtilities.invokeLater {
            val filterText = pendingFilterText ?: return@invokeLater
            pendingFilterText = null
            filter(filterText)
        }
    }

    private fun filter(query: String) {
        if (isFiltering) return
        isFiltering = true
        try {
            val trimmedQuery = query.trim()
            val visibleOptions = if (trimmedQuery.isBlank() || (restoresAllOptionsForExactQuery && trimmedQuery in allOptions)) {
                allOptions
            } else {
                allOptions.filter { option ->
                    isSearchMatchCandidate(option) && option.contains(trimmedQuery, ignoreCase = true)
                }
            }
            val editorText = editor?.text.orEmpty()
            val selectedItem = comboBox.selectedItem
            comboBoxModel.removeAllElements()
            visibleOptions.forEach(comboBoxModel::addElement)
            if (allowBlankSelection && isBlankSelectionActive) {
                if (editorText.isBlank()) {
                    clearSelection()
                } else {
                    clearComboBoxSelectionKeepingEditorText(editorText)
                }
            } else if (editorText.isNotBlank()) {
                comboBox.editor.item = editorText
            } else if (selectedItem != null && selectedItem in visibleOptions) {
                comboBox.selectedItem = selectedItem
            } else if (allowBlankSelection) {
                clearSelection()
            }
        } finally {
            isFiltering = false
        }
    }

    private fun restoreOptionsAfterCommittedSelection() {
        if (isFiltering || isRestoringCommittedSelection) return
        val selectedOption = comboBox.selectedItem?.toString()?.trim().orEmpty()
        if (selectedOption.isBlank() || selectedOption !in allOptions) return
        if (visibleOptions() == allOptions) return
        pendingFilterText = null
        restoreAllOptions(selectedOption)
    }

    private fun restoreAllOptions(selectedOption: String) {
        pendingFilterText = null
        isRestoringCommittedSelection = true
        try {
            filter("")
            if (selectedOption.isBlank()) {
                if (allowBlankSelection) {
                    clearSelection()
                } else {
                    selectFirstOption()
                }
            } else {
                isBlankSelectionActive = false
                comboBox.selectedItem = selectedOption
                comboBox.editor.item = selectedOption
            }
        } finally {
            isRestoringCommittedSelection = false
        }
    }

    private fun clearSelection() {
        isBlankSelectionActive = true
        comboBox.selectedIndex = -1
        comboBoxModel.selectedItem = null
        comboBox.selectedItem = null
        comboBox.editor.item = ""
    }

    private fun clearComboBoxSelectionKeepingEditorText(editorText: String) {
        comboBox.selectedIndex = -1
        comboBoxModel.selectedItem = null
        comboBox.selectedItem = null
        comboBox.editor.item = editorText
    }

    private fun selectFirstOption() {
        isBlankSelectionActive = false
        val firstOption = allOptions.firstOrNull().orEmpty()
        comboBox.selectedItem = firstOption
        comboBox.editor.item = firstOption
    }
}
