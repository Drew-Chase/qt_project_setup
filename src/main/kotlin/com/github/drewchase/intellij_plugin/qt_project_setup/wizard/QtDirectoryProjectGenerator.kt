package com.github.drewchase.intellij_plugin.qt_project_setup.wizard

import com.github.drewchase.intellij_plugin.qt_project_setup.generation.QtProjectFilesGenerator
import com.intellij.facet.ui.ValidationResult
import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.CustomStepProjectGenerator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.AbstractActionWithPanel
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.ProjectGeneratorPeer
import javax.swing.Icon

// We implement CustomStepProjectGenerator as well to correctly show settings UI
// because otherwise CLion doesn't add peer's component into project settings panel
class QtDirectoryProjectGenerator : DirectoryProjectGeneratorBase<QtProjectSettings>(),
                                    CustomStepProjectGenerator<QtProjectSettings> {

    private var peer: QtProjectSettingsPanel? = null

    companion object {
        private val LOG = Logger.getInstance(QtDirectoryProjectGenerator::class.java)
    }

    override fun getName(): String = "Qt Windowed Application"

    override fun getLogo(): Icon = AllIcons.Nodes.Module

    override fun getDescription(): String = "Creates a Qt 6 Windows application with CMake, QML support, and optional custom titlebar"

    override fun createPeer(): ProjectGeneratorPeer<QtProjectSettings> {
        LOG.info("QtDirectoryProjectGenerator.createPeer() called")
        return QtProjectSettingsPanel().also { peer = it }
    }

    override fun validate(baseDirPath: String): ValidationResult {
        LOG.info("QtDirectoryProjectGenerator.validate() called with path: $baseDirPath")
        return ValidationResult.OK
    }

    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        settings: QtProjectSettings,
        module: Module
    ) {
        LOG.info("QtDirectoryProjectGenerator.generateProject() called!")
        LOG.info("  project: ${project.name}")
        LOG.info("  baseDir: ${baseDir.path}")
        LOG.info("  settings: qtPath=${settings.qtPath}, windowTitle=${settings.windowTitle}")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Creating Qt Project...", false) {
            override fun run(indicator: ProgressIndicator) {
                LOG.info("Background task started")
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                indicator.text = "Generating Qt project files..."

                try {
                    LOG.info("Calling QtProjectFilesGenerator.generate()")
                    QtProjectFilesGenerator.generate(project, baseDir, settings)
                    LOG.info("QtProjectFilesGenerator.generate() completed")

                    indicator.fraction = 0.8
                    indicator.text = "Refreshing project..."

                    baseDir.refresh(false, true)

                    indicator.fraction = 1.0
                    indicator.text = "Done!"
                    LOG.info("Project generation completed successfully")
                } catch (e: Exception) {
                    LOG.error("Error generating project", e)
                    indicator.text = "Error: ${e.message}"
                    throw e
                }
            }
        })
    }

    // CustomStepProjectGenerator implementation - this is key for showing settings UI
    override fun createStep(
        projectGenerator: DirectoryProjectGenerator<QtProjectSettings>,
        callback: AbstractNewProjectStep.AbstractCallback<QtProjectSettings>
    ): AbstractActionWithPanel {
        LOG.info("QtDirectoryProjectGenerator.createStep() called")
        return QtProjectSettingsStep(projectGenerator)
    }
}
