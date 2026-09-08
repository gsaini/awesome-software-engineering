# Project: functional-service

## Purpose
Domain/business service. Owns domain logic and persistence.

## Scope of these specs
This `openspec/` covers **only what is internal to `functional-service`** — domain capabilities, business rules, persistence.

End-to-end capabilities and the contracts between services live in the **Store**
(`platform-spec`), which this repo references read-only. See `AGENTS.md`.

## Conventions
- Change IDs: `YYYY-MM-<slug>` — reuse the Store's ID for cross-repo work.
- Contract changes are proposed in the Store, never here.
