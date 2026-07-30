# ADR-0001 — Self-update is in-app only, checked at app entry points

**Status:** Accepted
**Date:** 2026-07-30

## Context

Puraa is distributed as a sideloaded APK and can never be on the Play Store:
store policy reserves SMS permissions for default SMS handlers, and Puraa's whole
function is reading SMS. So no store will ever push it an update. Left entirely
alone, an install runs its original build forever.

Four constraints shape any answer:

1. **`minSdk` is 26, and the fleet spans Android 11 to 17.** The API that lets an
   app install an update without a confirmation dialog —
   `SessionParams.setRequireUserAction(USER_ACTION_NOT_REQUIRED)` — is **API 31**.
   On the Moto E40 (Android 11, final) there is no dialog-free path at all, ever.
   On the Moto G51 5G (Android 12) and Pixel 8a (Android 17) there is, but only
   after Puraa has installed itself once and become its own installer of record —
   and on Android 14+, its *update owner*.
2. **The first update always prompts, on every device.** Until Puraa has installed
   itself once, the installer of record is whatever sideloaded it (adb, Chrome,
   Obtainium), and Android will not let one installer silently replace another's
   app.
3. **Puraa's premise is a phone nobody opens.** The design goal, stated in
   `ARCHITECTURE.md` §3 and promised in the README, was that after the one-time
   setup the relay phone needs no re-opening, restart, or permission re-grant.
   Both documents said this without qualification when this decision was taken.
4. **Puraa holds `RECEIVE_SMS`.** Any authority it grants itself is authority over
   a stream that includes OTPs and bank alerts.

Constraints 3 and 4 point in opposite directions, and that tension is the whole
decision.

## Decision

Puraa updates itself **only when the user opens the app**, and only when they tap.

- The check runs on every `ON_RESUME` (`ui/UpdatePrompt.kt`), following Google's
  [in-app updates guidance](https://developer.android.com/guide/playcore/in-app-updates/kotlin-java),
  which places the availability check at all app entry points.
- A newer release surfaces as a **non-dismissible card** on the relay screen plus a
  badge on the ⋮ menu. It is recomputed from a live check on every resume, so the
  only way to clear it is to update.
- **No background work of any kind.** No `WorkManager` job, no periodic check.
- **No notifications of any kind.** Puraa declares no update notification channel.
- Download, SHA-256 verification, and the `PackageInstaller` session all run in the
  foreground from the update dialog, which is also the only place failures are
  reported.

Nothing about updates is persisted. The check is a few hundred bytes of JSON, and a
cached "update available" would outlive the conditions that made it true.

## Alternatives considered

### A. An external updater (Obtainium, or the IzzyOnDroid F-Droid repo)

Rejected as *the* answer, though it still works fine alongside this one. It makes
updating conditional on the operator installing and configuring a second app, and
on every relay phone. IzzyOnDroid would also mean listing Puraa publicly, which
suits a general-purpose app and not a personal SMS relay.

### B. Daily background check → download → silent install, notification when a tap is needed

**Implemented first, then removed.** Two things killed it.

The decisive one: on every device where the install cannot be silent — Android 11,
*and the first update on all three phones* — this design's only possible output is
a notification. Notifications get swiped, muted and batched, and once one is gone
nothing records that an update was staged and waiting. The next day's check would
re-download and re-stage it, producing another swipeable notification. That is
background machinery whose sole deliverable is the least reliable surface Android
offers.

The second: it grants an app holding `RECEIVE_SMS` the standing authority to replace
its own binary unattended, and buys — at most — two taps.

### C. "Silent-or-nothing" background check (deferred, not rejected)

A background check gated on being able to install with *zero* interaction: verify via
`getInstallSourceInfo()` that Puraa is its own installer and update owner and that
the device is API ≥ 31, and otherwise do nothing at all — no check, no download, no
notification. No surface to swipe, no half-state to lose.

This is the honest fix for the cost accepted below, and it is roughly 40 lines. It
was deferred because it helps only Android 12+ (it can never help the Moto E40), and
it reintroduces exactly the trust property that ruled out option B. Revisit if a
relay is observed running stale.

### D. Play Store distribution

Closed by policy, not by choice. See `ARCHITECTURE.md` §2.

## Consequences

**Gained**

- No background footprint from updates, consistent with the app's design goal of
  being invisible on the device between messages.
- Nothing installs without an informed, present operator.
- A pending update cannot be lost. The card is regenerated on every resume.
- The confirm dialog can always be shown, because the commit always happens with the
  app in the foreground — Android 10+ drops activity starts from background apps, so
  a background commit could not have shown it at all.
- The app can say "you're already on the latest release", which a silent background
  check never can.
- Materially less code: no worker, no notification channel, no opt-out preference, no
  foreground-detection heuristic.

**Accepted cost**

**Updates require someone to open the app.** A relay phone left in a drawer keeps
forwarding SMS indefinitely on whatever build it has. This is a deliberate trade of
reach for simplicity and trust.

Because that contradicted an unqualified promise, this decision **rescoped success
criterion 2** from "zero-touch" to "zero-touch *relaying*", and the README now states
plainly that a phone you never open never updates. The cost is recorded in the docs
rather than left for an operator to discover.

**Unchanged**

The security anchor is untouched either way: an APK not signed with Puraa's release
key is rejected by the platform (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The HTTPS
manifest and SHA-256 pin sit on top of that, not in place of it. A forged manifest can
waste a download; it cannot install foreign code.

## Revisit when

- A relay is found running a build months behind — the accepted cost has become real,
  and option C is the answer.
- Every phone in the fleet is on Android 12+, which is the precondition for option C
  being worth anything.
- Android changes the silent-update preconditions again, as 14 did with update
  ownership. This has moved twice already (12, 14) and should be expected to move again.
