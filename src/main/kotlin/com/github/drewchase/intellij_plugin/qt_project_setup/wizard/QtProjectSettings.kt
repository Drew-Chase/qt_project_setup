package com.github.drewchase.intellij_plugin.qt_project_setup.wizard

enum class QtUiFramework(val displayName: String) {
    WIDGETS("Qt Widgets"),
    QML("QML (Qt Quick)");

    override fun toString(): String = displayName

    companion object {
        fun fromDisplayName(name: String): QtUiFramework =
            entries.find { it.displayName == name } ?: WIDGETS
    }
}

data class QtProjectSettings(
    var qtPath: String = "C:/Qt/6.8.0/mingw_64",
    var windowTitle: String = "My Application",
    var minWidth: Int = 800,
    var minHeight: Int = 600,
    var startupWidth: Int = 1280,
    var startupHeight: Int = 720,
    var useCustomTitlebar: Boolean = false,
    var useStaticQt: Boolean = false,
    var uiFramework: QtUiFramework = QtUiFramework.WIDGETS
)
