# Matchday LAN

**Matchday LAN** is an original 3D 11v11 Android football game built with Kotlin, Jetpack Compose, and OpenGL ES 3.0, featuring offline local Wi-Fi (LAN) multiplayer.

## Features

- **11v11 Football Simulation**: Full 22-player match simulation with ball physics, team formations, AI teammates/opponents, and match rules.
- **OpenGL ES 3.0 3D Rendering**: Custom 3D pitch, stadium, players, ball rendering, and dynamic match camera.
- **Offline Local Wi-Fi (LAN) Multiplayer**: Host or join matches over a local Wi-Fi network without requiring an internet connection or external servers.
- **Modern Android UI**: Built with Kotlin and Jetpack Compose for responsive menus, lobby management, and match HUD.

## Project Structure

```text
wwww/
├── .github/workflows/       # CI/CD workflows
├── app/
│   ├── src/                 # Android application source code & resources
│   ├── build.gradle.kts     # App-level Gradle configuration
│   └── proguard-rules.pro   # ProGuard/R8 rules
├── gradle/                  # Gradle wrapper & version catalog
├── build.gradle.kts         # Root Gradle build script
├── gradle.properties        # Gradle properties
├── settings.gradle.kts      # Project settings & module includes
├── LICENSE                  # MIT License
└── README.md                # Project documentation
```

## Setup & Build

### Requirements

- **Android Studio** (Ladybug or newer recommended)
- **JDK 17** (or compatible with Android Gradle Plugin)
- **Android SDK**:
  - `minSdk`: **24** (Android 7.0)
  - `compileSdk`: **36**
  - `targetSdk`: **36**

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/axenvoir/wwww
   cd wwww
   ```
2. Open the project in Android Studio and let Gradle sync, or build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```
3. Install the debug APK on a connected Android device or emulator:
   ```bash
   ./gradlew installDebug
   ```

## LAN Gameplay Guide

1. **Connect to the Same Network**: Ensure both Android devices are connected to the same local Wi-Fi network (or mobile hotspot).
2. **Host a Match**: On the first device, open **Matchday LAN**, navigate to the LAN Multiplayer lobby, and select **Host Match**. Note the displayed local IP address / room info.
3. **Join the Match**: On the second device, select **Join Match** and connect to the host's session on the local network.
4. **Kick Off**: Once both players are ready in the lobby, start the match and play 11v11 over LAN.

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
