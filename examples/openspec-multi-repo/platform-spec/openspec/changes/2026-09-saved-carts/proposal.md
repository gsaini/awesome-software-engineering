# Change: 2026-09-saved-carts

**Type:** cross-repo · **Repos:** `functional-service`, `ui-service`, `ui`
**Status:** proposed

## Why
Shoppers lose their cart between devices. Persisting carts server-side is the top
requested feature and is expected to reduce checkout abandonment.

## What changes
- **Contract (`ui-service-api`)**: add a `savedAt` field to the cart response — **additive, no version bump**.
- **Contract (`functional-service-api`)**: add `GetSavedCart` / `SaveCart` RPCs.
- **functional-service**: persist carts per user; idempotent `SaveCart`.
- **ui-service**: read/write the saved cart; keep the existing response shape valid.
- **ui**: hydrate the cart on load; a "restored" hint.

## Rollout order (contract-first, provider → consumer)
1. Merge the **contract delta** here (this change).
2. `functional-service` implements + deploys (provider).
3. `ui-service` implements + deploys (consumer of FS, provider to UI).
4. `ui` implements + deploys (consumer).

Each step is **backward-compatible**, so the three deploy independently — no lockstep.

## Out of scope
- Guest (signed-out) cart persistence.
- Cross-tenant cart sharing.

## Risks
- A saved cart may reference an unavailable SKU → the UI must handle a partially valid cart.
