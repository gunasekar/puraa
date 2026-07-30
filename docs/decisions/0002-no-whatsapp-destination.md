# ADR-0002 — WhatsApp is not a relay destination

**Status:** Accepted
**Date:** 2026-07-31

## Context

WhatsApp is the obvious third destination after Telegram and Discord. The code
isn't the obstacle — `send/MessageSink.kt` exists for exactly this, and a third
destination is about 250 lines and a day's work.

No delivery route fits an app that sleeps between messages. There is one route
that *technically* works, and its reliability is the problem.

(Separate question from WhatsApp as a *source*, already a non-goal — see
`ARCHITECTURE.md` §2.)

## Decision

Telegram or Discord only. No WhatsApp destination.

## The one workable route is Baileys, and it needs re-pairing

[Baileys](https://github.com/WhiskeySockets/Baileys) implements WhatsApp's
**linked-device** protocol — the companion-device mechanism behind WhatsApp Web.
It's the route every self-hosted project takes, because it uses your personal
number and costs nothing.

It also drops sessions routinely. A companion session is invalidated by logging
the device out from your phone, the primary phone being offline too long, a
protocol change, or two processes sharing the same auth state and corrupting the
keys. Every drop means **re-scanning a QR**. This is not a Baileys defect —
`whatsapp-web.js` and WPPConnect use the same mechanism and have the same
problem. WhatsApp Web was built for a human at a browser, not an unattended
process.

[OpenClaw](https://docs.openclaw.ai/channels/whatsapp) ships this route, and its
tracker is the evidence: [#51012](https://github.com/openclaw/openclaw/issues/51012)
— relink succeeds, then sends fail with "No active WhatsApp Web listener" and
401 session drops; [#23093](https://github.com/openclaw/openclaw/issues/23093) —
an open request to move off Baileys entirely, citing "frequent session logouts,
401 errors, and account bans, especially after reconnections."

Two things make it a non-starter here specifically:

- **A linked device is expected to stay connected. Puraa is dormant by design** —
  no service, no polling, awake for about a second per SMS (§1). That is the most
  hostile possible pattern for a companion session, and reconnect churn is
  exactly where OpenClaw's drops and bans cluster.
- A relay is **a phone in a drawer**. Re-pairing is a chore for someone who is
  watching. Nobody is watching. A dropped session means SMS silently stop
  arriving, which is the worst failure this app has.

Worth being precise about one thing: this is *not* "you'd need a server". Baileys
itself is Node and so couldn't live inside the app, but the protocol has JVM
implementations — [Cobalt](https://github.com/Auties00/Cobalt) covers WhatsApp
Web companion and Android on-device. It doesn't rescue the idea though: it wants
Java 21 against Puraa's `jvmTarget = 17` on Android's class library, and is
pre-1.0 with "major breaking changes between each release". The objection is the
dormancy mismatch above, not the hosting.

Plus the terms-of-service and ban risk on the number, which alone would give
pause for something carrying bank alerts.

## The official Cloud API is not an option

Not on reliability — it's a bearer token, nothing to pair. It simply isn't
available to a personal relay: a Meta Business account, a **dedicated phone
number** that can't be your personal WhatsApp, Meta-approved message templates,
and per-message billing outside a 24-hour window you'd have to keep re-opening.
Ruled out on setup, not on engineering.

## On-device automation

`whatsapp://send` only opens the UI and needs a tap. Automating it means
`ACCESSIBILITY` or `NOTIFICATION_LISTENER`, which Play Protect refuses on
sideloaded APKs ([RELEASE.md](../RELEASE.md#play-protect-blocks-the-first-install)).
Puraa currently attracts only the soft per-release scan; either permission flips
it to the hard "App not installed" block — damaging the working install story to
add one destination.

## Consequences

Two destinations that push free and unprompted, with nothing to re-pair. Users
who only check WhatsApp aren't served — the practical substitute is a Telegram
channel pinned with notifications on.

`MessageSink` stays a two-implementation abstraction. Nothing here argues against
a third destination in general, only against this one.

## Revisit when

- Puraa gains something always-connected — a server, or a persistent service on
  the phone. That removes the dormancy mismatch, though not the re-pairing or the
  ban risk.
- Linked-device sessions stop needing re-pairing, which won't happen while the
  mechanism is a companion device.
