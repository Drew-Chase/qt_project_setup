package com.github.drewchase.intellij_plugin.qt_project_setup.wizard

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.intellij.platform.DirectoryProjectGenerator

open class QtProjectSettingsStep(generator: DirectoryProjectGenerator<QtProjectSettings>)
    : ProjectSettingsStepBase<QtProjectSettings>(generator, AbstractNewProjectStep.AbstractCallback())
