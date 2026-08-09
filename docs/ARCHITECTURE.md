# Architecture

## The shape of the thing

A thin native shell around the AwesomeBookmarks web app, plus a small HTTP client for the things a browser tab cannot do.

```
┌──────────────────────── Android app ─────────────────────────┐
│  MainActivity ── WebView ──────────────┐                     │
│      │                                 │  cookies (shared)   │
│      │ SessionManager                  │                     │
│      │      ├── SecretStore  (Keystore AES/GCM)              │
│      │      ├── SettingsRepository (DataStore)               │
│      │      └── BookmarksApi ── OkHttp ┘                     │
│                                                              │
│  SaveBookmarkActivity (share target) / widget / shortcut     │
│      └── SaveRepository ── BookmarksApi                      │
└──────────────────────────────────────────────────────────────┘
                              │ HTTP
                              ▼
                 your AwesomeBookmarks server (:3001)
```

Single Gradle module. Kotlin, Compose, Hilt, OkHttp, DataStore. Three activities rather than one with navigation, so leaving the library for Settings or the save sheet never destroys the WebView.

## Package map

| Package | What lives there |
|---|---|
| `data` | `AppSettings`, `SettingsRepository` (DataStore), `SecretStore` (Keystore) |
| `network` | `BookmarksApi`, `SessionManager`, `WebViewCookieJar` |
| `save` | `SaveRepository`, the share target and its view model |
| `ui.web` | WebView configuration, clients, `Downloader`, floating button |
| `ui.setup` / `ui.settings` / `ui.lock` / `ui.theme` | Compose screens |
| `update` | GitHub release check, download, install, daily worker |
| `widget` | Home screen widget |

## The session model

This is the part worth reading, because it drove most of the design.

### What the server does

- `POST /api/auth/login` with `{identifier, password, totp?}` sets an httpOnly session cookie lasting 30 days. It answers `{"twoFactorRequired": true}` (a 200, not an error) when TOTP is on and no code was sent.
- Data is encrypted at rest. The key is derived from the password at login and kept **in memory only**, evicted after an idle timeout of about 30 minutes.
- A request with a valid cookie but no key in memory gets **`423 Locked`**, not 401.
- `/api/v1` accepts either the session cookie or a Bearer API token. Tokens carry a wrapped copy of the key, so they survive eviction.

### What the app does, and why not a token

A token would be the obvious choice for the share target. It cannot be the choice for the app, because a token cannot give the WebView a session, and the WebView is the library.

So: one credential. `WebViewCookieJar` bridges OkHttp's `CookieJar` to `android.webkit.CookieManager`, so signing in over HTTP instantly authenticates the WebView too. The password is stored encrypted, and `SessionManager.withSession` retries any call once after signing in again when the answer is 401 **or** 423.

The WebView side needs its own trigger, because the web app is a client-side router: an expired session does not reload the page, it pushes `/login` onto the history. That is why `BookmarksWebViewClient` watches `doUpdateVisitedHistory`, which fires for `pushState`, and not only the page load callbacks. It debounces at 2 seconds and gives up after 3 consecutive attempts so a wrong password surfaces as an error rather than a loop.

The cost of this design is that signing out inside the web interface does not stick. Settings → Sign out is the real one; it calls `logout`, clears cookies and wipes the stored password.

## Endpoints used

| Endpoint | Used for |
|---|---|
| `GET /api/auth/config` | Unauthenticated liveness probe, 3 s timeout, for address fallback |
| `POST /api/auth/login` | Interactive and silent sign in |
| `POST /api/auth/logout` | Sign out |
| `GET /api/v1/me` | "Is the session still usable" check |
| `GET /api/v1/folders` | Folder picker, flattened into a tree client side |
| `GET /api/v1/tags` | Tag autocomplete |
| `POST /api/ext/quick-add` | Saving a link, with tag names rather than ids |
| `GET /api/v1/version` | Server version for Settings. 404 on servers older than 0.20.2 |

Plus `api.github.com` for update checks, on a separate HTTP client with no cookie jar.

## Threading and lifecycle

- All HTTP work is `suspend` on `Dispatchers.IO`; `SessionManager` serialises with a `Mutex` so concurrent entry points cannot stampede sign ins.
- `MainViewModel` exposes `StateFlow<AppSettings?>` with `null` meaning "DataStore has not answered", so the lock screen never flashes for users who disabled it.
- `UpdateCheckWorker` reaches its dependencies through a Hilt `@EntryPoint` rather than `@HiltWorker`, avoiding `hilt-work`, a `Configuration.Provider` and a manifest override for one worker. R8 must not rename it, since WorkManager stores the class name in its database.

## Testing

Unit tests cover the pure logic: URL normalisation, candidate ordering, the always-tags parser and version comparison. The WebView and the share target are verified by running against a real server.
