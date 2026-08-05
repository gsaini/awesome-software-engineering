# ⏳ Durable Execution (Temporal & friends) — A Study Note

> **Level:** 🟡 Intermediate–Advanced · **Reading time:** ~20 min · **Prerequisites:** the [Message Queues](message-queues-event-driven.md), [Distributed Consensus](distributed-consensus.md), and [Concurrency](concurrency-parallelism.md) notes. Landscape current as of 2026 (Temporal 2.0's durable-execution release).

**Durable execution** is a way to write **long-running, multi-step business logic as ordinary code** — a normal function in Go/TypeScript/Python/Java — while a runtime guarantees it **runs to completion even if the process crashes, the server dies, or a step fails halfway through**. The engine persists every step's result and, on recovery, **replays** to reconstruct exactly where you were. It's the maturing answer to a problem every backend eventually hits: *"how do I reliably run a process that spans minutes, days, or weeks across flaky services?"* Temporal is the poster child (its **2.0** headlines durable execution); it's not alone.

> **The core magic:** normally, if your process crashes on step 4 of 7, you've lost your place — did step 3 commit? did the payment go through? Durable execution makes the *program itself* crash-proof: state lives in a persisted **event history**, not in memory, so a killed workflow resumes on another worker as if nothing happened. You write the happy path; the runtime handles crashes, retries, and timeouts. **It's what you'd build on top of your [idempotency + message-queue + consensus](message-queues-event-driven.md) notes — packaged as a programming model.**

## Table of contents

- [1. The problem it solves](#1-the-problem-it-solves)
- [2. How it works: event history & replay](#2-how-it-works-event-history--replay)
- [3. Workflows vs. activities](#3-workflows-vs-activities)
- [4. What you get for free](#4-what-you-get-for-free)
- [5. Sagas: orchestration made easy](#5-sagas-orchestration-made-easy)
- [6. The determinism constraint](#6-the-determinism-constraint)
- [7. The landscape](#7-the-landscape)
- [8. When to use it (and not)](#8-when-to-use-it-and-not)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. The problem it solves

Consider "process an order": charge the card → reserve inventory → book shipping → send email. Across four services, over an unknown duration, **anything can fail**:

- The process crashes after charging but before reserving → **money taken, no order**.
- A downstream service is down for 20 minutes → do you block? drop it? poll?
- You need to "wait 3 days, then send a reminder" → a cron? a queue with a delay? a DB flag + poller?

The traditional answers — **state machines in a DB + pollers**, **chained queue messages**, **cron jobs** — work, but you end up hand-building retry logic, timeout handling, state persistence, and recovery *for every workflow*. That glue is the [dual-write](message-queues-event-driven.md), [saga](#5-sagas-orchestration-made-easy), and idempotency plumbing you already know is fiddly and bug-prone. **Durable execution makes it the runtime's job.**

---

## 2. How it works: event history & replay

This is the part that will feel *deeply familiar* from your consensus note:

- As your workflow runs, every meaningful step (a call made, its result, a timer set, a signal received) is appended to a persisted, ordered **event history** — an **append-only log**.
- If the worker crashes, the workflow is picked up by **another worker**, which **replays the event history** from the start, feeding back the recorded results of already-completed steps *without re-executing their side effects* — until it reaches the point where it left off, then continues live.

```
Workflow code runs → each step's result appended to event history (the log)
        crash 💥
Another worker: replay the log → reconstruct exact state → resume where it stopped
```

> **This is the Replicated State Machine from [distributed consensus](distributed-consensus.md), wearing a new hat.** "Apply the same ordered log of events → arrive at the same state" is *exactly* how Raft replicas stay consistent. Durable execution applies it to **your business logic**: the event history is the log, replay is the state-machine application, and that's *why* your workflow code must be **deterministic** (§6) — same reason RSM commands must be.

---

## 3. Workflows vs. activities

The essential split (Temporal's vocabulary, mirrored elsewhere):

| | **Workflow** | **Activity** |
| - | ------------ | ------------ |
| **Is** | The **orchestration** logic — the sequence/decisions | A single **side-effecting** unit of work (call an API, write a DB) |
| **Runs** | Durably, via replay | Once per attempt, retried on failure |
| **Must be** | **Deterministic** (it's replayed) | Anything (I/O, non-determinism allowed) |
| **Guarantee** | Survives crashes, resumes exactly | **At-least-once** execution |

The rule: **workflows decide, activities do.** All the messy, non-deterministic, failure-prone I/O is quarantined into **activities**, which the runtime retries automatically. The workflow itself is pure orchestration and never re-runs a completed activity's side effect (replay feeds it the recorded result).

> Because activities are **at-least-once**, they **must be idempotent** — the runtime *will* retry them, so processing the same activity twice must be safe. This is your library's most-repeated lesson (**idempotency, ~8th sighting**), now a hard requirement of the model rather than a nice-to-have.

---

## 4. What you get for free

The value proposition is everything you'd otherwise hand-roll:

- **Automatic retries** with configurable **exponential backoff + jitter** (the same jitter from your caching/rate-limit notes) per activity.
- **Durable timers** — `sleep("30 days")` in workflow code *actually works*: the workflow suspends, consumes no resources, and wakes up in a month, crash-proof. No cron, no poller.
- **Timeouts** at every level (activity, workflow, heartbeat).
- **State persistence** — no DB schema for workflow state; the event history *is* the state.
- **Signals & queries** — send an external event *into* a running workflow (`signal`), or read its current state (`query`) without disturbing it.
- **Child workflows** and fan-out/fan-in for composing complex processes.
- **Visibility** — inspect exactly where any workflow is and what it's done.

---

## 5. Sagas: orchestration made easy

Your [message-queue note](message-queues-event-driven.md) introduced the problem of committing across services (dual-write) and hinted at **sagas** — sequences of local transactions with **compensating actions** to undo on failure. Durable execution makes sagas *natural*:

```python
try:
    await charge_card()        # activity
    await reserve_inventory()  # activity
    await book_shipping()      # activity
except Exception:
    # compensate in reverse — the runtime guarantees this block runs
    await refund_card()
    await release_inventory()
```

Because the workflow is durable, the **compensation logic is just a `try/except`** — no separate saga coordinator, no orchestration state machine in a DB. This is the big ergonomic win: **orchestration** (a central brain sequencing steps) becomes plain code, versus **choreography** (services reacting to events) which is more decoupled but far harder to reason about and debug. Durable execution makes orchestration cheap enough to prefer when you need a clear, debuggable process.

---

## 6. The determinism constraint

The one rule that trips everyone: **workflow code must be deterministic**, because it's replayed. Given the same event history, it must make the same decisions and calls in the same order — otherwise replay diverges from history and breaks.

So **inside a workflow you must not**:
- Call `now()`/`random()`/`uuid()` directly, read the filesystem/network, or use unordered iteration — anything whose result could differ between runs.
- Instead, get non-determinism *through the runtime*: a durable timer for time, a side-effect API for randomness, and **all I/O via activities** (whose results are recorded once and replayed).

> This is the **exact determinism requirement from the [consensus note](distributed-consensus.md)** (RSM commands must be deterministic) and the **[test-determinism](testing-strategy.md) lesson** (no wall-clock/random in code you need to reproduce). Three contexts, one rule: *if you need to replay it, it can't depend on non-deterministic inputs.* Also note **versioning**: changing workflow code while old workflows are mid-flight can break replay — you version/patch workflows, the same [backward-compatibility](serialization-schema-evolution.md) discipline as schema evolution.

---

## 7. The landscape

- **Temporal** — the category leader (open source + cloud); SDKs in Go/TS/Python/Java/.NET. **Temporal 2.0** headlines durable execution as *the* model. Descends from Uber's Cadence.
- **AWS Step Functions** — managed, JSON/ASL-defined state machines (less "just code," more visual/declarative).
- **Azure Durable Functions** — durable execution on Azure Functions.
- **Restate**, **Inngest**, **DBOS**, **Resonate** — a wave of newer, lighter-weight durable-execution engines (some database-backed, some serverless-first) — a sign the *pattern* is going mainstream, not just one product.

The trend line: durable execution is moving from "a Temporal thing" to a **general programming model** several tools offer — which is why it's worth understanding as a *concept*, not a vendor.

---

## 8. When to use it (and not)

**Great fit:**
- **Long-running, multi-step processes** — order fulfillment, onboarding, subscription lifecycles, provisioning.
- **Cross-service orchestration** needing reliability and compensation (sagas).
- **Human-in-the-loop / long waits** — "wait for approval, then continue" or "remind after 3 days."
- **Anything where losing your place mid-process is expensive.**

**Overkill / wrong tool:**
- **Simple request/response** — a plain [API call](api-design.md) is fine; don't wrap a synchronous lookup in a workflow.
- **High-frequency, low-latency** event processing — a [stream processor](message-queues-event-driven.md) fits better.
- **Fire-and-forget** background jobs with no orchestration — a [queue](message-queues-event-driven.md) is simpler.
- Teams unwilling to run/adopt the extra infrastructure and the determinism discipline.

---

## 9. Best practices & anti-patterns

**Do**
- **Keep workflows deterministic**; push *all* I/O and non-determinism into **activities**.
- **Make every activity idempotent** — at-least-once means retries will happen.
- **Use durable timers** for waits instead of cron/pollers.
- **Model failures with compensation** (saga try/except) rather than hoping.
- **Version/patch workflows** when changing logic that in-flight instances depend on.
- **Set timeouts and retry policies deliberately** per activity.

**Avoid**
- **Non-determinism in workflow code** (`now()`, `random()`, direct I/O) — the #1 bug; it breaks replay.
- **Non-idempotent activities** — double-charges on retry.
- **Business-critical state in memory** — the point is that the event history holds it.
- **Wrapping trivial synchronous work** in a workflow — needless complexity/infra.
- **Changing running workflows without versioning** — replay divergence.
- **Giant monolithic workflows** — decompose with child workflows.

---

## 10. Go deeper

Related material in this library — durable execution sits at the intersection of several:

- 📝 **[Message Queues & Event-Driven](message-queues-event-driven.md)** — the dual-write & saga problems durable execution solves ergonomically; orchestration vs. choreography.
- 📝 **[Distributed Consensus](distributed-consensus.md)** — event history + replay *is* the replicated-state-machine model; the determinism rule is shared.
- 📝 **[Concurrency & Parallelism](concurrency-parallelism.md)** — "let it crash," resume elsewhere: durable execution embraces the crash.
- 📝 **[Testing Strategy](testing-strategy.md)** & **[Serialization](serialization-schema-evolution.md)** — determinism and workflow versioning mirror test-determinism and backward-compatibility.
- 📝 **[Caching](caching-strategies.md)** / **[Load Balancing](load-balancing-rate-limiting.md)** — the built-in retry backoff + jitter is the same anti-thundering-herd tool.
- 📗 **[Designing Data-Intensive Applications](../books/)** — the reliability, logs, and derived-state foundations underneath.

### Primary references

- [Temporal documentation](https://docs.temporal.io/) and the durable-execution concept guide.
- [AWS Step Functions](https://docs.aws.amazon.com/step-functions/) and [Azure Durable Functions](https://learn.microsoft.com/azure/azure-functions/durable/) — managed alternatives.
- Pat Helland, *"Life Beyond Distributed Transactions"* — the conceptual ancestor (activities + idempotency instead of 2PC).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
