# ⚖️ Load Balancing & Rate Limiting — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~20 min · **Prerequisites:** basic client–server + HTTP; pairs with the [Caching](caching-strategies.md) and [API Design](api-design.md) notes.

Two traffic-management foundations that sit in front of almost every real system. **Load balancing** spreads incoming work across many servers so no single one is overwhelmed. **Rate limiting** caps how much work any one client can send so nobody can overwhelm *you*. One is about **distribution**; the other about **protection**.

> **The shared idea:** both are about controlling flow under load. A load balancer decides *where* a request goes; a rate limiter decides *whether* it goes at all. Together they're how a system stays up when traffic spikes or an abuser shows up.

## Table of contents

- [1. Load balancing: the job](#1-load-balancing-the-job)
- [2. L4 vs. L7](#2-l4-vs-l7)
- [3. Balancing algorithms](#3-balancing-algorithms)
- [4. Health checks & sessions](#4-health-checks--sessions)
- [5. Rate limiting: the job](#5-rate-limiting-the-job)
- [6. The rate-limiting algorithms](#6-the-rate-limiting-algorithms)
- [7. Distributed rate limiting](#7-distributed-rate-limiting)
- [8. Related resilience patterns](#8-related-resilience-patterns)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. Load balancing: the job

A **load balancer (LB)** sits between clients and a pool of servers, distributing requests so you can **scale horizontally** (add more boxes) and **tolerate failures** (route around dead ones). It gives you:

- **Scalability** — spread load across N servers instead of one bigger one.
- **Availability** — detect and skip unhealthy servers (no user hits a dead box).
- **A stable front** — clients see one address; servers come and go behind it.

LBs show up at every tier: a global one (DNS/anycast) picks a region, an edge one picks a data center, an internal one picks a service instance.

---

## 2. L4 vs. L7

The single most important distinction — *which OSI layer* the LB operates at:

| | **L4 (transport)** | **L7 (application)** |
| - | ------------------ | -------------------- |
| **Sees** | IP + TCP/UDP ports | Full HTTP: URL, headers, cookies, body |
| **Decides by** | Connection tuple | Request content (path, host, method) |
| **Can do** | Fast, cheap, protocol-agnostic forwarding | Path routing, TLS termination, header rewrites, per-route rules |
| **Cost** | Very low latency/overhead | More CPU (parses each request) |
| **Example** | AWS NLB, IPVS | Nginx, HAProxy, Envoy, AWS ALB |

**Rule of thumb:** L4 when you just need to spread raw connections fast (and for non-HTTP protocols); **L7 when you need to route on content** (`/api` → service A, `/img` → service B), terminate TLS, or do anything HTTP-aware. Most application traffic wants L7.

---

## 3. Balancing algorithms

How the LB picks a server:

- **Round robin** — next server in rotation. Simple; assumes all servers and all requests are equal (often false).
- **Weighted round robin** — bigger servers get proportionally more. Good for heterogeneous hardware.
- **Least connections** — send to the server with the fewest active connections. Adapts to uneven request durations — a strong default for L7.
- **Least response time** — least connections *plus* lowest latency. Even more adaptive.
- **Hash / IP hash** — hash a key (client IP, URL) → server. Gives **stickiness** (same key → same server), useful for cache locality.
- **Consistent hashing** — the important one at scale: when a server is added/removed, only `~1/N` of keys remap instead of nearly all. Essential for sharded caches and stateful pools (and the mechanism behind Dynamo-style systems in [the papers index](../papers/)).
- **Power of two choices** — pick two servers at random, send to the less loaded of the two. Cheap, and provably avoids the worst hot-spots far better than pure random.

> Rule of thumb: **least connections** for general web traffic; **consistent hashing** when the backend is stateful/cache-bearing and you care about stability under scaling.

---

## 4. Health checks & sessions

**Health checks** are what make an LB more than a splitter:
- **Passive** — watch real traffic; eject a server after N failures.
- **Active** — periodically probe a `/health` endpoint; only route to servers that pass.
- **Deep vs. shallow** — a shallow check says "process is up"; a deep one verifies dependencies (DB reachable). Deep checks catch more, but a too-strict one can eject the whole fleet during a shared blip — tune carefully.

**Sessions / stickiness** — HTTP is stateless, but some apps keep per-user server state (in-memory sessions). Options, best first:
1. **Stateless servers** — put session state in a shared store (Redis) or a signed token, so *any* server can handle *any* request. The clean answer; enables free rebalancing.
2. **Sticky sessions** (cookie/IP affinity) — pin a user to one server. Simple, but breaks even load and loses state when that server dies. A crutch, not a design.

> This is the [concurrency](concurrency-parallelism.md) "prefer no shared mutable state" lesson at the fleet level: stateless instances are to load balancing what immutability is to threads — they remove a whole class of problems.

---

## 5. Rate limiting: the job

A **rate limiter** caps how many requests a client can make in a window. Why it's essential:

- **Protect the backend** from overload (accidental or deliberate).
- **Fairness** — one noisy client can't starve everyone else.
- **Abuse/DoS defense** — throttle brute-force, scraping, credential stuffing (ties to [security](security-fundamentals.md)).
- **Cost control** — bound spend on metered downstreams (and LLM APIs).

The correct HTTP response when a client exceeds the limit is **`429 Too Many Requests`**, ideally with a **`Retry-After`** header and `RateLimit-*` headers telling the client its budget — so well-behaved clients can back off instead of hammering.

---

## 6. The rate-limiting algorithms

The classics, in increasing sophistication:

**Fixed window** — count requests per calendar window (e.g. 100/minute), reset at the boundary.
- ✅ Trivial. ❌ **Boundary burst**: a client can send 100 at 12:00:59 and 100 at 12:01:00 — 200 in two seconds.

**Sliding window log** — store a timestamp per request; count those within the last 60s.
- ✅ Exact. ❌ Memory grows with request volume (a timestamp each).

**Sliding window counter** — approximate the sliding window by weighting the current + previous fixed windows. The common production compromise — smooth, cheap, good enough.

**Token bucket** — a bucket holds up to *B* tokens, refilled at *R* tokens/sec; each request spends one; empty bucket → reject.
- ✅ **Allows controlled bursts** (up to *B*) while bounding the sustained rate (*R*). The most widely used — it matches how humans actually use apps (bursty).

**Leaky bucket** — requests enter a queue that drains at a fixed rate; overflow is dropped.
- ✅ **Smooths** output to a constant rate (good for protecting a fragile downstream). ❌ No bursts; adds queueing latency.

> Rule of thumb: **token bucket** when you want to allow bursts (most APIs); **leaky bucket** when the downstream needs a steady, smoothed flow. **Sliding-window counter** when you want simple, fair per-window limits without the boundary-burst bug.

The token-bucket vs. leaky-bucket contrast mirrors [backpressure](#8-related-resilience-patterns): shape the traffic vs. shed the traffic.

---

## 7. Distributed rate limiting

Limiting per-server is easy; the real problem is a **global** limit across many instances. If each of 10 servers allows 100/min locally, your "100/min" is actually 1000/min.

Approaches:
- **Centralized counter** (Redis) — every instance does an atomic `INCR`/token-bucket check against a shared store. Accurate, but adds a network hop and makes Redis a hot path/SPOF. Use atomic **Lua scripts** to avoid the check-then-set race (a [concurrency](concurrency-parallelism.md) race at the datastore layer).
- **Local + sync** — each instance limits locally against its *share* of the budget, periodically reconciling. Lower latency, approximate.
- **Sticky routing** — route a given key to a fixed instance (consistent hashing) so its counter is local and authoritative.

> The trade-off is the familiar one: **accuracy vs. latency/coupling.** A precise global limit costs a shared round-trip; an approximate local one is fast but can drift.

---

## 8. Related resilience patterns

Rate limiting rarely travels alone — the surrounding toolkit:

- **Throttling** — slow requests down (queue/delay) rather than reject outright.
- **Backpressure** — signal upstream to *slow down* when you're saturated, instead of accepting work you can't handle (the leaky-bucket instinct).
- **Load shedding** — under extreme load, drop low-priority requests early to keep the system alive for the rest. Fail some to save all.
- **Circuit breaker** — stop calling a failing dependency for a cooldown so it can recover and you fail fast (from *Release It!*).
- **Retries with exponential backoff + jitter** — retry failed calls, but back off and randomize so you don't synchronize a thundering herd (the same **jitter** fix as [cache stampede/avalanche](caching-strategies.md)).

> ⚠️ **Retries and rate limits interact dangerously.** Naïve retries against a rate-limited/overloaded service create a **retry storm** that deepens the outage. Always: backoff + jitter, a retry budget/cap, and respect `Retry-After`.

---

## 9. Best practices & anti-patterns

**Do**
- **Use L7** when you need content routing or TLS termination; L4 for raw speed / non-HTTP.
- **Default to least-connections**; use **consistent hashing** for stateful/cache backends.
- **Make servers stateless** — externalize session state; sticky sessions are a last resort.
- **Health-check with the right depth** — deep enough to catch real failures, not so strict it ejects the fleet on a blip.
- **Return `429` + `Retry-After` + `RateLimit-*` headers** so clients can behave.
- **Pick the limiter for the shape:** token bucket for bursts, leaky bucket for smoothing.
- **Pair rate limits with backoff+jitter and circuit breakers** to prevent retry storms.

**Avoid**
- **Fixed-window limits** where the boundary burst matters.
- **Sticky sessions as the primary design** (uneven load, state loss on failure).
- **Per-instance limits** when you promised a global limit (10× your intended rate).
- **Non-atomic distributed counters** (check-then-set race → over-admission).
- **Naïve retries** into an overloaded service (retry storm).
- **A single LB with no redundancy** — the LB itself must not be the SPOF.

---

## 10. Go deeper

Related material in this library:

- 📝 **[Caching Strategies](caching-strategies.md)** — consistent hashing (sharded caches), and jitter as the shared fix for stampedes and retry storms.
- 📝 **[API Design](api-design.md)** — `429`/`Retry-After` semantics and rate limiting as a cross-cutting API concern.
- 📝 **[Observability](observability.md)** — you can't tune limits or balancing you can't see; watch saturation (the USE method) and per-backend latency.
- 📝 **[Concurrency & Parallelism](concurrency-parallelism.md)** — stateless instances = "no shared mutable state" at the fleet level; distributed counters are atomics one layer up.
- 📄 **[Dynamo](../papers/)** — consistent hashing in a real distributed data store.
- 📗 **[Release It! — Michael Nygard](../books/)** — circuit breakers, bulkheads, and load shedding in production.

### Primary references

- Mitzenmacher, *"The Power of Two Choices in Randomized Load Balancing,"* 2001.
- Karger et al., *"Consistent Hashing and Random Trees,"* 1997.
- [System Design Primer — load balancing & rate limiting](https://github.com/donnemartin/system-design-primer).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
