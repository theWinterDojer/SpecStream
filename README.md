# SpecStream

A modern Android TV application that provides seamless access to Spectrum TV streaming with optimized remote control navigation.

## Features

✅ **Android TV Optimized** - Built specifically for TV devices and remote controls  
✅ **Dual WebView Architecture** - Smooth video playback with overlay guide system  
✅ **D-pad Navigation** - Complete support for TV remote controls  
✅ **Channel Guide** - Full program guide with direct channel navigation  
✅ **Professional UI** - Clean, TV-focused interface without desktop clutter  
✅ **Hardware Accelerated** - Smooth video playback on Android TV devices  

## Requirements

- **Android TV device** (NVIDIA Shield, Android TV boxes, etc.)
- **Android 8.0+** (API level 26+)
- **Spectrum TV subscription** and account

## Installation

### Option 1: Build from Source

1. **Clone the repository:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/SpecStream.git
   cd SpecStream
   ```

2. **Open in Android Studio:**
   - File → Open → Select the SpecStream folder
   - Let Gradle sync automatically

3. **Build and install:**
   ```bash
   ./gradlew assembleDebug
   ```
   Or use Android Studio: Build → Make Project

4. **Install on your Android TV:**
   - Connect your Android TV device via ADB
   - Run the app from Android Studio, or
   - Install the APK manually

### Option 2: Download Release APK

*Coming soon - releases will be available in the GitHub Releases section*

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