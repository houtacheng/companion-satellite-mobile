# Companion Satellite Mobile

Companion Satellite Mobile turns Android and iPhone home-screen widgets and system controls into Bitfocus Companion Satellite surfaces.

## Features

- Native Companion Satellite protocol; no Companion HTTP button API.
- Automatic LAN/internet switching for each Companion host.
- Multiple independently configured hosts.
- Android Quick Settings controls and live home-screen widgets.
- Multiple movable always-on-top Android Satellite rotary controls.
- iOS Control Center actions and interactive widgets.
- Widget layouts: 1×1, 3×2, 4×4, rotary 1×1, and 4×4 with rotary controls.
- CSV host import/export with duplicate detection.

## Download

Download the current Android build from the [project website](https://houtacheng.github.io/companion-satellite-mobile/) or the GitHub Releases page.

The iOS source is included. Because the current build uses an Apple Personal Team profile, iOS users must open `CompanionIOS.xcodeproj` in Xcode, choose their own development team, and install it on their device. Public iOS binary distribution requires Apple Developer Program signing.

## Companion and Cloudflare

Companion's web interface normally uses port `8000`, while the Satellite TCP service uses port `16622`. For internet access, publish `/satellite` through a WebSocket-capable Cloudflare Tunnel route to the Companion Satellite WebSocket service. The app derives the public Satellite URL automatically from the host URL.

## Privacy

Host names and addresses are stored locally. Example/private CSV files and Xcode user data are excluded from this repository.

## Build

### Android

- Android Studio / Android SDK 35
- JDK 17 or newer
- Gradle Android plugin 8.7.2

Open the repository in Android Studio and build the `app` target.

### iOS

- Xcode with iOS 18 SDK or newer
- A physical iPhone for Control Center testing

Open `CompanionIOS.xcodeproj`, select a development team for both the app and widget extension, and run on the device.

## Disclaimer

This is an independent companion client and is not affiliated with or endorsed by Bitfocus AS. Bitfocus Companion is a trademark of its respective owner.
