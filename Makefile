# Puraa — build & install helpers.
#
# Wraps Gradle (build/test) and adb (install/launch/logs) so the common
# device workflow is one command. Run `make` or `make help` for the list.
#
# Device selection: targets auto-pick the first attached device. If more
# than one is connected, pass SERIAL explicitly, e.g.
#   make install SERIAL=42241JEKB26693

GRADLE      := ./gradlew

# Debug build by default; `make install BUILD=release` for the release APK.
BUILD       ?= debug
DEBUG_APK   := app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK := app/build/outputs/apk/release/app-release.apk
APK          = $(if $(filter release,$(BUILD)),$(RELEASE_APK),$(DEBUG_APK))

# App version — derived from the git tag, the single source of truth (matches
# what Gradle builds). v0.2.0 → 0.2.0; off-tag builds get a descriptive suffix.
VERSION     := $(shell git describe --tags --always 2>/dev/null | sed 's/^v//')
DIST        := dist

# Debug builds get the .debug applicationId suffix (see app/build.gradle.kts).
PKG          = $(if $(filter release,$(BUILD)),com.puraa,com.puraa.debug)
ACTIVITY     = $(PKG)/com.puraa.MainActivity

# Locate adb: prefer PATH, else the Android SDK (ANDROID_HOME / ANDROID_SDK_ROOT,
# defaulting to the standard macOS location). Override with ADB_BIN=/path/to/adb.
ANDROID_SDK ?= $(firstword $(wildcard $(ANDROID_HOME) $(ANDROID_SDK_ROOT) $(HOME)/Library/Android/sdk))
ADB_BIN     ?= $(shell command -v adb 2>/dev/null || echo $(ANDROID_SDK)/platform-tools/adb)

# First attached device with state "device"; override with SERIAL=...
SERIAL      ?= $(shell $(ADB_BIN) devices | awk 'NR>1 && $$2=="device"{print $$1; exit}')
ADB          = $(ADB_BIN) $(if $(SERIAL),-s $(SERIAL),)

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

.PHONY: devices
devices: ## List attached devices
	@$(ADB_BIN) devices -l

.PHONY: build
build: ## Build the APK (BUILD=debug|release)
	$(GRADLE) $(if $(filter release,$(BUILD)),assembleRelease,assembleDebug)

.PHONY: test
test: ## Run unit tests
	$(GRADLE) testDebugUnitTest

.PHONY: install
install: build ## Build and install onto the device
	$(ADB) install -r $(APK)

.PHONY: release dist
release: ## Build signed release APK into dist/puraa-<version>.apk
	@test -f keystore.properties || { echo "keystore.properties missing — release would be UNSIGNED and uninstallable. See docs/RELEASE.md."; exit 1; }
	$(GRADLE) assembleRelease
	@mkdir -p $(DIST)
	@cp $(RELEASE_APK) $(DIST)/puraa-$(VERSION).apk
	@echo "→ $(DIST)/puraa-$(VERSION).apk ($$(du -h $(DIST)/puraa-$(VERSION).apk | cut -f1))"

dist: release ## Alias for `make release`

.PHONY: launch
launch: ## Launch the app (no build/install)
	$(ADB) shell am start -n $(ACTIVITY)

.PHONY: run
run: install launch ## Build, install, and launch

# Note: there is no adb "auto-configure" target by design. The relay is
# configured only by manual entry or by scanning a setup QR in person —
# Puraa registers no deep link, so nothing can provision it remotely.

.PHONY: logcat
logcat: ## Tail Puraa logs (relay pipeline tags)
	$(ADB) logcat -v time SmsReceiver:D RelayWorker:D TelegramClient:D OutboxRepository:D '*:S'

.PHONY: uninstall
uninstall: ## Remove the app from the device
	$(ADB) uninstall $(PKG)

.PHONY: reinstall
reinstall: uninstall install ## Uninstall then install (clears saved config)

.PHONY: clean
clean: ## Delete Gradle build outputs
	$(GRADLE) clean
