# Qt Project Setup Plugin

An IntelliJ/CLion plugin that generates Qt6 project scaffolding with CMake configuration, ready-to-build source files, and optional QML support.

## Features

- **Qt6 Project Generation** - Creates a complete Qt6 project structure with CMake build system
- **UI Framework Selection** - Choose between Qt Widgets or QML (Qt Quick) for your UI
- **Static/Dynamic Linking** - Support for both statically and dynamically linked Qt builds
- **Custom Titlebar** - Optional frameless window with custom titlebar (Windows native integration)
- **Demo Interface** - Generated projects include a demo startup page with animated button
- **Windows Icon Support** - Automatic resource file generation for application icons
- **Settings Persistence** - Qt path, static linking preference, and UI framework choices are cached

## Installation

1. Build the plugin using Gradle:
   ```bash
   ./gradlew buildPlugin
   ```
2. Install the plugin from `build/distributions/qt_project_setup-*.zip` in your IDE

## Usage

1. Open CLion or IntelliJ IDEA
2. Create a new project and select "Qt Application" from the project types
3. Configure your project settings:
   - **Qt Installation Path** - Path to your Qt installation (e.g., `C:/Qt/6.8.0/mingw_64`)
   - **Window Title** - The title for your application window
   - **UI Framework** - Qt Widgets or QML (Qt Quick)
   - **Minimum/Startup Size** - Window dimensions
   - **Use Static Qt** - Enable for statically linked Qt builds
   - **Custom Titlebar** - Enable for frameless window with custom titlebar

## Generated Project Structure

```
project/
├── .idea/
│   └── cmake.xml           # CMake configuration with Qt path
├── cmake/
│   └── QtStaticHelpers.cmake   # (or QtDynamicHelpers.cmake)
├── include/
│   └── ui/
│       ├── mainwindow.h
│       └── startup_page.h  # (Qt Widgets only)
├── src/
│   ├── main.cpp
│   └── ui/
│       ├── mainwindow.cpp
│       └── startup_page.cpp  # (Qt Widgets only)
├── ui/
│   ├── mainwindow.ui
│   └── startup_page.ui     # (Qt Widgets only)
├── res/
│   ├── resources.qrc
│   ├── app.rc              # Windows icon resource
│   ├── icons/
│   │   ├── app.ico
│   │   └── app.png
│   ├── styles/
│   │   └── base.qss
│   └── qml/                # (QML only)
│       ├── main.qml
│       └── AnimatedButton.qml
├── CMakeLists.txt
└── .gitignore
```

## Configuration Options

### Qt Installation Path
The path to your Qt6 installation directory. For dynamic builds, this should contain a `bin` directory with Qt DLLs. For static builds, it should contain a `lib` directory with static libraries.

**Validation:**
- Dynamic Qt: Checks for `bin/Qt6Core.dll`
- Static Qt: Checks for `lib/libQt6Core.a` (MinGW) or `lib/Qt6Core.lib` (MSVC)

### UI Framework

| Framework | Description |
|-----------|-------------|
| **Qt Widgets** | Traditional desktop UI with `.ui` files and C++ widgets. Includes a StartupPage component with animated button demo. |
| **QML (Qt Quick)** | Modern declarative UI using QML. Includes animated button component. Note: May have limitations with static Qt builds. |

### Static vs Dynamic Linking

| Mode | Description |
|------|-------------|
| **Dynamic** | Links against Qt DLLs. Smaller executable but requires Qt DLLs at runtime. Plugin auto-deploys required DLLs. |
| **Static** | Links Qt statically into executable. Larger file but no runtime dependencies. Requires static Qt build. |

### Custom Titlebar
When enabled, generates a frameless window with:
- Custom titlebar with minimize/maximize/close buttons
- Windows 11 Snap Layouts support
- Native window resize behavior
- Rounded corners on Windows 11+

## Build Output

The generated CMake configuration outputs binaries to:
- Debug: `bin/debug/`
- Release: `bin/release/`

For dynamic Qt builds on Windows, required DLLs and plugins are automatically copied to the output directory.

## Requirements

- JetBrains CLion or IntelliJ IDEA with C++ support
- Qt6 installation (6.x recommended)
- CMake 3.20 or later
- C++20 compatible compiler

## Development

### Building the Plugin
```bash
./gradlew buildPlugin
```

### Running in Development
```bash
./gradlew runIde
```

### Running Tests
```bash
./gradlew test
```

## License

See [LICENSE](LICENSE) for details.
