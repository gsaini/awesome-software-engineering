# AGENTS.md — platform-spec (the Store)

This repo is the **cross-repo source of truth** for the product's specs.

**It owns:**
- End-to-end capabilities that span more than one repo (`openspec/specs/<capability>/`)
- The **contracts between services** (`openspec/specs/contracts/`)
- **Cross-repo change proposals** (`openspec/changes/`) — one change, one plan, even when the code lands in three repos

**It does NOT own:** anything internal to a single service. Those specs live in that service's own `openspec/`.

## Rules for agents working here
1. A change to a **contract** is a breaking-change risk for another repo. Prefer **additive** changes; a breaking change needs a new version + a migration window.
2. Every contract change must be reflected in **contract tests** on both provider and consumer before it is archived.
3. Cross-repo changes fan out to per-repo tasks — use the **same change ID** in every repo.
