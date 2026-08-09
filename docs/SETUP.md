# Reaching your server

This app does not ship a server. It connects to an [AwesomeBookmarks](https://github.com/mateof/awesome-bookmarks-manager) instance you run. That project documents deployment; this page covers only what the phone needs.

## The short version

One container, one port:

```yaml
# docker-compose.yml
services:
  bookmarks:
    image: ghcr.io/mateof/awesome-bookmarks-manager:latest
    restart: unless-stopped
    ports:
      - "3001:3001"
    volumes:
      - ./data:/app/data
    environment:
      - MASTER_KEY=${MASTER_KEY}
      - SESSION_SECRET=${SESSION_SECRET}
```

Check it answers:

```bash
curl -s http://localhost:3001/api/auth/config
# {"registrationEnabled":true}
```

That endpoint is public and is exactly what the app probes to decide whether an address is alive and is the right kind of server.

## On your own network

Use the host's LAN address:

```
http://192.168.1.50:3001
```

Plain HTTP over a LAN you trust is what most people do, and the app allows cleartext for that reason. Your password crosses that network unencrypted on every sign in. If you do not trust the network, use HTTPS.

## From outside: Tailscale

Install Tailscale on the server and the phone, then use the tailnet name. Tailscale issues a real certificate, so nothing extra is needed on the phone.

Put the LAN address in **Server address** and the tailnet address in **Fallback address**: the app probes the first, falls back to the second, and remembers which one worked.

## From outside: reverse proxy

Caddy, two lines, automatic certificates:

```
bookmarks.example.com {
    reverse_proxy 127.0.0.1:3001
}
```

Set `COOKIE_SECURE=true` on the server when you serve over HTTPS, otherwise the session cookie is not marked secure.

## Self signed certificates

The app trusts **user installed certificate authorities** (`res/xml/network_security_config.xml`). Export your CA certificate, install it on the phone under **Settings → Security → Encryption & credentials → Install a certificate → CA certificate**, and use the `https://` address.

There is deliberately no "ignore certificate errors" switch. A prompt that teaches you to accept any certificate is worse than no HTTPS.

## Two-factor authentication and passkeys

If the account has TOTP enabled, the first sign in asks for the six digit code.

Later automatic sign ins **cannot** supply one, and the server demands a code on
every login unless the request comes from a network in `TRUSTED_NETWORKS` (with
`SKIP_2FA_ON_TRUSTED`, or for an admin). So either:

- add the phone's network to `TRUSTED_NETWORKS` on the server, or
- create an API token in the web app and paste it into **Settings → API token**.

The token is the portable answer, since it works from any network.

Passkeys need `WEBAUTHN_RP_ID` and `WEBAUTHN_ORIGIN` set, HTTPS, and a real
hostname; WebAuthn rejects IP addresses. They sign the WebView in but cannot
renew it in the background, so they need a token too.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| "No AwesomeBookmarks server answered at that address" | Wrong host or port, container not published, firewall | `curl http://ADDRESS/api/auth/config` from another machine |
| "The server rejected those credentials" | Typo, or the password changed | Sign in again from the app |
| "This account has two-factor authentication" | TOTP is on | Enter the code from your authenticator |
| Saving fails with a lock error | The server dropped its decryption key and the stored password no longer matches | Sign out and back in |
| The interface renders oddly | Old Android System WebView | Update it; Settings shows the installed version |
