# 📨 Message Queues & Event-Driven Architecture — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~22 min · **Prerequisites:** services calling services; pairs with the [API Design](api-design.md), [Caching](caching-strategies.md), and [Concurrency](concurrency-parallelism.md) notes.

A **message queue** lets one service hand work to another **without waiting for it**. That single change — from synchronous request/response to asynchronous messaging — is what makes systems decoupled, resilient, and elastic. **Event-driven architecture** takes it further: services announce *what happened* and anyone interested reacts.

> **The core reframe:** a synchronous call couples two services in **time** (both must be up, right now) and in **space** (the caller must know the callee). A message broker breaks both. The cost is that you trade immediate, simple consistency for **eventual** consistency and a new class of failure modes — duplicates, reordering, and messages that never get processed.

## Table of contents

- [1. Why async: what queues buy you](#1-why-async-what-queues-buy-you)
- [2. Queues vs. logs](#2-queues-vs-logs)
- [3. Messaging patterns](#3-messaging-patterns)
- [4. Delivery guarantees](#4-delivery-guarantees)
- [5. Ordering & partitioning](#5-ordering--partitioning)
- [6. Failure handling: retries, DLQs, poison messages](#6-failure-handling-retries-dlqs-poison-messages)
- [7. Events vs. commands (and the dual-write problem)](#7-events-vs-commands-and-the-dual-write-problem)
- [8. Backpressure & consumer scaling](#8-backpressure--consumer-scaling)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. Why async: what queues buy you

- **Decoupling** — the producer doesn't know or care who consumes, or whether they're up right now.
- **Buffering / load levelling** — a traffic spike fills the queue instead of crushing the consumer. The queue absorbs burst; consumers drain at their own pace. (Compare the **leaky bucket** from [rate limiting](load-balancing-rate-limiting.md) — same smoothing instinct.)
- **Resilience** — if the consumer is down, messages wait rather than erroring. Work resumes on recovery.
- **Elastic scaling** — add consumers to drain faster; queue depth is a natural autoscaling signal.
- **Responsiveness** — return to the user immediately, do the slow work (email, thumbnails, billing) in the background.

The costs are real: **eventual consistency**, harder debugging (no single stack trace — you need [distributed tracing](observability.md)), operational overhead, and the delivery hazards in §4.

---

## 2. Queues vs. logs

The most important architectural distinction in this space — two genuinely different models:

| | **Queue** (RabbitMQ, SQS) | **Log** (Kafka, Pulsar, Kinesis) |
| - | ------------------------- | -------------------------------- |
| **Model** | Messages are **consumed and removed** | An **append-only, immutable** ordered record |
| **After reading** | Message is gone (acked) | Message **stays** for a retention period |
| **Consumers** | Compete for messages; each handled once | Each consumer group tracks its **own offset** |
| **Replay** | ❌ Once it's gone, it's gone | ✅ Rewind the offset and reprocess history |
| **Best at** | Task/work distribution | Event streaming, multiple independent readers, replay |

The log's superpower is that **reading is not destructive**. Five different services can consume the same event stream at their own pace, a new service can join and replay from the beginning, and you can reprocess after fixing a bug. That's why Kafka underpins CDC and event sourcing.

> This is the idea from **["The Log" (Jay Kreps)](../papers/)** already in your papers index: an ordered, immutable log is the universal backbone for data integration — the same abstraction as a database's write-ahead log, promoted to an architectural primitive.

---

## 3. Messaging patterns

- **Point-to-point (work queue)** — one message → exactly one consumer from a competing pool. Task distribution.
- **Publish/subscribe** — one message → *every* interested subscriber. Broadcast/notification.
- **Topics & routing** — subscribers filter by topic/routing key/attributes so they only get what they care about.
- **Request/reply over messaging** — async RPC via a correlation ID and a reply queue. Works, but if you truly need a synchronous answer, [gRPC/HTTP](api-design.md) is usually the better tool.
- **Fan-out / fan-in** — one event triggers N parallel workers; results are aggregated. (Same shape as `parallel()` in the [concurrency](concurrency-parallelism.md) note, across machines.)
- **Competing consumers** — the standard scaling pattern: N workers pull from one queue.

---

## 4. Delivery guarantees

The single most consequential design decision:

| Guarantee | Meaning | Reality |
| --------- | ------- | ------- |
| **At-most-once** | Fire and forget; may be lost | Only for genuinely disposable data (metrics samples) |
| **At-least-once** | Never lost, **may be duplicated** | **The practical default** — what most brokers give you |
| **Exactly-once** | Delivered precisely once | Extremely hard end-to-end; often a marketing claim |

**Why duplicates are unavoidable:** the consumer processes a message, then crashes *before* acking. The broker never heard the ack, so it redelivers. There's no way to make "do the work" and "record that I did it" a single atomic step across two systems.

**The practical answer: at-least-once delivery + idempotent consumers.** Design the handler so processing the same message twice has the same effect as once — via an idempotency key, a dedup table of processed message IDs, or naturally idempotent operations (`SET status='paid'` rather than `balance -= 10`).

> "Exactly-once" systems (Kafka transactions) achieve **effectively-once** *within their own boundary* by making the read-process-write atomic. The moment you touch an external system (send an email, charge a card), you're back to at-least-once + idempotency. **You cannot un-send an email.**

This is the **fifth appearance of idempotency** in this library — DB retry-on-serialization-failure, loop restart, concurrency let-it-crash, API `Idempotency-Key`, and now consumers. Reliable systems assume operations *will* repeat and make that safe.

---

## 5. Ordering & partitioning

**Global ordering doesn't scale** — it means one consumer, no parallelism. So brokers give **ordering within a partition** (Kafka) or per-queue-with-single-consumer, and you choose what to partition by.

**Partition by the key whose order matters** — usually the entity ID:

```
partition = hash(order_id) % num_partitions
→ every event for order_42 lands in one partition, processed in order,
  while different orders process fully in parallel.
```

That's **consistent hashing / key-based routing** again — the same mechanism as sharded caches and stateful load balancing. Pick the key carefully:
- Too coarse (e.g. partition by country) → **hot partitions**, uneven load.
- Wrong key → events that must be ordered land in different partitions and race.

Related: **consumer groups** — each partition is owned by exactly one consumer in a group, so parallelism is capped at the partition count. **Rebalancing** reassigns partitions when consumers join/leave.

---

## 6. Failure handling: retries, DLQs, poison messages

A **poison message** is one that fails every time (malformed, references deleted data). Without a plan it retries forever, blocking the partition and burning resources.

The standard machinery:
- **Retries with exponential backoff + jitter** — transient failures usually resolve. Jitter prevents synchronized retry storms (the *third* place jitter has shown up, after cache stampedes and retry storms).
- **Retry limit** — after N attempts, stop.
- **Dead-letter queue (DLQ)** — move the failed message aside so the main flow continues. **Alert on DLQ depth**, and make it drainable after a fix; a DLQ nobody monitors is a silent data-loss bucket.
- **Idempotent handlers** — because retries mean reprocessing (§4).

> Design rule: **a single bad message must never be able to halt the pipeline.** Retry, then quarantine, then alert — the same shape as quarantining a [flaky test](testing-strategy.md) so it stops blocking everyone.

---

## 7. Events vs. commands (and the dual-write problem)

A subtle but clarifying distinction:

- **Command** — "do this" (`ChargeCard`). Directed at *one* handler, expects it to happen, imperative.
- **Event** — "this happened" (`OrderPlaced`). A statement of **fact about the past**, broadcast; the publisher doesn't care who reacts.

Event-driven systems favor **events**, because they invert the dependency: the order service doesn't need to know that billing, email, and analytics exist. Adding a consumer requires **no change to the producer** — that's the decoupling payoff. (Name events in the **past tense** — it keeps you honest that they're facts, not requests.)

**The dual-write problem** — the classic bug in this architecture:

```
db.save(order)              # ✅ succeeds
broker.publish(OrderPlaced) # ❌ crashes here
→ The order exists but nobody was told. Systems diverge, permanently.
```

You cannot atomically write to a database *and* a broker (no distributed transaction). Two standard fixes:

- **Transactional outbox** — in **one DB transaction**, write the order **and** an `outbox` row. A separate relay reads the outbox and publishes. The DB transaction guarantees both-or-neither; the relay guarantees at-least-once publish.
- **Change Data Capture (CDC)** — tail the database's replication log (Debezium) and publish those changes as events. The DB write *is* the event source, so they can't diverge.

> CDC is exactly the event-driven [cache invalidation](caching-strategies.md) mechanism from that note — now with a name and a reason it's trustworthy.

**Event sourcing** (related, more radical): store the **sequence of events** as the source of truth and derive current state by replaying them. Gives a perfect audit log and time travel, at the cost of real complexity (schema evolution, snapshots, rebuilds). Powerful — but don't adopt it just because you have a queue.

---

## 8. Backpressure & consumer scaling

**Queue depth is your key signal.** Growing depth means consumers can't keep up:
- **Scale out consumers** — but remember parallelism is capped by **partition count** in a log.
- **Apply backpressure** — slow the producer rather than accumulating unbounded work.
- **Load shed** — drop low-priority messages under extreme load (from the [rate-limiting](load-balancing-rate-limiting.md) note).

**Consumer lag** (how far behind the log head you are) is the metric to alert on — it's the [SLI](observability.md) for an async pipeline, the async equivalent of latency.

> An unbounded queue is a **latency problem disguised as a capacity solution**: it doesn't fix the mismatch, it just hides it until messages are hours stale.

---

## 9. Best practices & anti-patterns

**Do**
- **Assume at-least-once; make consumers idempotent.** Non-negotiable.
- **Use a log (Kafka) when you need replay or multiple independent consumers**; a queue for straightforward task distribution.
- **Partition by the key whose ordering matters**; watch for hot partitions.
- **Retry with backoff + jitter, cap attempts, then DLQ** — and alert on DLQ depth.
- **Solve dual-write with an outbox or CDC** — never "save then publish" and hope.
- **Publish events as past-tense facts**; version your event schemas (additive changes, like [API evolution](api-design.md)).
- **Monitor consumer lag and queue depth**; trace across the async hop with a propagated trace ID.

**Avoid**
- **Assuming exactly-once** across an external side effect.
- **Requiring global ordering** — it kills parallelism; partition instead.
- **Unbounded retries on a poison message** — it blocks the partition forever.
- **An unmonitored DLQ** — silent data loss.
- **Using a queue where a synchronous call is simpler** — async adds real cost; if the caller must have the answer now, just call.
- **Event sourcing by default** — adopt it for a reason, not because it's fashionable.
- **Fat events carrying huge payloads** — prefer an ID + fetch, or a documented, versioned schema.

---

## 10. Go deeper

Related material in this library:

- 📄 **[The Log — Jay Kreps](../papers/)** and **[Kafka](../papers/)** — the foundational reading for §2; the log as universal backbone.
- 📝 **[Caching Strategies](caching-strategies.md)** — CDC-based invalidation is the event-driven pattern from §7.
- 📝 **[API Design](api-design.md)** — sync vs. async boundaries, idempotency keys, and schema evolution.
- 📝 **[Load Balancing & Rate Limiting](load-balancing-rate-limiting.md)** — leaky-bucket smoothing, backpressure, load shedding, backoff+jitter.
- 📝 **[Observability](observability.md)** — consumer lag as an SLI; tracing across async hops.
- 📗 **[Designing Data-Intensive Applications — Kleppmann](../books/)** — **Ch. 11 "Stream Processing"** is the definitive treatment of everything here.
- 📗 **[Release It! — Nygard](../books/)** — failure handling and stability patterns in production.

### Primary references

- Jay Kreps, *"The Log: What every software engineer should know about real-time data's unifying abstraction"* (2013).
- Kleppmann, *Designing Data-Intensive Applications*, Ch. 11.
- Hohpe & Woolf, *Enterprise Integration Patterns* (2003) — the canonical messaging-pattern catalog.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
