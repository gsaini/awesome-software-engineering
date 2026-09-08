# Contract: ui-service ↔ functional-service

**Provider:** `functional-service` · **Consumer:** `ui-service` · **Version:** v1 · **Transport:** gRPC
**Schema of record:** `proto/functional/v1/*.proto` (in this repo)

## Requirements

### Requirement: The service SHALL expose cart and order operations
#### Scenario: Create order
- **WHEN** `CreateOrder` is called with a valid cart and idempotency key
- **THEN** exactly one order is created; a retry with the same key returns the same order

### Requirement: Protobuf field numbers SHALL never be reused
#### Scenario: Removing a field
- **WHEN** a field is retired
- **THEN** its tag is `reserved` and never reassigned

## Enforcement
- Proto backward-compatibility check (e.g. `buf breaking`) in `functional-service` CI.
- Contract tests verify `ui-service`'s expectations against the provider.
