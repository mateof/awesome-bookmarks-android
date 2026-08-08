# AwesomeBookmarks for Android

An Android companion app for a self-hosted [AwesomeBookmarks](https://github.com/mateof/awesome-bookmarks-manager) server.

The web interface already works in a mobile browser. What a browser tab cannot do is be *in the share sheet*, and that is the whole point: from any page in any browser, share it and the app saves it with the tags you want, in the folder you want, the way Wallabag does.

[![CI](https://github.com/mateof/awesome-bookmarks-android/actions/workflows/ci.yml/badge.svg)](https://github.com/mateof/awesome-bookmarks-android/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/mateof/awesome-bookmarks-android?include_prereleases&sort=semver)](https://github.com/mateof/awesome-bookmarks-android/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-26-brightgreen)](app/build.gradle.kts)

---

## What it does

| | Browser tab | This app |
|---|---|---|
| Save the page you are reading | Copy the URL, switch app, paste | Share → pick folder and tags → done |
| Tags while saving | No | Autocomplete against your server, unknown ones created |
| Folder while saving | No | Full folder tree, last one remembered |
| Home screen widget and launcher shortcut | No | Yes |
| Stays signed in | Until the server drops the session | Indefinitely, until you sign out |
| Biometric lock in front of your library | No | Optional, on by default |
| Automatic LAN then VPN fallback | No | Yes |
| Tells you when a new version ships | No | Yes, with one tap to install |
| HTML export downloads | Usually fail silently | Saved to Downloads |

Everything else, the whole library UI, is your server's own web app in a WebView. This app does not reimplement it.

## Requirements

- An AwesomeBookmarks server you can reach from the phone. See its own [deployment docs](https://github.com/mateof/awesome-bookmarks-manager); the short version is one Docker container on port 3001.
- An account on it.
- Android 8.0 (API 26) or newer, with a reasonably current Android System WebView (Chromium 108+).

## Install

Grab the APK from the [latest release](https://github.com/mateof/awesome-bookmarks-android/releases/latest).

## First run

1. Enter your server address, for example `http://192.168.1.50:3001`. The scheme is optional; plain HTTP is fine on a local network.
2. Optionally add a fallback address, for example your Tailscale hostname. The app probes the primary first and falls back automatically, then remembers whichever answered.
3. Sign in with the same email (or nickname) and password you use on the web. If your account has two-factor authentication, the app asks for the code.

## Saving a link

From any browser: **Share → AwesomeBookmarks**. The sheet opens with:

- the URL, pulled out of whatever the browser shared,
- the title, prefilled and editable, or left empty for the server to read from the page,
- a folder picker showing your whole tree, preselected to the last folder you used,
- a tag field that autocompletes against your existing tags and creates the ones that do not exist yet.

There is also **Save without asking** in Settings, which skips the sheet entirely and files the link straight into your default folder with your default tags.

## How the session works

The server keeps your data encrypted and derives the decryption key from your password at login, holding it in memory only and dropping it after about 30 idle minutes. After that even a valid session cookie starts getting `423 Locked`.

An API token would sidestep that, but a token cannot give the WebView a session and the WebView is most of the app. So the password is stored encrypted under an Android Keystore key and replayed automatically whenever the server answers 401 or 423. One login, and the share target still works after days of not opening the app.

That means the phone holds a credential to your whole library, which is why the **biometric app lock is on by default**. See [SECURITY.md](SECURITY.md).

One consequence worth knowing: **sign out from the app's Settings, not from the web interface inside it.** Signing out in the web UI only clears the cookie, and the app will helpfully sign you straight back in.

## Building

```bash
git clone https://github.com/mateof/awesome-bookmarks-android.git
cd awesome-bookmarks-android
./gradlew assembleDebug
./gradlew installDebug
./gradlew test
```

JDK 17 and the Android SDK (compileSdk 35). Put `sdk.dir` in `local.properties` if the build cannot find the SDK.

## Documentation

- [docs/SETUP.md](docs/SETUP.md): reaching your server from the phone, HTTPS, Tailscale, self signed certificates.
- [docs/FEATURES.md](docs/FEATURES.md): every feature and setting, and what it costs.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): module map, the session model, which endpoints are used.
- [docs/RELEASING.md](docs/RELEASING.md): version bumps, signing, the release workflow.
- [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), [CHANGELOG.md](CHANGELOG.md).

## Known limitations

- **No offline access.** Your bookmarks live on your server. Out of reach of the server, out of reach of your bookmarks. Saving while offline is not queued yet.
- The library UI is the server's web app, so anything that looks or behaves oddly there will look the same here.

## License

[MIT](LICENSE).
