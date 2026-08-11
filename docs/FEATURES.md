# Features

Everything here exists because a browser tab cannot do it. The library interface itself is your server's web app, untouched.

## Saving a link from anywhere

Registering as a `text/plain` share target is what puts the app in every browser's share sheet. Chrome shares a page as `Title https://url`, others send only the URL, so the sheet pulls the first URL out of whatever arrived and treats the rest as the title.

The sheet has:

- **URL**, editable, validated before sending.
- **Title**, prefilled from the share. Left empty, the server reads it from the page itself.
- **Folder**, your tree, **collapsed**, with only the top level showing until you open a branch. The branch holding your preselected folder opens by itself, so a remembered choice is never hidden. A search box above the list filters by name or by path and lists the matches flat, each with its path, because once two folders are called "Rust" the path is the only thing that tells them apart. Every row has an icon to create a folder inside it, and the root row creates one at the top level; the new folder is selected immediately. While the list is loading the sheet says so, and if it cannot be read it says that too with a retry, rather than claiming you have no folders.
- **Tags**, autocompleting against your existing tags. Enter or a comma commits one. Tags that do not exist are created by the server.

It posts to `/api/ext/quick-add`, the same endpoint the browser extension uses. That endpoint takes tag **names** and creates the missing ones, unlike `POST /api/v1/bookmarks` which takes ids and would mean a create-then-link round trip for every new tag typed.

### Settings that shape it

| Setting | Default | Effect |
|---|---|---|
| Remember the last folder | On | Preselects wherever you saved last, because links arrive in runs |
| Tags added to everything | empty | Comma separated, appended to every save on top of what you type |
| Save without asking | Off | Skips the sheet entirely: share a page and it lands in the default folder |

## Session that does not expire

The server derives your data encryption key from the password at login and holds it in memory only, dropping it after roughly 30 idle minutes. After that a valid session cookie still gets `423 Locked`.

The app stores the password encrypted under an AES-256/GCM key in the Android Keystore and replays the sign in whenever the server answers 401 or 423. You sign in once.

Two consequences:

- **Sign out from the app's Settings, not from the web interface.** The web sign out only clears the cookie; the app will sign back in within seconds. Settings → Sign out deletes the stored password, which is what actually ends it.
- The Keystore key is created with `setUserAuthenticationRequired(false)` on purpose, so the share target works while the app is locked. The gate is the app lock, not the key.

## Two-factor accounts and passkeys need an API token

The section above works by replaying your password. That is impossible for an
account with a second factor: the server demands a fresh TOTP code on every
login unless the request comes from a network listed in `TRUSTED_NETWORKS`. A
passkey is worse, since its ceremony cannot happen in the background at all.

Without help, such an account gets signed out roughly every half hour of
inactivity and **the share target stops working**.

The fix is an API token, created in the web app under **Settings → API** and
pasted into **Settings → API token** here. A token carries its own wrapped copy
of your encryption key, so it needs no password, no code and no ceremony, and
it never goes stale. When one is stored, every native call uses it: folders,
tags and saving a link. The WebView still uses the session cookie.

This is worth stating plainly because the app was designed the other way round
at first. One credential is the right default; it is the wrong answer the
moment a second factor exists.

The token is verified against the server before being stored, so a bad paste
fails immediately rather than at the next save. Signing out deletes it.

## Signing in with a passkey

Supported where the server supports it. A WebView refuses WebAuthn unless the
app opts in, which is why passwordless sign in silently does nothing in most
wrapper apps; this one calls `WebSettingsCompat.setWebAuthenticationSupport`
with `WEB_AUTHENTICATION_SUPPORT_FOR_APP`, scoping credentials to this app.

The server side has requirements the app cannot satisfy for you: `WEBAUTHN_RP_ID`
and `WEBAUTHN_ORIGIN` configured, HTTPS, and a **real hostname**, because
WebAuthn forbids IP addresses as relying party ids. Over `http://192.168.x.x`
there is nothing to enable.

A passkey signs the WebView in. It cannot renew the session in the background,
so pair it with an API token exactly as with two-factor authentication.

## Biometric app lock

**On by default**, because a stored password to your whole library deserves one. Biometrics or the device PIN, whichever the device offers, re-armed after a wait you choose in **Settings → Security**: every time you leave, 1, 5, 15, 30 or 60 minutes, or only when the app starts. The default is 30 minutes, because a shorter one turns every trip to the browser into another fingerprint.

## Dual address with automatic fallback

Two addresses, probed in order with a 3 second timeout against the public `/api/auth/config`. The winner is remembered so the next cold start goes straight there. LAN at home, VPN or reverse proxy outside.

## Launcher shortcut and widget

Long press the icon for **Save a link**. The 2x1 widget does the same on the left and opens the library on the right.

## Download rescue

The web app builds its Netscape HTML export in the browser and hands it over as a `blob:` URL. Android's `DownloadManager` cannot resolve those, which is why exporting from a mobile browser usually appears to do nothing. The app reads the blob back through a small injected script and writes it into Downloads through MediaStore. Ordinary downloads go through `DownloadManager` with the session cookie attached.

The script is namespaced under `AwesomeBookmarksBridge` and touches no application global.

## In app updates

The app is distributed outside any store, so nothing would tell you a new version exists. A daily background check asks this project's GitHub releases, notifies you, and downloads plus installs in place.

The GitHub client is a **separate** `OkHttpClient`. The one used for your server carries a cookie jar shared with the WebView, and cookie jars are not scoped by host, so reusing it would send your session cookie to github.com on every check.

## Other switches

| Setting | Default | What it does |
|---|---|---|
| Floating app button | On | Settings, reload, save and search from inside the web interface. Fades to 25% after 3 seconds, and **drag it anywhere** if it covers something you need. It remembers where you left it |
| Keep the screen on | Off | Holds `FLAG_KEEP_SCREEN_ON` while the app is open |
| Text size | 100% | WebView text zoom, 70% to 150% |
| Open external links in the browser | On | Links to other sites open in a Custom Tab instead of hijacking the app |
| Allow mixed content | Off | Last resort for an HTTPS server loading assets over plain http |

## Seeing what the server runs

**Settings → About** shows the server version, read from `GET /api/v1/version`.
That endpoint landed in server 0.20.2; against anything older the app says the
version is unknown rather than inventing one.

## Deliberately not implemented

- **Offline queueing of saves.** A share while offline fails rather than sitting in a queue. Worth doing, not done.
- **A native library UI.** The web app already is one, and reimplementing folders, snapshots, search and five view modes natively would be a permanent chase.
- **Ignoring TLS errors.** Install your CA instead.
