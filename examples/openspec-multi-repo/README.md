# 🗂️ OpenSpec Across Multiple Repos — Scaffold

A **copyable template** for running [OpenSpec](https://github.com/Fission-AI/OpenSpec) spec-driven
development across a service split: **UI → UI Service (BFF) → Functional Service**.

Companion to the note: **[Managing OpenSpec Across Multiple Related Repos](../../notes/openspec-multi-repo.md)**.

> ⚠️ **This shows the multi-repo *layout and conventions*, not OpenSpec's own templates.**
> In your real repos, run `openspec init` to generate the canonical scaffolding for your
> OpenSpec version, then apply the structure below. Check the OpenSpec docs for exact CLI
> (Stores, cross-repo changes) — it moves fast.

## The structure

```
platform-spec/          ← THE STORE (its own repo): cross-repo source of truth
  AGENTS.md
  openspec/
    project.md          → the repos, call direction, conventions
    specs/
      checkout/         → an END-TO-END capability (spans all three repos)
      contracts/
        ui-service-api/         → ui ↔ ui-service   (provider: ui-service)
        functional-service-api/ → ui-service ↔ functional-service
    changes/
      2026-09-saved-carts/      → ONE cross-repo change, ONE plan
        proposal.md   → why + what + rollout order
        tasks.md      → fan-out tasks per repo
        specs/...     → the contract DELTA

ui/  ui-service/  functional-service/     ← each product repo
  AGENTS.md               → points at the Store + which contracts it consumes/provides
  openspec/
    project.md            → scope: LOCAL specs only
    specs/<capability>/   → what THIS repo does internally
    changes/              → local-only changes
```

## The rule that makes it work

**Local specs live with their repo; the contracts between repos live in one enforced place.**

| Kind of spec | Where | Owner |
| ------------ | ----- | ----- |
| What *one* repo does internally | that repo's `openspec/` | that team |
| End-to-end behaviour + **contracts** | the **Store** (`platform-spec`) | platform team (others: read-only) |

## Using it

1. **Copy `platform-spec/`** into a new repo (rename to taste) — this is your Store.
2. **Copy the `openspec/` + `AGENTS.md`** shape into each of your three repos.
3. Replace the placeholder capabilities with real ones; put your real schemas
   (`openapi/*.yaml`, `proto/**/*.proto`) next to the contract specs in the Store.
4. **Wire enforcement** — consumer-driven contract tests (Pact-style) in each repo's CI,
   plus `buf breaking` (proto) / an OpenAPI diff check. *A contract with no test is documentation.*
5. Point each repo's `CLAUDE.md`/`AGENTS.md` at the Store so coding agents honour the contracts.

## The worked example

`changes/2026-09-saved-carts/` is a complete cross-repo change — read it to see the pattern:

- **one** proposal covering all three repos, with an explicit **rollout order**
- **contract-first**, then **provider → consumer** (`functional-service` → `ui-service` → `ui`)
- every step **additive/backward-compatible**, so the three deploy **independently** (no lockstep)
- the **same change ID** reused in every repo, linked from one tracking epic

## Anti-patterns this layout prevents

- Duplicating a contract spec in multiple repos → drift
- A Store with no contract tests → confident fiction
- Coordinating cross-repo changes only in chat
- Breaking a contract with no compatibility window → forced lockstep deploy
