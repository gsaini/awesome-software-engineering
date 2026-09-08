# Tasks: 2026-09-saved-carts

Use this same change ID (`2026-09-saved-carts`) for the per-repo changes and PRs,
and link them all from the tracking epic.

## 0. Contract (this repo — platform-spec)
- [ ] Add `savedAt` to the `ui-service-api` contract spec (additive)
- [ ] Add `GetSavedCart` / `SaveCart` to the `functional-service-api` contract spec
- [ ] Update `openapi/ui-service.v1.yaml` and `proto/functional/v1/cart.proto`

## 1. functional-service (provider) — deploy first
- [ ] Persist carts per user; `SaveCart` idempotent on `(user_id, idempotency_key)`
- [ ] Implement `GetSavedCart` / `SaveCart`
- [ ] Provider-side contract tests pass; `buf breaking` clean

## 2. ui-service (BFF) — deploy second
- [ ] Call `GetSavedCart` / `SaveCart`
- [ ] Return `savedAt` on `GET /api/v1/cart` (additive)
- [ ] Verify published consumer contracts still pass

## 3. ui (consumer) — deploy last
- [ ] Hydrate cart on load; show the "restored" hint
- [ ] Handle a cart containing unavailable SKUs
- [ ] Publish updated consumer contract expectations

## 4. Close out
- [ ] All contract tests green on both sides
- [ ] Archive this change into `specs/` (`openspec archive`)
