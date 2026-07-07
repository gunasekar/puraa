# Puraa

A small, lightweight Android app that forwards incoming SMS to a private
Telegram channel or a Discord channel as each message arrives — useful for
mirroring alerts such as bank, UPI, and OTP messages to a client you already
use, without a second app or any server.

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

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the internal design.

## Setup

Pick **one** destination — Telegram *or* Discord — and get its credentials.

### Telegram

1. In `@BotFather`, create a bot and save its token.
2. Create a private channel and add the bot as an admin with **Post
   Messages** enabled.
3. Capture the channel id (e.g. `-1001234567890`) and subscribe to the
   channel in your own Telegram app.

You'll enter the **bot token** and **channel id** in the app.

### Discord

1. In the target channel: **Edit Channel → Integrations → Webhooks → New
   Webhook**.
2. Name it, then **Copy Webhook URL**
   (`https://discord.com/api/webhooks/…`).

You'll paste that **webhook URL** in the app. Your normal Discord client
notifies you on new messages — no bot needed.

### On the phone to be relayed

Install the APK, open it once, choose **Telegram** or **Discord**, enter the
matching credentials above, add a device name and an optional sender whitelist
(e.g. `HDFCBK,ICICIB,CRED`), and grant SMS access. That's it — the app never
has to be opened again.

To skip the typing, configure one phone first, then use **Share setup (QR)**
on it: the next phone scans that QR to copy the config into the form for
review and Save. The QR is a self-contained Puraa code — not a link — so it
only means anything to this app.

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
