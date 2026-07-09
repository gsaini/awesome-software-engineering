# 🔒 Database Transactions & Isolation Levels — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~20 min · **Prerequisites:** basic SQL; the [Database Indexing](database-indexing.md) note is a good companion.

A **transaction** groups several reads and writes into one logical unit that either fully happens or fully doesn't. **Isolation levels** control how much concurrent transactions are allowed to see each other's in-flight work — trading correctness against performance. This is one of those topics that seems simple until two users hit the same row at once.

## Table of contents

- [1. ACID in one screen](#1-acid-in-one-screen)
- [2. The concurrency anomalies](#2-the-concurrency-anomalies)
- [3. The four isolation levels](#3-the-four-isolation-levels)
- [4. How isolation is implemented: locking vs. MVCC](#4-how-isolation-is-implemented-locking-vs-mvcc)
- [5. Snapshot isolation & write skew](#5-snapshot-isolation--write-skew)
- [6. What your database actually does by default](#6-what-your-database-actually-does-by-default)
- [7. Best practices & anti-patterns](#7-best-practices--anti-patterns)
- [8. Go deeper](#8-go-deeper)

---

## 1. ACID in one screen

| Property | Guarantee | In plain terms |
| -------- | --------- | -------------- |
| **Atomicity** | All-or-nothing | If any part fails, the whole transaction rolls back — no half-writes. |
| **Consistency** | Invariants preserved | The DB moves from one valid state to another (constraints, foreign keys hold). |
| **Isolation** | Concurrency is safe | Concurrent transactions don't corrupt each other; ideally appear to run one-at-a-time. |
| **Durability** | Committed = permanent | Once committed, data survives crashes, power loss, restarts. |

> ⚠️ The **C** is the odd one out. Consistency here is mostly the *application's* responsibility (you define the invariants); atomicity, isolation, and durability are what the database engine actually provides. Some argue ACID is really "AID."

Isolation is the property with a **dial** — the other three are essentially on/off. That dial is the rest of this note.

---

## 2. The concurrency anomalies

Weaker isolation lets more of these "read/write phenomena" occur. You pick an isolation level by deciding **which anomalies you can tolerate**.

- **Dirty read** — you read another transaction's *uncommitted* change, which may later roll back. You acted on data that never really existed.
- **Non-repeatable read** — you read a row twice in one transaction and get **different values**, because another transaction committed an update in between.
- **Phantom read** — you run the same **range query** twice and get **different rows**, because another transaction committed an insert/delete matching your filter.
- **Lost update** — two transactions read the same value, both modify it, and the second write silently clobbers the first (classic read-modify-write race, e.g. two people incrementing a counter).
- **Write skew** — two transactions read an overlapping set, then each writes to a *different* row based on what it read; individually fine, together they violate an invariant (the subtle one — see §5).

---

## 3. The four isolation levels

The ANSI SQL standard defines four levels by which anomalies they forbid:

| Isolation level | Dirty read | Non-repeatable read | Phantom read |
| --------------- | :--------: | :-----------------: | :----------: |
| **Read Uncommitted** | ✅ possible | ✅ possible | ✅ possible |
| **Read Committed**   | ❌ prevented | ✅ possible | ✅ possible |
| **Repeatable Read**  | ❌ prevented | ❌ prevented | ✅ possible* |
| **Serializable**     | ❌ prevented | ❌ prevented | ❌ prevented |

*In the ANSI standard, Repeatable Read still permits phantoms — but several real engines (PostgreSQL's snapshot-based RR, MySQL InnoDB with gap locks) prevent them too. Standards and reality diverge here; always check your engine.*

**Serializable** is the gold standard: the result is *as if* the transactions ran one after another in some serial order. It's the only level that also rules out lost updates and write skew — at the highest concurrency cost.

> The trade-off in one line: **stronger isolation = fewer anomalies = less concurrency (more locking/aborts).** Pick the *weakest* level that keeps your data correct.

---

## 4. How isolation is implemented: locking vs. MVCC

Two broad strategies:

### Pessimistic — locking (2PL)

**Two-Phase Locking**: acquire locks (shared for reads, exclusive for writes) in a growing phase, release them only in a shrinking phase. Strong serializability, but readers block writers and writers block readers → contention and **deadlocks** (two transactions each waiting on a lock the other holds; the DB detects the cycle and aborts one).

### Optimistic — MVCC

**Multi-Version Concurrency Control**: instead of locking reads, the engine keeps **multiple versions** of each row. Each transaction reads from a consistent **snapshot**, so:

- **Readers never block writers, and writers never block readers.** (The headline benefit.)
- Writes create a new version; old versions are cleaned up later (Postgres calls this `VACUUM`).

MVCC is why PostgreSQL, Oracle, and MySQL/InnoDB can offer strong read consistency without read locks. It's the dominant modern approach.

---

## 5. Snapshot isolation & write skew

**Snapshot Isolation (SI)** is what most MVCC engines give you at the "Repeatable Read" setting: every statement (or transaction) sees the database as of a single consistent point in time. SI prevents dirty reads, non-repeatable reads, and (in practice) phantoms.

**But SI still allows write skew** — the anomaly people get bitten by in production:

```
Invariant: at least one doctor must stay on call.

Two doctors, both on call, both try to go off call at the same time:

  Txn A: SELECT count(*) FROM oncall WHERE active  -- sees 2, OK to leave
  Txn B: SELECT count(*) FROM oncall WHERE active  -- sees 2, OK to leave
  Txn A: UPDATE me SET active = false              -- commits
  Txn B: UPDATE me SET active = false              -- commits
  → Now ZERO doctors on call. Invariant violated.
```

Each transaction read a snapshot, made a decision, and wrote a *different* row — so there's no direct write-write conflict for SI to catch. Only **Serializable** prevents this.

Fixes:
- Use **`SERIALIZABLE`** isolation. PostgreSQL implements **Serializable Snapshot Isolation (SSI)** — it tracks read/write dependencies and *aborts* transactions that would break serializability (so your app must retry on serialization failures).
- Or take an explicit lock: **`SELECT ... FOR UPDATE`** to materialize the conflict SI can't see.

---

## 6. What your database actually does by default

Defaults and naming differ — a frequent source of bugs:

| Engine | Default level | Notable quirks |
| ------ | ------------- | -------------- |
| **PostgreSQL** | Read Committed | "Repeatable Read" = snapshot isolation (no phantoms); "Serializable" = SSI with retry-on-abort. |
| **MySQL / InnoDB** | Repeatable Read | Uses gap locks to prevent phantoms at RR. |
| **Oracle** | Read Committed | Has no true Read Uncommitted; "Serializable" is actually snapshot isolation (allows write skew!). |
| **SQL Server** | Read Committed | Offers optimistic `SNAPSHOT` and lock-based `SERIALIZABLE`. |

> Two engines set to "the same" level can behave differently. Read *your* engine's docs — especially what its "Serializable" really guarantees.

---

## 7. Best practices & anti-patterns

**Do**
- **Keep transactions short** — they hold locks/versions; long transactions cause contention and bloat.
- **Choose the weakest correct level** — don't pay for Serializable if Read Committed is provably safe for the query.
- **Handle serialization failures** — under SSI/optimistic levels, wrap transactions in a retry loop.
- **Use explicit locks (`SELECT ... FOR UPDATE`)** for read-modify-write to avoid lost updates.
- **Know your engine's defaults and naming.**

**Avoid**
- Read-modify-write in application code without a lock or atomic operation (→ lost updates).
- Assuming snapshot isolation is serializable (→ write skew on invariants across rows).
- Long-running transactions that touch many rows and sit open (lock contention, deadlocks, `VACUUM` bloat).
- Cranking everything to Serializable "to be safe" without measuring the throughput cost.

---

## 8. Go deeper

Related material in this library:

- 📝 **[Database Indexing](database-indexing.md)** — the storage-side companion to this concurrency-side note.
- 📗 **[Designing Data-Intensive Applications — Martin Kleppmann](../books/)** — **Chapter 7, "Transactions"** is the definitive treatment of everything above (and where the on-call write-skew example comes from).
- 📗 **[Database Internals — Alex Petrov](../books/)** — how concurrency control and MVCC are implemented under the hood.
- 🎓 **[CMU 15-445: Database Systems](../courses/)** — lectures on concurrency control, 2PL, and MVCC.

### Primary sources

- Berenson et al., *"A Critique of ANSI SQL Isolation Levels,"* 1995 — the paper that clarified the anomalies and snapshot isolation.
- M. Kleppmann, *Designing Data-Intensive Applications*, Ch. 7, 2017.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
