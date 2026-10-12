# Eagle Files Manager

Offline-first Android file manager for **Android 14 (API 34) and newer**.

[الوثائق العربية](README_AR.md)

## ⬇️ Download APK

<p align="center">
  <a href="https://github.com/areagleegy/Eagle-files-manager/actions/runs/38247954049">
    <img src="https://img.shields.io/badge/Download-Android%20APK-2ea44f?style=for-the-badge&logo=android" alt="Download Android APK" />
  </a>
</p>

**How to download and install**
1. Tap the **Download Android APK** button above.
2. On the GitHub Actions run page, find **Artifacts** and download `eagle-files-manager-android14plus-debug-apk`.
3. Extract the downloaded ZIP archive and install `app-debug.apk` on your Android 14+ device. Android may ask you to allow installation from your browser or file manager.

> **Note:** GitHub Actions currently provides the APK inside a ZIP artifact, not as a standalone APK link. This is a debug/test build, not a signed production release. The artifact for the linked run expires on **November 11, 2026**.

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

## Build status

The Android APK is built with GitHub Actions. See [workflow runs](https://github.com/areagleegy/Eagle-files-manager/actions/workflows/android-apk.yml) for build history.
