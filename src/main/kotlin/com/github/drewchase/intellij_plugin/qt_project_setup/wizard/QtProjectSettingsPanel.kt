package com.github.drewchase.intellij_plugin.qt_project_setup.wizard

import com.intellij.ide.util.PropertiesComponent
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
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class QtProjectSettingsPanel : GeneratorPeerImpl<QtProjectSettings>() {

    companion object {
        private val LOG = Logger.getInstance(QtProjectSettingsPanel::class.java)
        private const val QT_PATH_PROPERTY_KEY = "qt.project.setup.qt.path"
        private const val USE_STATIC_QT_PROPERTY_KEY = "qt.project.setup.use.static.qt"
        private const val DEFAULT_QT_PATH = "C:/Qt/6.8.0/mingw_64"
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
        text = PropertiesComponent.getInstance().getValue(QT_PATH_PROPERTY_KEY, DEFAULT_QT_PATH)
    }
    private val windowTitleField = JBTextField("My Application")
    private val minWidthSpinner = JSpinner(SpinnerNumberModel(800, 100, 10000, 10))
    private val minHeightSpinner = JSpinner(SpinnerNumberModel(600, 100, 10000, 10))
    private val startupWidthSpinner = JSpinner(SpinnerNumberModel(1280, 100, 10000, 10))
    private val startupHeightSpinner = JSpinner(SpinnerNumberModel(720, 100, 10000, 10))
    private val useCustomTitlebarCheckbox = JBCheckBox("Use custom frameless titlebar", false)
    private val useStaticQtCheckbox = JBCheckBox(
        "Use statically linked Qt",
        PropertiesComponent.getInstance().getBoolean(USE_STATIC_QT_PROPERTY_KEY, false)
    )

    init {
        LOG.info("QtProjectSettingsPanel: Constructor called")
    }

    override fun getSettings(): QtProjectSettings {
        LOG.info("QtProjectSettingsPanel.getSettings() called")
        val qtPath = qtPathField.text
        val useStaticQt = useStaticQtCheckbox.isSelected

        // Cache settings for future use
        val properties = PropertiesComponent.getInstance()
        properties.setValue(QT_PATH_PROPERTY_KEY, qtPath)
        properties.setValue(USE_STATIC_QT_PROPERTY_KEY, useStaticQt)

        return QtProjectSettings(
            qtPath = qtPath,
            windowTitle = windowTitleField.text,
            minWidth = minWidthSpinner.value as Int,
            minHeight = minHeightSpinner.value as Int,
            startupWidth = startupWidthSpinner.value as Int,
            startupHeight = startupHeightSpinner.value as Int,
            useCustomTitlebar = useCustomTitlebarCheckbox.isSelected,
            useStaticQt = useStaticQt
        )
    }

    // This overload is called with the location field and validation callback
    override fun getComponent(myLocationField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent {
        LOG.info("QtProjectSettingsPanel.getComponent(locationField, checkValid) called")
        this.checkValid = checkValid

        // Add listener to trigger validation when Qt path changes
        qtPathField.textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = checkValid.run()
            override fun removeUpdate(e: DocumentEvent?) = checkValid.run()
            override fun changedUpdate(e: DocumentEvent?) = checkValid.run()
        })

        // Add listener to trigger validation when static Qt checkbox changes
        useStaticQtCheckbox.addActionListener { checkValid.run() }

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
                label("Startup Width:")
                cell(startupWidthSpinner)
            }

            row("Minimum Height:") {
                cell(minHeightSpinner)
                label("Startup Height:")
                cell(startupHeightSpinner)
            }

            row {
                cell(useStaticQtCheckbox)
                cell(useCustomTitlebarCheckbox)
            }
        }
        LOG.info("QtProjectSettingsPanel.attachTo() completed")
    }

    override fun validate(): ValidationInfo? {
        LOG.info("QtProjectSettingsPanel.validate() called")
        val qtPath = qtPathField.text
        val useStaticQt = useStaticQtCheckbox.isSelected

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

        if (useStaticQt) {
            // For static Qt, check for lib directory with static libraries
            val libDir = File(file, "lib")
            if (!libDir.exists() || !libDir.isDirectory) {
                return ValidationInfo("Static Qt installation path should contain a 'lib' directory", qtPathField)
            }
            // Check for static library (Qt6Core.lib for MSVC or libQt6Core.a for MinGW)
            val qt6CoreLib = File(libDir, "libQt6Core.a")
            val qt6CoreLibMsvc = File(libDir, "Qt6Core.lib")
            if (!qt6CoreLib.exists() && !qt6CoreLibMsvc.exists()) {
                return ValidationInfo("Static Qt installation path should contain Qt6Core static library in 'lib' directory", qtPathField)
            }
        } else {
            // For dynamic Qt, check for bin directory with DLLs
            val binDir = File(file, "bin")
            if (!binDir.exists() || !binDir.isDirectory) {
                return ValidationInfo("Qt installation path should contain a 'bin' directory", qtPathField)
            }
            // Check if Qt6Core.dll exists
            val qt6CoreDll = File(binDir, "Qt6Core.dll")
            if (!qt6CoreDll.exists()) {
                return ValidationInfo("Qt installation path should contain 'Qt6Core.dll'", qtPathField)
            }
        }
        return null
    }
}
