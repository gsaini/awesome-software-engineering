# Contract: ui ↔ ui-service (BFF API)

**Provider:** `ui-service` · **Consumer:** `ui` · **Version:** v1 · **Transport:** REST/JSON
**Schema of record:** `openapi/ui-service.v1.yaml` (in this repo)

## Requirements

### Requirement: The BFF SHALL expose a cart summary for the current user
#### Scenario: Fetch cart
- **WHEN** `GET /api/v1/cart` is called with a valid user token
- **THEN** it returns `{ items: [...], subtotal, currency }` for **that caller only**

### Requirement: Breaking changes SHALL NOT ship without a new version
#### Scenario: Adding a field
- **WHEN** a new optional field is added to a response
- **THEN** it is additive and `ui` continues to work unchanged (no version bump)

#### Scenario: Removing or renaming a field
- **WHEN** a field is removed, renamed, or retyped
- **THEN** a new version (`/api/v2/`) is introduced with a migration window

## Enforcement
- Consumer-driven contract tests: `ui` publishes expectations; `ui-service` CI verifies them.
