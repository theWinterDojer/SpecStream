# SpecStream 📺

Android TV application that provides access to Spectrum TV streaming in a simple WebView Wrapper.

## Features

**Android TV Optimized** - Built specifically for TV devices with D-pad controls  
**Dual WebView Architecture** - Smooth video playback with overlay guide system   
**Channel Guide** - Access to full program guide with direct channel navigation  
**Hardware Accelerated** - Enhanced playback on Android TV devices

## Requirements

- **Android TV device** (NVIDIA Shield, Android TV boxes, etc.)
- **Android 8.0+** (API level 26+)
- **Spectrum TV subscription** and account

## Installation

### Option 1: Download APK 

1. **Download the latest APK** from the [Releases](https://github.com/YOUR_USERNAME/SpecStream/releases/latest) section
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

## Legal Disclaimer

**IMPORTANT**: This is an unofficial, third-party application developed independently and is NOT affiliated with, endorsed by, or connected to Charter Communications, Spectrum, or any of their subsidiaries.

- **Valid Subscription Required**: Users must have a legitimate, paid Spectrum TV subscription
- **No Content Piracy**: This app does not store, redistribute, or provide unauthorized access to content
- **Web Browser Equivalent**: Functions similarly to accessing watch.spectrum.net through a standard web browser
- **User Responsibility**: Users are responsible for complying with Spectrum's Terms of Service
- **Use at Own Risk**: This application is provided "as-is" without warranties of any kind

**Trademark Notice**: "Spectrum" is a trademark of Charter Communications. This application is not endorsed by or affiliated with Charter Communications.

## Support

For issues or questions:
- Open an issue on GitHub
- Check the troubleshooting section in releases

---

**Enjoy streaming Spectrum TV on your Android TV! 📺** 
