package com.github.drewchase.intellij_plugin.qt_project_setup.wizard

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import java.io.File
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class QtProjectSettingsPanel : GeneratorPeerImpl<QtProjectSettings>() {

    companion object {
        private val LOG = Logger.getInstance(QtProjectSettingsPanel::class.java)
    }

    // Callback for validation - set by the wizard framework
    var checkValid: Runnable? = null

    // UI Components
    private val qtPathField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Qt Installation")
                .withDescription("Choose the Qt installation directory (e.g., C:/Qt/6.8.0/mingw_64)")
        )
        text = "C:/Qt/6.8.0/mingw_64"
    }
    private val windowTitleField = JBTextField("My Application")
    private val minWidthSpinner = JSpinner(SpinnerNumberModel(800, 100, 10000, 10))
    private val minHeightSpinner = JSpinner(SpinnerNumberModel(600, 100, 10000, 10))
    private val startupWidthSpinner = JSpinner(SpinnerNumberModel(1280, 100, 10000, 10))
    private val startupHeightSpinner = JSpinner(SpinnerNumberModel(720, 100, 10000, 10))
    private val useCustomTitlebarCheckbox = JBCheckBox("Use custom frameless titlebar", false)

    init {
        LOG.info("QtProjectSettingsPanel: Constructor called")
    }

    override fun getSettings(): QtProjectSettings {
        LOG.info("QtProjectSettingsPanel.getSettings() called")
        return QtProjectSettings(
            qtPath = qtPathField.text,
            windowTitle = windowTitleField.text,
            minWidth = minWidthSpinner.value as Int,
            minHeight = minHeightSpinner.value as Int,
            startupWidth = startupWidthSpinner.value as Int,
            startupHeight = startupHeightSpinner.value as Int,
            useCustomTitlebar = useCustomTitlebarCheckbox.isSelected
        )
    }

    // This overload is called with the location field and validation callback
    override fun getComponent(myLocationField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent {
        LOG.info("QtProjectSettingsPanel.getComponent(locationField, checkValid) called")
        this.checkValid = checkValid
//        return super.getComponent(myLocationField, checkValid)
        return panel {
            attachTo(this)
        }
    }

    // Build the UI using Kotlin UI DSL - modeled after RsNewProjectPanel.attachTo()
    fun attachTo(panel: Panel) {
        LOG.info("QtProjectSettingsPanel.attachTo() called")
        with(panel) {
            row("Qt Installation Path:") {
                cell(qtPathField)
                    .align(AlignX.FILL)
                    .resizableColumn()
                    .comment("e.g., C:/Qt/6.8.0/mingw_64")
            }

            row("Window Title:") {
                cell(windowTitleField)
                    .align(AlignX.FILL)
            }

            row("Minimum Width:") {
                cell(minWidthSpinner)
            }

            row("Minimum Height:") {
                cell(minHeightSpinner)
            }

            row("Startup Width:") {
                cell(startupWidthSpinner)
            }

            row("Startup Height:") {
                cell(startupHeightSpinner)
            }

            row {
                cell(useCustomTitlebarCheckbox)
            }
        }
        LOG.info("QtProjectSettingsPanel.attachTo() completed")
    }

    override fun validate(): ValidationInfo? {
        LOG.info("QtProjectSettingsPanel.validate() called")
        val qtPath = qtPathField.text
        if (qtPath.isBlank()) {
            return ValidationInfo("Qt installation path cannot be empty", qtPathField)
        }
        val file = File(qtPath)
        if (!file.exists()) {
            return ValidationInfo("Qt installation path does not exist", qtPathField)
        }
        if (!file.isDirectory) {
            return ValidationInfo("Qt installation path must be a directory", qtPathField)
        }
        val binDir = File(file, "bin")
        if (!binDir.exists() || !binDir.isDirectory) {
            return ValidationInfo("Qt installation path should contain a 'bin' directory", qtPathField)
        }
        return null
    }
}
