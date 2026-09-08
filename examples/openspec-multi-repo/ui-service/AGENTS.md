# AGENTS.md — ui-service

**Role:** Backend-for-Frontend (BFF). Aggregates and shapes data for the UI.

## Where specs live
- **Local specs (this repo owns):** `openspec/specs/` — orchestration, aggregation, view-model shaping
- **Cross-repo specs & contracts (read-only):** the **Store** at `platform-spec`
  - Contract this repo **consumes:** `specs/contracts/functional-service-api/` (as consumer)
  - Contract this repo **provides:** `specs/contracts/ui-service-api/` (as provider)

## Rules for agents working here
1. **Never change a contract from this repo.** Contracts are owned by `platform-spec`.
   If a change requires a new/changed interface, open a **cross-repo change** in the Store first.
2. Implement **against the contract** in the Store — treat it as the boundary you must honour.
3. Contract tests run in CI. If they fail, the interface — not the test — is what to fix.
4. For a change spanning repos, reuse the **same change ID** as the Store's change.
