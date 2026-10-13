# Eagle Files Manager

Offline-first Android file manager for **Android 14 (API 34) and newer**.

[الوثائق العربية](README_AR.md)

## ⬇️ Download APK

<p align="center">
  <a href="https://github.com/areagleegy/Eagle-files-manager/releases/latest/download/app-debug.apk">
    <img src="https://img.shields.io/badge/Download-APK%20Directly-2ea44f?style=for-the-badge&logo=android" alt="Download APK directly" />
  </a>
</p>

The button above is the **direct APK URL** and works after the first GitHub Release is published with the APK attached.

**To publish a release without creating a tag manually:** open the [build workflow](https://github.com/areagleegy/Eagle-files-manager/actions/workflows/android-apk.yml), click **Run workflow** on `main`, set `publish_release` to `true` and `release_tag` to `v1.2.0`, then start the run. After tests and the APK build succeed, the workflow creates the release and attaches `app-debug.apk`. For a build-only run, leave `publish_release` set to `false`.

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
