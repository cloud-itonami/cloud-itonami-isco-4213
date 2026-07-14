# cloud-itonami-isco-4213

Open Occupation Blueprint for **ISCO-08 4213**: Pawnbrokers and Money-lenders.

This repository designs a forkable OSS business for an independent pawnbroking and small-loan practice: a collateral intake, appraisal-support and vault-handling robot manages pledged items under a governor-gated actor, so the practice keeps its own loan records instead of renting a closed lending SaaS.

**Maturity: `:implemented`.** `src/pawnbroking/` implements the
`PawnbrokingActor` as a `langgraph.graph/state-graph`
(`pawnbroking.actor`) wired to a `Pawnbroking Advisor` (`pawnbroking.advisor`)
and an independent `PawnbrokingGovernor` (`pawnbroking.governor`),
following the itonami actor pattern (ADR-2607011000): `:intake -> :advise
-> :govern -> :decide -+-> :commit (:ok?) +-> :request-approval (:escalate?,
human-in-the-loop interrupt) +-> :hold (:hard?)`. 14 tests / 29 assertions
green (`clojure -M:test`). HARD invariants (always hold, never
overridable): client provenance, no-actuation (`:effect` must be
`:propose`), a registered item basis for any loan-offer proposal, the
proposed loan amount not exceeding the item's registered appraised
value (a loan beyond the item's registered appraised value is an
unsecured advance, not a pawn loan), and a completed appraisal before
any loan can be offered (offering a loan against unappraised collateral
is an unsecured guess, not a pawn valuation). Always-escalate ops
(human sign-off regardless of confidence, mapping this repo's Trust
Controls in [`docs/business-model.md`](docs/business-model.md)):
`:approve-over-appraisal-disbursement` and
`:approve-unappraised-loan-offer`.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a collateral intake, appraisal-support and vault-handling robot performs item photographing, tagging and secure vault storage under an actor that proposes
actions and an independent **Pawnbroking Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
loan disbursement above the item's registered appraised-value ceiling) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
collateral submission + appraisal + loan request
        |
        v
Pawnbroking Advisor -> Pawnbroking Governor -> offer loan/release collateral, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `4213`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
