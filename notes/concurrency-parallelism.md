# 🧵 Concurrency & Parallelism — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~22 min · **Prerequisites:** you can read a function with shared variables; the [Transactions & Isolation](database-transactions-isolation.md) note rhymes with this one.

**Concurrency** is *dealing with* many things at once; **parallelism** is *doing* many things at once. They sound like synonyms and are constantly confused — getting the distinction right is the foundation for everything else here.

> **Rob Pike's line:** *"Concurrency is about structure, parallelism is about execution."* Concurrency is a way to **structure** a program as independent tasks that can make progress interleaved. Parallelism is **running** computations literally simultaneously on multiple cores. You can have concurrency on a single core (interleaving), and you need concurrency to *get* parallelism — but they're not the same thing.

## Table of contents

- [1. Concurrency vs. parallelism](#1-concurrency-vs-parallelism)
- [2. Why it's hard: shared mutable state](#2-why-its-hard-shared-mutable-state)
- [3. The concurrency bugs](#3-the-concurrency-bugs)
- [4. Synchronization primitives](#4-synchronization-primitives)
- [5. Deadlock & friends](#5-deadlock--friends)
- [6. Models: threads, async, actors, CSP](#6-models-threads-async-actors-csp)
- [7. CPU-bound vs. I/O-bound](#7-cpu-bound-vs-io-bound)
- [8. The memory model & atomics](#8-the-memory-model--atomics)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. Concurrency vs. parallelism

| | Concurrency | Parallelism |
| - | ----------- | ----------- |
| **About** | Structure — dealing with many tasks | Execution — doing many at once |
| **Needs multiple cores?** | No (can interleave on one) | Yes |
| **Example** | One cook juggling 3 dishes, switching between them | 3 cooks, one dish each |
| **Goal** | Responsiveness, throughput under waiting | Raw speed on divisible work |

Concurrency is a **design property**; parallelism is a **runtime property**. A well-structured concurrent program *can* run in parallel if hardware allows — but concurrency is useful even without parallelism (e.g. a server handling thousands of mostly-waiting connections on a few threads).

---

## 2. Why it's hard: shared mutable state

Almost every concurrency bug traces to one root cause: **multiple execution flows touching the same mutable data without coordination.**

The canonical example — two threads incrementing a shared counter:

```
counter = 0        # shared

# `counter += 1` is NOT atomic — it's really three steps:
#   1. read counter      → register
#   2. add 1             → register
#   3. write register    → counter

Thread A: read 0 ─┐
Thread B: read 0 ─┤   both read before either writes
Thread A: write 1 ┘
Thread B: write 1      ← B overwrites A's work

# Two increments, result = 1. One update was lost.
```

That "read-modify-write" hazard is **exactly the lost-update problem** from the [transactions note](database-transactions-isolation.md) — the same bug, one layer down. Databases solve it with isolation; in-process code solves it with the primitives in §4.

> **The safest concurrent state is no shared mutable state.** Immutability, message passing, and confinement (each piece of data owned by one task) eliminate whole categories of bugs by construction.

---

## 3. The concurrency bugs

- **Race condition** — correctness depends on the *timing/order* of operations. The counter above is one. Races are often invisible in testing and appear under load.
- **Data race** — a *specific*, stricter thing: two threads access the same memory location concurrently, at least one writes, with no synchronization. In C++/Go/Rust this is **undefined behavior**. (Every data race is a race condition; not every race condition is a data race.)
- **Deadlock** — two+ tasks each wait forever for a resource the other holds (§5).
- **Livelock** — tasks keep *reacting* to each other and change state but make no progress (two people stepping aside in a hallway, forever).
- **Starvation** — a task never gets the resource because others keep jumping ahead (unfair scheduling/locks).
- **Priority inversion** — a low-priority task holds a lock a high-priority task needs, and a medium task preempts the low one (famously nearly killed the Mars Pathfinder mission).

---

## 4. Synchronization primitives

Tools to coordinate access. Roughly increasing in abstraction:

| Primitive | What it does | Use for |
| --------- | ------------ | ------- |
| **Atomic** | Indivisible read-modify-write on one value (CAS, fetch-add) | Counters, flags — lock-free and fast |
| **Mutex (lock)** | Only one holder at a time | Protect a critical section / shared structure |
| **RWLock** | Many readers *or* one writer | Read-heavy shared data |
| **Semaphore** | N permits — bounds concurrency | Connection pools, rate limiting |
| **Condition variable** | Wait until a predicate is true, then wake | Producer/consumer signalling |
| **Barrier** | All N tasks wait until all arrive | Phased parallel algorithms |
| **Channel / queue** | Pass data between tasks safely | Message passing (§6) |

Golden rules for locks:
- **Hold locks for the shortest time possible** (like keeping DB transactions short).
- **Protect *invariants*, not just variables** — everything that must stay consistent together goes under the same lock.
- **The critical section is a cost** — it serializes execution, capping your parallel speedup (see Amdahl below).

---

## 5. Deadlock & friends

A **deadlock** needs all four **Coffman conditions** to hold simultaneously — break any one and you can't deadlock:

1. **Mutual exclusion** — resources aren't shareable.
2. **Hold and wait** — a task holds one resource while waiting for another.
3. **No preemption** — resources can't be forcibly taken.
4. **Circular wait** — a cycle of tasks each waiting on the next.

The most practical fix targets #4: **acquire locks in a consistent global order** everywhere. If every code path locks `A` before `B`, no cycle can form.

Other defenses:
- **Lock timeouts / `tryLock`** — give up rather than wait forever.
- **Lock-free / single lock** — fewer locks, fewer cycles.
- **Deadlock detection** — databases build a wait-for graph and abort a victim (the transactions note's deadlock handling).

> **Detection vs. avoidance vs. prevention:** *prevention* removes a Coffman condition by design (lock ordering); *avoidance* refuses allocations that could deadlock (Banker's algorithm — rare in practice); *detection* lets it happen and recovers (what most databases do).

---

## 6. Models: threads, async, actors, CSP

Different languages give you different concurrency models. Know the shapes:

**OS threads + shared memory + locks** (Java, C++, pthreads)
- Preemptive, true parallelism, but shared mutable state → all the bugs above. Threads are relatively heavy (MBs of stack).

**Async / event loop** (JavaScript, Python `asyncio`, Rust `async`)
- **Cooperative** concurrency on (often) one thread: tasks `await` at explicit yield points; the loop runs others while one waits on I/O.
- No data races *within* the loop (single-threaded), but **not parallel** — a CPU-bound task blocks everything. Great for **I/O-bound** work with huge fan-out.

**Actors** (Erlang/Elixir, Akka)
- Independent actors with **private state**, communicating only by **async messages**. No shared memory → no locks. Erlang's "let it crash" + supervision trees give extreme fault tolerance.

**CSP — Communicating Sequential Processes** (Go)
- Lightweight **goroutines** communicating over **channels**. *"Don't communicate by sharing memory; share memory by communicating."* Goroutines are cheap (KBs), so you can spawn millions.

> The trend across all the safe models is the same: **replace shared mutable state with message passing.** It trades a little overhead for eliminating races by construction — the same instinct as immutability.

A note on Python: the **GIL (Global Interpreter Lock)** historically meant threads couldn't run Python bytecode in parallel (fine for I/O, useless for CPU — use processes instead). Recent versions ship an experimental **free-threaded (no-GIL)** build changing this.

---

## 7. CPU-bound vs. I/O-bound

The single most important question before choosing an approach:

| Workload | Bottleneck | Right tool |
| -------- | ---------- | ---------- |
| **I/O-bound** | Waiting on network/disk/DB | **Async / concurrency** — overlap the waiting; one thread handles thousands of connections |
| **CPU-bound** | Doing actual computation | **Parallelism** — multiple threads/processes/cores; async gives you *nothing* here |

Getting this wrong is a classic mistake: throwing `async` at a CPU-bound task (no speedup, it can't overlap computation), or spawning hundreds of OS threads for I/O (memory blowup where an event loop would sail).

**Amdahl's Law** bounds parallel speedup: if a fraction *p* of the work is parallelizable, max speedup on *N* cores is `1 / ((1−p) + p/N)`. If 5% is inherently serial, you can *never* exceed 20× no matter how many cores. **The serial part dominates** — which is why minimizing critical sections matters so much.

---

## 8. The memory model & atomics

On multicore hardware, threads don't automatically see each other's writes in order. Compilers and CPUs **reorder** operations for speed, and each core has its own caches. Without synchronization, Thread B may see Thread A's writes **out of order or not at all**.

- A **memory model** (Java JMM, C++11, Go) defines the rules for when one thread's write is guaranteed **visible** to another — the **happens-before** relationship.
- **Atomics** provide both indivisibility *and* ordering guarantees (**memory barriers/fences**), which is why a plain `volatile`/non-atomic variable is not enough for lock-free code.
- **Compare-And-Swap (CAS)** is the workhorse of lock-free algorithms: "set X to B, but only if it's still A." Loop on it and you have a lock-free update.

> Practical takeaway: **you cannot reason about multithreaded visibility with single-threaded intuition.** Use atomics, locks, or higher-level primitives that establish happens-before — don't hand-roll clever flag tricks.

---

## 9. Best practices & anti-patterns

**Do**
- **Prefer no shared mutable state** — immutability, confinement, message passing.
- **Prefer high-level primitives** — a thread pool / channel / `Executor` over raw threads and hand-rolled locks.
- **Match the tool to the workload** — async for I/O-bound, parallelism for CPU-bound.
- **Lock in a consistent global order**; hold locks briefly; protect invariants.
- **Make shared operations atomic** (or guarded), never "probably fine."
- **Test under contention** — load, fuzzing, thread sanitizers (`-fsanitize=thread`, Go's `-race`).

**Avoid**
- **Sharing mutable state "just this once"** — it's the root of most races.
- **Rolling your own lock-free code** without deeply understanding the memory model.
- **Async for CPU-bound work** (no parallelism) or **thousands of OS threads for I/O** (use an event loop).
- **Nested locks in inconsistent order** (→ deadlock).
- **Assuming operations are atomic** because they're one line (`counter += 1` is not).
- **Blocking calls inside an event loop** — one blocking op stalls every task.

---

## 10. Go deeper

Related material in this library:

- 📝 **[Transactions & Isolation Levels](database-transactions-isolation.md)** — the same hazards (lost update, deadlock) at the database layer; locking (2PL) vs. MVCC is this note's mutex-vs-optimistic story writ large.
- 📝 **[Loop Engineering](loop-engineering.md)** — "let the loop restart" and Erlang's "let it crash" are the same fault-tolerance instinct.
- 📗 **[Designing Data-Intensive Applications — Kleppmann](../books/)** — Ch. 7–9 push these ideas into distributed systems.
- 📗 **[Computer Systems: A Programmer's Perspective (CS:APP)](../books/)** — Ch. 12, concurrent programming from the machine up.
- 🎓 **[Berkeley CS162: Operating Systems](../courses/)** — threads, synchronization, and deadlock with real projects.

### Primary references

- Rob Pike, *"Concurrency Is Not Parallelism"* (2012 talk) — the canonical framing.
- Herlihy & Shavit, *The Art of Multiprocessor Programming* — the definitive text on synchronization and lock-free algorithms.
- G. Amdahl, *"Validity of the single processor approach..."* 1967 — Amdahl's Law.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
