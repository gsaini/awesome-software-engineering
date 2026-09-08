# Project: ui

## Purpose
Browser client. Consumes the BFF; owns presentation and view logic.

## Scope of these specs
This `openspec/` covers **only what is internal to `ui`** — UI capabilities, view logic, client-side state.

End-to-end capabilities and the contracts between services live in the **Store**
(`platform-spec`), which this repo references read-only. See `AGENTS.md`.

## Conventions
- Change IDs: `YYYY-MM-<slug>` — reuse the Store's ID for cross-repo work.
- Contract changes are proposed in the Store, never here.
