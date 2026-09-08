# Checkout Specification (cross-cutting)

## Purpose
End-to-end checkout behaviour. Spans `ui`, `ui-service`, and `functional-service`,
so it lives here rather than in any single repo.

## Requirements

### Requirement: The system SHALL let a signed-in shopper complete a purchase
#### Scenario: Successful checkout
- **WHEN** a signed-in shopper with a non-empty cart submits valid payment details
- **THEN** an order is created, payment is captured, and a confirmation is shown

#### Scenario: Payment declined
- **WHEN** payment capture is declined
- **THEN** no order is created and the shopper is shown a recoverable error

### Requirement: Checkout SHALL be idempotent per submission
#### Scenario: Duplicate submission
- **WHEN** the same checkout submission is retried with the same idempotency key
- **THEN** exactly one order exists and the original result is returned
