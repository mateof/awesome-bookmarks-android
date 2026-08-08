# Security

## Reporting a vulnerability

Open a [private security advisory](https://github.com/mateof/awesome-bookmarks-android/security/advisories/new) rather than a public issue.

## Threat model

This app stores your account password on the device, indefinitely. That is a deliberate trade and worth being explicit about.

### Why the password and not an API token

The server can issue API tokens that carry a wrapped copy of your data encryption key, which is exactly what a headless client wants. But a token cannot authenticate the WebView, and the WebView is the library interface. Using both would mean two credentials and two revocation stories for no gain, so the app keeps one: the password.

If you would rather not have a password on the phone, the honest answer is to use the web app in a browser instead. You lose the share sheet, which is the reason this exists.

### What is protected

- The password is encrypted with AES-256/GCM under a key generated in the Android Keystore, hardware backed where available. The key never leaves the device and no other app can use it.
- The encrypted credentials are excluded from cloud backup and device-to-device transfer, so they do not travel with a phone restore.
- The optional app lock (on by default) puts biometrics or the device credential in front of the library.
- User installed certificate authorities are trusted, so a self hosted HTTPS setup verifies properly. There is no "ignore certificate errors" option, on purpose.

### What is not protected

- **A rooted or compromised device.** Keystore raises the cost of extraction; it does not make it impossible on a device an attacker fully controls.
- **Someone with your unlocked phone and the app lock disabled.** That is what the lock is for.
- **Plain HTTP.** Over `http://` your password crosses the network in the clear on every sign in, as does everything you read and save. Fine on a LAN you trust, not fine over the open internet. Use HTTPS or a VPN.

### Design notes

The Keystore key is created with `setUserAuthenticationRequired(false)`. Binding it to biometrics would stop the share target working while the phone is locked, which is most of the point. The trade is stated here rather than hidden.

Signing out from **Settings → Sign out** is the only action that deletes the stored password. Signing out inside the embedded web interface only clears the cookie, and the app will sign back in.

### In app updates

The app holds `REQUEST_INSTALL_PACKAGES` so it can install a newer release it downloaded from this project's GitHub releases. It only downloads from `api.github.com` and the `browser_download_url` GitHub returns, over HTTPS, and the install always goes through the system installer, which verifies the signature against the installed app. Turning the setting off stops every check.
