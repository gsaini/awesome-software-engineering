# Delta: Contract ui ↔ ui-service — saved carts

> Delta spec for change `2026-09-saved-carts`. Merged into
> `specs/contracts/ui-service-api/spec.md` on archive.

## ADDED Requirements

### Requirement: The BFF SHALL return a persisted cart for a signed-in shopper
#### Scenario: Cart restored on a new device
- **WHEN** a signed-in shopper calls `GET /api/v1/cart` on a device with no local cart
- **THEN** the response contains their most recently saved cart and a `savedAt` timestamp

#### Scenario: Saved cart contains an unavailable item
- **WHEN** a saved cart references a SKU that is no longer purchasable
- **THEN** the item is returned flagged as unavailable rather than omitted silently

## Compatibility
`savedAt` is **optional and additive** — existing `ui` clients ignore it. No version bump.
