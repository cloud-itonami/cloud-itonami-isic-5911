# Contributing to cloud-itonami-isic-5911

Contributions should preserve the actor's scope: back-office production
operations coordination only, with CRITICAL exclusions of on-set
safety-authority finalization and direct equipment actuation (see
README.md).

- All code must be .cljc (portable Clojure, no JVM-only constructs).
- Tests must pass: `clojure -M:test`
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Finalize an on-set safety-clearance decision (stunt-clearance sign-off, hazard-clearance sign-off).
- Override or finalize a minor-performer work-hour limit.
- Directly actuate rigging, stunt, or pyrotechnic equipment.
- Finalize talent compensation/contract terms, or adjudicate insurance/legal/union matters.

Contributions that cross these boundaries will be rejected.
