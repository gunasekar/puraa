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
