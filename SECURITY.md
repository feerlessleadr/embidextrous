# Security

## Reporting a problem
Please report security problems **privately**: on the repository page open the **Security** tab and choose
**Report a vulnerability** (GitHub's private reporting). If that button is not there, open an ordinary issue that says
only "I would like to report a security problem" and no details, and a private channel will be arranged.

Please do not post API keys, tokens, passwords, server addresses or logs from your own server in public issues.

## What the app handles
- A sign-in token for your Emby server (never the password), stored encrypted with a key held in the Android Keystore, and sent
  only in the `X-Emby-Token` header, never in a URL. Signing out ends the session on the server and deletes the key.
- The Hall Monitor plugin key, stored the same way.
- Release builds only talk to `https://` addresses and follow no redirects.
- An administrator token on the phone can control the whole Emby server. Keep the phone locked, and use a regular (non-administrator)
  account where you only need Hall Monitor.

## Supported versions
Only the latest release receives fixes.
