# Architecture decision records

One file per decision that was **hard to make and expensive to reverse** — the
kind where someone six months from now will otherwise re-litigate it from
scratch, or quietly undo it without knowing what it was protecting.

Not for every design choice. [`ARCHITECTURE.md`](../ARCHITECTURE.md) describes
how the system works *now*; an ADR records why one path was taken and what the
alternatives cost. If a decision has no rejected alternatives, it isn't an ADR.

## Convention

- Filename: `NNNN-kebab-case-title.md`, numbered sequentially, never renumbered.
- Never edit a decision's substance after it's accepted. Supersede it with a new
  ADR and set the old one's status to `Superseded by ADR-NNNN`.
- **Status** is one of:

  | Status | Meaning |
  | --- | --- |
  | `Proposed` | Written up, not yet settled. |
  | `Accepted` | In force. The code should match it. |
  | `Superseded by ADR-NNNN` | Replaced. Kept for the reasoning, not the conclusion. |
  | `Deprecated` | No longer applies, and nothing replaced it. |

- Include a **Revisit when** section. A decision made under constraints that no
  longer hold should be reconsidered on purpose, not by accident.

## Index

| # | Decision | Status |
| --- | --- | --- |
| [0001](0001-in-app-only-self-update.md) | Self-update is in-app only, checked at app entry points | Accepted |
| [0002](0002-no-whatsapp-destination.md) | WhatsApp is not a relay destination | Accepted |
