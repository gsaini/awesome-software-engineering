# 📜 Event Sourcing & CQRS — A Study Note

> **Level:** 🔴 Advanced · **Reading time:** ~18 min · **Prerequisites:** [Message Queues & Event-Driven](message-queues-event-driven.md), [Database Transactions & Isolation Levels](database-transactions-isolation.md), [Serialization & Schema Evolution](serialization-schema-evolution.md).

Most systems store the **current state** and overwrite it. **Event sourcing** stores the **sequence of changes** instead, and derives current state by replaying them. **CQRS** (Command Query Responsibility Segregation) uses **separate models for writing and reading**. The two are independent ideas that are often used together, because an event log is a good write model and a poor thing to query.

> **The one-line thesis:** *the log is the truth, and every table is a cache of it.* That buys you history, audit, and new read models for free, and costs you schema evolution, eventual consistency, and deletion. Use it where the history is the business, and nowhere else.

## Table of contents

- [1. State vs. events](#1-state-vs-events)
- [2. Event sourcing in 60 lines](#2-event-sourcing-in-60-lines)
- [3. The concepts that matter](#3-the-concepts-that-matter)
- [4. CQRS](#4-cqrs)
- [5. Projections, and rebuilding them](#5-projections-and-rebuilding-them)
- [6. Schema evolution: the hard part](#6-schema-evolution-the-hard-part)
- [7. Deletion and privacy](#7-deletion-and-privacy)
- [8. Don't confuse these](#8-dont-confuse-these)
- [9. When it earns its cost](#9-when-it-earns-its-cost)
- [10. Best practices & anti-patterns](#10-best-practices--anti-patterns)
- [11. Go deeper](#11-go-deeper)

---

## 1. State vs. events

Take a bank account with a balance of 70:

| | **State-oriented** | **Event-sourced** |
| - | ------------------ | ----------------- |
| **What's stored** | `balance = 70` | `Deposited 100`, `Withdrawn 30` |
| **A change is** | an `UPDATE` that destroys the old value | an `INSERT` of a new fact |
| **"Why is it 70?"** | Unknown, unless someone kept a separate log | The answer is the data |
| **"What was it last Tuesday?"** | Unknown | Replay up to Tuesday |
| **A new report on old behaviour** | Only from data you thought to keep | Replay history into a new shape |
| **A bug corrupted the state** | The old values are gone | Fix the code and replay |

Accounting has worked this way for centuries: a ledger is append-only, and a mistake is corrected with a new entry, never with an eraser. Git is the same idea (see [Git Internals](git-internals.md)), and so is a database's write-ahead log, as Pat Helland's line goes: *"the truth is the log; the database is a cache of a subset of the log."*

## 2. Event sourcing in 60 lines

The whole mechanism fits in SQLite. This code was run for this note.

```python
import json, sqlite3

db = sqlite3.connect(":memory:")
db.row_factory = sqlite3.Row
db.execute("""CREATE TABLE events (
    stream TEXT NOT NULL, version INTEGER NOT NULL, type TEXT NOT NULL, data TEXT NOT NULL,
    position INTEGER PRIMARY KEY AUTOINCREMENT, UNIQUE (stream, version))""")


class Conflict(Exception):
    pass


def append(stream, expected, events):
    """Optimistic concurrency: UNIQUE (stream, version) rejects a writer working from stale state."""
    try:
        with db:
            for i, (type_, data) in enumerate(events, start=expected + 1):
                db.execute("INSERT INTO events (stream, version, type, data) VALUES (?, ?, ?, ?)",
                           (stream, i, type_, json.dumps(data)))
    except sqlite3.IntegrityError:
        raise Conflict(f"{stream} moved past version {expected}") from None


def evolve(balance, e):
    """Apply one past fact. It can't fail: the event already happened."""
    d = json.loads(e["data"])
    return {"Deposited": balance + d["amount"], "Withdrawn": balance - d["amount"]}[e["type"]]


def rehydrate(stream):
    """Current state is a left fold over the stream."""
    balance, version = 0, 0
    for e in db.execute("SELECT * FROM events WHERE stream = ? ORDER BY version", (stream,)):
        balance, version = evolve(balance, e), e["version"]
    return balance, version


def withdraw(stream, amount):
    """A command: load, decide (this is where rules live and it *can* fail), append."""
    balance, version = rehydrate(stream)
    if amount > balance:
        raise ValueError("insufficient funds")
    append(stream, version, [("Withdrawn", {"amount": amount})])


append("acct-1", 0, [("Deposited", {"amount": 100})])
withdraw("acct-1", 30)
print(rehydrate("acct-1"))                                     # (70, 2)
append("acct-1", 1, [("Withdrawn", {"amount": 90})])           # Conflict: acct-1 moved past version 1
```

The core shape is two functions: `decide(state, command) -> events` and `evolve(state, event) -> state`. The first validates and can refuse. The second records what already happened and must never refuse, so that replay always works.

## 3. The concepts that matter

- **Event.** An immutable fact, named in the past tense, from the domain's point of view: `OrderShipped`, not `OrderUpdated`. If you can't name it without saying "updated", you're logging rows rather than recording facts.
- **Stream.** The ordered events of one thing, usually one **aggregate** (one order, one account). It is the consistency boundary.
- **Aggregate.** The cluster of state that must stay consistent. Rules are enforced within one stream, in one transaction. Rules across streams are eventually consistent.
- **Optimistic concurrency.** Each append states the version it read. Two writers from the same version can't both win, and the loser reloads and decides again. No locks are held during the decision. Compare the lost-update discussion in [Transactions & Isolation](database-transactions-isolation.md).
- **Snapshot.** A saved fold result at version N, so a stream with 50,000 events doesn't replay from zero. It is a performance cache and can always be deleted. Long streams often mean the aggregate is too big: model "account for this month" and close it, as accounting does at period end.
- **Global position.** One order across all streams, which is what projections read. Beware: `position` from an autoincrement can show gaps or commit out of order under concurrent writers, so a reader tracking "last seen" can skip an event that commits late. Real event stores solve this explicitly. Single-writer SQLite doesn't have the problem. Postgres with concurrent transactions does.

## 4. CQRS

Greg Young's framing builds on Bertrand Meyer's Command-Query Separation, applied at the level of a whole model: **the shape that's good for enforcing rules is not the shape that's good for answering questions.**

```text
                     ┌── commands ──► write model ──► events
 client ─────────────┤                                   │ (async)
                     └── queries  ──► read models  ◄─────┘ projections
                                      (denormalized, one per screen or question)
```

CQRS comes in degrees, and most systems need the mildest one:

| Level | What's separated | Typical use |
|---|---|---|
| **0** | Different code paths and DTOs, same tables | Most apps that "want CQRS" |
| **1** | Same database, read-optimized views or tables | Reporting pages, dashboards |
| **2** | Separate read store (search index, cache, replica), updated asynchronously | Read and write scale or shape differ a lot |
| **3** | Level 2, with event sourcing as the write side | Audit-heavy domains |

Fowler's warning on [CQRS](https://martinfowler.com/bliki/CQRS.html) is the important part: *"for most systems CQRS adds risky complexity."* It belongs on a specific part of a system (a bounded context), not on the whole thing.

**The cost is eventual consistency that users can see.** After a successful write, a read may return the old value. The standard ways of handling that:

- **Return the result of the command.** The UI shows what it just did without re-reading.
- **Read your writes.** The command returns a position, and the query waits until the projection reaches it (or times out).
- **Be honest in the UI.** "Your order is being processed" is often the truthful state anyway.

## 5. Projections, and rebuilding them

A **projection** folds events into a read model. It tracks a checkpoint and has to tolerate replay. Here the checkpoint and the read model commit together, which gives exactly-once *effects* inside one database:

```python
db.execute("CREATE TABLE balances (stream TEXT PRIMARY KEY, balance INTEGER NOT NULL)")
db.execute("CREATE TABLE checkpoint (position INTEGER NOT NULL)")
db.execute("INSERT INTO checkpoint VALUES (0)")


def project(batch=100):
    with db:  # read model and checkpoint commit together
        pos = db.execute("SELECT position FROM checkpoint").fetchone()[0]
        for e in db.execute("SELECT * FROM events WHERE position > ? ORDER BY position LIMIT ?", (pos, batch)).fetchall():
            delta = json.loads(e["data"])["amount"] * (1 if e["type"] == "Deposited" else -1)
            db.execute("INSERT INTO balances VALUES (?, ?) ON CONFLICT (stream) DO UPDATE SET balance = balance + ?",
                       (e["stream"], delta, delta))
            db.execute("UPDATE checkpoint SET position = ?", (e["position"],))
```

Calling `project()` twice leaves `acct-1` at 70: the second call finds nothing past its checkpoint. When the read model lives in a different system, such as a search index, you can't commit them together, so writes must be **idempotent** (see [Message Queues](message-queues-event-driven.md)).

**The superpower is the rebuild.** A new report needs a new shape? Write a new projection, start at position 0, and let it catch up alongside the old one. Then switch reads over and drop the old one. It's the blue-green pattern from [Progressive Delivery](feature-flags-progressive-delivery.md), applied to read models.

**The catch is that replay has rules.** Projections must not repeat side effects. Replaying must not re-send emails or re-charge cards, so effects belong in a separate consumer that only handles new events. Replay time also grows with history: millions of events can mean hours, so plan for it before you need it.

## 6. Schema evolution: the hard part

In a normal system you migrate the table and move on. Here, **the events are immutable and your code must read all of them forever.** A 2017 event in a shape nobody remembers still has to fold correctly. The study by Overeem, Spoor and Jansen, [*The dark side of event sourcing*](https://ieeexplore.ieee.org/document/7884621), is about exactly this.

| Technique | How | Trade-off |
|---|---|---|
| **Additive changes** | New optional fields with defaults | The default; safe and cheap (the [Schema Evolution](serialization-schema-evolution.md) rules apply) |
| **Weak schema** | Readers tolerate missing and unknown fields | Fails softly; needs discipline |
| **Upcasting** | Convert old events to the new shape **on read**, in a chain v1→v2→v3 | The store stays immutable; the chain is code you keep forever |
| **New event type** | `AddressChangedV2` alongside the old one | Explicit, but consumers handle both |
| **Copy-and-transform** | Rewrite the stream to a new store, then cut over | The heavy option; for real model changes |

A common mistake makes this worse: **events that expose your internal model.** Publishing a domain event to other teams freezes its shape for them too. Keep private events inside the service, and publish a smaller, stable integration event from them.

## 7. Deletion and privacy

"Immutable forever" collides with GDPR's right to erasure, and with any user who asks you to delete their data. The usual answers:

- **Crypto-shredding.** Encrypt personal fields with a per-subject key stored outside the log. To erase, delete the key, and the events remain but those fields are unreadable. Whether that legally counts as erasure is a question for your lawyers, not this note.
- **Keep personal data out of events.** Store a reference, and keep the data in a normal mutable table you can delete from. This is the simplest option, if your domain allows it.
- **Rewrite the stream.** Possible, but it breaks the immutability that made the log trustworthy.

## 8. Don't confuse these

| Term | What it is | Source of truth |
|---|---|---|
| **Event sourcing** | Current state is derived from the events | The events |
| **Transactional outbox** | State tables, plus events recorded in the same transaction for others to consume | The state tables |
| **Change data capture** | Row changes read from the database's log | The state tables |
| **Event notification** | "Something changed", with little data; consumers call back | The publisher's state |
| **Event-carried state transfer** | Events carry enough data that consumers keep their own copy | The publisher's state |
| **Audit log** | A record *about* changes, written alongside them | The state tables; the log can drift |

Fowler's [*What do you mean by "Event-Driven"?*](https://martinfowler.com/articles/201701-event-driven.html) separates most of these. The test is simple: **if you deleted the events, could you still run the system?** If you could, you have an outbox or a log, not event sourcing.

A Kafka topic is usually not an event store either. Event sourcing needs *load one aggregate's stream* and *append only if the version is still N*. A topic with thousands of aggregates per partition gives you neither directly. Kafka works well as the transport *from* an event store to consumers.

## 9. When it earns its cost

| Use it when | Avoid it when |
|---|---|
| History **is** the domain: ledgers, payments, claims, trading | It's CRUD with no interesting behaviour |
| Audit is a hard requirement, not a nice-to-have | "We might want analytics later": CDC or an outbox gives you that |
| Business rules are about *how* state changed, not just what it is | The team is new to both DDD and async systems |
| You'll keep asking new questions of old behaviour | You need strong read-after-write everywhere |
| A bounded part of the system has these properties | You're applying it to the whole system |

Try the cheaper options first: an audit table, an outbox for integration, read replicas or materialized views for read scale. Event sourcing is a poor default and a very hard decision to reverse in either direction.

**The honest cost:** teams often report underestimating the operations and evolution work, which only shows up a year or two in, when the event history has outlived the people who designed it.

## 10. Best practices & anti-patterns

**Do**

- ✅ Name events in past tense, in domain language, and keep them small.
- ✅ Keep aggregates and streams small, and close them on a natural boundary.
- ✅ Enforce uniqueness of `(stream, version)` at the storage layer.
- ✅ Keep `evolve` pure and total. Replaying must not call anything or be able to fail.
- ✅ Give every projection a checkpoint, and be able to rebuild it from zero. Test the rebuild.
- ✅ Decide your upcasting approach and your deletion approach **before** the first production event.
- ✅ Store `id`, type, version, timestamp, and correlation and causation ids on every event.

**Avoid**

- ❌ **CRUD events** (`CustomerUpdated` with the whole row). You pay the full cost and keep none of the meaning.
- ❌ **Event sourcing the whole system**, instead of the part where history matters.
- ❌ **Side effects in projections**, which fire again on every replay.
- ❌ **Publishing internal events** as your public contract.
- ❌ **Querying the event store** for reads; that's what projections are for.
- ❌ **Treating CQRS and event sourcing as a package.** Either works alone.
- ❌ **Storing personal data in events** without a deletion plan.

## 11. Go deeper

Related material in this library:

- 📝 **[Message Queues & Event-Driven](message-queues-event-driven.md)**: the outbox, delivery guarantees, and idempotency.
- 📝 **[Stream Processing](stream-processing.md)**: stream–table duality is the same insight; a table is a fold of a stream.
- 📝 **[Serialization & Schema Evolution](serialization-schema-evolution.md)**: the compatibility rules events live under forever.
- 📝 **[Durable Execution](durable-execution.md)**: Temporal's event history is event sourcing for *workflows*: replay rebuilds state, which is why workflow code must be deterministic.
- 📝 **[Database Transactions & Isolation Levels](database-transactions-isolation.md)**: lost updates, and why optimistic concurrency works.
- 📝 **[Consistency Models & CAP](consistency-models-cap.md)**: the read-your-writes guarantee CQRS has to work to restore.
- 📝 **[Graph Engineering](graph-engineering.md)**: checkpointed agent state is a close cousin: each step is an update to immutable state, and you can resume from any point.

### Primary references

- Martin Fowler, [*Event Sourcing*](https://martinfowler.com/eaaDev/EventSourcing.html) (2005) and [*CQRS*](https://martinfowler.com/bliki/CQRS.html) (2011).
- Greg Young, [*CQRS Documents*](https://cqrs.files.wordpress.com/2010/11/cqrs_documents.pdf) (2010) and [*Versioning in an Event Sourced System*](https://leanpub.com/esversioning).
- Pat Helland, [*Immutability Changes Everything*](https://www.cidrdb.org/cidr2015/Papers/CIDR15_Paper16.pdf) (CIDR 2015).
- Overeem, Spoor & Jansen, [*The dark side of event sourcing: Managing data conversion*](https://ieeexplore.ieee.org/document/7884621) (SANER 2017).
- Microsoft Azure Architecture Center: [Event Sourcing pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing), [CQRS pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/cqrs).
- Tools: [KurrentDB](https://www.kurrent.io) (formerly EventStoreDB), [Marten](https://martendb.io) (event sourcing on Postgres).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
