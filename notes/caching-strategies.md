# ⚡ Caching Strategies & Invalidation — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~20 min · **Prerequisites:** basic client–server model; the [Database Indexing](database-indexing.md) note is a good companion (both are read-optimization).

A **cache** is a fast, temporary copy of data kept close to where it's needed, so you don't recompute or re-fetch it. Caching is one of the highest-leverage performance tools there is — and one of the easiest to get subtly wrong.

> *"There are only two hard things in computer science: cache invalidation and naming things."* — Phil Karlton. This note is mostly about the first one.

## Table of contents

- [1. Why (and why not) cache](#1-why-and-why-not-cache)
- [2. Where caches live](#2-where-caches-live)
- [3. Read & write patterns](#3-read--write-patterns)
- [4. Eviction policies](#4-eviction-policies)
- [5. Cache invalidation — the hard part](#5-cache-invalidation--the-hard-part)
- [6. The three classic failure modes](#6-the-three-classic-failure-modes)
- [7. Consistency & staleness](#7-consistency--staleness)
- [8. Best practices & anti-patterns](#8-best-practices--anti-patterns)
- [9. Go deeper](#9-go-deeper)

---

## 1. Why (and why not) cache

**Why:** cut latency (serve from memory, not disk/network), reduce load on the origin (DB, API, service), and lower cost. A good cache absorbs the majority of reads before they ever reach your database.

**Why not (the cost):** a cache is a **second copy of the truth**, so it can go stale. You trade **consistency** and **complexity** for speed. Every cache decision is really a decision about *how stale is acceptable*.

> The governing metric is **hit rate** — the fraction of reads served from cache. A cache with a low hit rate adds latency and complexity while delivering little; measure it before and after.

---

## 2. Where caches live

Caching happens at every layer — a **hierarchy**, each closer/faster but smaller:

| Layer | Example | Notes |
| ----- | ------- | ----- |
| **Client / browser** | HTTP cache, `Cache-Control`, service workers | Closest to the user; you control it via headers. |
| **CDN / edge** | Cloudflare, CloudFront | Caches static (and increasingly dynamic) content near users. |
| **Reverse proxy** | Nginx, Varnish | In front of your app servers. |
| **Application (in-process)** | Caffeine, an in-memory `Map`, LRU cache | Nanosecond access, but per-instance and lost on restart. |
| **Distributed cache** | **Redis**, Memcached | Shared across instances; the workhorse of app caching. |
| **Database** | buffer pool, query cache | The DB caches its own hot pages (see [indexing](database-indexing.md)). |

A request ideally gets satisfied as high up (as close to the user) as possible.

---

## 3. Read & write patterns

How your app interacts with the cache. Split into **read** and **write** strategies — you mix one of each.

### Read patterns

**Cache-aside (lazy loading)** — the default; the app manages the cache:

```
read(key):
    v = cache.get(key)
    if v is not None:  return v          # hit
    v = db.get(key)                       # miss
    cache.set(key, v, ttl)                # populate for next time
    return v
```
- ✅ Only requested data is cached; cache failure ≠ outage (you fall back to the DB).
- ❌ First read is always a miss (cold cache); app owns the caching logic.

**Read-through** — the cache library sits inline and loads from the DB itself on a miss. Same effect as cache-aside, but the loading logic lives in the cache layer, not your app.

### Write patterns

| Pattern | What happens on write | Trade-off |
| ------- | --------------------- | --------- |
| **Write-through** | Write to cache **and** DB synchronously | Cache always fresh; writes are slower (two hops). |
| **Write-behind (write-back)** | Write to cache now, flush to DB **async** | Fast writes, absorbs bursts; **risk of data loss** if the cache dies before flush. |
| **Write-around** | Write to DB only, **skip** the cache | Avoids polluting cache with write-heavy data that's rarely read; first read is a miss. |

**Refresh-ahead** — proactively refresh hot keys *before* they expire, so users rarely hit a miss. Great for predictable hot data; wasteful if predictions are wrong.

> Most systems use **cache-aside reads + write-around or explicit invalidation on write**. Write-through/back shine when you need the cache authoritative or writes bursty.

---

## 4. Eviction policies

Caches are bounded, so something must go when they fill. The policy decides what:

- **LRU (Least Recently Used)** — evict what hasn't been touched longest. The sensible default; matches temporal locality.
- **LFU (Least Frequently Used)** — evict the least-accessed. Better when popularity is stable, but slow to forget old hot items.
- **FIFO** — evict oldest inserted. Simple, ignores access patterns.
- **TTL (Time To Live)** — every entry expires after a set time regardless of use. Usually combined with LRU/LFU as an upper bound on staleness.
- **Random** — surprisingly decent and cheap; used as a fallback.

Redis exposes these directly via `maxmemory-policy` (e.g. `allkeys-lru`, `volatile-ttl`, `allkeys-lfu`).

---

## 5. Cache invalidation — the hard part

Keeping the cache honest when the underlying data changes. Main approaches, roughly increasing in freshness and effort:

1. **TTL / expiration** — let entries simply expire. Dead simple, and often enough. The TTL is your **explicit staleness bound**: a 60s TTL means "reads may be up to 60s stale." Tune per-data (prices: seconds; a country list: hours).
2. **Write invalidation (delete-on-write)** — when data changes, **delete** the cache key (don't try to update it). Next read repopulates from the DB. Deleting is safer than updating — see the race in §7.
3. **Write-through update** — update the cache in lockstep with the DB write. Freshest, but couples the write path to the cache.
4. **Versioned / namespaced keys** — embed a version in the key (`user:42:v7`). "Invalidate" by bumping the version so old keys are just abandoned (and later evicted). Avoids race-y deletes.
5. **Event-driven invalidation** — emit change events (via pub/sub or **CDC** — change-data-capture from the DB log) that fan out cache purges. Scales to many caches/services, at the cost of infrastructure.

> Rule of thumb: **start with TTLs**, add **delete-on-write** for data that must be fresher, and reach for **event-driven** invalidation only when TTLs and deletes can't meet your freshness needs.

---

## 6. The three classic failure modes

Named problems worth recognizing on sight:

### Cache stampede (thundering herd)
A hot key expires and **thousands of concurrent requests all miss at once**, hammering the DB simultaneously.
- **Mitigations:** a **lock / single-flight** so only one request recomputes while others wait; **request coalescing**; **probabilistic early expiration** (refresh slightly before TTL, jittered); **staggered TTLs**.

### Cache penetration
Requests for keys that **don't exist** always miss the cache and hit the DB every time (sometimes maliciously).
- **Mitigations:** **negative caching** (cache the "not found" result briefly); a **Bloom filter** to reject keys that definitely don't exist before touching the DB.

### Cache avalanche
**Many keys expire at the same moment** (e.g. all set with the same TTL at deploy), so the DB gets a load spike.
- **Mitigations:** add **jitter/randomness** to TTLs; warm the cache gradually; layer an in-process cache in front of the distributed one.

---

## 7. Consistency & staleness

Because a cache is a *copy*, it can disagree with the source of truth. The subtle bug is a **race in cache-aside**:

```
Thread A: db.update(x = 2)
Thread B: reads db (gets old x = 1, pre-commit timing) …
Thread B: cache.set(x, 1)         ← writes STALE value
Thread A: cache.delete(x)         ← already ran; B repopulated after
Result: cache holds x = 1 forever (until TTL), DB has x = 2.
```

Defenses:
- **Delete, don't update, on write** — and delete *after* the DB commit. Deleting shrinks (doesn't eliminate) the window; a lingering TTL caps the damage.
- **Set a TTL even on "invalidated on write" keys** — a safety net so any missed invalidation self-heals.
- **Versioned keys** sidestep the race entirely (old version is simply never read again).
- Accept that app caching is usually **eventually consistent** — design for "recently stale is OK," and keep the TTL short for data where it isn't.

> This is the same read-vs-write tension from the rest of your notes: [indexing](database-indexing.md) trades write cost for read speed; caching trades **freshness** for read speed. And a short TTL is to staleness what an [isolation level](database-transactions-isolation.md) is to anomalies — an explicit knob on how much inconsistency you'll tolerate.

---

## 8. Best practices & anti-patterns

**Do**
- **Measure hit rate** (and miss/eviction rates) — cache what's actually hot, don't guess.
- **Always set a TTL** — even on explicitly invalidated keys, as a self-healing backstop.
- **Delete on write** rather than update; delete after commit.
- **Add TTL jitter** to avoid synchronized expiry.
- **Cache the expensive stuff** — results of slow queries/computations, not trivially cheap lookups.
- **Fail open** — if the cache is down, serve from the origin, don't error.

**Avoid**
- Caching data you can't tolerate being stale without an invalidation plan.
- One global TTL for everything (→ avalanche; wrong freshness per data type).
- Caching highly personalized or rarely-reused data (low hit rate, wasted memory).
- Trusting write-behind for data you can't afford to lose.
- Treating the cache as a system of record — it's a *copy*, and it can vanish.

---

## 9. Go deeper

Related material in this library:

- 📝 **[Database Indexing](database-indexing.md)** & **[Transactions & Isolation Levels](database-transactions-isolation.md)** — the same read-vs-write and consistency trade-offs from the storage side.
- 📗 **[Designing Data-Intensive Applications — Martin Kleppmann](../books/)** — replication, consistency, and derived data (a cache is derived data).
- 🎓 **[System Design Primer](../courses/)** — has an excellent, diagram-heavy caching section with these exact patterns.
- 📄 **[The Log — Jay Kreps](../papers/)** — the backbone of event-driven (CDC) cache invalidation.

### Primary references

- [System Design Primer — Caching](https://github.com/donnemartin/system-design-primer#cache).
- Redis docs — [key eviction](https://redis.io/docs/latest/develop/reference/eviction/).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
