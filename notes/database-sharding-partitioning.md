# 🧩 Database Sharding & Partitioning — A Detailed Study Note

> **Level:** 🔴 Advanced · **Reading time:** ~22 min · **Prerequisites:** the [Database Indexing](database-indexing.md), [Transactions & Isolation](database-transactions-isolation.md), [Consistency & CAP](consistency-models-cap.md), and [Multi-Region](multi-region-deployment.md) notes. DDIA Ch. 6 is the companion.

**Partitioning** (a.k.a. **sharding**) splits one large dataset across **multiple nodes** so a system can scale **beyond a single machine's** storage and throughput. When one database can't hold the data or serve the load, you cut it into pieces — *shards* — each on its own server. This note covers how to split (the strategies), the one decision that makes or breaks it (the shard key), and the hard parts everyone underestimates (cross-shard queries, rebalancing, hotspots).

> **The core reframe:** partitioning and [replication](multi-region-deployment.md) are **orthogonal** and usually combined. *Replication* makes **copies** of data (for availability and read scale); *partitioning* **splits** data (for write and storage scale). A real large system does both — each shard is itself replicated (e.g. a Raft group). Partitioning is what you reach for when *replication alone can't help*, because the bottleneck is the sheer size or write volume that no single node can hold.

## Table of contents

- [1. Why (and why not) shard](#1-why-and-why-not-shard)
- [2. Vocabulary & vertical vs. horizontal](#2-vocabulary--vertical-vs-horizontal)
- [3. Partitioning strategies](#3-partitioning-strategies)
- [4. The shard key — the decision that matters most](#4-the-shard-key--the-decision-that-matters-most)
- [5. Hotspots & skew](#5-hotspots--skew)
- [6. Rebalancing](#6-rebalancing)
- [7. Request routing](#7-request-routing)
- [8. The hard parts](#8-the-hard-parts)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. Why (and why not) shard

**Why:** one node has limits — disk, RAM, CPU, write throughput, connection count. When your dataset or write load outgrows the biggest single machine (vertical scaling has a ceiling), you **partition to scale horizontally**: N shards ≈ N× the capacity.

**Why not (until you must):** sharding is a **big step up in complexity** — cross-shard queries, distributed transactions, rebalancing, operational burden, and a shard key that's painful to change. **Exhaust the cheaper options first:**
1. **Scale up** (a bigger box).
2. **Read replicas** ([replication](multi-region-deployment.md)) — offload reads.
3. **Caching** ([caching note](caching-strategies.md)) — absorb hot reads.
4. **Then** shard, when write volume or data size genuinely exceeds one node.

> Rule: **don't shard prematurely.** It's one of the hardest architectural changes to reverse. Shard when you have a *measured* single-node ceiling, not because it sounds scalable.

---

## 2. Vocabulary & vertical vs. horizontal

The same idea has many names — all mean "a piece of the data": **partition** (DDIA/Postgres), **shard** (MongoDB, Elasticsearch, Vitess), **region** (HBase), **tablet** (Bigtable, Spanner), **vnode** (Cassandra).

Two axes of "splitting":
- **Vertical partitioning** — split by **columns/features** (e.g. move rarely-used or large columns to a separate table/store; or split by service — users DB, orders DB). Bounded benefit.
- **Horizontal partitioning (= sharding)** — split by **rows**, distributing records across nodes by a **shard key**. This is what "sharding" means and what scales without limit.

This note is about **horizontal partitioning**.

---

## 3. Partitioning strategies

How do you decide which shard a row lives on? Four main schemes:

### Range partitioning
Assign contiguous **key ranges** to shards (A–F → shard 1, G–M → shard 2, …).
- ✅ **Efficient range scans** (`WHERE date BETWEEN …` hits one/few shards); keys stay sorted.
- ❌ **Hotspots** — sequential keys (timestamps, auto-increment IDs) send *all* new writes to one shard (the "latest" range). Used by HBase, Bigtable, Spanner (which auto-splits ranges).

### Hash partitioning
Hash the key, assign by hash → shard (`shard = hash(key) % N` or a hash range).
- ✅ **Even distribution** — spreads load well, no sequential hotspot.
- ❌ **Kills range queries** (adjacent keys scatter across shards). Used by Cassandra, DynamoDB.

### Consistent hashing
Hash keys *and* nodes onto a ring; a key belongs to the next node clockwise. Adding/removing a node **remaps only ~1/N of keys** instead of nearly all — the crucial property for rebalancing. **Virtual nodes** even out the distribution.
- This is the **exact mechanism from your [caching](caching-strategies.md) and [load-balancing](load-balancing-rate-limiting.md) notes** — third context for the same idea (sharded caches → LB routing → data partitioning).

### Directory / lookup-based
A **lookup service** maps key → shard explicitly (a table you control).
- ✅ Maximum flexibility (move any key anywhere; supports geo/tenant placement).
- ❌ The lookup layer is an extra hop and a potential **bottleneck/SPOF** — must itself be HA.
- Enables **entity/geo partitioning** — put a tenant's or region's data on a specific shard (the [multi-region "data homing"](multi-region-deployment.md) pattern).

---

## 4. The shard key — the decision that matters most

The **shard key** (partition key) determines which shard each row lands on. It's the **single highest-stakes choice** in a sharded system, because it dictates distribution *and* which queries are cheap — and it's **very hard to change later** (changing it usually means re-sharding the whole dataset).

A good shard key balances two goals:
- **Even distribution** — spread data and load across shards (avoid hotspots).
- **Query locality** — the queries you run most should hit **one shard**, not all of them (avoid scatter-gather).

These can conflict — and resolving them is the craft:
- **User/tenant-based** (`user_id`) — great when most queries are "this user's data" (co-located on one shard). The common default for multi-tenant apps.
- **Composite keys** — e.g. `(tenant_id, timestamp)` — distribute by tenant, range within.
- **Avoid low-cardinality keys** (e.g. `country`, `status`) — too few values → uneven, few shards do all the work.
- **Avoid monotonic keys alone** (timestamp, auto-increment) with range partitioning → write hotspot.

> The test: *"What are my top few queries, and does the shard key let them hit one shard?"* If your hottest query has to fan out to every shard, the key is wrong.

---

## 5. Hotspots & skew

Even a decent scheme can develop **skew** — some shards far busier than others:

- **Sequential-key hotspot** — range-partitioned timestamps/IDs funnel all writes to the "newest" shard. Fix: **hash** the key, or a **composite** key.
- **Celebrity / hot-key problem** — one key gets disproportionate traffic (a viral user, a mega-tenant) that hash partitioning *can't* fix (it's one key → one shard). Fixes: **salting / key-splitting** (append a random suffix to spread a hot key across sub-partitions, then gather on read), application-level splitting, or a dedicated shard.
- **Data skew** — one tenant with 1000× the data. Fix: split that partition, or place it alone.

> Hash partitioning cures *sequential* hotspots but **not** a single *hot key* — that one needs application-aware splitting. Know the difference.

---

## 6. Rebalancing

Adding/removing nodes must move data **without moving everything** and without downtime:

- **❌ `hash(key) % N`** — the naïve trap: changing N remaps **almost every key**. Never rebalance this way.
- **Fixed number of partitions** — create *many more* partitions than nodes up front (e.g. 1000 partitions, 10 nodes → 100 each); rebalancing just **reassigns whole partitions** to new nodes. Simple and predictable (Riak, Elasticsearch, Couchbase).
- **Dynamic partitioning** — **split** a partition when it grows too large, **merge** when it shrinks (like a [B-tree](database-indexing.md) node splitting). Adapts to data volume (HBase, MongoDB, RethinkDB, Spanner/Cockroach ranges).
- **Consistent hashing** — moves only ~1/N of keys on membership change (§3).

Rebalancing should usually be **semi-automated** — automatic detection, human-approved execution — because an automatic rebalance during an incident can make things worse (a cascading storm).

---

## 7. Request routing

Once data is spread out, how does a request find the right shard? (This is **service discovery** for partitions.) Three approaches:

1. **Any node forwards** — the client hits any node; if it doesn't own the key, it routes to the one that does (Cassandra-style gossip).
2. **A routing tier / proxy** — a partition-aware proxy (Vitess's vtgate, a coordinator) sits in front and directs queries.
3. **Client-aware** — the client library knows the partition map and connects directly.

All three need an up-to-date **partition map**, often coordinated via **ZooKeeper/etcd** (a [consensus](distributed-consensus.md)-backed source of truth for "who owns what").

---

## 8. The hard parts

What makes sharding genuinely hard — and why you avoid it until you must:

- **Cross-shard queries** — a query the shard key doesn't localize becomes **scatter-gather** (hit every shard, merge results): slow, and as slow as the slowest shard. **Design queries around the shard key**; denormalize/co-locate related data so hot queries stay single-shard.
- **Cross-shard transactions** — an atomic write across shards is a **distributed transaction** needing **2PC** (blocking, slow) or a **[saga](message-queues-event-driven.md)**. Best answer: **keep transactions within a single shard** by choosing the shard key so related data co-locates. (Spanner/Cockroach *do* offer cross-shard ACID via [consensus](distributed-consensus.md) + 2PC — at a latency cost.)
- **Secondary indexes** — an index on a non-shard-key column is hard:
  - **Local (document-partitioned)** index — each shard indexes its own data; **reads scatter-gather** across all shards. Simple writes, expensive reads.
  - **Global (term-partitioned)** index — the index itself is partitioned by the indexed term; **reads hit one shard, but writes touch multiple** (and go async). Faster reads, complex writes.
- **Resharding** — changing the shard key or partition count is a major, risky migration. Get the key right early.
- **Referential integrity / joins** — cross-shard joins and FKs mostly don't work; you denormalize or join in the app.

> The unifying theme: **everything is easy within a shard and hard across shards.** The whole game is choosing a shard key so your important operations stay *within* one shard.

---

## 9. Best practices & anti-patterns

**Do**
- **Exhaust scale-up, replicas, and caching first** — shard only at a measured single-node ceiling.
- **Choose the shard key for even distribution *and* single-shard hot queries** — it's the decision you can't easily undo.
- **Co-locate related data** (by tenant/entity) so transactions and joins stay within a shard.
- **Over-provision partitions** (many more than nodes) or use consistent hashing for painless rebalancing.
- **Hash sequential keys**; **salt/split hot keys**.
- **Combine with replication** — replicate each shard for availability.
- **Use a proven sharding layer** (Vitess for MySQL, Citus for Postgres, or a natively-sharded DB) over hand-rolling.

**Avoid**
- **Sharding prematurely** — massive complexity for a problem you may not have.
- **`hash % N` rebalancing** — remaps everything on a node change.
- **A low-cardinality or monotonic shard key** — uneven load / write hotspot.
- **Designing queries that fan out to every shard** — scatter-gather kills your latency.
- **Cross-shard transactions as the norm** — keep them within a shard.
- **Assuming you can change the shard key later cheaply** — you usually can't.

---

## 10. Go deeper

Related material in this library:

- 📝 **[Multi-Region Deployment](multi-region-deployment.md)** — geo-partitioning / data homing is directory-based sharding by region; each shard is also replicated.
- 📝 **[Consistency & CAP](consistency-models-cap.md)** & **[Distributed Consensus](distributed-consensus.md)** — each shard is a replicated group (Raft/Paxos); cross-shard ACID needs consensus + 2PC.
- 📝 **[Database Indexing](database-indexing.md)** — local vs. global secondary indexes; partition pruning; B-tree-style splits.
- 📝 **[Transactions & Isolation](database-transactions-isolation.md)** — cross-shard = distributed transaction (2PC).
- 📝 **[Caching](caching-strategies.md)** & **[Load Balancing](load-balancing-rate-limiting.md)** — consistent hashing, the same mechanism in three contexts.
- 📝 **[Message Queues](message-queues-event-driven.md)** — Kafka *partitions* by key are this exact idea for a log; cross-shard = cross-partition ordering.
- 📄 **[Bigtable](../papers/)**, **[Dynamo](../papers/)**, **[Spanner](../papers/)** — range tablets, consistent-hashing, and auto-sharded ranges respectively.
- 📗 **[Designing Data-Intensive Applications](../books/)** — **Ch. 6 "Partitioning"** is the definitive treatment.

### Primary references

- Kleppmann, *Designing Data-Intensive Applications*, Ch. 6.
- The [Dynamo](../papers/) (consistent hashing) and [Bigtable](../papers/)/[Spanner](../papers/) (range tablets) papers.
- Vitess / Citus docs — production sharding for MySQL / Postgres.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
