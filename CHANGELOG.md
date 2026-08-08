# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[semantic versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.1]

### Fixed
- Coming back from a link opened in the browser no longer loses your place and no
  longer asks for biometrics again. Three separate faults added up to that:
  - The lock grace period was measured from when you unlocked, not from when you
    left the app, so a minute of ordinary use was enough to re-arm it on every
    return.
  - Locking replaced the content instead of covering it, which released the
    WebView and destroyed the page you were on. The lock is now an opaque
    overlay drawn on top, and it swallows touches since the content is live
    underneath.
  - The WebView kept no state at all, so any activity recreation started over.
    Its navigation history is now saved and restored, guarded by an origin check
    so a history from a previous server address cannot resurface.

## [0.1.0]

First release.

### Added
- WebView client for a self-hosted AwesomeBookmarks server.
- Share target: save the page you are reading from any browser, with a folder
  picker over your whole tree and a tag field that autocompletes against your
  server and creates unknown tags, the way Wallabag does.
- Remembered default folder, always-on tags, and an optional "save without
  asking" mode that skips the sheet entirely.
- Session that does not expire: the password is stored encrypted in the Android
  Keystore and replayed whenever the server answers 401 or 423, which is what
  keeps the share target working after days of not opening the app.
- Two-factor (TOTP) sign in.
- Biometric app lock, on by default, with a 60 second grace period.
- Primary and fallback server addresses with automatic probing.
- Launcher shortcut and home screen widget for saving a link.
- `blob:` download rescue, so the HTML export reaches the Downloads folder.
- In app updates from GitHub releases, with a daily background check.
- English and Spanish translations.

[Unreleased]: https://github.com/mateof/awesome-bookmarks-android/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/mateof/awesome-bookmarks-android/releases/tag/v0.1.1
[0.1.0]: https://github.com/mateof/awesome-bookmarks-android/releases/tag/v0.1.0
