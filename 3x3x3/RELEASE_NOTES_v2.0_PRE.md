# Release: Twenty-Seven v2.0-pre

Welcome to the first pre-release of **Twenty-Seven**, a modernized version of the classic 3D Tic-Tac-Toe concept. Originally built for the Android Experiments 2016 contest, this version has been completely refreshed for 2026.

## 🎲 Game Concept
**Twenty-Seven** expands the traditional 3×3 game of Tic-Tac-Toe into a three-dimensional 3×3×3 cube.
- **Winning Condition:** Connect three markers in any direction—horizontally, vertically, or diagonally—across the entire cube.
- **Multi-Device Gameplay:** The vertical planes of the 3D cube are physically distributed across multiple Android devices. Using Google's Nearby Messages API, players interact with a single shared game state across their screens.

## 🚀 What's New in v2.0
This pre-release marks the transition from the original 2016 experimental code to a modern Android environment:
- **Target SDK 35:** Fully updated for modern Android versions (Android 15+).
- **Nearby Connections Refresh:** Updated the multi-device communication logic to handle modern Bluetooth and WiFi permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `NEARBY_WIFI_DEVICES`).
- **Modern Tech Stack:** Migrated to ViewBinding, Timber for logging, and updated AndroidX dependencies.
- **Enhanced Stability:** Improved game state synchronization and win-checking logic.

## 🛠 Technical Requirements
- **Minimum SDK:** Android 5.0 (Lollipop)
- **Target SDK:** Android 15 (API 35)
- **Permissions:** The app requires Bluetooth, Location, and Nearby Devices permissions to synchronize game planes between devices.

## 🧪 Testing Instructions
To fully experience the 3D gameplay:
1. Install the APK on **three** Android devices.
2. Ensure Bluetooth and Location are enabled on all devices.
3. Open the app on each device.
4. Each device will represent one of the three vertical planes of the 3x3x3 cube.
5. Place your markers and watch as the game state updates across all screens!

## ⚠️ Pre-release Note
This is an alpha/pre-release version. You may encounter connectivity issues depending on your local network environment. Please report any bugs or synchronization errors in the GitHub Issues tab.

---
*Built with Claude Opus 4.6 and Gemini CLI.*
