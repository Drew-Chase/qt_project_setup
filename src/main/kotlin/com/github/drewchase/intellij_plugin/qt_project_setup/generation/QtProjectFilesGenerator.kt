package com.github.drewchase.intellij_plugin.qt_project_setup.generation

import com.github.drewchase.intellij_plugin.qt_project_setup.wizard.QtProjectSettings
import com.github.drewchase.intellij_plugin.qt_project_setup.wizard.QtUiFramework
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.CountDownLatch

object QtProjectFilesGenerator {

    fun generate(project: Project, baseDir: VirtualFile, settings: QtProjectSettings) {
        val latch = CountDownLatch(1)
        var error: Throwable? = null

        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                try {
            // Create directory structure
            val ideaDir = VfsUtil.createDirectoryIfMissing(baseDir, ".idea")
            val cmakeDir = VfsUtil.createDirectoryIfMissing(baseDir, "cmake")
            val srcDir = VfsUtil.createDirectoryIfMissing(baseDir, "src")
            val srcUiDir = VfsUtil.createDirectoryIfMissing(srcDir, "ui")
            val includeDir = VfsUtil.createDirectoryIfMissing(baseDir, "include")
            val includeUiDir = VfsUtil.createDirectoryIfMissing(includeDir, "ui")
            val uiDir = VfsUtil.createDirectoryIfMissing(baseDir, "ui")
            val resDir = VfsUtil.createDirectoryIfMissing(baseDir, "res")
            val stylesDir = VfsUtil.createDirectoryIfMissing(resDir, "styles")
            val qmlDir = VfsUtil.createDirectoryIfMissing(resDir, "qml")
            val iconsDir = VfsUtil.createDirectoryIfMissing(resDir, "icons")

            val projectName = project.name
            val projectNameUpper = projectName.uppercase().replace("-", "_").replace(" ", "_")
            val qtPath = settings.qtPath.replace("\\", "/")
            
            // Generate .gitignore
            writeFile(baseDir, ".gitignore", generateGitIgnore())

            // Generate .idea/cmake.xml with Qt path
            writeFile(ideaDir, "cmake.xml", generateCmakeXml(qtPath))

            val useQml = settings.uiFramework == QtUiFramework.QML

            // Generate CMakeLists.txt
            writeFile(baseDir, "CMakeLists.txt", generateCMakeLists(projectName, settings.useStaticQt, useQml))

            // Generate cmake helper based on static/dynamic linking
            if (settings.useStaticQt) {
                writeFile(cmakeDir, "QtStaticHelpers.cmake", generateQtStaticHelpers())
            } else {
                writeFile(cmakeDir, "QtDynamicHelpers.cmake", generateQtDynamicHelpers())
            }

            // Generate src/main.cpp
            writeFile(srcDir, "main.cpp", generateMainCpp(settings, useQml))

            // Generate mainwindow files based on titlebar setting
            if (settings.useCustomTitlebar) {
                writeFile(includeUiDir, "mainwindow.h", generateMainWindowHeaderCustom(projectNameUpper, useQml))
                writeFile(srcUiDir, "mainwindow.cpp", generateMainWindowCppCustom(useQml))
                writeFile(uiDir, "mainwindow.ui", generateMainWindowUiCustom(settings))
            } else {
                writeFile(includeUiDir, "mainwindow.h", generateMainWindowHeader(projectNameUpper, useQml))
                writeFile(srcUiDir, "mainwindow.cpp", generateMainWindowCpp(useQml))
                writeFile(uiDir, "mainwindow.ui", generateMainWindowUi(settings))
            }

            // Generate resources
            writeFile(resDir, "resources.qrc", generateResourcesQrc(useQml))
            writeFile(stylesDir, "base.qss", generateBaseQss(settings.useCustomTitlebar))

            // Generate QML files (only when QML framework is selected)
            if (useQml) {
                writeFile(qmlDir, "main.qml", generateMainQml())
                writeFile(qmlDir, "AnimatedButton.qml", generateAnimatedButtonQml())
            } else {
                // Generate StartupPage widget for Qt Widgets mode
                writeFile(includeUiDir, "startup_page.h", generateStartupPageHeader(projectNameUpper))
                writeFile(srcUiDir, "startup_page.cpp", generateStartupPageCpp())
                writeFile(uiDir, "startup_page.ui", generateStartupPageUi())
            }

            // Copy icon files
            copyResourceFile(iconsDir, "app.ico", "/Qt.ico")
            copyResourceFile(iconsDir, "app.png", "/Qt_logo_1024x1024.png")

            // Generate Windows resource file for executable icon
            writeFile(resDir, "app.rc", generateWindowsResourceFile())
                } catch (e: Throwable) {
                    error = e
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for the write action to complete
        latch.await()
        error?.let { throw it }
    }

    private fun writeFile(dir: VirtualFile?, fileName: String, content: String) {
        dir?.let {
            val file = dir.createChildData(this, fileName)
            VfsUtil.saveText(file, content)
        }
    }

    private fun copyResourceFile(dir: VirtualFile?, fileName: String, resourcePath: String) {
        dir?.let {
            val inputStream = QtProjectFilesGenerator::class.java.getResourceAsStream(resourcePath)
            if (inputStream != null) {
                val file = dir.createChildData(this, fileName)
                file.getOutputStream(this).use { output ->
                    inputStream.use { input ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun generateGitIgnore(): String = """
.idea/
cmake-build-*/
bin/
*.user
""".trimIndent()

    private fun generateCmakeXml(qtPath: String): String = """
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="CMakeSharedSettings">
    <configurations>
      <configuration PROFILE_NAME="Debug" ENABLED="true" GENERATION_DIR="bin/obj/debug" CONFIG_NAME="Debug" GENERATION_OPTIONS="-DCMAKE_PREFIX_PATH=$qtPath" />
      <configuration PROFILE_NAME="Release" ENABLED="true" GENERATION_DIR="bin/obj/release" CONFIG_NAME="Release" GENERATION_OPTIONS="-DCMAKE_PREFIX_PATH=$qtPath" />
    </configurations>
  </component>
</project>
""".trimIndent()

    private fun generateCMakeLists(projectName: String, useStaticQt: Boolean, useQml: Boolean): String {
        val cmakeHelper = if (useStaticQt) "QtStaticHelpers" else "QtDynamicHelpers"
        val configureFunction = if (useStaticQt) "configure_qt_static_target" else "configure_qt_dynamic_target"

        // Include QML modules only when QML framework is selected
        val qtModules = if (useQml) {
            """    Widgets
    Gui
    Quick
    Qml
    QuickWidgets
    QuickControls2"""
        } else {
            """    Widgets
    Gui"""
        }

        return """
cmake_minimum_required(VERSION 3.20)
project($projectName)

include(cmake/$cmakeHelper.cmake)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

file(GLOB_RECURSE SOURCES "src/*.cpp")
file(GLOB_RECURSE HEADERS "include/*.h")
file(GLOB_RECURSE UI "ui/*.ui")

# Windows application icon resource
if(WIN32)
    set(WIN_RC res/app.rc)
endif()

add_executable(${"$"}{CMAKE_PROJECT_NAME}
    ${"$"}{SOURCES}
    ${"$"}{HEADERS}
    ${"$"}{UI}
    res/resources.qrc
    ${"$"}{WIN_RC}
)

target_include_directories(${"$"}{CMAKE_PROJECT_NAME} PRIVATE include)

set_target_properties(${"$"}{CMAKE_PROJECT_NAME} PROPERTIES
    AUTOUIC ON
    AUTOUIC_SEARCH_PATHS "${"$"}{PROJECT_SOURCE_DIR}/ui"
)

# Link Windows libraries for custom titlebar support
if(WIN32)
    target_link_libraries(${"$"}{CMAKE_PROJECT_NAME} PRIVATE dwmapi)
endif()

$configureFunction(${"$"}{CMAKE_PROJECT_NAME}
$qtModules
)
""".trimIndent()
    }

    private fun generateQtDynamicHelpers(): String = """
# QtDynamicHelpers.cmake
#
# A module to simplify the creation of dynamically linked Qt6 applications.
# Usage: configure_qt_dynamic_target(target_name Module1 Module2 ...)

message(STATUS "QtDynamicHelpers module loaded.")

# Use CMAKE_SOURCE_DIR to break out of the "obj" build folder
set(OUTPUT_BASE_DIR "${"$"}{CMAKE_SOURCE_DIR}/bin")
if (MSVC)
    # MSVC appends /Debug or /Release automatically
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${"$"}{OUTPUT_BASE_DIR}")
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${"$"}{OUTPUT_BASE_DIR}")
    set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY "${"$"}{OUTPUT_BASE_DIR}")
else ()
    string(TOLOWER "${"$"}{CMAKE_BUILD_TYPE}" BUILD_TYPE_LOWER)
    if (NOT BUILD_TYPE_LOWER)
        set(BUILD_TYPE_LOWER "debug")
    endif ()

    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${"$"}{OUTPUT_BASE_DIR}/${"$"}{BUILD_TYPE_LOWER}")
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${"$"}{OUTPUT_BASE_DIR}/${"$"}{BUILD_TYPE_LOWER}")
    set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY "${"$"}{OUTPUT_BASE_DIR}/${"$"}{BUILD_TYPE_LOWER}")
endif ()

function(configure_qt_dynamic_target TARGET_NAME)
    if(NOT TARGET_NAME)
        message(FATAL_ERROR "configure_qt_dynamic_target() requires a TARGET_NAME.")
    endif()

    set(QT_MODULES ${"$"}{ARGN})
    if(NOT QT_MODULES)
        message(WARNING "No Qt modules specified. Defaulting to Widgets.")
        set(QT_MODULES Widgets)
    endif()

    # Build dependency list (some modules have implicit dependencies)
    set(ALL_MODULES ${"$"}{QT_MODULES})

    # Widgets requires Gui
    if("Widgets" IN_LIST ALL_MODULES AND NOT "Gui" IN_LIST ALL_MODULES)
        list(APPEND ALL_MODULES "Gui")
    endif()

    # Quick/Qml dependencies
    if("Quick" IN_LIST ALL_MODULES OR "QuickWidgets" IN_LIST ALL_MODULES)
        if(NOT "Qml" IN_LIST ALL_MODULES)
            list(APPEND ALL_MODULES "Qml")
        endif()
        if(NOT "Gui" IN_LIST ALL_MODULES)
            list(APPEND ALL_MODULES "Gui")
        endif()
    endif()

    # All modules require Core
    if(NOT "Core" IN_LIST ALL_MODULES)
        list(APPEND ALL_MODULES "Core")
    endif()

    message(STATUS "Configuring Qt build for target: ${"$"}{TARGET_NAME}")
    message(STATUS "  - Qt Modules: ${"$"}{ALL_MODULES}")

    # Find and link Qt packages
    find_package(Qt6 COMPONENTS ${"$"}{ALL_MODULES} REQUIRED)

    set(QT_LIBS "")
    foreach(MOD ${"$"}{ALL_MODULES})
        list(APPEND QT_LIBS "Qt6::${"$"}{MOD}")
    endforeach()

    target_link_libraries(${"$"}{TARGET_NAME} PRIVATE ${"$"}{QT_LIBS})

    # Enable AUTO features
    set_target_properties(${"$"}{TARGET_NAME} PROPERTIES
        AUTOMOC ON
        AUTORCC ON
        AUTOUIC ON
    )

    # Windows-specific: Hide console in Release
    if(WIN32)
        set_target_properties(${"$"}{TARGET_NAME} PROPERTIES
            WIN32_EXECUTABLE ${"$"}<${"$"}<CONFIG:Release>:TRUE>
        )
    endif()

    # Windows DLL deployment
    if(WIN32 AND NOT DEFINED CMAKE_TOOLCHAIN_FILE)
        set(DEBUG_SUFFIX)
        if(MSVC AND CMAKE_BUILD_TYPE MATCHES "Debug")
            set(DEBUG_SUFFIX "d")
        endif()

        # Find Qt installation path from CMAKE_PREFIX_PATH
        set(QT_INSTALL_PATH "${"$"}{CMAKE_PREFIX_PATH}")
        if(NOT EXISTS "${"$"}{QT_INSTALL_PATH}/bin")
            set(QT_INSTALL_PATH "${"$"}{QT_INSTALL_PATH}/..")
            if(NOT EXISTS "${"$"}{QT_INSTALL_PATH}/bin")
                set(QT_INSTALL_PATH "${"$"}{QT_INSTALL_PATH}/..")
            endif()
        endif()

        message(STATUS "Qt install path: ${"$"}{QT_INSTALL_PATH}")

        # Copy platform plugin (required for any Qt GUI app)
        # Qt looks for plugins in <exe_dir>/platforms/ by default
        if(EXISTS "${"$"}{QT_INSTALL_PATH}/plugins/platforms/qwindows${"$"}{DEBUG_SUFFIX}.dll")
            add_custom_command(TARGET ${"$"}{TARGET_NAME} POST_BUILD
                COMMAND ${"$"}{CMAKE_COMMAND} -E make_directory
                    "${"$"}<TARGET_FILE_DIR:${"$"}{TARGET_NAME}>/platforms/")
            add_custom_command(TARGET ${"$"}{TARGET_NAME} POST_BUILD
                COMMAND ${"$"}{CMAKE_COMMAND} -E copy
                    "${"$"}{QT_INSTALL_PATH}/plugins/platforms/qwindows${"$"}{DEBUG_SUFFIX}.dll"
                    "${"$"}<TARGET_FILE_DIR:${"$"}{TARGET_NAME}>/platforms/")
        endif()

        # Copy styles plugin (for native look)
        if(EXISTS "${"$"}{QT_INSTALL_PATH}/plugins/styles")
            add_custom_command(TARGET ${"$"}{TARGET_NAME} POST_BUILD
                COMMAND ${"$"}{CMAKE_COMMAND} -E make_directory
                    "${"$"}<TARGET_FILE_DIR:${"$"}{TARGET_NAME}>/styles/")
            add_custom_command(TARGET ${"$"}{TARGET_NAME} POST_BUILD
                COMMAND ${"$"}{CMAKE_COMMAND} -E copy_directory
                    "${"$"}{QT_INSTALL_PATH}/plugins/styles"
                    "${"$"}<TARGET_FILE_DIR:${"$"}{TARGET_NAME}>/styles/")
        endif()

        # Copy imageformats plugins
        if(EXISTS "${"$"}{QT_INSTALL_PATH}/plugins/imageformats")
            add_custom_command(TARGET ${"$"}{TARGET_NAME} POST_BUILD
                COMMAND ${"$"}{CMAKE_COMMAND} -E make_directory
                    "${"$"}<TARGET_FILE_DIR:${"$"}{TARGET_NAME}>/imageformats/")
            add_custom_command(TARGET ${"$"}{TARGET_NAME} POST_BUILD
                COMMAND ${"$"}{CMAKE_COMMAND} -E copy_directory
                    "${"$"}{QT_INSTALL_PATH}/plugins/imageformats"
                    "${"$"}<TARGET_FILE_DIR:${"$"}{TARGET_NAME}>/imageformats/")
        endif()

        # Copy Qt DLLs for each module
        foreach(QT_LIB ${"$"}{ALL_MODULES})
            set(DLL_PATH "${"$"}{QT_INSTALL_PATH}/bin/Qt6${"$"}{QT_LIB}${"$"}{DEBUG_SUFFIX}.dll")
            if(EXISTS "${"$"}{DLL_PATH}")
                add_custom_command(TARGET ${"$"}{TARGET_NAME} POST_BUILD
                    COMMAND ${"$"}{CMAKE_COMMAND} -E copy
                        "${"$"}{DLL_PATH}"
                        "${"$"}<TARGET_FILE_DIR:${"$"}{TARGET_NAME}>")
            endif()
        endforeach()

        # Copy MinGW runtime DLLs (required for MinGW builds)
        if(MINGW)
            set(MINGW_RUNTIME_DLLS
                "libgcc_s_seh-1.dll"
                "libstdc++-6.dll"
                "libwinpthread-1.dll"
            )
            foreach(DLL_NAME ${"$"}{MINGW_RUNTIME_DLLS})
                set(DLL_PATH "${"$"}{QT_INSTALL_PATH}/bin/${"$"}{DLL_NAME}")
                if(EXISTS "${"$"}{DLL_PATH}")
                    add_custom_command(TARGET ${"$"}{TARGET_NAME} POST_BUILD
                        COMMAND ${"$"}{CMAKE_COMMAND} -E copy
                            "${"$"}{DLL_PATH}"
                            "${"$"}<TARGET_FILE_DIR:${"$"}{TARGET_NAME}>")
                endif()
            endforeach()
        endif()

        # Copy QML runtime if using Quick/Qml
        if("Qml" IN_LIST ALL_MODULES OR "Quick" IN_LIST ALL_MODULES)
            # Additional QML-related DLLs (including hidden runtime dependencies)
            set(QML_EXTRA_DLLS
                "Qt6QmlModels"
                "Qt6QmlWorkerScript"
                "Qt6QmlCore"
                "Qt6QmlMeta"
                "Qt6QmlNetwork"
                "Qt6QuickTemplates2"
                "Qt6QuickLayouts"
                "Qt6QuickControls2"
                "Qt6QuickControls2Impl"
                "Qt6QuickControls2Basic"
                "Qt6QuickControls2BasicStyleImpl"
                "Qt6QuickControls2Fusion"
                "Qt6QuickControls2FusionStyleImpl"
                "Qt6QuickControls2Material"
                "Qt6QuickControls2MaterialStyleImpl"
                "Qt6QuickControls2Universal"
                "Qt6QuickControls2UniversalStyleImpl"
                "Qt6QuickControls2Imagine"
                "Qt6QuickControls2ImagineStyleImpl"
                "Qt6QmlLocalStorage"
                "Qt6QmlXmlListModel"
                "Qt6Network"
                "Qt6OpenGL"
                "Qt6ShaderTools"
                "Qt6Svg"
            )
            foreach(DLL_NAME ${"$"}{QML_EXTRA_DLLS})
                set(DLL_PATH "${"$"}{QT_INSTALL_PATH}/bin/${"$"}{DLL_NAME}${"$"}{DEBUG_SUFFIX}.dll")
                if(EXISTS "${"$"}{DLL_PATH}")
                    add_custom_command(TARGET ${"$"}{TARGET_NAME} POST_BUILD
                        COMMAND ${"$"}{CMAKE_COMMAND} -E copy
                            "${"$"}{DLL_PATH}"
                            "${"$"}<TARGET_FILE_DIR:${"$"}{TARGET_NAME}>")
                endif()
            endforeach()

            # Copy QML modules
            if(EXISTS "${"$"}{QT_INSTALL_PATH}/qml")
                add_custom_command(TARGET ${"$"}{TARGET_NAME} POST_BUILD
                    COMMAND ${"$"}{CMAKE_COMMAND} -E copy_directory
                        "${"$"}{QT_INSTALL_PATH}/qml"
                        "${"$"}<TARGET_FILE_DIR:${"$"}{TARGET_NAME}>/qml")
            endif()

            # Create qt.conf to help Qt find plugins in subdirectories
            file(WRITE "${"$"}{CMAKE_BINARY_DIR}/qt.conf" "[Paths]\nPlugins = .\nQml2Imports = ./qml\n")
            add_custom_command(TARGET ${"$"}{TARGET_NAME} POST_BUILD
                COMMAND ${"$"}{CMAKE_COMMAND} -E copy
                    "${"$"}{CMAKE_BINARY_DIR}/qt.conf"
                    "${"$"}<TARGET_FILE_DIR:${"$"}{TARGET_NAME}>/qt.conf")
        endif()
    endif()

    message(STATUS "Configuration for ${"$"}{TARGET_NAME} complete.")
endfunction()
""".trimIndent()

    private fun generateQtStaticHelpers(): String = """
# QtStaticHelpers.cmake
#
# A module to simplify the creation of statically linked Qt6 applications.

message(STATUS "QtStaticHelpers module loaded.")

# Use CMAKE_SOURCE_DIR to break out of the "obj" build folder
set(OUTPUT_BASE_DIR "${"$"}{CMAKE_SOURCE_DIR}/bin")
if (MSVC)
    # MSVC appends /Debug or /Release automatically
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${"$"}{OUTPUT_BASE_DIR}")
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${"$"}{OUTPUT_BASE_DIR}")
    set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY "${"$"}{OUTPUT_BASE_DIR}")
else ()
    string(TOLOWER "${"$"}{CMAKE_BUILD_TYPE}" BUILD_TYPE_LOWER)
    if (NOT BUILD_TYPE_LOWER)
        set(BUILD_TYPE_LOWER "debug")
    endif ()

    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${"$"}{OUTPUT_BASE_DIR}/${"$"}{BUILD_TYPE_LOWER}")
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "${"$"}{OUTPUT_BASE_DIR}/${"$"}{BUILD_TYPE_LOWER}")
    set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY "${"$"}{OUTPUT_BASE_DIR}/${"$"}{BUILD_TYPE_LOWER}")
endif ()

#[[
# configure_qt_static_target
#
# Applies all necessary properties and links all required libraries
# to build a target as a standalone, statically-linked Qt executable.
#
# Usage:
#   configure_qt_static_target(<TARGET_NAME>
#                              [COMPONENTS ...])
#
# Parameters:
#   TARGET_NAME: The name of the executable target to configure.
#   COMPONENTS: A list of Qt6 components to find and link (e.g., Widgets Gui Core).
#
]]#
function(configure_qt_static_target TARGET_NAME)
    # --- 1. Argument Parsing ---
    if(NOT TARGET_NAME)
        message(FATAL_ERROR "configure_qt_static_target() requires a TARGET_NAME as the first argument.")
    endif()

    # The rest of the arguments are the Qt components
    list(APPEND QT_COMPONENTS ${"$"}{ARGN})
    if(NOT QT_COMPONENTS)
        message(WARNING "No Qt components specified for ${"$"}{TARGET_NAME}. Defaulting to Widgets.")
        set(QT_COMPONENTS Widgets)
    endif()

    message(STATUS "Configuring static Qt build for target: ${"$"}{TARGET_NAME}")
    message(STATUS "  - Required Qt Components: ${"$"}{QT_COMPONENTS}")

    # --- 2. Find and Link Qt Libraries ---
    find_package(Qt6 COMPONENTS ${"$"}{QT_COMPONENTS} REQUIRED)

    # Build the list of qualified Qt6::Component names
    set(QT_LIBRARIES_TO_LINK "")
    foreach(COMPONENT ${"$"}{QT_COMPONENTS})
        list(APPEND QT_LIBRARIES_TO_LINK "Qt6::${"$"}{COMPONENT}")
    endforeach()

    target_link_libraries(${"$"}{TARGET_NAME} PRIVATE ${"$"}{QT_LIBRARIES_TO_LINK})

    # --- 3. Handle Static Plugins (The "Magic") ---
    # A static Qt build must manually link the platform integration plugin.
    if(WIN32)
        message(STATUS "  - Linking Windows platform plugin.")
        target_link_libraries(${"$"}{TARGET_NAME} PRIVATE Qt6::QWindowsIntegrationPlugin)
    elseif(UNIX AND NOT APPLE)
        message(STATUS "  - Linking Linux (XCB) platform plugin.")
        # Note: You must have built Qt with xcb-static support
        target_link_libraries(${"$"}{TARGET_NAME} PRIVATE Qt6::QXcbIntegrationPlugin)
    elseif(APPLE)
        message(STATUS "  - Linking macOS platform plugin.")
        target_link_libraries(${"$"}{TARGET_NAME} PRIVATE Qt6::QCocoaIntegrationPlugin)
    endif()

    # --- 4. Set Target Properties for Build and Optimization ---
    # Enable AUTOMOC, AUTORCC, AUTOUIC for the specific target
    set_property(TARGET ${"$"}{TARGET_NAME} PROPERTY AUTOMOC ON)
    set_property(TARGET ${"$"}{TARGET_NAME} PROPERTY AUTORCC ON)
    set_property(TARGET ${"$"}{TARGET_NAME} PROPERTY AUTOUIC ON)

    # --- 5. Set Compiler and Linker Flags using Generator Expressions ---
    # Apply compiler flags for Release builds to optimize for size
    target_compile_options(${"$"}{TARGET_NAME} PRIVATE
        ${"$"}<${"$"}<CONFIG:Release>:-Os>
    )

    # Apply linker flags for static linking and stripping symbols in Release
    target_link_libraries(${"$"}{TARGET_NAME} PRIVATE
        ${"$"}<${"$"}<CONFIG:Release>:-static -s>
        ${"$"}<${"$"}<CONFIG:Debug>:-static-libgcc -static-libstdc++>
    )

    # Hide console window in Release builds on Windows
    if(WIN32)
        set_target_properties(${"$"}{TARGET_NAME} PROPERTIES
            WIN32_EXECUTABLE ${"$"}<${"$"}<CONFIG:Release>:TRUE>
        )
    endif()

    message(STATUS "Configuration for ${"$"}{TARGET_NAME} complete.")
endfunction()
""".trimIndent()

    private fun generateMainCpp(settings: QtProjectSettings, useQml: Boolean): String {
        return if (!useQml) {
            // Qt Widgets only - no QML/Quick
            """
#include <QApplication>
#include <QIcon>
#include "ui/mainwindow.h"

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);

    // Set the application icon
    app.setWindowIcon(QIcon(":/icons/app.png"));

    // Create and show the main window
    MainWindow window;
    window.show();

    return QApplication::exec();
}
""".trimIndent()
        } else {
            // QML/Quick support
            """
#include <QApplication>
#include <QQmlApplicationEngine>
#include <QQuickStyle>
#include <QDir>
#include <QFileInfo>
#include <QIcon>
#include "ui/mainwindow.h"

#ifdef Q_OS_WIN
#include <windows.h>
#include <string>
#include <cstdlib>
#endif

int main(int argc, char *argv[]) {
#ifdef Q_OS_WIN
    // Add application directory to DLL search path for QML plugins
    wchar_t exePath[MAX_PATH];
    GetModuleFileNameW(nullptr, exePath, MAX_PATH);
    std::wstring exeDir(exePath);
    exeDir = exeDir.substr(0, exeDir.find_last_of(L"\\/"));

    // Prepend exe directory to PATH so QML plugins can find Qt DLLs
    wchar_t currentPath[32767];
    GetEnvironmentVariableW(L"PATH", currentPath, 32767);
    std::wstring newPath = exeDir + L";" + currentPath;
    SetEnvironmentVariableW(L"PATH", newPath.c_str());

    // Also use AddDllDirectory for modern Windows DLL loading
    SetDefaultDllDirectories(LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
    AddDllDirectory(exeDir.c_str());
#endif

    QApplication app(argc, argv);

    // Set the application icon
    app.setWindowIcon(QIcon(":/icons/app.png"));

    // Set the Quick Controls style
    QQuickStyle::setStyle("Fusion");

    // Create and show the main window
    MainWindow window;
    window.show();

    return QApplication::exec();
}
""".trimIndent()
        }
    }

    private fun generateMainWindowHeader(projectNameUpper: String, useQml: Boolean): String {
        val contentIncludes = if (useQml) "#include <QQuickWidget>\n" else "#include \"ui/startup_page.h\"\n"
        val contentMembers = if (useQml) """
    QQuickWidget *qmlWidget;
    void setupQmlWidget();""" else """
    StartupPage *startupPage;
    void setupStartupPage();"""

        return """
#pragma once

#ifndef ${projectNameUpper}_MAINWINDOW_H
#define ${projectNameUpper}_MAINWINDOW_H

#include <QMainWindow>
$contentIncludes
QT_BEGIN_NAMESPACE
namespace Ui {
    class MainWindow;
}
QT_END_NAMESPACE

class MainWindow : public QMainWindow {
    Q_OBJECT

public:
    explicit MainWindow(QWidget *parent = nullptr);
    ~MainWindow() override;

private:
    Ui::MainWindow *ui;$contentMembers
    void loadStyleSheet();
};

#endif // ${projectNameUpper}_MAINWINDOW_H
""".trimIndent()
    }

    private fun generateMainWindowHeaderCustom(projectNameUpper: String, useQml: Boolean): String {
        val contentIncludes = if (useQml) "#include <QQuickWidget>\n" else "#include \"ui/startup_page.h\"\n"
        val contentMembers = if (useQml) """
    QQuickWidget *qmlWidget;
    void setupQmlWidget();""" else """
    StartupPage *startupPage;
    void setupStartupPage();"""

        return """
#pragma once

#ifndef ${projectNameUpper}_MAINWINDOW_H
#define ${projectNameUpper}_MAINWINDOW_H

#include <QMainWindow>
#include <QPoint>
$contentIncludes
QT_BEGIN_NAMESPACE
namespace Ui {
    class MainWindow;
}
QT_END_NAMESPACE

class MainWindow : public QMainWindow {
    Q_OBJECT

public:
    explicit MainWindow(QWidget *parent = nullptr);
    ~MainWindow() override;

protected:
    void mousePressEvent(QMouseEvent *event) override;
    void mouseMoveEvent(QMouseEvent *event) override;
    void mouseReleaseEvent(QMouseEvent *event) override;
    void mouseDoubleClickEvent(QMouseEvent *event) override;
    bool eventFilter(QObject *obj, QEvent *event) override;
#ifdef Q_OS_WIN
    bool nativeEvent(const QByteArray &eventType, void *message, qintptr *result) override;
#endif

private slots:
    void toggleMaximize();

private:
    bool isDragging = false;
    bool dragStartedMaximized = false;
    bool isMaxButtonPressed = false;
    QPoint dragPosition;

    Ui::MainWindow *ui;$contentMembers
    void loadStyleSheet();
    void setupWindowEffects();
    void installEventFilterRecursive(QWidget *widget);
};

#endif // ${projectNameUpper}_MAINWINDOW_H
""".trimIndent()
    }

    private fun generateMainWindowCpp(useQml: Boolean): String {
        return if (!useQml) {
            """
#include "ui/mainwindow.h"
#include "ui_mainwindow.h"
#include <QFile>
#include <QTextStream>
#include <QVBoxLayout>

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent)
    , ui(new Ui::MainWindow)
    , startupPage(nullptr)
{
    ui->setupUi(this);
    loadStyleSheet();
    setupStartupPage();
}

MainWindow::~MainWindow() {
    delete ui;
}

void MainWindow::loadStyleSheet() {
    QFile styleSheet(":/styles/base.qss");
    if (styleSheet.open(QFile::ReadOnly | QFile::Text)) {
        QTextStream stream(&styleSheet);
        this->setStyleSheet(stream.readAll());
        styleSheet.close();
    }
}

void MainWindow::setupStartupPage() {
    startupPage = new StartupPage(this);

    // Add to the content widget
    if (ui->contentWidget->layout()) {
        ui->contentWidget->layout()->addWidget(startupPage);
    } else {
        QVBoxLayout *layout = new QVBoxLayout(ui->contentWidget);
        layout->setContentsMargins(0, 0, 0, 0);
        layout->addWidget(startupPage);
    }
}
""".trimIndent()
        } else {
            """
#include "ui/mainwindow.h"
#include "ui_mainwindow.h"
#include <QFile>
#include <QTextStream>
#include <QVBoxLayout>

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent)
    , ui(new Ui::MainWindow)
    , qmlWidget(nullptr)
{
    ui->setupUi(this);
    loadStyleSheet();
    setupQmlWidget();
}

MainWindow::~MainWindow() {
    delete ui;
}

void MainWindow::loadStyleSheet() {
    QFile styleSheet(":/styles/base.qss");
    if (styleSheet.open(QFile::ReadOnly | QFile::Text)) {
        QTextStream stream(&styleSheet);
        this->setStyleSheet(stream.readAll());
        styleSheet.close();
    }
}

void MainWindow::setupQmlWidget() {
    qmlWidget = new QQuickWidget(this);
    qmlWidget->setResizeMode(QQuickWidget::SizeRootObjectToView);
    qmlWidget->setSource(QUrl("qrc:/qml/main.qml"));

    // Add to the content widget
    if (ui->contentWidget->layout()) {
        ui->contentWidget->layout()->addWidget(qmlWidget);
    } else {
        QVBoxLayout *layout = new QVBoxLayout(ui->contentWidget);
        layout->setContentsMargins(0, 0, 0, 0);
        layout->addWidget(qmlWidget);
    }
}
""".trimIndent()
        }
    }

    private fun generateMainWindowCppCustom(useQml: Boolean): String {
        val contentMemberInit = if (useQml) "\n      , qmlWidget(nullptr)" else "\n      , startupPage(nullptr)"
        val setupContentCall = if (useQml) "\n    setupQmlWidget();" else "\n    setupStartupPage();"
        val contentSetupFunction = if (useQml) """

void MainWindow::setupQmlWidget() {
    qmlWidget = new QQuickWidget(this);
    qmlWidget->setResizeMode(QQuickWidget::SizeRootObjectToView);
    qmlWidget->setSource(QUrl("qrc:/qml/main.qml"));

    // Install event filter for cursor updates
    installEventFilterRecursive(qmlWidget);

    if (ui->contentWidget->layout()) {
        ui->contentWidget->layout()->addWidget(qmlWidget);
    } else {
        QVBoxLayout *layout = new QVBoxLayout(ui->contentWidget);
        layout->setContentsMargins(0, 0, 0, 0);
        layout->addWidget(qmlWidget);
    }
}""" else """

void MainWindow::setupStartupPage() {
    startupPage = new StartupPage(this);

    // Install event filter for cursor updates
    installEventFilterRecursive(startupPage);

    if (ui->contentWidget->layout()) {
        ui->contentWidget->layout()->addWidget(startupPage);
    } else {
        QVBoxLayout *layout = new QVBoxLayout(ui->contentWidget);
        layout->setContentsMargins(0, 0, 0, 0);
        layout->addWidget(startupPage);
    }
}"""
        val vboxLayoutInclude = "#include <QVBoxLayout>\n"

        return """
#include "ui/mainwindow.h"
#include "ui_mainwindow.h"
#include <QFile>
#include <QTextStream>
#include <QMouseEvent>
${vboxLayoutInclude}#include <QScreen>
#include <QApplication>
#include <QEvent>
#include <QStyle>

#ifdef Q_OS_WIN
#include <windows.h>
#include <windowsx.h>
#include <dwmapi.h>

#ifndef DWMWA_WINDOW_CORNER_PREFERENCE
#define DWMWA_WINDOW_CORNER_PREFERENCE 33
#endif
#ifndef DWMWCP_ROUND
#define DWMWCP_ROUND 2
#endif
#endif

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent)
      , ui(new Ui::MainWindow)$contentMemberInit {
    ui->setupUi(this);

    // Enable mouse tracking for resize cursor updates
    setMouseTracking(true);
    centralWidget()->setMouseTracking(true);

    // Install event filter on all child widgets to catch mouse moves
    installEventFilterRecursive(this);

    loadStyleSheet();$setupContentCall
    setupWindowEffects();

#ifdef Q_OS_WIN
    // Add thick frame for resize and animations, but we'll hide the title bar
    HWND hwnd = reinterpret_cast<HWND>(winId());
    LONG style = GetWindowLong(hwnd, GWL_STYLE);
    style |= WS_THICKFRAME | WS_CAPTION | WS_MAXIMIZEBOX | WS_MINIMIZEBOX | WS_SYSMENU;
    SetWindowLong(hwnd, GWL_STYLE, style);

    connect(ui->closeButton, &QPushButton::clicked, this, &MainWindow::close);
    connect(ui->minimizeButton, &QPushButton::clicked, this, [this] {
        ShowWindow(reinterpret_cast<HWND>(winId()), SW_MINIMIZE);
    });
    connect(ui->maximizeButton, &QPushButton::clicked, this, &MainWindow::toggleMaximize);
#endif
}

MainWindow::~MainWindow() {
    delete ui;
}

void MainWindow::toggleMaximize() {
#ifdef Q_OS_WIN
    HWND hwnd = reinterpret_cast<HWND>(winId());
    if (IsZoomed(hwnd)) {
        ShowWindow(hwnd, SW_RESTORE);
    } else {
        ShowWindow(hwnd, SW_MAXIMIZE);
    }
    // Force a frame change to ensure NCCALCSIZE is called and layouts are updated
    SetWindowPos(hwnd, nullptr, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED);
#else
    if (isMaximized()) {
        showNormal();
    } else {
        showMaximized();
    }
#endif
}

void MainWindow::setupWindowEffects() {
#ifdef Q_OS_WIN
    HWND hwnd = reinterpret_cast<HWND>(winId());

    // Enable rounded corners on Windows 11+
    int preference = DWMWCP_ROUND;
    DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, &preference, sizeof(preference));
#endif
}

void MainWindow::installEventFilterRecursive(QWidget *widget) {
    if (!widget) return;

    widget->setMouseTracking(true);
    widget->installEventFilter(this);

    for (QObject *child: widget->children()) {
        if (QWidget *childWidget = qobject_cast<QWidget *>(child)) {
            installEventFilterRecursive(childWidget);
        }
    }
}

bool MainWindow::eventFilter(QObject *obj, QEvent *event) {
    // Event filter kept for potential future use
    return QMainWindow::eventFilter(obj, event);
}

#ifdef Q_OS_WIN
bool MainWindow::nativeEvent(const QByteArray &eventType, void *message, qintptr *result) {
    MSG *msg = static_cast<MSG *>(message);

    if (msg->message == WM_NCCALCSIZE) {
        if (msg->wParam == TRUE) {
            NCCALCSIZE_PARAMS* params = reinterpret_cast<NCCALCSIZE_PARAMS*>(msg->lParam);

            if (IsZoomed(msg->hwnd)) {
                // When maximized, remove the invisible border Windows adds
                HMONITOR hMonitor = MonitorFromWindow(msg->hwnd, MONITOR_DEFAULTTONEAREST);
                MONITORINFO mi;
                mi.cbSize = sizeof(mi);
                GetMonitorInfo(hMonitor, &mi);
                params->rgrc[0] = mi.rcWork;
            } else {
                // Adjust for the invisible borders added by WS_THICKFRAME
                int border_x = GetSystemMetrics(SM_CXSIZEFRAME) + GetSystemMetrics(SM_CXPADDEDBORDER);
                int border_y = GetSystemMetrics(SM_CYSIZEFRAME) + GetSystemMetrics(SM_CXPADDEDBORDER);
                RECT* clientRect = &params->rgrc[0];
                clientRect->left += border_x;
                clientRect->right -= border_x;
                clientRect->bottom -= border_y;
                // Leave only 1 pixel at the top for Windows 11 Snap Layouts.
                // Using border_y here causes a black bar because the top invisible border
                // is handled differently by Windows when WS_CAPTION is present.
                clientRect->top += 1;
            }

            *result = 0;
            return true;
        }
    }

    // Handle hit testing for resize edges and maximize button
    if (msg->message == WM_NCHITTEST) {
        POINT pt = {GET_X_LPARAM(msg->lParam), GET_Y_LPARAM(msg->lParam)};

        // Convert to client coordinates using Windows API
        POINT clientPt = pt;
        ScreenToClient(msg->hwnd, &clientPt);
        QPoint localPos(clientPt.x, clientPt.y);

        // Check title bar buttons FIRST using local coordinates
        if (ui->maximizeButton) {
            QPoint btnPos = ui->maximizeButton->mapFrom(this, localPos);
            if (ui->maximizeButton->rect().contains(btnPos)) {
                *result = HTMAXBUTTON;
                return true;
            }
        }

        if (ui->closeButton) {
            QPoint btnPos = ui->closeButton->mapFrom(this, localPos);
            if (ui->closeButton->rect().contains(btnPos)) {
                *result = HTCLIENT;
                return true;
            }
        }

        if (ui->minimizeButton) {
            QPoint btnPos = ui->minimizeButton->mapFrom(this, localPos);
            if (ui->minimizeButton->rect().contains(btnPos)) {
                *result = HTCLIENT;
                return true;
            }
        }

        // Handle resize edges (only when not maximized)
        if (!IsZoomed(msg->hwnd)) {
            RECT clientRect;
            GetClientRect(msg->hwnd, &clientRect);
            int w = clientRect.right - clientRect.left;
            int h = clientRect.bottom - clientRect.top;

            int x = clientPt.x;
            int y = clientPt.y;

            // Use the invisible borders (negative coordinates) plus a 2-pixel
            // internal area for resize handle detection.
            const int hit = 2;
            bool left = x < hit;
            bool right = x >= w - hit;
            bool top = y < hit;
            bool bottom = y >= h - hit;

            if (left && top) { *result = HTTOPLEFT; return true; }
            if (right && top) { *result = HTTOPRIGHT; return true; }
            if (left && bottom) { *result = HTBOTTOMLEFT; return true; }
            if (right && bottom) { *result = HTBOTTOMRIGHT; return true; }
            if (left) { *result = HTLEFT; return true; }
            if (right) { *result = HTRIGHT; return true; }
            if (top) { *result = HTTOP; return true; }
            if (bottom) { *result = HTBOTTOM; return true; }
        }

        // Return HTCAPTION for titlebar to enable native window dragging & snap
        if (ui->titlebar) {
            QPoint titlePos = ui->titlebar->mapFrom(this, localPos);
            if (ui->titlebar->rect().contains(titlePos)) {
                *result = HTCAPTION;
                return true;
            }
        }
    }

    if (msg->message == WM_GETMINMAXINFO) {
        // Just handle minimum size from Qt
        MINMAXINFO *mmi = reinterpret_cast<MINMAXINFO *>(msg->lParam);
        mmi->ptMinTrackSize.x = minimumWidth();
        mmi->ptMinTrackSize.y = minimumHeight();
        *result = 0;
        return true;
    }

    // Handle maximize button click for Snap Layouts
    if (msg->message == WM_NCLBUTTONDOWN) {
        if (msg->wParam == HTMAXBUTTON) {
            isMaxButtonPressed = true;
            *result = 0;
            return true;
        }
    }

    if (msg->message == WM_NCLBUTTONUP) {
        if (msg->wParam == HTMAXBUTTON && isMaxButtonPressed) {
            isMaxButtonPressed = false;
            toggleMaximize();
            *result = 0;
            return true;
        }
        isMaxButtonPressed = false;
    }

    // Handle mouse hover for Snap Layouts popup
    if (msg->message == WM_NCMOUSEMOVE) {
        if (msg->wParam == HTMAXBUTTON) {
            // Use dynamic property to trigger hover style
            ui->maximizeButton->setProperty("hovered", true);
            ui->maximizeButton->style()->unpolish(ui->maximizeButton);
            ui->maximizeButton->style()->polish(ui->maximizeButton);
            ui->maximizeButton->update();
            *result = 0;
            return false;  // Let Windows handle for Snap Layouts
        } else {
            ui->maximizeButton->setProperty("hovered", false);
            ui->maximizeButton->style()->unpolish(ui->maximizeButton);
            ui->maximizeButton->style()->polish(ui->maximizeButton);
            ui->maximizeButton->update();
        }
    }

    if (msg->message == WM_NCMOUSELEAVE) {
        ui->maximizeButton->setProperty("hovered", false);
        ui->maximizeButton->style()->unpolish(ui->maximizeButton);
        ui->maximizeButton->style()->polish(ui->maximizeButton);
        ui->maximizeButton->update();
    }

    if (msg->message == WM_SIZE) {
        // Ensure Qt layouts are updated when the native window size changes
        this->update();
    }

    return QMainWindow::nativeEvent(eventType, message, result);
}
#endif

void MainWindow::mousePressEvent(QMouseEvent *event) {
    QMainWindow::mousePressEvent(event);
}

void MainWindow::mouseMoveEvent(QMouseEvent *event) {
    QMainWindow::mouseMoveEvent(event);
}

void MainWindow::mouseReleaseEvent(QMouseEvent *event) {
    QMainWindow::mouseReleaseEvent(event);
}

void MainWindow::mouseDoubleClickEvent(QMouseEvent *event) {
    if (event->button() == Qt::LeftButton && ui->titlebar && ui->titlebar->underMouse()) {
        toggleMaximize();
        event->accept();
        return;
    }
    QMainWindow::mouseDoubleClickEvent(event);
}

void MainWindow::loadStyleSheet() {
    QFile styleSheet(":/styles/base.qss");
    if (styleSheet.open(QFile::ReadOnly | QFile::Text)) {
        QTextStream stream(&styleSheet);
        this->setStyleSheet(stream.readAll());
        styleSheet.close();
    }
}$contentSetupFunction
""".trimIndent()
    }

    private fun generateMainWindowUi(settings: QtProjectSettings): String = """
<?xml version="1.0" encoding="UTF-8"?>
<ui version="4.0">
 <class>MainWindow</class>
 <widget class="QMainWindow" name="MainWindow">
  <property name="geometry">
   <rect>
    <x>0</x>
    <y>0</y>
    <width>${settings.startupWidth}</width>
    <height>${settings.startupHeight}</height>
   </rect>
  </property>
  <property name="minimumSize">
   <size>
    <width>${settings.minWidth}</width>
    <height>${settings.minHeight}</height>
   </size>
  </property>
  <property name="windowTitle">
   <string>${settings.windowTitle}</string>
  </property>
  <widget class="QWidget" name="centralwidget">
   <layout class="QVBoxLayout" name="verticalLayout">
    <property name="spacing">
     <number>0</number>
    </property>
    <property name="leftMargin">
     <number>0</number>
    </property>
    <property name="topMargin">
     <number>0</number>
    </property>
    <property name="rightMargin">
     <number>0</number>
    </property>
    <property name="bottomMargin">
     <number>0</number>
    </property>
    <item>
     <widget class="QWidget" name="contentWidget" native="true">
      <property name="sizePolicy">
       <sizepolicy hsizetype="Preferred" vsizetype="Expanding">
        <horstretch>0</horstretch>
        <verstretch>1</verstretch>
       </sizepolicy>
      </property>
     </widget>
    </item>
   </layout>
  </widget>
 </widget>
 <resources>
  <include location="../res/resources.qrc"/>
 </resources>
 <connections/>
</ui>
""".trimIndent()

    private fun generateMainWindowUiCustom(settings: QtProjectSettings): String = """
<?xml version="1.0" encoding="UTF-8"?>
<ui version="4.0">
 <class>MainWindow</class>
 <widget class="QMainWindow" name="MainWindow">
  <property name="geometry">
   <rect>
    <x>0</x>
    <y>0</y>
    <width>${settings.startupWidth}</width>
    <height>${settings.startupHeight}</height>
   </rect>
  </property>
  <property name="minimumSize">
   <size>
    <width>${settings.minWidth}</width>
    <height>${settings.minHeight}</height>
   </size>
  </property>
  <property name="windowTitle">
   <string>${settings.windowTitle}</string>
  </property>
  <widget class="QWidget" name="centralwidget">
   <layout class="QVBoxLayout" name="verticalLayout">
    <property name="spacing">
     <number>0</number>
    </property>
    <property name="leftMargin">
     <number>0</number>
    </property>
    <property name="topMargin">
     <number>0</number>
    </property>
    <property name="rightMargin">
     <number>0</number>
    </property>
    <property name="bottomMargin">
     <number>0</number>
    </property>
    <item>
     <widget class="QFrame" name="titlebar">
      <property name="minimumSize">
       <size>
        <width>0</width>
        <height>40</height>
       </size>
      </property>
      <property name="maximumSize">
       <size>
        <width>16777215</width>
        <height>40</height>
       </size>
      </property>
      <layout class="QHBoxLayout" name="titlebarLayout">
       <property name="leftMargin">
        <number>10</number>
       </property>
       <property name="topMargin">
        <number>0</number>
       </property>
       <property name="rightMargin">
        <number>0</number>
       </property>
       <property name="bottomMargin">
        <number>0</number>
       </property>
       <item>
        <widget class="QLabel" name="iconLabel">
         <property name="minimumSize">
          <size>
           <width>24</width>
           <height>24</height>
          </size>
         </property>
         <property name="maximumSize">
          <size>
           <width>24</width>
           <height>24</height>
          </size>
         </property>
         <property name="text">
          <string/>
         </property>
         <property name="pixmap">
          <pixmap resource="../res/resources.qrc">:/icons/app.png</pixmap>
         </property>
         <property name="scaledContents">
          <bool>true</bool>
         </property>
        </widget>
       </item>
       <item>
        <spacer name="iconSpacer">
         <property name="orientation">
          <enum>Qt::Orientation::Horizontal</enum>
         </property>
         <property name="sizeType">
          <enum>QSizePolicy::Fixed</enum>
         </property>
         <property name="sizeHint" stdset="0">
          <size>
           <width>8</width>
           <height>0</height>
          </size>
         </property>
        </spacer>
       </item>
       <item>
        <widget class="QLabel" name="titleLabel">
         <property name="text">
          <string>${settings.windowTitle}</string>
         </property>
        </widget>
       </item>
       <item>
        <spacer name="titlebarSpacer">
         <property name="orientation">
          <enum>Qt::Orientation::Horizontal</enum>
         </property>
         <property name="sizeHint" stdset="0">
          <size>
           <width>0</width>
           <height>0</height>
          </size>
         </property>
        </spacer>
       </item>
       <item>
        <widget class="QPushButton" name="minimizeButton">
         <property name="minimumSize">
          <size>
           <width>48</width>
           <height>40</height>
          </size>
         </property>
         <property name="maximumSize">
          <size>
           <width>48</width>
           <height>40</height>
          </size>
         </property>
         <property name="font">
          <font>
           <pointsize>12</pointsize>
          </font>
         </property>
         <property name="text">
          <string>−</string>
         </property>
         <property name="flat">
          <bool>true</bool>
         </property>
        </widget>
       </item>
       <item>
        <widget class="QPushButton" name="maximizeButton">
         <property name="minimumSize">
          <size>
           <width>48</width>
           <height>40</height>
          </size>
         </property>
         <property name="maximumSize">
          <size>
           <width>48</width>
           <height>40</height>
          </size>
         </property>
         <property name="font">
          <font>
           <pointsize>12</pointsize>
          </font>
         </property>
         <property name="text">
          <string>□</string>
         </property>
         <property name="flat">
          <bool>true</bool>
         </property>
        </widget>
       </item>
       <item>
        <widget class="QPushButton" name="closeButton">
         <property name="minimumSize">
          <size>
           <width>48</width>
           <height>40</height>
          </size>
         </property>
         <property name="maximumSize">
          <size>
           <width>48</width>
           <height>40</height>
          </size>
         </property>
         <property name="text">
          <string>✕</string>
         </property>
         <property name="flat">
          <bool>true</bool>
         </property>
        </widget>
       </item>
      </layout>
     </widget>
    </item>
    <item>
     <widget class="QWidget" name="contentWidget" native="true">
      <property name="sizePolicy">
       <sizepolicy hsizetype="Preferred" vsizetype="Expanding">
        <horstretch>0</horstretch>
        <verstretch>1</verstretch>
       </sizepolicy>
      </property>
     </widget>
    </item>
   </layout>
  </widget>
 </widget>
 <resources>
  <include location="../res/resources.qrc"/>
 </resources>
 <connections/>
</ui>
""".trimIndent()

    private fun generateResourcesQrc(useQml: Boolean): String {
        val qmlResources = if (!useQml) "" else """
  <qresource prefix="/">
    <file>qml/main.qml</file>
    <file>qml/AnimatedButton.qml</file>
  </qresource>"""

        return """
<RCC>
  <qresource prefix="/">
    <file>styles/base.qss</file>
    <file>icons/app.ico</file>
    <file>icons/app.png</file>
  </qresource>$qmlResources
</RCC>
""".trimIndent()
    }

    private fun generateBaseQss(useCustomTitlebar: Boolean): String {
        val titlebarStyles = if (useCustomTitlebar) """

/* Custom Titlebar Styles */
QFrame#titlebar {
    background-color: #3c3f41;
    border-bottom: 1px solid #555555;
}

QFrame#titlebar QLabel {
    color: #ffffff;
    font-weight: bold;
    font-size: 14px;
}

QFrame#titlebar QPushButton {
    background-color: transparent;
    border: none;
    color: #ffffff;
    font-weight: bold;
    border-radius: 0px;
}

QFrame#titlebar QPushButton#minimizeButton:hover,
QFrame#titlebar QPushButton#maximizeButton:hover,
QFrame#titlebar QPushButton#maximizeButton[hovered="true"] {
    background-color: #505050;
}

QFrame#titlebar QPushButton#closeButton {
    background-color: transparent;
}

QFrame#titlebar QPushButton#closeButton:hover {
    background-color: #e81123;
}

/* Main content area */
QWidget#contentWidget {
    background-color: #2b2b2b;
}
""" else ""

        return """
/* Base Stylesheet */

QMainWindow {
    background-color: #2b2b2b;
    color: #ffffff;
}

QWidget#centralwidget {
    background-color: #2b2b2b;
}

QWidget#contentWidget {
    background-color: #2b2b2b;
}
$titlebarStyles
QPushButton {
    background-color: #3c3f41;
    color: #ffffff;
    border: 1px solid #555555;
    padding: 8px 16px;
    border-radius: 4px;
}

QPushButton:hover {
    background-color: #4a4d50;
}

QPushButton:pressed {
    background-color: #2b2b2b;
}

QLabel {
    color: #ffffff;
}
""".trimIndent()
    }

    private fun generateMainQml(): String = """
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

Rectangle {
    id: root
    color: "#2b2b2b"

    ColumnLayout {
        anchors.centerIn: parent
        spacing: 20

        Text {
            Layout.alignment: Qt.AlignHCenter
            text: "Welcome to your Qt Application"
            color: "#ffffff"
            font.pixelSize: 24
            font.bold: true
        }

        Text {
            Layout.alignment: Qt.AlignHCenter
            text: "Click the animated button below!"
            color: "#aaaaaa"
            font.pixelSize: 14
        }

        AnimatedButton {
            Layout.alignment: Qt.AlignHCenter
            text: "Click Me!"
            onClicked: {
                clickCount++
                statusText.text = "Clicked " + clickCount + " time" + (clickCount > 1 ? "s" : "")
            }

            property int clickCount: 0
        }

        Text {
            id: statusText
            Layout.alignment: Qt.AlignHCenter
            text: "Ready"
            color: "#888888"
            font.pixelSize: 12
        }
    }
}
""".trimIndent()

    private fun generateAnimatedButtonQml(): String = """
import QtQuick
import QtQuick.Controls

Button {
    id: root

    property color baseColor: "#4a90d9"
    property color hoverColor: "#5da3ec"
    property color pressedColor: "#3d7fc8"

    implicitWidth: 160
    implicitHeight: 50

    background: Rectangle {
        id: buttonBackground
        radius: 8
        color: root.pressed ? root.pressedColor : (root.hovered ? root.hoverColor : root.baseColor)

        // Glow effect
        Rectangle {
            id: glowEffect
            anchors.fill: parent
            radius: parent.radius
            color: "transparent"
            border.color: "#ffffff"
            border.width: 2
            opacity: 0
        }

        // Pulse animation
        SequentialAnimation {
            id: pulseAnimation
            running: true
            loops: Animation.Infinite

            PropertyAnimation {
                target: buttonBackground
                property: "scale"
                from: 1.0
                to: 1.05
                duration: 1000
                easing.type: Easing.InOutQuad
            }
            PropertyAnimation {
                target: buttonBackground
                property: "scale"
                from: 1.05
                to: 1.0
                duration: 1000
                easing.type: Easing.InOutQuad
            }
        }

        // Glow animation on hover
        states: State {
            name: "hovered"
            when: root.hovered
            PropertyChanges {
                target: glowEffect
                opacity: 0.5
            }
        }

        transitions: Transition {
            PropertyAnimation {
                properties: "opacity"
                duration: 200
            }
        }

        Behavior on color {
            ColorAnimation { duration: 150 }
        }
    }

    contentItem: Text {
        text: root.text
        font.pixelSize: 16
        font.bold: true
        color: "#ffffff"
        horizontalAlignment: Text.AlignHCenter
        verticalAlignment: Text.AlignVCenter
    }

    // Click animation
    onPressed: {
        clickAnimation.start()
    }

    SequentialAnimation {
        id: clickAnimation
        PropertyAnimation {
            target: root
            property: "scale"
            to: 0.95
            duration: 50
        }
        PropertyAnimation {
            target: root
            property: "scale"
            to: 1.0
            duration: 100
            easing.type: Easing.OutBack
        }
    }
}
""".trimIndent()

    private fun generateWindowsResourceFile(): String = """
IDI_ICON1 ICON "icons/app.ico"
""".trimIndent()

    private fun generateStartupPageHeader(projectNameUpper: String): String = """
#pragma once

#ifndef ${projectNameUpper}_STARTUP_PAGE_H
#define ${projectNameUpper}_STARTUP_PAGE_H

#include <QWidget>
#include <QPropertyAnimation>
#include <QSequentialAnimationGroup>

QT_BEGIN_NAMESPACE
namespace Ui {
    class StartupPage;
}
QT_END_NAMESPACE

class StartupPage : public QWidget {
    Q_OBJECT

public:
    explicit StartupPage(QWidget *parent = nullptr);
    ~StartupPage() override;

protected:
    bool eventFilter(QObject *obj, QEvent *event) override;

private slots:
    void onButtonClicked();

private:
    Ui::StartupPage *ui;
    int clickCount = 0;

    QSequentialAnimationGroup *pulseAnimation;
    QPropertyAnimation *clickAnimation;

    void setupAnimations();
    void startPulseAnimation();
};

#endif // ${projectNameUpper}_STARTUP_PAGE_H
""".trimIndent()

    private fun generateStartupPageCpp(): String = """
#include "ui/startup_page.h"
#include "ui_startup_page.h"
#include <QEvent>
#include <QTimer>
#include <QGraphicsOpacityEffect>

StartupPage::StartupPage(QWidget *parent)
    : QWidget(parent)
    , ui(new Ui::StartupPage)
    , pulseAnimation(nullptr)
    , clickAnimation(nullptr)
{
    ui->setupUi(this);

    // Connect button click
    connect(ui->clickButton, &QPushButton::clicked, this, &StartupPage::onButtonClicked);

    // Install event filter for hover effects
    ui->clickButton->installEventFilter(this);

    setupAnimations();
    startPulseAnimation();
}

StartupPage::~StartupPage() {
    delete ui;
}

void StartupPage::setupAnimations() {
    // Pulse animation (scale effect simulated via stylesheet)
    pulseAnimation = new QSequentialAnimationGroup(this);

    // We'll use opacity for a subtle pulse effect since Qt Widgets doesn't have easy scale
    auto *glowEffect = new QGraphicsOpacityEffect(ui->clickButton);
    glowEffect->setOpacity(1.0);
    ui->clickButton->setGraphicsEffect(glowEffect);

    auto *fadeOut = new QPropertyAnimation(glowEffect, "opacity", this);
    fadeOut->setDuration(1000);
    fadeOut->setStartValue(1.0);
    fadeOut->setEndValue(0.85);
    fadeOut->setEasingCurve(QEasingCurve::InOutQuad);

    auto *fadeIn = new QPropertyAnimation(glowEffect, "opacity", this);
    fadeIn->setDuration(1000);
    fadeIn->setStartValue(0.85);
    fadeIn->setEndValue(1.0);
    fadeIn->setEasingCurve(QEasingCurve::InOutQuad);

    pulseAnimation->addAnimation(fadeOut);
    pulseAnimation->addAnimation(fadeIn);
    pulseAnimation->setLoopCount(-1); // Infinite loop
}

void StartupPage::startPulseAnimation() {
    if (pulseAnimation) {
        pulseAnimation->start();
    }
}

void StartupPage::onButtonClicked() {
    clickCount++;
    QString text = QString("Clicked %1 time%2").arg(clickCount).arg(clickCount > 1 ? "s" : "");
    ui->statusLabel->setText(text);

    // Click animation - briefly change style
    QString originalStyle = ui->clickButton->styleSheet();
    ui->clickButton->setStyleSheet(originalStyle + "background-color: #3d7fc8;");

    QTimer::singleShot(100, this, [this, originalStyle]() {
        ui->clickButton->setStyleSheet(originalStyle);
    });
}

bool StartupPage::eventFilter(QObject *obj, QEvent *event) {
    if (obj == ui->clickButton) {
        if (event->type() == QEvent::Enter) {
            // Hover enter - add glow effect
            ui->clickButton->setStyleSheet(
                ui->clickButton->styleSheet() +
                "border: 2px solid rgba(255, 255, 255, 0.5);"
            );
        } else if (event->type() == QEvent::Leave) {
            // Hover leave - remove glow effect
            QString style = ui->clickButton->styleSheet();
            style.remove("border: 2px solid rgba(255, 255, 255, 0.5);");
            ui->clickButton->setStyleSheet(style);
        }
    }
    return QWidget::eventFilter(obj, event);
}
""".trimIndent()

    private fun generateStartupPageUi(): String = """
<?xml version="1.0" encoding="UTF-8"?>
<ui version="4.0">
 <class>StartupPage</class>
 <widget class="QWidget" name="StartupPage">
  <property name="geometry">
   <rect>
    <x>0</x>
    <y>0</y>
    <width>600</width>
    <height>400</height>
   </rect>
  </property>
  <property name="styleSheet">
   <string notr="true">QWidget#StartupPage {
    background-color: #2b2b2b;
}</string>
  </property>
  <layout class="QVBoxLayout" name="verticalLayout">
   <property name="spacing">
    <number>20</number>
   </property>
   <item>
    <spacer name="topSpacer">
     <property name="orientation">
      <enum>Qt::Orientation::Vertical</enum>
     </property>
     <property name="sizeHint" stdset="0">
      <size>
       <width>20</width>
       <height>40</height>
      </size>
     </property>
    </spacer>
   </item>
   <item>
    <widget class="QLabel" name="welcomeLabel">
     <property name="styleSheet">
      <string notr="true">QLabel {
    color: #ffffff;
    font-size: 24px;
    font-weight: bold;
}</string>
     </property>
     <property name="text">
      <string>Welcome to your Qt Application</string>
     </property>
     <property name="alignment">
      <set>Qt::AlignmentFlag::AlignCenter</set>
     </property>
    </widget>
   </item>
   <item>
    <widget class="QLabel" name="subtitleLabel">
     <property name="styleSheet">
      <string notr="true">QLabel {
    color: #aaaaaa;
    font-size: 14px;
}</string>
     </property>
     <property name="text">
      <string>Click the animated button below!</string>
     </property>
     <property name="alignment">
      <set>Qt::AlignmentFlag::AlignCenter</set>
     </property>
    </widget>
   </item>
   <item>
    <layout class="QHBoxLayout" name="buttonLayout">
     <item>
      <spacer name="leftButtonSpacer">
       <property name="orientation">
        <enum>Qt::Orientation::Horizontal</enum>
       </property>
       <property name="sizeHint" stdset="0">
        <size>
         <width>40</width>
         <height>20</height>
        </size>
       </property>
      </spacer>
     </item>
     <item>
      <widget class="QPushButton" name="clickButton">
       <property name="minimumSize">
        <size>
         <width>160</width>
         <height>50</height>
        </size>
       </property>
       <property name="styleSheet">
        <string notr="true">QPushButton {
    background-color: #4a90d9;
    color: #ffffff;
    font-size: 16px;
    font-weight: bold;
    border: none;
    border-radius: 8px;
    padding: 12px 24px;
}

QPushButton:hover {
    background-color: #5da3ec;
}

QPushButton:pressed {
    background-color: #3d7fc8;
}</string>
       </property>
       <property name="text">
        <string>Click Me!</string>
       </property>
      </widget>
     </item>
     <item>
      <spacer name="rightButtonSpacer">
       <property name="orientation">
        <enum>Qt::Orientation::Horizontal</enum>
       </property>
       <property name="sizeHint" stdset="0">
        <size>
         <width>40</width>
         <height>20</height>
        </size>
       </property>
      </spacer>
     </item>
    </layout>
   </item>
   <item>
    <widget class="QLabel" name="statusLabel">
     <property name="styleSheet">
      <string notr="true">QLabel {
    color: #888888;
    font-size: 12px;
}</string>
     </property>
     <property name="text">
      <string>Ready</string>
     </property>
     <property name="alignment">
      <set>Qt::AlignmentFlag::AlignCenter</set>
     </property>
    </widget>
   </item>
   <item>
    <spacer name="bottomSpacer">
     <property name="orientation">
      <enum>Qt::Orientation::Vertical</enum>
     </property>
     <property name="sizeHint" stdset="0">
      <size>
       <width>20</width>
       <height>40</height>
      </size>
     </property>
    </spacer>
   </item>
  </layout>
 </widget>
 <resources/>
 <connections/>
</ui>
""".trimIndent()
}
