<p align="center">
  <img src="docs/assets/puraa-wordmark.svg" width="240" alt="Puraa">
</p>

<p align="center">
  A small, lightweight Android app that forwards incoming SMS — bank, UPI, and
  OTP alerts — to a private Telegram or Discord channel, the instant each
  message lands. No second app, no server.
</p>

<p align="center">
  <a href="https://gunasekar.github.io/puraa/"><b>Setup&nbsp;QR&nbsp;↗</b></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/gunasekar/puraa/releases/latest">Download the APK</a>
</p>

<p align="center">
  <a href="https://github.com/gunasekar/puraa/releases/latest"><img src="https://img.shields.io/github/v/release/gunasekar/puraa?display_name=tag&amp;sort=semver" alt="Latest release"></a>
</p>

---

## How it works

Puraa runs on the phone whose SMS you want to forward. When an SMS arrives it
reads it live, keeps the ones matching your optional sender filter, and posts
each to **one configured destination** — a private Telegram channel (via a
Telegram bot) *or* a Discord channel (via a webhook). You read the messages in
a **normal Telegram/Discord client** on any device — there's no second app to
install and nothing to host.

- **Plaintext.** The message travels TLS-encrypted to the destination and sits
  readable in your private channel. Fine for transaction alerts; be eyes-open
  about OTPs (filter those senders out if you'd rather not forward them).
- **Only live SMS.** It forwards a message the instant it arrives and never
  reads old or historical SMS (except an explicit, manual "push last 15 min").
- **Invisible on the phone.** No always-on service, no permanent notification,
  no polling. The app is dormant between messages and only does work — for
  about a second — when an SMS needs forwarding.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the internal design, and
[docs/decisions/](docs/decisions/) for the reasoning behind the big calls.

## Setup

Pick **one** destination — Telegram *or* Discord — and get its credentials.

### Telegram

1. In `@BotFather`, send `/newbot` and save the token it replies with.
2. Create a private channel and add the bot as an admin with **Post
   Messages** enabled and every other permission off — posting is all
   Puraa needs.
3. Capture the channel id: open the channel on
   [web.telegram.org](https://web.telegram.org) and read it out of the
   address bar (`#-3491234567`). Private channels are addressed with a
   `-100` prefix, so that one is `-1003491234567`. Subscribe to the
   channel in your own Telegram app.

You'll enter the **bot token** and **channel id** in the app.

### Discord

1. In the target channel: **Edit Channel → Integrations → Webhooks → New
   Webhook**.
2. Name it, then **Copy Webhook URL**
   (`https://discord.com/api/webhooks/…`).

You'll paste that **webhook URL** in the app. Your normal Discord client
notifies you on new messages — no bot needed.

### Why not WhatsApp?

The only route open to a personal account is a library like **Baileys**, which
links in as a WhatsApp Web companion device. A linked device is expected to stay
connected — Puraa is asleep between messages by design — and the session drops
anyway: a logout, the phone offline too long, a protocol change. Each drop means
re-scanning a QR, on a phone you set up once and left in a drawer, and until you
do your SMS quietly stop arriving.

The official Business API never needs re-pairing, but isn't open to a personal
relay — it wants a business account, a spare phone number, approved templates,
and per-message billing.

Telegram and Discord push to you unprompted, forever, free, with nothing to
re-pair. If WhatsApp is just where you happen to look, a Telegram channel pinned
with notifications on gets you the same reflex. Details in
[ADR-0002](docs/decisions/0002-no-whatsapp-destination.md).

### Installing the APK

Grab the latest `.apk` from the [Releases](https://github.com/gunasekar/puraa/releases/latest)
page and tap it on the phone. Android puts three guards in the way of a
sideloaded app:

1. **Let the installing app install apps.** Chrome, Files — whichever you tap
   the `.apk` from. **Settings → Apps → Special app access → Install unknown
   apps →** pick it **→ Allow from this source**. *Switch it back off after —
   later versions are installed by Puraa itself, not by Chrome, so this grant
   is only for getting the first APK onto the phone.*
2. **Turn Play Protect off.** It blocks the first install of a sideloaded SMS
   app, usually as a bare "App not installed". **Play Store → profile → Play
   Protect → ⚙ → Scan apps with Play Protect: off.** *Turn it back on after —
   only the first install is gated.*
3. **Allow the restricted SMS setting** — see below. Scoped to Puraa, stays on.

`adb install -r puraa-<version>.apk` from a computer sidesteps all three.

#### If the SMS permission is greyed out ("Restricted setting")

On **Android 13 and newer**, SMS is a *restricted setting*: when an app is
installed from a browser or file manager, Android silently blocks the SMS
permission. The in-app prompt does nothing, and in Settings the SMS toggle is
greyed out with *"Restricted setting — for your security this setting is
currently unavailable."* This is an OS security measure, not a Puraa bug.

To unblock it:

1. **Settings → Apps → Puraa** (App info).
2. Tap the **⋮ menu** in the top-right corner.
3. Tap **Allow restricted settings** and confirm with your PIN/fingerprint.
4. Go to **Permissions → SMS → Allow** — the toggle is now grantable.

If the ⋮ menu doesn't show that option yet, open Puraa and tap the SMS button
once so the system records a blocked attempt, then return to App info — the
entry appears afterwards. Installing over `adb` avoids this step entirely.

#### Updates

That's the only install you have to do by hand. **Every time you open Puraa it
checks its own [Releases](https://github.com/gunasekar/puraa/releases/latest)**;
if there's a newer version you get a card on the main screen and a dot on the ⋮
menu. Tap **Update now** and it downloads, verifies, and installs. No store, no
Obtainium, no hunting for APKs.

**The first time, you'll need to allow Puraa to install apps** — the same
"Install unknown apps" screen as guard 1, but for Puraa rather than Chrome.
Android will otherwise refuse the install outright. The update dialog offers a
shortcut, and Android's refusal also links to it. One-time grant.

**That's the only time Android itself asks.** From the second update on, its
install confirmation is gone for good — Puraa now owns its own package.

**Play Protect is forever, though.** Expect this on every update: *"Play Protect
hasn't seen this app before"* → **Scan app** → *"This app looks safe"* →
**Install**. It recognises apps by their exact file, so a new release is always
new to it. Two taps per update, and there's no way around it short of turning
Play Protect off. (On Android 11 and older, Android's own confirmation also
never goes away — no version of that phone can skip it.)

Your relay settings, message history, and permissions are kept throughout —
tested, nothing to re-enter.

You can also check any time via **⋮ menu → Check for updates**, which tells you
when you're already on the latest release.

**Puraa never updates itself in the background.** Nothing is downloaded or
installed unless you tap. The flip side, and it's a real one: **a relay phone
you never open never updates.** It keeps forwarding SMS on whatever version it
has, indefinitely. Open the app every so often if you want fixes — that is the
one thing the relay can't do for you. (This was a deliberate trade; see
[ADR-0001](docs/decisions/0001-in-app-only-self-update.md).)

### On the phone to be relayed

With the app installed (above), open it once, choose **Telegram** or **Discord**, enter the
matching credentials above, add a device name and an optional sender whitelist
(e.g. `HDFCBK,ICICIB,CRED`), and grant SMS access. That's it — **forwarding needs
no further attention**: no re-opening, no restart, no permission re-grant. The
only reason to open Puraa again is to pick up an update ([above](#updates)).

To skip the typing, **scan a setup QR** instead. Generate one on the
[Puraa website](https://gunasekar.github.io/puraa/) — pick Telegram or Discord,
fill in your destination, and the page builds a QR you can download or show on
screen. (An already-configured phone can also produce one via **Share setup
(QR)**.) On the relay phone, tap **Scan setup QR code**, point it at the QR,
and it fills the form for review and Save. The QR is a self-contained Puraa
code — not a link — so it only means anything to this app.

Those are the only two ways to configure the relay — manual entry or an
in-person QR scan. Puraa registers no deep link, so nothing can point a relay
remotely.

## Building

Open this project in Android Studio (it syncs Gradle and generates the
wrapper on first open), or run `./gradlew assembleDebug`. Min SDK is 26, so it
runs on **any phone on Android 8.0 or newer**.

Delivery is most reliable on stock or near-stock Android (Pixel, Motorola,
etc.), which is what it's tested on. Heavily-skinned OEMs (Samsung, Xiaomi,
Oppo, Vivo, …) run aggressive background/battery killers that can delay or
drop forwarding — grant the battery-optimisation exemption Puraa offers at
setup on those devices.
