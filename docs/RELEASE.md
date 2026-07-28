# Releasing Puraa

How to build a signed, installable release APK.

## One command

```sh
make release        # (alias: make dist)
```

This runs `assembleRelease` and copies the result to
`dist/puraa-<version>.apk` (e.g. `dist/puraa-0.2.0.apk`, the version taken from
the git tag — see [Versioning](#versioning)). `dist/` is gitignored.

Install it on any phone by sideloading — no Developer Options or USB debugging
needed: transfer the `.apk` (Drive/email/USB), tap it, and clear the three
guards in the [README](../README.md#installing-the-apk), one of which is
Play Protect (below).

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

## Play Protect blocks the first install

Play Protect's enhanced fraud protection refuses sideloaded APKs declaring any
of `RECEIVE_SMS`, `READ_SMS`, `NOTIFICATION_LISTENER`, or `ACCESSIBILITY`.
Puraa declares the first two — they *are* the app — so a fresh install is
blocked until the user pauses Play Protect. It usually surfaces as a bare
**"App not installed"** with no reason given.

**This is not a build defect.** The check reads the manifest at install time;
signing, `versionCode`, and the CI pipeline have nothing to do with it. When a
report like this comes in, don't audit the APK — point at the README steps.

Only the first install is gated; later APKs go over an existing Puraa
untouched. An app store would avoid it entirely (stores are exempt, only
"internet-sideloading" is blocked), but Play Store policy limits SMS
permissions to default SMS handlers, so that route is closed to Puraa.

## Versioning

**The git tag is the single source of truth.** There are no version numbers to
edit in `app/build.gradle.kts` — the build derives them from git:

- `versionName` — `git describe --tags` with the leading `v` stripped. On an
  exact tag it's clean (`v0.2.0` → `0.2.0`); an untagged commit gets a
  descriptive suffix (`0.2.0-3-gabc123`).
- `versionCode` — the commit count (`git rev-list --count HEAD`), so it always
  increases.

The Makefile's `dist/` filename and the CI release artifact are named from the
same `git describe`, so tag, app version, and APK name can never drift.

### Cutting a release

```sh
git tag v0.3.0      # SemVer, prefixed with v
git push --tags
```

Pushing the tag triggers `.github/workflows/release.yml`, which builds the
signed APK and publishes it to a GitHub Release named for the tag. To build one
locally at the tagged commit, run `make release`.
