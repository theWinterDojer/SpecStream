# SpecStream 📺

A simple Android TV application that provides access to Spectrum TV streaming. 

## Features

**Android TV Optimized** - Built specifically for TV devices and remote controls  
**Dual WebView Architecture** - Smooth video playback with overlay guide system  
**D-pad Navigation** - Complete support for TV remote controls  
**Channel Guide** - Full program guide with direct channel navigation  
**Hardware Accelerated** - Enhanced playback on Android TV devices  

## Requirements

- **Android TV device** (NVIDIA Shield, Android TV boxes, etc.)
- **Android 8.0+** (API level 26+)
- **Spectrum TV subscription** and account

## Installation

### Option 1: Download APK (Recommended)

1. **Download the latest APK** from the [Releases](https://github.com/YOUR_USERNAME/SpecStream/releases) section
2. **Transfer to your Android TV** via USB drive or network transfer
3. **Enable Unknown Sources** on your Android TV:
   - Settings → Device Preferences → About → Build (click 7 times to enable Developer Options)
   - Settings → Device Preferences → Developer Options → Unknown Sources → Allow
4. **Install the APK** using a file manager or ADB

### Option 2: Build from Source

For developers who want to modify or contribute:

1. **Clone and build:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/SpecStream.git
   cd SpecStream
   ./gradlew assembleDebug
   ```

2. **Open in Android Studio** for development and debugging

## Usage

1. **Launch SpecStream** from your Android TV home screen
2. **Log into your Spectrum account** when prompted
3. **Navigate with your TV remote:**
   - **D-pad Up/Down**: Open channel guide
   - **D-pad Left/Right**: Navigate within guide (when open)
   - **D-pad Center/Enter**: Select channel
   - **Back button**: Hide guide or exit app (double-press)

## Supported Devices

- ✅ **NVIDIA Shield TV**
- ✅ **Android TV boxes**
- ✅ **Smart TVs with Android TV**
- ✅ **Chromecast with Google TV**

## Technical Details

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Latest)
- **Architecture**: Dual WebView with JavaScript bridge
- **TV Features**: Leanback launcher, D-pad navigation, hardware acceleration

## Contributing

Contributions are welcome! Please feel free to submit issues, feature requests, or pull requests.

## License

This project is open source. Please see the LICENSE file for details.

## Disclaimer

This is an unofficial application. SpecStream is not affiliated with Charter Communications or Spectrum. Users must have a valid Spectrum TV subscription to use this application.

## Support

For issues or questions:
- Open an issue on GitHub
- Check the troubleshooting section in releases

---

**Enjoy streaming Spectrum TV on your Android TV! 📺** 