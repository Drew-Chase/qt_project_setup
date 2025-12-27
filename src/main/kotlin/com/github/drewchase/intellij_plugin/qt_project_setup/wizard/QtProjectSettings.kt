package com.github.drewchase.intellij_plugin.qt_project_setup.wizard

data class QtProjectSettings(
    var qtPath: String = "C:/Qt/6.8.0/mingw_64",
    var windowTitle: String = "My Application",
    var minWidth: Int = 800,
    var minHeight: Int = 600,
    var startupWidth: Int = 1280,
    var startupHeight: Int = 720,
    var useCustomTitlebar: Boolean = false,
    var useStaticQt: Boolean = false
)
