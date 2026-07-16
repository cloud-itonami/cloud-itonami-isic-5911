# cloud-itonami-isic-5911

**Motion Picture, Video and Television Programme Production Activities** — ISIC Rev.4 class 5911.

A coordination-only actor for film/video/TV programme production, behind an independent Governor that earns advisor trust through structured oversight: proposal → advise → govern → decide → commit|hold|escalate.

## Scope

This is a **production OPERATIONS COORDINATION actor**, not an on-set safety authority and not an equipment-actuation controller. It drafts and proposes back-office coordination artifacts; a human always makes the actual safety, HR, legal, and creative-finalization decisions.

## Features

- **Closed proposal-op allowlist**: `log-production-record`, `schedule-production-operation`, `flag-onset-safety-concern`, `coordinate-post-production-handoff` (all `:effect :propose`).
- **Three HARD governor checks** (permanent, un-overridable):
  1. **Production verified** — target production must exist AND be registered/verified in the store.
  2. **Effect is :propose** — any other `:effect` value is rejected.
  3. **Scope exclusion** — finalizing an on-set safety-clearance decision (stunt-clearance sign-off, hazard-clearance sign-off), overriding a minor-performer work-hour limit, direct rigging/stunt/pyrotechnic actuation, and talent-compensation/insurance/legal/union adjudication are permanently blocked.
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: production-record logging only (approval-gated)
  - Phase 2: + shoot-day/location/crew scheduling, post-production handoff (approval-gated)
  - Phase 3: auto-commits clean, high-confidence proposals (on-set safety concerns always escalate)
- **Append-only audit ledger** — every decision is an immutable log entry.
- **langgraph-clj StateGraph** — one request = one supervised run; human-in-the-loop via `interrupt-before`.

## CRITICAL safety guardrail

`:flag-onset-safety-concern` **always** escalates to a human — it is never a member of any phase's `:auto` set, at any phase, permanently. This actor never finalizes an on-set-safety-authority decision (clearing a stunt as safe, overriding a minor-performer work-hour limit); those are always either a hard permanent block (if attempted directly) or handled entirely outside this actor by a human safety authority.

Scope-exclusion terms in `filmprodops.governor/scope-excluded-terms` are phrased as the finalization/execution ACTION ("finalize the stunt clearance", not the bare noun "stunt") so the governor never self-trips on the advisor's own legitimate default rationale text when reporting a genuine on-set concern. See `filmprodops.governor-test/default-mock-advisor-proposals-never-self-trip`.

## Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:dev:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

## Test suite

- `test/filmprodops/governor_test.clj` — unit tests of governor hard checks, scope exclusion, and the self-trip regression guard
- `test/filmprodops/advisor_test.clj` — advisor proposal shape and consistency
- `test/filmprodops/phase_test.clj` — rollout phase logic
- `test/filmprodops/governor_contract_test.clj` — full graph integration, audit trail
- `test/filmprodops/store_contract_test.clj` — Store protocol and MemStore implementation

## Modules

- `filmprodops.store` — SSoT (MemStore, String-keyed production directory, append-only ledger)
- `filmprodops.advisor` — contained intelligence node (mock + real-LLM seam)
- `filmprodops.governor` — independent compliance layer
- `filmprodops.phase` — staged rollout (0→3)
- `filmprodops.operation` — langgraph-clj StateGraph
- `filmprodops.sim` — demo driver

## License

AGPL-3.0-or-later. See LICENSE file.

## Governance

This actor is part of the cloud-itonami Wave 4 (human-services) fleet. See ADR-2607121000 and ADR-2607152500 for design decisions, and `90-docs/adr/` in `com-junkawasaki/root` for the ISIC-5911-specific coverage ADR.
