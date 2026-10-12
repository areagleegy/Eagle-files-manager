# Eagle Files Manager

Offline-first Android file manager for **Android 14 (API 34) and newer**.

[الوثائق العربية](README_AR.md)

## ⬇️ Download APK

<p align="center">
  <a href="https://github.com/areagleegy/Eagle-files-manager/releases/latest/download/app-debug.apk">
    <img src="https://img.shields.io/badge/Download-APK%20Directly-2ea44f?style=for-the-badge&logo=android" alt="Download APK directly" />
  </a>
</p>

The button above is the **direct APK URL** and will work once a GitHub Release has been published with the APK attached.

**Until the first release is published:** use the [latest successful GitHub Actions build](https://github.com/areagleegy/Eagle-files-manager/actions/runs/38247954049), download the `eagle-files-manager-android14plus-debug-apk` artifact, extract the ZIP, then install `app-debug.apk`.

> **Build type:** debug/test APK, not a signed production release. Installation may require allowing your browser or file manager to install unknown apps.

## Features

- Local file browsing, search, and file-type filtering
- Create, rename, copy, move, and delete files
- Multi-select files and create ZIP archives
- View images with zoom
- Play audio and video
- Preview PDF and text documents
- List and extract ZIP archives with safety checks

## Privacy and compatibility

- Designed for Android 14 (API 34) and newer.
- Offline-first: the app does not request Android's `INTERNET` permission.

## Build and release

GitHub Actions runs unit tests, builds the debug APK, and checks that the built app does not request network permission. Pushing a version tag such as `v0.1.0` will run the release job and attach `app-debug.apk` to a GitHub Release.

- [Build workflow](https://github.com/areagleegy/Eagle-files-manager/actions/workflows/android-apk.yml)
- [GitHub Releases](https://github.com/areagleegy/Eagle-files-manager/releases)
