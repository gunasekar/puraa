# Releasing Puraa

How to build a signed, installable release APK.

## One command

```sh
make release        # (alias: make dist)
```

This runs `assembleRelease` and copies the result to
`dist/puraa-<versionName>.apk` (e.g. `dist/puraa-0.1.0-v1.apk`). `dist/` is
gitignored.

Install it on any phone by sideloading — no Developer Options or USB debugging
needed: transfer the `.apk` (Drive/email/USB), tap it, allow the opening app to
"install unknown apps", tap **Install**.

## Signing

Release signing is read from a **gitignored** `keystore.properties` at the repo
root (see `app/build.gradle.kts`). If it's absent, the release build is produced
**unsigned** and cannot be installed — `make release` guards against this.

Keep the keystore and `keystore.properties` **outside the repo** (e.g. in a
private, encrypted location) and symlink `keystore.properties` into the repo
root. Both are gitignored so they're never committed.

`keystore.properties` fields:

```properties
storeFile=/abs/path/to/puraa-release.keystore
storePassword=…
keyAlias=puraa
keyPassword=…
```

Verify a built APK's signature:

```sh
$ANDROID_HOME/build-tools/<ver>/apksigner verify --print-certs dist/puraa-<version>.apk
```

### ⚠️ Back up the keystore

The keystore is Puraa's **permanent signing identity**. Lose it and you can
never ship an update that installs over an existing `com.puraa` — users would
have to uninstall and reinstall. Keep a copy somewhere safe and independent of
this machine.

## Versioning

Bump before each release in `app/build.gradle.kts`:

- `versionCode` — integer, **must increase** for every build you distribute.
- `versionName` — human string; drives the `dist/` filename.
