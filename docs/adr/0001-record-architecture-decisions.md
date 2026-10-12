# 0001. Record architecture decisions

- Status: Accepted
- Date: 2026-10-12
- Deciders: Servante maintainers

## Context

Servante is built milestone by milestone, partly by agents following `prompts/02-build.md`, over a long
period and with contributors who were not present when a choice was made. Decisions such as the UI
toolkit or where secrets live are expensive to reverse and easy to re-litigate from scratch. Without a
written trail, the next person reads the code and guesses at the reason.

## Options considered

1. **Architecture Decision Records in the repository**: one short markdown file per decision, next to the
   code. Pros: versioned with the change that implements it, reviewable in a pull request, cheap to write.
   Cons: needs discipline to keep current.
2. **A wiki or an external document**: Pros: nicer to browse. Cons: drifts from the code, not reviewed,
   invisible to an agent working in the checkout.
3. **Do nothing, rely on commit messages and code comments**: Pros: no extra work. Cons: commit messages
   explain a diff, not a choice between options; the alternatives that were rejected are lost.

## Decision

We will keep Architecture Decision Records in `docs/adr/`, numbered sequentially, following the format in
the `adr-writer` skill, because the reason behind a decision has to travel with the code that implements it.

## Consequences

- Positive: a new contributor or agent can read why the stack is what it is in a few minutes.
- Positive: rejecting an option is recorded, so the same debate does not reopen every milestone.
- Negative: one more file to write when a real decision is made.
- Follow-up: an accepted ADR is never edited in place. Supersede it with a new record and link both ways.
  The index below this file is the list of records.
