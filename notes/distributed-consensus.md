# 🤝 Distributed Consensus (Raft & Paxos) — A Detailed Study Note

> **Level:** 🔴 Advanced · **Reading time:** ~24 min · **Prerequisites:** the [Consistency Models & CAP](consistency-models-cap.md) note (this is its direct sequel), plus [Transactions](database-transactions-isolation.md) and [Concurrency](concurrency-parallelism.md).

Yesterday's note asked *what* consistency guarantees are possible. This one answers the *how*: **consensus** is the problem of getting a group of unreliable machines to **agree on a single value** — the next command in a log, who the leader is, whether a transaction commits — even when nodes crash and the network drops messages. It's the machinery underneath every strongly-consistent (CP) system you've met.

> **The core problem:** you have N machines, some will crash, messages arrive late or not at all, and yet **everyone must agree on the same value, and never un-agree.** That sounds easy until you realize any node can fail at any instant — including right after it decides but before it tells anyone. Consensus is how you get [linearizability](consistency-models-cap.md) out of unreliable parts.

## Table of contents

- [1. Why consensus matters](#1-why-consensus-matters)
- [2. What "agreement" must guarantee](#2-what-agreement-must-guarantee)
- [3. The FLP result & failure models](#3-the-flp-result--failure-models)
- [4. Replicated state machines](#4-replicated-state-machines)
- [5. Paxos](#5-paxos)
- [6. Raft](#6-raft)
- [7. Quorums & why odd numbers](#7-quorums--why-odd-numbers)
- [8. Where it's used in the wild](#8-where-its-used-in-the-wild)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. Why consensus matters

Almost every "strongly consistent" building block reduces to consensus:

- **Leader election** — pick one primary so writes have a single serialization point.
- **Replicated logs** — agree on the *order* of operations across replicas (the [linearizable](consistency-models-cap.md) log).
- **Distributed locks / leases** — only one holder at a time.
- **Atomic commit** — all participants commit or none do.
- **Configuration/membership** — agree on who's in the cluster.

If you've used etcd, ZooKeeper, Consul, Spanner, CockroachDB, or Kafka's controller — you've relied on a consensus algorithm. It's the CP corner of [CAP](consistency-models-cap.md) made real.

---

## 2. What "agreement" must guarantee

A correct consensus protocol satisfies:

- **Agreement (safety)** — no two nodes decide *different* values. The non-negotiable one.
- **Validity/Integrity** — the value decided was actually proposed by some node (no inventing values).
- **Termination (liveness)** — every non-faulty node eventually decides.

The split between **safety** ("nothing bad happens" — never disagree) and **liveness** ("something good eventually happens" — eventually decide) is the key lens. As §3 shows, you can always keep safety; it's liveness that the impossibility results and real networks threaten.

> Design mantra: **never sacrifice safety for liveness.** A stalled system that's still correct can recover; a system that decided two different values has corrupted data forever.

---

## 3. The FLP result & failure models

**FLP (Fischer–Lynch–Paterson, 1985)** — a famous impossibility: in a **fully asynchronous** network (no bound on message delay), **no deterministic consensus protocol can guarantee termination** if even one node can crash. Why? You can't distinguish a *crashed* node from a merely *slow* one.

This isn't defeatism — it tells you where to bend. Real systems dodge FLP by:
- **Assuming partial synchrony** — the network is *eventually* well-behaved (timeouts work most of the time). Protocols stay **safe always**, and become **live once the network settles**. Raft/Paxos live here.
- **Randomization** — break symmetry with random timeouts (Raft's election jitter — [that word again](load-balancing-rate-limiting.md)).

**Failure models** — what kind of failure you tolerate:
- **Crash-stop / crash-recovery** — nodes fail by stopping (or stop then restart). Paxos/Raft handle this; tolerate **⌊(N−1)/2⌋** failures (a majority must survive).
- **Byzantine** — nodes may lie, corrupt, or act maliciously. Needs **BFT** protocols (PBFT, and blockchain-style), tolerating only **⌊(N−1)/3⌋**. Far more expensive; used where you don't trust participants (public blockchains).

> Raft and Paxos are **crash-fault-tolerant, not Byzantine** — they assume nodes are honest but may crash. That assumption is what makes a simple majority enough.

---

## 4. Replicated state machines

The unifying idea behind consensus in practice:

> If every replica starts in the same state and applies the **same commands in the same order**, they end in the same state. So the whole problem reduces to **agreeing on an ordered log of commands.**

```
        ┌── consensus keeps these logs identical ──┐
Leader:  [ set x=1 | set y=2 | del x | set y=5 ]
Node B:  [ set x=1 | set y=2 | del x | set y=5 ]   ← same order → same state
Node C:  [ set x=1 | set y=2 | del x | ....... ]   ← lagging, will catch up
              apply in order → deterministic state machine
```

This is the **Replicated State Machine (RSM)** model: consensus agrees on the *log entries and their order*; each node then deterministically applies them. It's exactly the [message-queue log](message-queues-event-driven.md) idea (ordered, immutable, replayable) with agreement bolted on — and it's why "the log" keeps being the universal abstraction.

---

## 5. Paxos

**Paxos** (Leslie Lamport, 1998) is the foundational consensus algorithm — famously correct and famously hard to understand. It agrees on **one value** via roles (**proposers**, **acceptors**, **learners**) and a two-phase protocol:

1. **Phase 1 — Prepare/Promise:** a proposer picks a ballot number `n`, asks acceptors to *promise* not to accept anything older than `n`. If a majority promise (and report any value they've already accepted), it proceeds.
2. **Phase 2 — Accept/Accepted:** the proposer asks acceptors to *accept* value `v` at ballot `n` (using the highest previously-accepted value if there was one). A majority accepting = **chosen**.

The genius: **majority quorums overlap**, so any two rounds share at least one acceptor, which carries forward already-chosen values — guaranteeing agreement. The catch: **plain Paxos decides one value**; real systems need a sequence.

- **Multi-Paxos** — elect a stable leader so you can skip Phase 1 for a stream of entries (leader runs Phase 2 repeatedly). This is what people actually deploy — and it converges on the same shape as Raft.

> Paxos's reputation ("the greatest disagreement is over whether it's understandable") is *why Raft was created* — same guarantees, deliberately designed to be teachable.

---

## 6. Raft

**Raft** (Ongaro & Ousterhout, 2014) — "understandable consensus." Same power as Multi-Paxos, decomposed into three human-sized pieces:

### Leader election
- Every node is **Follower**, **Candidate**, or **Leader**. Time is divided into **terms** (a logical clock).
- Followers expect regular **heartbeats** from the leader. If a **randomized election timeout** elapses with no heartbeat, a follower becomes a Candidate, increments the term, and requests votes.
- A candidate winning a **majority** becomes Leader. **Randomized timeouts** make split votes rare (and self-heal) — jitter breaking symmetry, exactly as in retries/caching.

### Log replication
- Clients send commands to the **leader only**. The leader appends to its log and sends `AppendEntries` to followers.
- Once a **majority** have stored an entry, the leader marks it **committed**, applies it to its state machine, and returns to the client. Followers apply committed entries in order.

### Safety
- **Election restriction** — a candidate can only win if its log is at least as up-to-date as the voter's, so a leader **never** overwrites committed entries.
- Entries flow **leader → followers only**; conflicting follower entries are overwritten. This one-directional flow is what makes Raft tractable.

```
Client → Leader: "set x=5"
Leader appends, replicates ↓ to a MAJORITY
  Follower1 ✅   Follower2 ✅   Follower3 (lagging)
Majority stored → Leader COMMITS → applies → replies to client
```

> Raft's trick isn't a new capability — it's **decomposition**. Election + replication + safety as three separable problems is why it, not Paxos, is in most modern systems (etcd, Consul, CockroachDB, TiKV).

---

## 7. Quorums & why odd numbers

Consensus needs a **majority quorum** (`⌊N/2⌋ + 1`) for every decision. Two majorities always **overlap in at least one node**, which carries the agreed value forward — the same overlap trick as Paxos and as [`W+R>N`](consistency-models-cap.md) quorum reads.

**Why clusters are odd-sized (3, 5, 7):**

| N | Majority | Failures tolerated |
| - | -------- | ------------------ |
| 3 | 2 | 1 |
| 4 | 3 | 1 |
| 5 | 3 | 2 |
| 6 | 4 | 2 |

Going from 3→4 or 5→6 **adds a node but not fault tolerance** — you just need a bigger majority, so it's *worse* (more nodes that can fail, same resilience). **Odd sizes maximize tolerance per node.** This is also why a 2-node cluster is pointless: majority is 2, so it tolerates *zero* failures and both must be up.

> **Split brain** is what quorums prevent: if a partition splits 5 nodes into 3+2, only the side with the **majority (3)** can elect a leader and make progress; the minority (2) **steps down** and refuses writes. That's [CAP's CP choice](consistency-models-cap.md) in action — the minority sacrifices availability to preserve consistency.

---

## 8. Where it's used in the wild

- **etcd / ZooKeeper / Consul** — consensus as a *service*: config, leader election, locks, service discovery. Kubernetes stores all cluster state in **etcd** (Raft).
- **Spanner / CockroachDB / TiKV / YugabyteDB** — Raft/Paxos **per shard** to replicate data consistently; this is how a distributed DB offers [strict serializability](consistency-models-cap.md).
- **Kafka** — moved its controller/metadata to a Raft protocol (**KRaft**), replacing its ZooKeeper dependency.
- **Distributed locks & leader election** for any service that needs "exactly one primary."

> The pattern: you rarely implement consensus yourself — you **delegate it** to etcd/ZooKeeper or use a database that embeds it. Knowing how it works tells you its costs (a majority round-trip per write) and limits (latency-bound, small clusters).

---

## 9. Best practices & anti-patterns

**Do**
- **Use odd-sized clusters** (3 or 5); 5 tolerates 2 failures — the common sweet spot.
- **Delegate consensus** to a proven system (etcd, ZooKeeper, a Raft/Paxos DB) rather than rolling your own.
- **Understand the cost** — every committed write needs a majority round-trip; keep the consensus group small and co-located when latency matters.
- **Keep state-machine commands deterministic** — non-determinism breaks replica convergence (no wall-clock, no random, no map-iteration order in applied commands — the [test-determinism](testing-strategy.md) lesson again).
- **Separate the consensus log from bulk data** where possible (consensus for metadata/ordering, cheaper replication for large payloads).

**Avoid**
- **Even-sized clusters** (no fault-tolerance benefit, bigger majority) and **2-node "HA"** (tolerates zero failures).
- **Rolling your own consensus** — it's a graveyard of subtle safety bugs; use a library.
- **Assuming consensus survives a lost majority** — lose the majority and the system *correctly* stops accepting writes. That's safety working, not a bug to route around.
- **Confusing Raft/Paxos (crash-fault) with Byzantine tolerance** — they assume honest nodes.
- **Sacrificing safety for liveness** — a wedged-but-correct cluster recovers; a split-brained one corrupts data.

---

## 10. Go deeper

This note completes the distributed-systems arc:

- 📝 **[Consistency Models & CAP](consistency-models-cap.md)** — the *what*; consensus is *how* you implement the CP/linearizable side.
- 📝 **[Message Queues & Event-Driven](message-queues-event-driven.md)** — the replicated *log* is the same abstraction; consensus agrees on its order.
- 📝 **[Transactions & Isolation](database-transactions-isolation.md)** — atomic commit and strict serializability are built on consensus per shard.
- 📄 **[In Search of an Understandable Consensus Algorithm (Raft)](../papers/)** — the actual paper; genuinely readable, read it next.
- 📄 **[Paxos Made Simple](../papers/)** and **[Time, Clocks, and the Ordering of Events](../papers/)** — Lamport's foundations, in your index.
- 📄 **[Spanner](../papers/)** — consensus (Paxos) per shard + TrueTime = globally strict-serializable.
- 📗 **[Designing Data-Intensive Applications — Kleppmann](../books/)** — **Ch. 9 "Consistency and Consensus"** ties CAP, linearizability, and consensus together.

### Primary references

- Ongaro & Ousterhout, *"In Search of an Understandable Consensus Algorithm (Raft),"* 2014.
- Lamport, *"Paxos Made Simple,"* 2001, and *"The Part-Time Parliament,"* 1998.
- Fischer, Lynch & Paterson, *"Impossibility of Distributed Consensus with One Faulty Process,"* 1985 (FLP).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
