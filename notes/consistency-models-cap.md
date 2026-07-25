# 🌐 Consistency Models & CAP — A Detailed Study Note

> **Level:** 🔴 Advanced · **Reading time:** ~24 min · **Prerequisites:** the [Transactions & Isolation](database-transactions-isolation.md), [Caching](caching-strategies.md), and [Message Queues](message-queues-event-driven.md) notes. This is the capstone that unifies them.

The moment you keep **more than one copy** of data — for availability, latency, or scale — the copies can **disagree**. A **consistency model** is the contract that says *what guarantees a reader gets* about how up-to-date and ordered its view is. Strong models are easy to reason about but slow and fragile under failure; weak models are fast and available but push complexity onto you. This note maps that spectrum and the theorems (CAP, PACELC) that say why you can't have it all.

> **The one-sentence thesis of the whole month:** every "how stale / how ordered / how consistent?" knob you've met — [isolation levels](database-transactions-isolation.md), [cache TTLs](caching-strategies.md), [at-least-once delivery](message-queues-event-driven.md) — is a local instance of the *same* global trade-off between **consistency** and **availability/latency**. This note is that trade-off in its general form.

## Table of contents

- [1. Why copies disagree](#1-why-copies-disagree)
- [2. The CAP theorem](#2-the-cap-theorem)
- [3. PACELC — the better framing](#3-pacelc--the-better-framing)
- [4. The consistency spectrum](#4-the-consistency-spectrum)
- [5. Linearizability vs. serializability](#5-linearizability-vs-serializability)
- [6. Session (client-centric) guarantees](#6-session-client-centric-guarantees)
- [7. Quorums & tunable consistency](#7-quorums--tunable-consistency)
- [8. Conflict resolution](#8-conflict-resolution)
- [9. Choosing in practice](#9-choosing-in-practice)
- [10. Best practices & anti-patterns](#10-best-practices--anti-patterns)
- [11. Go deeper](#11-go-deeper)

---

## 1. Why copies disagree

Replication is non-negotiable in real systems — you keep copies to survive machine failure, to serve reads locally with low latency, and to scale beyond one box. But a write has to *propagate*, and while it's in flight:

- A reader hitting a different replica sees the **old value**.
- The network can **partition** — replicas can't talk, so they can't agree.
- Two clients can write **concurrently** to different replicas — now which wins?

The consistency model is the promise the system makes about all three. There's no free lunch: **stronger promises require more coordination**, and coordination costs latency and availability.

---

## 2. The CAP theorem

Given three properties, a distributed system can guarantee only **two**:

- **C — Consistency** (here meaning **linearizability**: every read sees the latest write, as if there were one copy).
- **A — Availability** (every request to a non-failing node gets a non-error response).
- **P — Partition tolerance** (the system keeps working despite the network dropping/delaying messages between nodes).

**The crucial nuance everyone misses:** in a distributed system, **partitions are not optional** — networks *will* fail. So **P is a given**, and CAP is really a **forced choice during a partition**: when nodes can't communicate, do you…

- **Stay consistent (CP)** → refuse requests you can't safely serve (sacrifice availability), or
- **Stay available (AP)** → answer with possibly-stale data (sacrifice consistency)?

```
        Partition happens. A node gets a read it can't verify is current.
                 │
      ┌──────────┴───────────┐
   CP: refuse / error      AP: answer with maybe-stale data
   (bank balance,          (shopping cart, social feed,
    inventory count)        DNS, "likes" count)
```

> ⚠️ Common misreadings to avoid: (1) CAP is **only about the partition moment** — when the network is healthy you can have *both* C and A. (2) CAP's "C" is **linearizability**, *not* the ACID "C" (which is about invariants) — [same word, different meaning](database-transactions-isolation.md). (3) "CA" systems aren't really a thing in distributed computing — you can't opt out of partitions.

---

## 3. PACELC — the better framing

CAP only describes the *partition* case, which is rare. **PACELC** (Daniel Abadi) completes it:

> **If** there is a **P**artition, choose **A**vailability or **C**onsistency; **E**lse (normal operation), choose **L**atency or **C**onsistency.

This captures the trade-off you actually pay **every day**, not just during failures: even with a healthy network, stronger consistency means waiting for replicas to agree → **higher latency**. It's the honest framing.

| System | Classification | Meaning |
| ------ | -------------- | ------- |
| **Dynamo, Cassandra, Riak** | **PA/EL** | Available under partition; low latency otherwise. Eventual consistency. |
| **Traditional RDBMS (sync replication)** | **PC/EC** | Consistent always; pays latency for it. |
| **Google Spanner** | **PC/EC** | Chooses consistency even normally (TrueTime), accepting latency. |
| **MongoDB (default)** | **PA/EC** (tunable) | Leans available under partition, consistent otherwise. |

> The takeaway: **"consistency vs. latency" is a bill you pay on every request**, while "consistency vs. availability" is only billed during partitions. PACELC makes both visible.

---

## 4. The consistency spectrum

From strongest (most coordination, easiest to reason about) to weakest (least coordination, fastest):

**🔒 Linearizability (strong / atomic consistency)**
The system behaves as if there's **one single copy** and every operation takes effect **atomically at some instant** between its start and end — so once a write completes, *every* subsequent read (by anyone, in real time) sees it. The gold standard; also the "C" in CAP. Expensive: needs coordination (consensus) on every operation.

**📏 Sequential consistency**
All nodes see operations in the **same total order**, and that order respects each process's own program order — but it need **not** match real-time (a read may miss a write that finished earlier on another client). Weaker than linearizable, still strong.

**➡️ Causal consistency**
Operations that are **causally related** (B read A's write, then wrote) are seen in that order by everyone; **concurrent** operations may be seen in different orders on different nodes. Crucially, causal is **the strongest model still achievable while remaining available under a partition** — so it's the sweet spot for many AP systems. Tracked with **vector clocks**.

**🔁 Eventual consistency**
The only promise: **if writes stop, all replicas eventually converge** to the same value. Says nothing about *when* or what you see meanwhile. Weakest useful model — and what powers highly-available systems (DNS, shopping carts, social feeds). Your [cache TTL](caching-strategies.md) is eventual consistency with a *bound* on the staleness.

```
Linearizable → Sequential → Causal → Eventual
────────────────────────────────────────────►
strong, coordinated, slow        weak, uncoordinated, fast, available
```

---

## 5. Linearizability vs. serializability

The single most clarifying distinction in this whole area — two different "strongest" guarantees that people conflate:

| | **Linearizability** | **Serializability** |
| - | ------------------- | ------------------- |
| **About** | **Single object**, recency & real-time order | **Multiple objects**, transaction ordering |
| **From** | Distributed systems / [replication](consistency-models-cap.md) | Databases / [isolation levels](database-transactions-isolation.md) |
| **Guarantees** | A read sees the latest write, in real time | Transactions appear to run in *some* serial order |
| **Doesn't guarantee** | Multi-object transaction atomicity | Real-time recency (the "serial order" can be stale) |

- **Serializability** (your [transactions note](database-transactions-isolation.md)'s top isolation level) is about *transactions* over *many* objects being equivalent to *some* sequential execution.
- **Linearizability** is about *one* object always reflecting the *latest* write in *real time*.

Combine both and you get **strict serializability** — transactions are serial *and* respect real-time order. That's the strongest guarantee a database can offer (Spanner targets it). Seeing these two as one topic — *this note and the transactions note are the same subject at different granularities* — is the payoff of studying both.

---

## 6. Session (client-centric) guarantees

Full linearizability is often overkill; a single client usually just wants **its own** experience to make sense. These cheaper **session guarantees** deliver that:

- **Read-your-writes** — after you write, *you* always see it (even if others don't yet). Prevents "I updated my profile but it shows the old one."
- **Monotonic reads** — you never see time go *backwards* (once you've seen a value, you won't later see an older one).
- **Monotonic writes** — your writes are applied in the order you made them.
- **Writes-follow-reads** — if you read X then write Y, everyone who sees Y also sees X (causal ordering).

Together these approximate **causal consistency** per-client, at a fraction of the cost — often the pragmatic target for user-facing systems. (Read-your-writes is exactly why a [cache](caching-strategies.md) should be invalidated/bypassed for the user who just wrote.)

---

## 7. Quorums & tunable consistency

Dynamo-style systems let you **dial** consistency per operation with three numbers:

- **N** — replicas per item.
- **W** — replicas that must ack a **write**.
- **R** — replicas that must respond to a **read**.

The key inequality:

```
W + R > N   ⟹   any read quorum overlaps any write quorum
            ⟹   a read is guaranteed to see the latest committed write
                (strong-ish consistency)
```

Examples with **N=3**:
- `W=3, R=1` — fast reads, slow/fragile writes (all replicas must ack).
- `W=1, R=3` — fast writes, slow reads.
- `W=2, R=2` — balanced, and `2+2 > 3` ✅ so reads see latest.
- `W=1, R=1` — `1+1 < 3` ❌ → **eventual** consistency, maximum availability.

This is **the CAP dial made numeric** — you trade consistency for availability/latency by choosing W and R. **Sloppy quorums** (write to *any* N healthy nodes during a partition, reconcile later via hinted handoff) push further toward availability.

---

## 8. Conflict resolution

Under AP/eventual consistency, two replicas can accept conflicting concurrent writes. How they reconcile matters:

- **Last-Write-Wins (LWW)** — keep the write with the highest timestamp. Simple, but **silently discards data** and depends on clock sync (dangerous — clocks skew). Cassandra's default; use with care.
- **Version vectors / vector clocks** — detect whether two writes are causally ordered or genuinely **concurrent**, so the system can surface real conflicts instead of guessing (Dynamo returns siblings for the app to merge).
- **CRDTs (Conflict-free Replicated Data Types)** — data structures (counters, sets, sequences) mathematically designed so **any two replicas merge deterministically without coordination**. The elegant answer: concurrent edits *always* converge correctly (the basis of collaborative editors and Riak's data types).

> The progression mirrors everything else: LWW is cheap and lossy; CRDTs cost design effort but eliminate the conflict class by construction — the same "remove the problem by design" instinct as immutability and idempotency.

---

## 9. Choosing in practice

The real lesson: **consistency is a per-operation decision, not a per-system one.** One product mixes levels:

| Data | Right level | Why |
| ---- | ----------- | --- |
| Account balance, inventory, payments | **Strong (linearizable / strict-serializable)** | A stale read means double-spend or overselling |
| Username / profile edits | **Read-your-writes** | You must see your own change; others can lag |
| "Likes", view counts, feeds | **Eventual** | Being briefly off by one is invisible and cheap |
| Shopping cart | **Eventual + merge** | Never reject an add during a partition; reconcile later |
| DNS, config distribution | **Eventual** | Availability and speed dominate; propagation delay is fine |

Ask of each operation: *what does a stale or reordered read actually cost here?* Pay for strong consistency only where the answer is "a lot."

---

## 10. Best practices & anti-patterns

**Do**
- **Pick consistency per operation** based on the cost of staleness — mix freely within one system.
- **Reserve linearizable/strict-serializable for the few operations that need it** (money, inventory, uniqueness).
- **Use session guarantees** (read-your-writes, monotonic reads) as the pragmatic default for user-facing reads.
- **Tune quorums deliberately** (`W+R>N` for strong reads); know your defaults.
- **Prefer CRDTs or version vectors over LWW** when concurrent writes are real.
- **Reason with PACELC** — remember you pay the consistency/latency bill even without partitions.

**Avoid**
- **Assuming your datastore is strongly consistent** — read its actual defaults (many are eventual or tunable, and "SERIALIZABLE" sometimes means snapshot isolation).
- **Confusing CAP's C with ACID's C**, or **linearizability with serializability**.
- **LWW with unsynchronized clocks** — silent data loss.
- **Defaulting everything to strong consistency** — you pay latency and availability you didn't need.
- **"CA" thinking** — you cannot opt out of partitions in a distributed system.
- **Ignoring clock skew** — timestamps across machines are not a reliable order (why Spanner needed TrueTime).

---

## 11. Go deeper

This note is the hub the rest of the library has been pointing at:

- 📝 **[Transactions & Isolation Levels](database-transactions-isolation.md)** — serializability is this note's linearizability for *transactions*; together = strict serializability.
- 📝 **[Caching Strategies](caching-strategies.md)** — a cache is eventual consistency with a TTL as the staleness bound; read-your-writes is why you invalidate on write.
- 📝 **[Message Queues & Event-Driven](message-queues-event-driven.md)** — at-least-once + convergence is eventual consistency for events; CDC keeps derived data eventually consistent.
- 📝 **[Load Balancing](load-balancing-rate-limiting.md)** — quorum/consistent-hashing routing is the same partitioning mechanism.
- 📄 **[Dynamo](../papers/)** (quorums, vector clocks, eventual consistency), **[Spanner](../papers/)** (TrueTime, external consistency), and **[Time, Clocks, and the Ordering of Events](../papers/)** (Lamport — the foundation of causal ordering) — all in your papers index, now readable with context.
- 📗 **[Designing Data-Intensive Applications — Kleppmann](../books/)** — **Ch. 5 (Replication) & Ch. 9 (Consistency and Consensus)** are the definitive treatment.

### Primary references

- E. Brewer, *"CAP Twelve Years Later: How the 'Rules' Have Changed,"* 2012.
- D. Abadi, *"Consistency Tradeoffs in Modern Distributed Database System Design (PACELC),"* 2012.
- Herlihy & Wing, *"Linearizability: A Correctness Condition for Concurrent Objects,"* 1990.
- Kleppmann, *Designing Data-Intensive Applications*, Ch. 5 & 9.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
