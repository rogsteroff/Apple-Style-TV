# Apple Style TV — TV updater agent (design)

Goal: update the launcher directly on Android TV, without a PC or manually copying APK files.

## Delivery
- GitHub Actions compiles the APK.
- Publish a **public, stable HTTPS APK URL** from a GitHub Release (workflow artifacts require GitHub authentication and cannot be downloaded anonymously by the TV).
- A separate Android TV updater app checks the release metadata, compares versions, and downloads the APK.

## Installation and permissions
- The updater must use Android PackageInstaller with a user-visible confirmation, unless the device grants privileged installer capabilities.
- Never expose ADB on the internet, store GitHub tokens in the TV app, silently enable accessibility services, or bypass Android's installation prompts.
- Verify package name `com.gathin.tvhome.applestyle`, versionCode, and APK signing certificate before offering an update.
- Android 8+ requires permission to install unknown apps for the updater.
- Keep the existing launcher operational if downloading or installation fails.

## First setup
1. Build and publish a release APK from the intended branch.
2. Install the updater once using Downloader.
3. Allow installation from the updater when prompted.
4. The updater checks for releases and offers the next install.

## Current status
This is a design scaffold, **not yet a functional installer**. A release-publishing workflow and Android updater APK still need implementation and verification.
