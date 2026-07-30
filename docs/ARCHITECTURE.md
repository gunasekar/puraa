# Puraa — Architecture

This document explains what Puraa is scoped to do and *how* it works internally. All decisions described
here are revisable; significant ones should be lifted into
[`docs/decisions/`](decisions/) ADRs as they harden.

---

## 1. System overview

Puraa is a single-purpose Android app that runs on the phone whose SMS you
want to forward. It watches incoming SMS, keeps the ones that match a sender
filter, and forwards each as a plaintext message to **one configured
destination** — a private **Telegram channel** (via a bot) *or* a **Discord
channel** (via a webhook). Exactly one destination is active per install,
never both. You read the messages in a **normal Telegram/Discord client** on
any device — Puraa has no receiving app of its own.

There is deliberately **no encryption** and **no listener/inbox app**. The
message travels TLS-encrypted to the destination and is stored readable in the
channel. This is the whole product: *incoming SMS → your Telegram/Discord*,
nothing more.

### High-level data flow

```mermaid
flowchart LR
    SIM["Mobile carrier — SMS"] --> Receiver["SmsReceiver (SMS_RECEIVED broadcast)"]
    Receiver --> Filter["SenderFilter — keep / drop"]
    Filter --> Outbox[("Outbox — Room/SQLite")]
    Outbox --> Worker["RelayWorker (expedited WorkManager)"]
    Worker -->|HTTPS sendMessage| Channel["Private Telegram channel"]
    Channel -->|native Telegram client| Reader["Your Telegram (push + history)"]
```

The design goal alongside correctness is to be **invisible on the
device**: the app is event-driven and holds no long-running process.
Between messages, nothing of Puraa's is running — no foreground
service, no polling loop, no persistent notification. It consumes CPU,
memory, and battery only for the ~1 second it takes to forward a
message, a few times a day.

---

## 2. Scope

### In scope

- Android app (sideloaded APK, no Play Store) installed on the phone whose
  SMS is to be forwarded.
- **One-way forwarding**: that phone → exactly **one** private channel — a
  Telegram channel (via a bot) *or* a Discord channel (via a webhook).
- **Plaintext** messages. TLS protects the hop to the destination; the message
  then sits readable in the private channel. Acceptable for transaction alerts
  (the SMS already travelled over plaintext GSM) — the operator must be
  eyes-open about it, especially for OTPs.
- **Configurable sender filter** per install:
  - *Whitelist mode* (default): forward only SMS from listed sender ids
    (e.g. `HDFCBK`, `SBIINB`, `ICICIB`).
  - *All-SMS mode*: forward every incoming SMS.
- **Only live SMS.** Forwards a message the moment it arrives and **never**
  forwards historical/old SMS — a hard requirement. The one deliberate
  exception is the manual "push last 15 min" backfill (§5).
- **Shared-channel routing.** Any number of relay phones can post into one
  channel; each forwarded message carries the relay's device name for
  attribution.
- **In-app update from GitHub Releases** (§13). With no store to push updates,
  the app checks its own releases **when it is opened** and installs on a tap.
  No background checks, no silent installs.

### Non-goals

- **No listener/inbox app of our own** — messages are read in a normal
  Telegram/Discord client.
- **No encryption** — plaintext only. If sensitive content (e.g. OTPs) is a
  concern, the mitigation is to filter those senders out, not to add crypto.
- **No background footprint** — the app is event-driven and dormant between
  messages (see §10, Reliability).
- **No bidirectional messaging** (destination → SIM).
- **No Play Store distribution, accounts, sign-up, or billing.** The cost is
  that Play Protect blocks the *first* sideloaded install, since Puraa declares
  SMS permissions — see
  [RELEASE.md](RELEASE.md#play-protect-blocks-the-first-install).
- **No iOS** — iOS does not permit third-party SMS access.
- **No MMS, RCS, or non-SMS notifications** (WhatsApp, etc.) as a *source*.
- **No WhatsApp as a destination** either — the only route open to a personal
  account (Baileys, a WhatsApp Web companion device) expects a device that stays
  connected, and needs re-pairing whenever the session drops
  ([ADR-0002](decisions/0002-no-whatsapp-destination.md)).

---

## 3. Success criteria

Objective, testable outcomes for a healthy install:

1. **Latency** — every matching SMS that arrives on the source phone appears in
   the destination channel within ~30 seconds.
2. **Zero-touch relaying** — after the one-time setup, forwarding needs no
   re-opening, restart, or permission re-grant. Scoped to *relaying* on purpose:
   picking up a new version does require opening the app, since there is no
   background updater (§13, [ADR-0001](decisions/0001-in-app-only-self-update.md)).
3. **Zero footprint** — no noticeable impact on the source phone's performance
   or battery: no always-on service, persistent notification, or polling.
4. **Filter fidelity** — no non-matching SMS is forwarded, unless the install
   is deliberately configured in all-SMS mode.
5. **No backfill** — no historical/old SMS is ever forwarded; only messages
   that arrive after setup.

---

## 4. Destinations

A relay forwards to exactly one destination, chosen at setup (or from a
scanned setup code). Both are one-way "post a message to a channel"
transports, resolved at send time to a single `MessageSink`
(`send/MessageSink.kt`) so the outbox and worker stay destination-agnostic.

| Destination | Transport | Config | Delivered via |
| ----------- | --------- | ------ | ------------- |
| **Telegram** | Bot API `sendMessage` | bot token + channel id | `TelegramClient` |
| **Discord**  | Channel **webhook** `POST {"content": …}` | webhook URL | `DiscordClient` |

Discord needs no bot or gateway — a channel webhook is the direct analog
of a Telegram channel post. The native Discord app push-notifies just like
Telegram. The webhook URL is a secret (anyone with it can post) and is stored
encrypted like the bot token.

### Telegram topology

One **Telegram bot** (`bot_send`) and one **private channel**. The bot is an
admin of the channel with **Post Messages** enabled. Any number of relay
phones can post into the same channel using the same bot token. You subscribe
to that channel in a normal Telegram app and get native push notifications and
full history for free.

| Element        | Role                                                                 |
| -------------- | ------------------------------------------------------------------- |
| `bot_send`     | Admin of the channel with **Post Messages** on. Every relay uses this token. |
| Channel        | Private, invite-only. Holds the message history. Members: `bot_send` (admin), you (subscriber). |
| Relay app      | `POST /bot<TOKEN>/sendMessage` with `chat_id=<channel>` and `text=<rendered SMS>`. |

Per-relayer attribution lives inside the message body (`Device: <relay
name>` line), so a single channel serves any number of relay phones — no need
to slice it per phone.

### Why only one bot now

An earlier design had a second bot and an in-app listener that polled
`getUpdates`. That required two bots (a bot doesn't receive `getUpdates`
for its own posts). With the listener removed and messages read in a normal
Telegram client, the receiving bot and its second `@BotFather` step are gone.
One bot, one token, one channel.

### Why a channel (not a group or DM)

- **DMs**: a bot can't DM a user first; the user must start the chat.
- **Groups**: messier history/privacy semantics.
- **Channels**: broadcast-shaped (one-to-many), and the reader can
  subscribe in a normal Telegram client for free push and history.

---

## 5. Reading SMS

Puraa reads incoming SMS with a **`SMS_RECEIVED` broadcast receiver**
(`SmsReceiver`), holding the `RECEIVE_SMS` permission. The OS delivers the
message — sender, body, timestamp — the instant it arrives.

### Why this, and the "latest only" guarantee

The relay must **only ever forward messages it hears live, never
historical SMS**. The broadcast receiver satisfies this *by construction*:
`SMS_RECEIVED` is an event that fires only for a message arriving now.
There is no query over the SMS database, no "last seen" cursor to keep
correct, and therefore no way to accidentally backfill the phone's SMS
history into the destination. "Send only what we hear" is the only thing this
mechanism *can* do.

Two alternatives were considered and rejected:

| Approach | Why not |
| --- | --- |
| **`ContentObserver` on the SMS provider** (`READ_SMS`) | Works, but "no backfill" depends on seeding a last-seen row id correctly at first run — a bug there dumps the entire SMS history. More moving parts for a property the broadcast gives for free. |
| **`NotificationListenerService`** (read the SMS notification) | Android 14+ redacts sensitive notification content (OTPs) unless you hold a signature-only permission, so bodies can't be read reliably. |

`SMS_RECEIVED` is exempt from the Android 8+ implicit-broadcast
restrictions, so the manifest-registered receiver fires even when the app
isn't running and after a reboot — no boot receiver or always-on process
is needed to keep receiving.

Multipart (long) SMS arrive as several PDUs sharing a sender and
timestamp; `SmsReceiver` concatenates their bodies back into one message.

### Manual "push last 15 min" (the one exception)

The automatic path never reads history. But the status screen has an
explicit **"Push last 15 minutes"** button (`RecentSmsPush`) for catching
up after a gap. Because it reaches *back* in time it must **query the SMS
inbox**, which needs `READ_SMS` — requested lazily on first tap. It pushes
every message in the window **ignoring the sender filter** and **without
de-duplicating** against what may already have been forwarded: a
deliberate, user-initiated "send me everything recent" hammer. This is the
only place Puraa reads the provider, and only on an explicit tap.

---

## 6. Sending SMS

The receiver does the minimum synchronous work — parse, filter, write one
row to the outbox — then returns. The network send happens separately.

### Outbox (Room / SQLite)

Every kept SMS is written to a small `outbox` table before any network
call. This keeps the broadcast handler fast (well under its time limit)
and makes delivery durable: a send can be retried across process death,
network loss, and reboots without losing the message.

### RelayWorker (expedited WorkManager)

Sending is a `CoroutineWorker` scheduled by `SmsReceiver` when a message
is enqueued. It drains the outbox — FIFO — to the destination, marks each row
sent, and stops. Nothing runs between sends.

- **Expedited**: the job runs within seconds even under Doze, so an SMS
  reaches the destination promptly. On Android < 12 WorkManager runs it as a
  brief foreground service (hence a momentary notification and the
  `FOREGROUND_SERVICE*` permissions); on 12+ it runs as an expedited job
  with no notification.
- **Network constraint**: the work is *deferred*, not failed, while
  offline, and drains automatically when connectivity returns.
- **Retry/backoff**: a transient send failure is retried *inside* the
  worker with a short backoff (immediate, +1s, +3s) so a momentary blip
  costs ~1s rather than a full reschedule. Only if those are exhausted
  does the worker return `Result.retry()` and let WorkManager reschedule
  (network constraint + 10s exponential backoff). Work is enqueued with
  `KEEP`, so a new SMS is never chained behind an in-flight or
  backing-off drain — the running worker loops over all ready rows.
- **Bounded**: the outbox is trimmed so a long offline stretch can't grow
  storage without limit; the phone's native SMS inbox remains the source
  of truth.

### Why WorkManager instead of a foreground service

An always-on foreground service (the previous design) sits in memory with
a permanent notification 24/7 just to hold a drain loop. That is the
single biggest thing an SMS forwarder can do to *impact* a phone. Event-
driven expedited work gives the same near-real-time delivery with **zero
idle footprint**, which is the explicit performance goal for this app.

---

## 7. Message (wire) format

The message body is human-readable plaintext:

```
Device: moto g84
From:   HDFCBK
At:     06 Jul 2026, 02:15 PM

Rs 1,234.00 debited from A/c XX1234 on 06-Jul-26.
```

The first three lines are a header (relay device name, SMS sender id,
receive time); everything after the blank line is the SMS body verbatim,
so multi-line messages round-trip exactly. Produced by
`Envelope.encodePlaintext`. There is no encryption and no version byte —
what's on the wire is what the reader sees in Telegram.

---

## 8. Components

```
com.puraa
├── MainActivity            # Compose host: relay setup ↔ relay status
├── PuraaApplication      # Creates notification channels; schedules the update check
├── config/
│   ├── ConfigStore         # EncryptedSharedPreferences — destination + its params, filter, device
│   ├── Destination         # TELEGRAM | DISCORD (exactly one active)
│   ├── SetupCode           # Self-contained "Share setup" QR payload (encode/decode)
│   └── RelaySetup          # Parsed setup fields (destination inferred)
├── relay/
│   ├── SmsReceiver         # BroadcastReceiver for SMS_RECEIVED — the SMS source
│   ├── SenderFilter        # Whitelist / all-SMS decision
│   ├── RecentSmsPush       # Manual "push last 15 min" backfill (reads the inbox)
│   ├── OutboxEntity/Dao    # Room table of pending + sent messages
│   ├── OutboxRepository    # Envelope-encodes and enqueues; drains via the worker
│   ├── RelayWorker         # Expedited WorkManager job that posts via the active sink
│   ├── RelayAnnouncer      # Posts the one-off "configured" confirmation
│   ├── RelayNotifications  # Notification channel + foreground info for the worker
│   └── AppDatabase         # Room database (outbox only)
├── send/
│   └── MessageSink + Sinks # Destination abstraction; resolves the active sink from config
├── telegram/
│   └── TelegramClient      # OkHttp wrapper over Bot API sendMessage
├── discord/
│   └── DiscordClient       # OkHttp wrapper over a channel webhook POST
├── envelope/
│   └── Envelope            # Plaintext wire format (same text for either destination)
├── update/
│   ├── UpdateManifest      # Parsed `update.json` (versionCode, APK URL, SHA-256)
│   ├── Updater             # Check → stream into a PackageInstaller session → verify → commit
│   ├── InstallResultReceiver # PackageInstaller callback: opens the confirm dialog
│   └── UpdateStatus        # In-memory flow carrying the install outcome back to the UI
└── ui/
    ├── RelaySetupScreen    # Setup form (destination toggle) + QR scan
    ├── RelayScreen         # Running status, stat tiles, "push last 15 min"
    ├── UpdatePrompt        # ON_RESUME check + the "update available" card
    ├── UpdateDialog        # The whole update flow: check, download, verify, install
    ├── StatusPill          # Active / Inactive status pill (both screens)
    ├── RelayPermissions    # Runtime-permission + battery-exemption helpers
    └── theme/              # Color, Theme, Type
```

---

## 9. Local storage

- **`EncryptedSharedPreferences`** (hardware-backed, Android Jetpack
  Security): the active destination and its secrets — Telegram bot token +
  channel id, or the Discord webhook URL — plus the sender whitelist, relay
  device name, and a `relayActive` flag (whether the relay is running or
  paused — see §11). Nothing about updates is persisted — see §13.
- **Room / SQLite** (`puraa.db`): the `outbox` table. Pending rows survive
  reboots; a failed row backs off and is parked as `FAILED` after `MAX_ATTEMPTS`
  (6) so it can't block the queue; the last **20 terminal rows** (`SENT` and
  `FAILED` combined) are retained (unencrypted, app-private), older ones
  trimmed, and the "Recent activity" list shows the most recent 10 of them.
  Room migrations preserve the outbox across app updates (no destructive
  fallback).

Nothing is stored off-device. The SMS body already lives in the phone's
native SMS app; the outbox holds a copy of recent messages (queued plus the
last 20 terminal rows) in app-private storage. Backups are disabled, so this is
exposed only to physical/rooted access — which the threat model accepts.

---

## 10. Reliability

| Concern                | How it's handled                                                        |
| ---------------------- | ----------------------------------------------------------------------- |
| App not running        | `SMS_RECEIVED` is a manifest broadcast, exempt from background limits — it wakes the app. |
| Device reboot          | Manifest receiver works post-boot; WorkManager reschedules pending sends itself. No boot receiver needed. |
| Network down           | WorkManager network constraint defers the send; it drains on reconnect. |
| Send failure           | Fast in-worker retries (immediate/+1s/+3s) absorb transient blips; only persistent failures fall back to `Result.retry()` → WorkManager 10s backoff. |
| Telegram rate limit    | SMS volume is far below Telegram's limits; the FIFO drain is naturally paced. |
| Doze / App Standby     | Expedited work runs promptly even in Doze. Target devices are stock Motorola (no aggressive OEM killers). |
| Storage growth offline | Outbox trimmed to a bounded size; native SMS inbox is the source of truth. |

An optional **battery-optimisation exemption** is requested at setup to
keep sends prompt on idle phones; it is belt-and-suspenders, not required
for correctness.

---

## 11. Configuration and onboarding

### One-time Telegram setup

1. In `@BotFather`: create one bot; save its token.
2. Create one private channel.
3. Add the bot to the channel as an admin with **Post Messages** on.
4. Capture the channel id (e.g. `-1001234567890`).

### Relay setup (on the source phone)

First launch opens the relay setup screen directly (there is only one
mode). The screen has a **Telegram / Discord** toggle that swaps between
the token+channel fields and a single webhook field, plus a device name and
optional sender whitelist. Fill these in and tap **Save**, granting
`RECEIVE_SMS` (and notification permission on Android 13+).

The secrets don't have to be typed, though. Besides manual entry there is
exactly one shortcut — **scan a setup QR** (a button on the setup screen).
Another phone's "Share setup (QR)" shows a QR of its config; scanning it
**pre-fills the form for review and Save** (that Save is the confirmation, and
the only action that ever writes config). The QR carries a **self-contained
Puraa setup code** — not a URL and not a deep link, so a generic scanner sees
only opaque text and nothing routes anywhere. Because only an in-person camera
scan applies it, there is no remote path to inject config. A Discord webhook
must be a real Discord host (`discord.com` / `discordapp.com`) to save, so
config can't be pointed elsewhere either way.

These are the **only two** ways to configure the relay: manual entry or an
in-person QR scan. Puraa deliberately registers **no deep link / VIEW
handler**, so there is no exported, remotely-deliverable link an attacker
could craft to re-point a relay.

The only OS prompt is the `RECEIVE_SMS` dialog, which Android mandates.

### Running vs. paused (Stop and reconfigure)

Config has two independent facts: whether it is *configured* (has valid
destination + secrets) and whether it is *running*. `ConfigStore.relayActive`
tracks the latter; `isRelayRunning() = isRelayConfigured() && relayActive` is
the single gate for both routing (which screen shows) and forwarding
(`SmsReceiver` / `RecentSmsPush`). Saving a valid setup sets `relayActive`
true.

**Stop and reconfigure** (a menu action on the running screen) sets
`relayActive = false` and clears the pending queue, but **keeps** the
destination, token/webhook, filter, and device name. So a mistaken stop is
undone by re-saving the setup form, which comes back **pre-filled** with the
previous values — no secret has to be re-entered. A paused relay opens to the
setup screen on next launch.

The status is surfaced by a **status pill** (`ui/StatusPill`): a teal
"Active" on the running screen, a muted "Inactive" on the setup screen.

### Configuration confirmation

On Save, the relay posts a one-off confirmation to the destination through
the normal outbox + `RelayWorker` path:

```
✅ Puraa relay configured
Device: moto g84
Destination: Telegram
Filter: HDFCBK, ICICIB, CRED
This phone will now forward matching SMS here.
```

This gives immediate proof — in whichever destination is active — that the
phone is live and with which settings. The bot token / webhook URL is never
included; it's a secret and the channel already implies it.

### Setup QR code

The QR is a **self-contained setup code** (`config/SetupCode.kt`) — not a
URL and not a deep link: a magic header line plus newline-delimited
`key=value` fields, understood only by Puraa's own scan flow. A generic QR
scanner sees opaque text and can do nothing with it; there is no dependency
on any website and nothing for the OS to route.

```
PURAA-SETUP/1        PURAA-SETUP/1
bot=<token>          discord=<webhook_url>
ch=<channel-id>      filter=<csv>          # filter omitted when empty
filter=<csv>
```

Values need no escaping — newline is the only delimiter and none of the
values (token, channel id, webhook URL, CSV filter) contain one. The device
name is never encoded; the scanning phone names itself.

**No deep link, by design.** An earlier iteration also accepted a
`puraa://relay?…` custom-scheme link (and briefly an `https://…` App Link).
Both were removed: an `exported` + `BROWSABLE` VIEW handler is *remotely
deliverable* — an attacker who knows the scheme can plant a tappable link in a
page/message/email, and one careless tap-then-Save on the phone would re-point
its relay. Even though a link never configured silently, the handler itself
was the attack surface. With it gone, the app doesn't register as a handler at
all, so a crafted link resolves to nothing. Configuration is manual entry or
an in-person QR scan, full stop.

---

## 12. Permissions

| Permission | Why |
| --- | --- |
| `RECEIVE_SMS` | Read incoming SMS the instant they arrive (the automatic path). |
| `READ_SMS` | Query the inbox for the manual "push last 15 min" backfill only; requested lazily on first tap. |
| `CAMERA` | Scan a setup QR code; requested only when you tap "Scan setup QR code". |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Post to the destination; gate on connectivity. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | WorkManager's brief foreground window for expedited sends on Android < 12. |
| `POST_NOTIFICATIONS` | Show that brief foreground notification on Android 13+. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep sends prompt on idle phones (optional). |
| `REQUEST_INSTALL_PACKAGES` | Drive the `PackageInstaller` session that installs Puraa's own updates (§13). |
| `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Let the *second* and later self-updates skip the confirmation dialog (§13). |

Both install permissions are **normal** permissions — neither prompts, and
neither can install anything but Puraa: an APK not signed with the release key
is rejected by the platform.

`REQUEST_INSTALL_PACKAGES` is only half of what's needed, though. The other half
is the user-granted **"Install unknown apps"** app op, readable via
`PackageManager.canRequestPackageInstalls()`.

**It is required for self-update too** — verified on a Pixel 8a (Android 16).
Updating one's own package is *not* exempt: without the op, the download,
digest check and commit all succeed, and the platform then refuses at the
confirmation step with *"isn't allowed to install unknown apps from this
source"*, reported back as `INSTALL_FAILED_ABORTED: User rejected permissions`.

The update dialog still treats a missing op as a **hint**, not a gate, offering
a shortcut to that settings page while leaving "Update now" enabled. Android's
own refusal dialog also offers a route to Settings, so the state is recoverable
either way, and one code path beats two.

Notably **not** requested: notification-listener access and
`RECEIVE_BOOT_COMPLETED`.

---


## 13. In-app update

Puraa is sideloaded, so nothing else will ever update it. The app therefore
ships its own release checker — but **only an in-app one**. There is no
background work, no notification channel, and nothing installs unasked.

> Recorded as [ADR-0001](decisions/0001-in-app-only-self-update.md), including the
> alternatives that were rejected and the one that was deferred.

### Why not a background updater

The first cut of this was a daily `WorkManager` check that downloaded and
silently installed. It was removed, for two reasons.

The first is that on any phone where the install can't be silent — Android 11
and older, and the first update on *every* phone — a background check's only
possible output is a notification. Notifications get swiped, muted, and batched,
and once one is gone there is no trace that an update was waiting. That is the
worst of both worlds: background machinery whose single deliverable is the least
reliable surface Android offers.

The second is trust. Silent self-installation of an app holding `RECEIVE_SMS` is
a large amount of authority to hold, and it buys little when the operator can
approve an update in two taps.

So Puraa follows the shape Google's own [in-app updates
guidance](https://developer.android.com/guide/playcore/in-app-updates/kotlin-java)
prescribes: **check at every app entry point**, and let the user drive.

### The manifest, not the tag

CI attaches an `update.json` asset to every GitHub Release
(`.github/workflows/release.yml`):

```json
{
  "versionCode": 128,
  "versionName": "0.3.0",
  "apk": "https://github.com/gunasekar/puraa/releases/download/v0.3.0/puraa-0.3.0.apk",
  "sha256": "…",
  "size": 9123456
}
```

The app fetches it from
`https://github.com/gunasekar/puraa/releases/latest/download/update.json` — a
permanent redirect to that asset on the newest release. That URL is a plain
download, **not** the GitHub API: no 60-request/hour rate limit and no API
response shape to keep up with.

`versionCode` is the same monotonic commit count the build stamps into the APK
(see [RELEASE.md](RELEASE.md#versioning)), so "is there something newer?" is one
integer comparison against `PackageInfo.longVersionCode`. No tag-string parsing,
no SemVer edge cases. Unknown JSON keys are ignored so a future release can add
fields without becoming unreadable to builds already in the field — a manifest
an old build can't parse is a build that can never update again.

### The flow

```mermaid
flowchart TD
    R["App opened / resumed (ON_RESUME)"] --> C["GET update.json"]
    C -->|"versionCode ≤ installed, or offline"| D["Nothing shown"]
    C -->|newer| B["Card on RelayScreen, badge on the ⋮ menu"]
    B --> T["User taps Update now"]
    T --> S["Stream APK → PackageInstaller session"]
    S --> V{"SHA-256 matches?"}
    V -->|no| X["Abandon session, report in the dialog"]
    V -->|yes| K[commit]
    K --> PP["Play Protect scan, if enabled — trips on every release"]
    PP --> P{"Puraa owns the package?"}
    P -->|yes| I["No confirmation of Android's own"]
    P -->|"no, first time"| Q["Android's confirm dialog, opened directly"]
```

Nothing about updates is persisted. A cached "update available" would outlive the
conditions that made it true (offline, or installed by hand), and the check is a
few hundred bytes of JSON — so it is simply re-run on every resume. If the
network is down, no card appears, which is correct: there is nothing that could
be installed anyway.

The APK is streamed **into** the install session rather than to a cache file and
copied: one pass, no temp file to clean up, and the digest is computed from the
very bytes that were written. A session is only staging — `commit()` is the one
irreversible step, and a digest mismatch abandons the session before it. Any
session left over from an abandoned attempt is discarded before a new one
starts, so they can't accumulate.

### The card is not dismissible

Deliberately. It is regenerated from a live check on every resume, so the only
way to clear it is to update. This is the one thing the notification-based
design got wrong, and the reason the surface moved in-app.

### Why the confirm dialog can be shown at all

Because the commit always happens with the app in the foreground.
`InstallResultReceiver` receives `STATUS_PENDING_USER_ACTION` and launches
Android's confirmation Intent directly. A background commit could not do this —
Android 10+ drops activity starts from a background app — which is the concrete
mechanism behind the design decision above.

### Android's dialog: one tap, then none

The **first** self-update needs a confirmation: whoever installed Puraa (adb, a
file manager) is the *installer of record*, and Android will not let a different
installer replace an app without asking. Committing it makes Puraa its own
installer — `installerPackageName` flips from `null` to `com.puraa`.

**From the second update onward that dialog does not appear.** Measured on a
Pixel 8a (Android 16) across two consecutive real releases: v0.3.0 → v0.4.0
prompted, v0.4.0 → v0.4.1 did not. The `USER_ACTION_NOT_REQUIRED` +
update-ownership path works exactly as intended.

**Play Protect does not go away, though.** Its scan (below) still interposes on
every release, and after the first update it is the *only* remaining
interaction. So "then zero" is true of Android's own installer and false of the
end-to-end experience on any device with Play Protect enabled — which is most of
them. Budget one scan per update, forever.

Two platform details shape this:

- `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` is **API 31**. On Android 11
  and older there is no silent path at all — every update shows the dialog,
  forever. `minSdk` is 26, so this is a live case, not a hypothetical.
- **Android 14 update ownership.** On 14+ a dialog-free install requires the
  installer to *own* the package, not merely to have installed it, so the session
  also calls `setRequestUpdateOwnership(true)`. Ownership transfers on that first
  confirmed install. It locks out any other updater — which costs nothing here,
  because Play Store policy reserves SMS permissions for default SMS handlers, so
  Puraa can never ship there (§2).

### What is trusted

| Layer | Guarantee |
| --- | --- |
| HTTPS to `github.com` | The manifest, and the digest inside it, arrive untampered. |
| SHA-256 in the manifest | The downloaded APK is byte-identical to the one CI built and hashed. |
| **Release signing key** | The real anchor. An APK not signed by Puraa's key fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. A forged manifest can waste a download; it can never install foreign code. |

### Permission to install at all

Separate from the confirmation dialog: Android also requires the user to have
allowed **Puraa itself** to install apps ("Install unknown apps" → Puraa), which
is a different grant from the one Chrome needs to deliver the first APK. It is
required for self-update — see §12 — and is a one-time grant per install.

### Play Protect scans every release

On a device with Play Protect enabled, committing the session hands off to
*"Play Protect hasn't seen this app before"* → **Scan app** → *"This app looks
safe"* → **Install**. That check keys on the **APK hash**, so a new release
trips it *every time*, not only on a first install. The APK is uploaded to
Google as part of the scan.

Observed on a Pixel 8a (Android 16) on **both** updates tested — 0.4.0-dirty →
0.4.0 and 0.4.0 → 0.4.1 — which is what establishes that it recurs rather than
being a first-install artefact. It is a soft gate, not the hard "App not
installed" block that greets a fresh sideload of an SMS app
([RELEASE.md](RELEASE.md#play-protect-blocks-the-first-install)).

It is also the *durable* cost of the design. Android's own confirmation stops
after the first update; this one does not, because a new release is by
definition an APK hash Play Protect has never seen. Two taps per release, for
as long as Puraa is sideloaded.

### The accepted cost

Updates now require someone to **open the app**. A relay phone left in a drawer
will keep forwarding SMS indefinitely on whatever build it has — relaying is
zero-touch (§3 criterion 2), updating is not. This is a deliberate trade of reach
for simplicity and trust, and it is the one success-criterion tension in the
design. Operators should open Puraa occasionally.

Self-update is compiled out of debug builds (`BuildConfig.SELF_UPDATE_ENABLED`).
A debug build is `com.puraa.debug` signed with the debug key, so a release APK
could never install over it.

---

## 14. Threat model (brief)

Puraa is a convenience tool, not a security product. In plaintext mode:

| Threat | Covered? |
| --- | --- |
| Network attacker between phone and destination | **Yes** — TLS to `api.telegram.org` / `discord.com`. |
| The destination service, or someone with server/channel access | **No** — they see the plaintext SMS. Acceptable for transaction alerts; be eyes-open about OTPs. |
| Malicious app or QR injecting config | **Mitigated** — a QR only *pre-fills* the setup screen; config is written solely by an explicit user Save; there is no deep link at all; Discord webhooks are host-validated to a Discord host (`discord.com` / `discordapp.com`). |
| Secret (bot token / webhook URL) leaked from the APK or a shared QR | Partial — an attacker could post to / drain the channel. Rotate the bot token via `@BotFather` or regenerate the Discord webhook. |
| Physical access to a rooted phone | **No** — the secret and queued/recent messages are on the device. |
| Malicious APK pushed through the self-updater (§13) | **Mitigated** — HTTPS manifest, SHA-256 pin, and above all the release signing key: an APK signed by anyone else is rejected by the platform. Compromise of the signing key itself is not covered — see [RELEASE.md](RELEASE.md#-back-up-the-keystore). |

The main practical risk is **secret leakage** (bot token or webhook URL),
not broken transport — especially via a shared setup QR. Keep the channel
invite-only, treat the token/webhook as secrets (delete setup material after
use, rotate if exposed), and consider excluding OTP senders from the whitelist
since OTPs are live credentials.
