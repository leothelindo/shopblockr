# ShopBlockr - TikTok Shopping Distraction Blocker

ShopBlockr is an Android accessibility service that helps protect users from shopping distractions by detecting sponsored content and shopping elements in TikTok. The app provides protective warnings and overlays to help users maintain focus and avoid impulsive shopping decisions.

## Features

- **Real-time Detection**: Uses accessibility services to detect shopping-related content in TikTok
- **Protective Overlays**: Shows warning overlays when sponsored content is detected
- **Usage Analytics**: Tracks blocked shopping attempts and potential savings
- **Customizable Settings**: Configure blocking sensitivity and overlay preferences
- **Device Compatibility**: Optimized for various Android devices
- **Helpful Tips**: Provides guidance on mindful social media usage

## How It Works

ShopBlockr works by monitoring TikTok through Android's accessibility services. When shopping-related content, sponsored posts, or shopping buttons are detected, the app displays protective overlays to help users make conscious decisions about their engagement.

## Permissions Required

- **Accessibility Service**: To read screen content and detect shopping elements
- **System Alert Window**: To display protective overlays
- **Vibrate**: For haptic feedback on warnings
- **Foreground Service**: To maintain protection while TikTok is active
- **Post Notifications**: For Android 13+ notification requirements
- **Package Usage Stats**: To track app usage patterns

## Download

### APK Download

Currently, you'll need to build the APK from source. Follow the build instructions below to generate your own APK file.

**Note**: The APK is not yet available for direct download. Please build from source for now.

## Building from Source

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK API 33 or higher
- Gradle 8.0+

### Build Instructions

1. Clone this repository:
   ```bash
   git clone <repository-url>
   cd ad_blocker
   ```

2. Open the project in Android Studio

3. Build the APK:
   ```bash
   ./gradlew assembleRelease
   ```

4. The APK will be generated at:
   ```
   app/build/outputs/apk/release/app-release.apk
   ```

### Debug Build (for development)

```bash
./gradlew assembleDebug
```

## Installation

1. Enable "Unknown Sources" in your Android device settings
2. Download or build the APK file
3. Install the APK on your device
4. Grant accessibility permissions when prompted
5. Enable the ShopBlockr accessibility service in Settings > Accessibility

## Usage

1. Launch ShopBlockr and complete the initial setup
2. Enable the accessibility service when prompted
3. Open TikTok - ShopBlockr will automatically start protecting you
4. When shopping content is detected, protective overlays will appear
5. Use the main app to view analytics and adjust settings

## Privacy & Security

- ShopBlockr only processes screen content locally on your device
- No personal data is transmitted to external servers
- The app only monitors TikTok when active
- All analytics data remains on your device

## Compatibility

- Android 7.0 (API 24) and above
- Optimized for TikTok's current interface
- Tested on various screen sizes and resolutions

## Contributing

This is a personal project focused on helping users maintain healthy social media habits. If you'd like to contribute improvements or report issues, please feel free to submit pull requests or open issues.

## License

This project is intended for personal use and educational purposes.

## Disclaimer

ShopBlockr is an independent tool and is not affiliated with TikTok or ByteDance. Use of this app should comply with TikTok's terms of service and your local laws regarding accessibility services.