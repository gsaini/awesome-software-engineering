# Project: Acme Platform (Store)

## Purpose
Cross-repo source of truth for product capabilities and the contracts between services.
Product repos reference this **read-only**; the platform team owns it.

## The repos
| Repo | Role | Owns (local specs) |
| ---- | ---- | ------------------ |
| `ui` | Browser client | UI capabilities, view logic |
| `ui-service` | BFF — aggregates for the UI | Orchestration, view-model shaping |
| `functional-service` | Domain/business service | Domain capabilities, persistence |

## Call direction
`ui` → `ui-service` (BFF) → `functional-service`

## Contracts (owned here)
- `specs/contracts/ui-service-api/` — the `ui` ↔ `ui-service` interface
- `specs/contracts/functional-service-api/` — the `ui-service` ↔ `functional-service` interface

## Conventions
- **Change IDs:** `YYYY-MM-<slug>` — reuse the *same ID* across every repo a change touches.
- **Contract evolution:** additive first. Breaking = new version + migration window (never a lockstep deploy).
- **Enforcement:** contract tests (Pact-style) run in each repo's CI. A spec with no test is documentation, not a contract.
