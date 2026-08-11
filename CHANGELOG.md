# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[semantic versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.1]

### Fixed
- The Unlock button on the lock screen did nothing, leaving anyone whose
  fingerprint prompt was dismissed, or who has no fingerprint set up, unable to
  get into the app at all. The lock overlay swallows touches so they cannot
  reach the page behind it, but it was consuming them in the Initial pointer
  pass, which runs parent to child and therefore ate its own button's taps
  before the button saw them. It now consumes in the Main pass, which runs
  child to parent, so the overlay's controls work and everything else is still
  blocked.
- A device that cannot authenticate at all, with no biometrics enrolled and no
  device credential, now opens instead of showing a lock it could never satisfy.

## [0.4.0]

### Changed
- The app lock now waits **30 minutes** by default before asking again, instead
  of 60 seconds, and the wait is a setting: every time you leave, 1, 5, 15, 30
  or 60 minutes, or only when the app starts. A minute was short enough that
  reading one linked page and coming back meant another fingerprint, which is
  friction without much security to show for it.

### Changed
- Relicensed from MIT to **GPL-3.0-or-later**. The full licence text is in
  LICENSE and every source file carries an SPDX notice. All dependencies are
  Apache-2.0, which is compatible with GPL-3.0.

## [0.3.0]

### Changed
- The folder picker starts **collapsed** and you open branches as you need them.
  Showing an entire tree expanded is unusable past a handful of folders. The
  branch containing the preselected folder is opened automatically so a
  remembered choice is never hidden under a closed parent.

### Added
- A search box in the folder picker. It filters by folder name or by path and
  lists matches flat, each with its path, since the path is what tells two
  folders with the same name apart.

## [0.2.1]

### Fixed
- The folder picker claimed you had no folders when it was simply still loading
  them, or when the request had failed. Three different situations shared one
  message, and the one it chose was the only one that was usually false. It now
  shows a spinner while loading and the reason plus a retry when it fails, and
  only says the list is empty when the server really returned nothing.

### Added
- Create folders from the save sheet, either at the root or inside any existing
  folder. The new folder is selected right away, since wanting it is why you
  opened the picker.

## [0.2.0]

### Added
- **Optional API token** (Settings → API token). Accounts with two-factor
  authentication or a passkey cannot have their sign in replayed in the
  background, so the session died about every half hour of inactivity and the
  share target stopped working. A token carries its own copy of the account's
  encryption key: when one is stored every native call uses it and saving keeps
  working from any network. Verified against the server before being stored.
- **Passkey sign in**, where the server allows it. A WebView refuses WebAuthn
  unless the app opts in, so this was silently impossible before. Needs the
  server configured with `WEBAUTHN_RP_ID` and `WEBAUTHN_ORIGIN`, over HTTPS,
  with a real hostname.
- **Server version in Settings → About**, read from `GET /api/v1/version`.
  Servers older than 0.20.2 do not have it and are reported as unknown.

### Fixed
- A failed background sign in caused by a second factor no longer looks like a
  wrong password. It says what it is and points at the API token.

### Changed
- androidx.webkit 1.12.1 to 1.16.0. The WebAuthn constants exist in 1.12.1 but
  are not yet accepted by `isFeatureSupported`, so calling it there is not valid.

## [0.1.2]

### Changed
- The floating button can be dragged anywhere on screen and stays where you put
  it. Pinned to the bottom end corner it kept landing on top of controls
  underneath and swallowing taps meant for them. The position is stored as a
  fraction of the free space, so it holds its relative place across rotation and
  screen sizes, and it is clamped so the control is always fully visible,
  including when expanding makes it taller near an edge.

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

[Unreleased]: https://github.com/mateof/awesome-bookmarks-android/compare/v0.4.1...HEAD
[0.4.1]: https://github.com/mateof/awesome-bookmarks-android/releases/tag/v0.4.1
[0.4.0]: https://github.com/mateof/awesome-bookmarks-android/releases/tag/v0.4.0
[0.3.0]: https://github.com/mateof/awesome-bookmarks-android/releases/tag/v0.3.0
[0.2.1]: https://github.com/mateof/awesome-bookmarks-android/releases/tag/v0.2.1
[0.2.0]: https://github.com/mateof/awesome-bookmarks-android/releases/tag/v0.2.0
[0.1.2]: https://github.com/mateof/awesome-bookmarks-android/releases/tag/v0.1.2
[0.1.1]: https://github.com/mateof/awesome-bookmarks-android/releases/tag/v0.1.1
[0.1.0]: https://github.com/mateof/awesome-bookmarks-android/releases/tag/v0.1.0
