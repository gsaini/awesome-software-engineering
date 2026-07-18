# 🔌 API Design (REST / gRPC / GraphQL) — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~22 min · **Prerequisites:** HTTP basics; you've called an API before.

An API is a **contract** between a provider and its consumers. Unlike internal code, you often can't refactor it freely — other people depend on it, sometimes forever. So API design is really about **designing a contract you can live with, evolve, and not break.**

> **The reframe:** internal code optimizes for *change*; a public API optimizes for *stability*. Once someone integrates, your mistakes are permanent until a major version. Design the interface as if you can't take it back — because mostly you can't.

## Table of contents

- [1. The three styles at a glance](#1-the-three-styles-at-a-glance)
- [2. REST done properly](#2-rest-done-properly)
- [3. gRPC](#3-grpc)
- [4. GraphQL](#4-graphql)
- [5. Choosing between them](#5-choosing-between-them)
- [6. Cross-cutting concerns](#6-cross-cutting-concerns)
- [7. Versioning & evolution](#7-versioning--evolution)
- [8. Errors, idempotency & pagination](#8-errors-idempotency--pagination)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. The three styles at a glance

| | **REST** | **gRPC** | **GraphQL** |
| - | -------- | -------- | ----------- |
| **Model** | Resources + HTTP verbs | Remote procedure calls | A typed query graph |
| **Transport** | HTTP/1.1 or 2, JSON | HTTP/2, Protobuf (binary) | Usually HTTP, JSON; single endpoint |
| **Contract** | OpenAPI (optional) | `.proto` (mandatory) | SDL schema (mandatory) |
| **Shape of data** | Server decides per endpoint | Server decides per method | **Client** decides per query |
| **Best at** | Public APIs, CRUD, caching | Internal service-to-service | Aggregating many sources for varied clients |
| **Weak at** | Over/under-fetching | Browser support, human-readability | HTTP caching, cost control |

There's no "best" — each optimizes a different axis. §5 is the decision guide.

---

## 2. REST done properly

REST models the world as **resources** (nouns) manipulated with **HTTP methods** (verbs). Most "REST" APIs are really just JSON-over-HTTP; a few principles separate a good one:

**Resources are nouns; methods are verbs.**

```
GET    /users            # list
POST   /users            # create
GET    /users/42         # read one
PUT    /users/42         # replace
PATCH  /users/42         # partial update
DELETE /users/42         # delete
GET    /users/42/orders  # nested resource
```
❌ `POST /createUser`, `GET /getUser?id=42` — verbs in the path are the #1 REST smell.

**Use HTTP method semantics correctly** — this is where correctness and caching live:

| Method | Safe? (no change) | Idempotent? (repeat = same effect) |
| ------ | :---------------: | :--------------------------------: |
| GET | ✅ | ✅ |
| PUT | ❌ | ✅ (sets to a value) |
| DELETE | ❌ | ✅ (already-gone stays gone) |
| POST | ❌ | ❌ (creates each time) |
| PATCH | ❌ | not necessarily |

**Use status codes honestly:** `200/201/204` success, `400` bad request, `401` unauthenticated, `403` unauthorized, `404` not found, `409` conflict, `422` validation, `429` rate-limited, `5xx` server. Don't return `200 {"error": ...}` — it lies to every client, proxy, and cache.

**Richardson Maturity Model** (a ladder, not a mandate): L0 one endpoint → L1 resources → L2 HTTP verbs & status codes (where most good APIs sensibly stop) → L3 **HATEOAS** (responses embed links to next actions; rarely worth it in practice).

**REST's big win: HTTP caching** — `GET`s are cacheable by browsers, CDNs, and proxies via `Cache-Control`/`ETag`. This connects straight to your [caching note](caching-strategies.md); neither gRPC nor GraphQL gets this for free.

---

## 3. gRPC

A high-performance **RPC** framework: you call a remote method as if it were local. Contract-first via **Protocol Buffers**:

```proto
service UserService {
  rpc GetUser (GetUserRequest) returns (User);
  rpc ListUsers (ListUsersRequest) returns (stream User);  // server streaming
}
message User { int64 id = 1; string email = 2; }
```

Why it's fast and safe:
- **Protobuf** is a compact **binary** format — smaller and faster to (de)serialize than JSON.
- **HTTP/2** underneath: multiplexed streams, header compression, and **four call types** — unary, server-streaming, client-streaming, and **bidirectional streaming**.
- **Code generation** from the `.proto` gives you typed client + server stubs in every language — the contract is enforced by the compiler, not by hope.

Trade-offs: **not natively browser-friendly** (needs gRPC-Web + a proxy), payloads aren't human-readable, and it's overkill for simple public CRUD. Its home is **internal service-to-service** communication where performance and a strict schema matter.

---

## 4. GraphQL

A **query language** for APIs: the client asks for exactly the fields it wants from a single endpoint, against a typed **schema**.

```graphql
query {
  user(id: 42) {
    name
    orders(last: 3) { total, placedAt }   # exactly these fields, nothing more
  }
}
```

What it solves — the REST pain of **over-fetching** (endpoint returns more than you need) and **under-fetching** (you call 3 endpoints to build one screen). One round trip, client-shaped response. Great when **many different clients** (web, iOS, Android) need **different slices** of the same graph.

What it costs:
- **HTTP caching largely breaks** — everything is a `POST` to `/graphql`, so CDNs/browsers can't cache by URL. You push caching into the client (Apollo/Relay) and server (persisted queries, DataLoader).
- **The N+1 problem** — a naïve resolver fires one DB query per item in a list. The standard fix is **batching with DataLoader**.
- **Cost/complexity control** — a client can request a hugely expensive nested query. You need **query depth/complexity limits** and rate limiting by cost, not by request count.
- **Observability is harder** — every call is `POST /graphql`, so your [metrics/traces](observability.md) need to key on the operation name, not the URL.

---

## 5. Choosing between them

A quick decision guide:

- **Public API, third-party developers, CRUD, want HTTP caching & ubiquity** → **REST**.
- **Internal microservices, high throughput, strict contract, streaming** → **gRPC**.
- **Many client types needing different data shapes from many sources; want to kill over/under-fetching** → **GraphQL**.

They're not exclusive: a common architecture is **REST or GraphQL at the edge** (public, cacheable, browser-friendly) and **gRPC between internal services** (fast, typed). Pick per boundary, not per company.

---

## 6. Cross-cutting concerns

Independent of style, every good API needs:

- **Authentication & authorization** — usually OAuth2/OIDC bearer tokens; authorize **server-side on every request** (→ [security note](security-fundamentals.md), broken access control is OWASP #1).
- **Rate limiting** — protect the backend; return `429` + a `Retry-After` header. (Token bucket / sliding window.)
- **Input validation** — validate on an **allowlist**; never trust the client.
- **Pagination** — never return an unbounded list (§8).
- **Consistent naming & formats** — pick `snake_case` or `camelCase` and never mix; ISO-8601 timestamps in UTC; a documented money format (minor units or decimal strings, never floats).
- **Observability** — structured logs, a request/trace ID propagated through, metrics per endpoint/operation.
- **Documentation** — OpenAPI/`.proto`/SDL as the machine-readable contract; keep it in sync (ideally generated).

---

## 7. Versioning & evolution

The hardest part of APIs is changing them without breaking consumers.

**Backward-compatible ("non-breaking") changes** — safe to ship anytime:
- Adding a new endpoint / optional field / enum value (if clients tolerate unknowns).
- Making a required request field optional.

**Breaking changes** — need a new version:
- Removing/renaming a field, changing a type, making an optional field required, changing error semantics.

**How to version:**
- **REST:** URL path (`/v1/`, `/v2/`) is the most common and cache/proxy-friendly; header-based versioning is cleaner but harder to test/debug.
- **gRPC/Protobuf:** evolve in place — **never reuse or renumber a field tag**; only add new tags and reserve removed ones. This is protobuf's superpower: old and new clients coexist.
- **GraphQL:** typically **versionless** — add fields freely, and `@deprecated` old ones rather than cutting a v2.

> The golden rule: **additive change is cheap; removal is expensive.** Design fields to be optional and forward-tolerant so you rarely need a v2 at all. (Postel's law: be liberal in what you accept, conservative in what you send.)

---

## 8. Errors, idempotency & pagination

**Errors** — return a consistent, machine-readable envelope, not bare prose:

```jsonc
{ "error": { "code": "insufficient_funds",
             "message": "Balance too low for this transfer",
             "request_id": "req_a3f9",           // ties to your traces
             "details": [ /* field-level issues */ ] } }
```
A stable `code` clients can branch on beats a human-readable `message` they'll fragile-parse.

**Idempotency** — for `POST`/payments, accept an **`Idempotency-Key`** header: the server stores the first result under that key and **returns the same response** on retries, so a network retry doesn't double-charge. This directly extends the *safe-retry* thread — retries are only safe if the operation is idempotent (naturally, like `PUT`, or made so with a key).

**Pagination** — never return an unbounded collection:
- **Offset/limit** (`?offset=40&limit=20`) — simple, but slow on deep pages and **skips/duplicates rows if data shifts** mid-scroll.
- **Cursor/keyset** (`?after=<opaque_cursor>`) — stable and fast at scale; the right default for large or live datasets. Return the cursor; don't make clients construct it.

---

## 9. Best practices & anti-patterns

**Do**
- **Design the contract first** (OpenAPI/`.proto`/SDL), review it, *then* implement.
- **Nouns for resources, HTTP verbs for actions**; honest status codes.
- **Make writes idempotent** (idempotency keys for non-idempotent creates).
- **Paginate everything**; default to cursor pagination for scale.
- **Version deliberately**; prefer additive, forward-tolerant changes.
- **Return structured, coded errors** with a request/trace ID.
- **Document as a generated artifact**, kept in sync with code.

**Avoid**
- **Verbs in REST paths** (`/getUser`, `/createOrder`).
- **`200 OK` with an error body** — breaks clients, proxies, and caches.
- **Leaking internals** — DB column names, stack traces, internal IDs in the public contract.
- **Unbounded list endpoints.**
- **Breaking changes without a version bump.**
- **Chatty APIs** that force N calls to render one screen (the problem GraphQL/gRPC-streaming exist to solve).
- **Reusing a protobuf field number** — silent, dangerous data corruption.

---

## 10. Go deeper

Related material in this library:

- 📝 **[Caching Strategies](caching-strategies.md)** — HTTP caching is REST's structural advantage; `ETag`/`Cache-Control` live here.
- 📝 **[Security Fundamentals](security-fundamentals.md)** — authn/authz, rate limiting, and input validation are API concerns first.
- 📝 **[Observability](observability.md)** — per-endpoint metrics and trace-ID propagation; note the GraphQL "everything is POST /graphql" gotcha.
- 📝 **[Transactions & Isolation](database-transactions-isolation.md)** — idempotency and retries lean on the same safe-retry logic.
- 📗 **[Designing Data-Intensive Applications — Kleppmann](../books/)** — Ch. 4 on encoding/evolution (protobuf, schema compatibility) is the deep version of §7.

### Primary references

- Roy Fielding, *Architectural Styles and the Design of Network-based Software Architectures* (2000) — the dissertation that defined REST.
- [gRPC docs](https://grpc.io/docs/) and [Protocol Buffers](https://protobuf.dev/).
- [GraphQL specification & Learn](https://graphql.org/learn/).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
