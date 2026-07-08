package com.livteam.commitninja.settings

import com.intellij.openapi.ui.ComboBox
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

class SearchableStringComboBox(
    private val allOptions: List<String>,
) {
    private val comboBoxModel = DefaultComboBoxModel<String>()
    val comboBox = ComboBox(comboBoxModel)
    private var isFiltering = false
    private var isRestoringCommittedSelection = false
    private var pendingFilterText: String? = null

    init {
        comboBox.isEditable = true
        allOptions.forEach(comboBoxModel::addElement)
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

    fun visibleOptions(): List<String> =
        (0 until comboBox.itemCount).map { comboBox.getItemAt(it).toString() }

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
            val visibleOptions = if (trimmedQuery.isBlank() || trimmedQuery in allOptions) {
                allOptions
            } else {
                allOptions.filter { option -> option.contains(trimmedQuery, ignoreCase = true) }
            }
            val editorText = editor?.text.orEmpty()
            val selectedItem = comboBox.selectedItem
            comboBoxModel.removeAllElements()
            visibleOptions.forEach(comboBoxModel::addElement)
            if (editorText.isNotBlank()) {
                comboBox.editor.item = editorText
            } else if (selectedItem != null && selectedItem in visibleOptions) {
                comboBox.selectedItem = selectedItem
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
            comboBox.selectedItem = selectedOption
            comboBox.editor.item = selectedOption
        } finally {
            isRestoringCommittedSelection = false
        }
    }
}
