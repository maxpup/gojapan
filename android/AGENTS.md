# Android release signing

Read `SIGNING.md` before packaging or publishing an APK.

The user requested preserving the signing key used by `v1.0-fixes-20261111` for all future updates. Its certificate SHA-256 is:

`a47f4395e10d8aae0e8442d54c5150a5c60ebb538c5537d13a9d04be3e111add`

- Use `tools/build-signed.py`; do not publish a runner-generated debug APK or an unsigned APK.
- Never generate or substitute another key if credentials are missing. Report the missing key instead.
- In the current managed workspace, protected signing configuration is `/workspace/.secrets/gojapan-signing/credentials.json`. Do not print it or commit/upload private key files.
- Only the public certificate fingerprint belongs in Git. Keep versionCode increasing for updates.
- GitHub Actions Secrets backup was blocked by integration permissions (HTTP 403) on 2026-11-11. Do not claim permanent/off-workspace backup is complete until it has actually succeeded.
