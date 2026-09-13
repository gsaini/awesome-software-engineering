# 🌊 Stream Processing — A Detailed Study Note

> **Level:** 🔴 Advanced · **Reading time:** ~22 min · **Prerequisites:** the [Message Queues & Event-Driven](message-queues-event-driven.md), [Database Sharding](database-sharding-partitioning.md), and [Consistency & CAP](consistency-models-cap.md) notes. DDIA Ch. 11 is the companion.

**Stream processing** is computing over **unbounded, continuously arriving data** — transforming, aggregating, joining, and enriching events *as they happen* rather than in nightly batches. If [message queues](message-queues-event-driven.md) are the *transport* for events, stream processing is the **compute layer** on top: the thing that turns a firehose of raw events into running aggregates, enriched records, and materialised views.

> **The core reframe:** batch processing assumes the data is **finished** — you can see all of it, sort it, and compute an exact answer. A stream is **never finished**. That single difference creates every hard problem in this note: you must produce answers over data that is *incomplete*, arrives *out of order*, and may show up *late*. Stream processing is really the discipline of **deciding when you know enough to emit an answer** — and what to do when you were wrong.

## Table of contents

- [1. Batch vs. stream](#1-batch-vs-stream)
- [2. Stateless vs. stateful operators](#2-stateless-vs-stateful-operators)
- [3. Time — the hard part](#3-time--the-hard-part)
- [4. Windowing](#4-windowing)
- [5. Stream–table duality & joins](#5-streamtable-duality--joins)
- [6. Processing guarantees & exactly-once](#6-processing-guarantees--exactly-once)
- [7. Fault tolerance, state & scaling](#7-fault-tolerance-state--scaling)
- [8. Lambda vs. Kappa architecture](#8-lambda-vs-kappa-architecture)
- [9. The landscape](#9-the-landscape)
- [10. Best practices & anti-patterns](#10-best-practices--anti-patterns)
- [11. Go deeper](#11-go-deeper)

---

## 1. Batch vs. stream

| | **Batch** | **Stream** |
| - | --------- | ---------- |
| Data | **Bounded** — a finite, complete set | **Unbounded** — never complete |
| Latency | Minutes → hours | Milliseconds → seconds |
| Completeness | You have everything | Data is partial, out-of-order, sometimes late |
| Reprocessing | Just re-run the job | **Replay the log** from an offset |
| Answer | Exact | **Provisional** — correct *as of what you've seen* |

The two have largely **converged**: modern engines (Flink, Beam, Spark) treat **batch as a special case of streaming** — a bounded stream. Kafka's retained, replayable [log](message-queues-event-driven.md) is what makes this possible: reprocessing history is just reading the same stream from offset 0.

---

## 2. Stateless vs. stateful operators

The distinction that determines everything about operational difficulty:

- **Stateless** — `map`, `filter`, `flatMap`. Each event is handled independently. Trivially parallel, trivially recoverable (just replay).
- **Stateful** — `aggregate`, `count`, `reduce`, **joins**, **windows**. The operator must *remember* something across events: a running total, the contents of a window, the other side of a join.

State is what makes stream processing hard: it must be **partitioned** (so it scales), **durable** (so it survives a crash), and **consistent** with your position in the stream.

In practice, state lives in an **embedded local store** (Kafka Streams uses RocksDB) kept on the same node as the partition it serves — local reads are fast — and is made durable by a **changelog topic** or **periodic checkpoints** (§7).

> **State must be partitioned by the same key you aggregate/join on.** If your key isn't co-located, the operator can't see the data it needs. This is the **[shard key](database-sharding-partitioning.md) craft, again** — pick the key so related records land on the same task.

---

## 3. Time — the hard part

There are (at least) **three different clocks**, and conflating them is the classic bug:

| Time | Means |
| ---- | ----- |
| **Event time** | When the event **actually happened** (embedded in the record) |
| **Ingestion time** | When it entered the streaming system |
| **Processing time** | When your operator got around to it |

**Event time is what you almost always want** ("how many orders were placed in the 3pm hour?" is about when orders *happened*, not when a lagging consumer processed them). But event time forces you to handle reality: a mobile client was offline for 20 minutes, a partition lagged, a retry landed out of order.

### Watermarks

A **watermark** is the system's heuristic assertion: *"I believe I've now seen all events with timestamp ≤ T."* It's what lets a windowed aggregation decide **when to emit a result** instead of waiting forever.

```
events arrive out of order:  10:03  10:01  10:04  10:02  …
watermark advances to 10:05  ──►  "the 10:00–10:05 window can now be emitted"
   then a 10:02 event arrives  ──►  LATE DATA
```

- **Late data** (arriving after the watermark passes) is handled by an **allowed lateness / grace period** (update the already-emitted result), a **side output** (route it somewhere for reconciliation), or **dropping** it.
- The watermark is a **completeness-vs-latency dial**: advance it aggressively → low latency, more late data; advance it conservatively → higher latency, more complete results.

> That dial is the **same trade-off as a [cache TTL](caching-strategies.md) or an [isolation level](database-transactions-isolation.md)**: an explicit knob on how much incorrectness you'll accept in exchange for speed. Watermarks are staleness bounds for time.

---

## 4. Windowing

You can't aggregate an infinite stream — so you cut it into **windows**:

| Window | Shape | Use for |
| ------ | ----- | ------- |
| **Tumbling** | Fixed size, **non-overlapping** (every 5 min) | Periodic reports: "orders per 5 minutes" |
| **Hopping / sliding** | Fixed size, **overlapping** (5-min window every 1 min) | Smoothed rolling metrics, moving averages |
| **Session** | **Dynamic** — closes after a gap of inactivity | User sessions, activity bursts (no fixed length) |
| **Global** | No windowing; custom trigger | Running totals over all time |

Every event belongs to exactly one tumbling window, but to **several** hopping windows — so hopping windows multiply your state and output volume. Session windows are the most expressive and the most state-hungry (they merge as new events arrive).

---

## 5. Stream–table duality & joins

The most elegant idea in the field: **a stream and a table are two views of the same thing.**

- A **stream** is a **changelog** — the sequence of *changes* to a table.
- A **table** is a **snapshot** — the *current state* produced by collapsing that changelog (the latest value per key).

```
stream (KStream):  (user1, "login") (user1, "logout") (user2, "login") …   ← every event
table  (KTable):   user1 → "logout",  user2 → "login"                      ← latest per key
```

Kafka expresses this directly: a **compacted topic** *is* a table; `KStream` vs `KTable` are the two views. And it's the same insight as a **database's write-ahead log** and a **materialised view** — the [log-as-backbone](message-queues-event-driven.md) theme, one more time.

**Joins** follow from the duality:

- **Stream–table** — *enrichment*: join each event to the current state (order events × the customer table). The most common and cheapest join.
- **Stream–stream** — must be **windowed** (you can't wait forever for the other side): "clicks joined to impressions within 5 minutes."
- **Table–table** — materialised-view maintenance; output is another changelog.

Both sides of a join must be **co-partitioned on the join key** — same key, same partition count — or the engine has to repartition first.

---

## 6. Processing guarantees & exactly-once

| Guarantee | Meaning |
| --------- | ------- |
| **At-most-once** | May lose events; never duplicates. Rarely acceptable. |
| **At-least-once** | Never loses; **may duplicate**. The default. |
| **Exactly-once (EOS)** | Each event affects the result **once** — *within the system's boundary*. |

**How exactly-once actually works** — it is not magic, and the caveat matters:

- **Kafka Streams** wraps *read → process → write → commit offsets* in a **single transaction**, plus an idempotent producer. Either the output records *and* the offset commit land, or neither does.
- **Flink** uses **distributed checkpoints** (Chandy–Lamport barriers) plus **two-phase-commit sinks** to the same effect.

> ⚠️ **Exactly-once means "effectively once within the boundary the framework controls."** The moment you make an **external side effect** — call a payment API, send an email — you are back to at-least-once and **your handler must be idempotent**. This is *precisely* the caveat from the [durable-execution](durable-execution.md) note: *you cannot un-send an email.* **Idempotency** is, once again, the load-bearing principle.

---

## 7. Fault tolerance, state & scaling

- **Checkpointing / changelogs** — Flink snapshots operator state to durable storage periodically; Kafka Streams mirrors each state store to a compacted **changelog topic**. On failure, restore state and resume from the corresponding offset. (**Savepoints** are manual checkpoints for upgrades/migrations.)
- **Parallelism is capped by partition count** — each partition is processed by one task, so you scale by adding partitions, exactly as in the [message-queue](message-queues-event-driven.md) note. Rebalancing reassigns partitions (and their state) when instances join or leave — state migration is the expensive part.
- **Repartitioning (shuffle)** — changing the key (`groupBy` on a new field) forces data across the network into a repartition topic. It's the costliest operation; design keys to minimise it.
- **Backpressure** — when a downstream operator can't keep up, the pressure must propagate upstream rather than buffering unboundedly. **Consumer lag and watermark lag are your key [SLIs](observability.md)** — an unbounded queue is a latency problem disguised as a capacity solution.

---

## 8. Lambda vs. Kappa architecture

- **Lambda** (Marz) — run **two** paths: a batch layer for accurate/complete results and a speed layer for low-latency approximations, then merge them. Correct, but you maintain **the same logic twice** in two systems.
- **Kappa** (Kreps) — run **one** streaming path. Need to reprocess? **Replay the log** from the beginning into a new job version, then swap. Requires a durable, retained, replayable log.

Kappa won for most workloads precisely because the log makes reprocessing trivial — and because maintaining two implementations of the same business logic is a reliable source of subtle divergence.

---

## 9. The landscape

| Tool | Shape | Strength |
| ---- | ----- | -------- |
| **Kafka Streams** | A **library** embedded in your app (no cluster) | Simplest operationally if you're already on Kafka |
| **Apache Flink** | A distributed **cluster/runtime** | The **event-time & state gold standard**; true streaming, strong EOS, huge state |
| **Spark Structured Streaming** | Micro-batch heritage (+ continuous mode) | Natural if your team already lives in Spark |
| **ksqlDB** | **SQL** over Kafka streams | Fast path for simple transforms/aggregations |
| **Apache Beam** | A **portable programming model** | Write once, run on Flink/Dataflow/Spark |
| **Materialize / RisingWave** | **Streaming databases** | Incrementally-maintained materialised views, queried with SQL |

Managed options: Google **Dataflow**, Amazon **Managed Service for Apache Flink**, Confluent Cloud.

**When *not* to stream:** if minutes-to-hours latency is fine, **batch is simpler and cheaper** — fewer moving parts, exact answers, no watermark tuning. Stream because a business requirement needs freshness, not because streaming sounds modern. (Same "simplest thing that works" restraint as *don't-shard-early* and *don't-K8s-by-default*.)

---

## 10. Best practices & anti-patterns

**Do**
- **Use event time + watermarks** for anything time-based; decide your **allowed lateness** deliberately.
- **Choose the key so related data co-partitions** — it drives parallelism, join correctness, and shuffle cost.
- **Keep handlers idempotent** — exactly-once stops at the system boundary.
- **Bound your state** — TTL/retention on state stores; session windows and hopping windows grow fast.
- **Monitor consumer lag and watermark lag** as first-class SLIs; plan for backpressure.
- **Use savepoints** for upgrades so you can restore state across versions.
- **Prefer batch when latency doesn't matter.**

**Avoid**
- **Using processing time when you meant event time** — results silently change when a consumer lags.
- **Assuming "exactly-once" covers external side effects** — it does not.
- **Unbounded state** — the #1 production failure (a session window with no timeout, a growing keyspace).
- **Needless repartitioning** — rekeying mid-pipeline is the expensive operation.
- **Lambda architecture by default** — two implementations of one logic will diverge.
- **Ignoring late data** — silently dropping it makes results quietly wrong.

---

## 11. Go deeper

Related material in this library:

- 📝 **[Message Queues & Event-Driven](message-queues-event-driven.md)** — the transport beneath this; partitions, ordering, at-least-once, the log.
- 📝 **[Database Sharding & Partitioning](database-sharding-partitioning.md)** — the key/co-partitioning craft is identical.
- 📝 **[Durable Execution](durable-execution.md)** — checkpoint/replay and the exactly-once-stops-at-the-boundary caveat.
- 📝 **[Consistency & CAP](consistency-models-cap.md)** — watermarks are a completeness/latency dial, like TTLs and isolation levels.
- 📝 **[Caching](caching-strategies.md)** — materialised views and state stores are derived data with the same freshness discipline.
- 📝 **[Observability](observability.md)** — consumer lag and watermark lag as SLIs.
- 📝 **[Serialization & Schema Evolution](serialization-schema-evolution.md)** — events outlive the code that wrote them; schemas must evolve compatibly.
- 📄 **[The Log — Jay Kreps](../papers/)** and **[Kafka](../papers/)** — the foundational reading.
- 📗 **[Designing Data-Intensive Applications](../books/)** — **Ch. 11 "Stream Processing"** is the definitive treatment.

### Primary references

- Kleppmann, *Designing Data-Intensive Applications*, Ch. 11.
- Akidau et al., *"The Dataflow Model"* (2015) and *Streaming Systems* (O'Reilly) — the canonical event-time/watermark/windowing treatment.
- [Apache Flink](https://flink.apache.org/) and [Kafka Streams](https://kafka.apache.org/documentation/streams/) documentation.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
