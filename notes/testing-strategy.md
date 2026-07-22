# 🧪 Testing Strategy — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~20 min · **Prerequisites:** you've written a test before; pairs with the [CRAP Score](crap-score.md) and [Agent Evaluator](building-agent-evaluators.md) notes.

Testing isn't about proving code is correct (you can't) — it's about **buying confidence to change it**. A good test suite is a safety net that lets you refactor and ship without fear; a bad one is a slow, flaky tax that everyone learns to ignore. Strategy is choosing *which* tests to write so you get maximum confidence per unit of effort.

> **The reframe:** the goal is not "100% coverage" or "lots of tests." The goal is **confidence that a change didn't break anything**, delivered **fast enough that you actually run them.** Every decision below trades against those two axes.

## Table of contents

- [1. Why we test](#1-why-we-test)
- [2. The pyramid (and its rivals)](#2-the-pyramid-and-its-rivals)
- [3. The kinds of tests](#3-the-kinds-of-tests)
- [4. Test doubles](#4-test-doubles)
- [5. What makes a good test](#5-what-makes-a-good-test)
- [6. TDD](#6-tdd)
- [7. Coverage vs. confidence](#7-coverage-vs-confidence)
- [8. Flaky tests](#8-flaky-tests)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. Why we test

- **Confidence to change** — the real product of a test suite. Without it, every refactor is a gamble and the code ossifies.
- **Executable specification** — tests document what the code is *supposed* to do, and never go stale (a wrong test fails).
- **Regression safety net** — a bug fixed with a test stays fixed.
- **Design pressure** — hard-to-test code is usually badly-coupled code. Testability and good design correlate.

> Tests are a cost *and* an asset. The strategy question is maximizing the asset (confidence) while minimizing the cost (write time, run time, maintenance).

---

## 2. The pyramid (and its rivals)

**The Test Pyramid** (Mike Cohn) — the classic distribution:

```
        /\        E2E         few · slow · brittle · high-confidence-per-test
       /  \       ─────
      / int \     Integration
     /------- \   ─────
    /   unit   \  Unit       many · fast · cheap · narrow
   /------------\
```

The shape encodes the trade-off: **push most tests down** where they're fast and cheap; keep the slow, flaky ones at the top rare. More tests as you go down.

Two rival shapes worth knowing:

- **Testing Trophy** (Kent C. Dodds) — for modern app/UI code, emphasizes the **integration** layer ("write tests that resemble how the software is used") over lots of isolated unit tests. Static analysis (types, lint) forms the base.
- **Ice-cream cone** (the *anti*-pattern) — the pyramid inverted: tons of slow manual/E2E tests, few unit tests. Slow, flaky, expensive. What happens when nobody enforces the pyramid.

> There's no universal ratio. The durable rule: **push tests to the lowest level that still gives real confidence** for *your* kind of software.

---

## 3. The kinds of tests

| Type | Scope | Confidence | Speed |
| ---- | ----- | ---------- | ----- |
| **Unit** | One function/class in isolation | Logic correctness | ⚡⚡⚡ |
| **Integration** | Several units + real collaborators (DB, queue) | Wiring actually works | ⚡⚡ |
| **End-to-end (E2E)** | The whole system, like a user | The system *works* | 🐢 |
| **Contract** | Provider ↔ consumer agree on an API shape | Services won't break each other | ⚡⚡ |
| **Smoke** | A few critical paths | "Is it fundamentally alive?" | ⚡⚡ |
| **Regression** | Pins a fixed bug | It stays fixed | varies |
| **Property-based** | Invariants over generated inputs | Edge cases you'd never enumerate | ⚡⚡ |
| **Performance/load** | Latency, throughput under load | Non-functional SLOs | 🐢 |
| **Mutation** | Tests of your tests (§7) | Are the tests actually strong? | 🐢 |

Two under-used gems:
- **Property-based testing** (QuickCheck, fast-check, Hypothesis) — instead of hand-picking examples, assert a *property* ("reverse(reverse(x)) == x") and let the tool generate hundreds of inputs, shrinking any failure to a minimal case. Finds edges you'd never think of.
- **Contract testing** (Pact) — consumer-driven contracts verify a provider and consumer still agree, without spinning up both. The reliability answer for the [API-design](api-design.md) versioning problem.

---

## 4. Test doubles

"Mock" is used loosely; the precise taxonomy (Meszaros / Fowler) matters because it clarifies *what you're actually testing*:

| Double | What it is |
| ------ | ---------- |
| **Dummy** | A placeholder passed but never used (fill a parameter). |
| **Stub** | Returns canned answers to calls (`getUser()` → a fixed user). |
| **Spy** | A stub that also *records* how it was called. |
| **Mock** | Pre-programmed with expectations; **fails if not called as specified**. |
| **Fake** | A working lightweight implementation (in-memory DB, fake clock). |

**Two schools:**
- **Classicist (Detroit/Chicago)** — use real objects; double only what's awkward (network, time, randomness). Tests verify *state/outcome*.
- **Mockist (London)** — mock all collaborators; test one unit in true isolation. Tests verify *interactions*.

> The classicist critique of over-mocking: **mocks test that you called things a certain way, not that the result is correct.** Heavily-mocked tests pass while the real integration is broken, and they shatter on every refactor. Prefer **fakes over mocks**, and mock only at genuine boundaries (I/O, time, third parties).

---

## 5. What makes a good test

**FIRST** (from *Clean Code*):
- **Fast** — milliseconds, or you won't run them.
- **Independent** — no ordering dependencies; no shared mutable state (the [concurrency](concurrency-parallelism.md) lesson, applied to tests).
- **Repeatable** — same result every run, any environment (no reliance on wall-clock, network, or `Math.random`).
- **Self-validating** — a clear pass/fail, no manual log-reading.
- **Timely** — written with (or before) the code, not bolted on later.

**Structure — AAA / Given-When-Then:**

```js
test("withdraw fails when balance is insufficient", () => {
  const account = new Account({ balance: 50 });   // Arrange
  const result = account.withdraw(100);           // Act
  expect(result.ok).toBe(false);                  // Assert
  expect(account.balance).toBe(50);               // (unchanged)
});
```

**The single most important principle: test *behavior*, not *implementation*.** Assert on observable outcomes (return values, state, emitted events), never on private internals or "was this method called." Behavior tests survive refactors; implementation tests break on every one — which trains people to delete tests. A good test changes only when the *requirement* changes.

Also: **name tests by the behavior** (`withdraw_fails_when_insufficient`, not `test3`), and keep **one logical assertion / concept per test** so a failure names the exact broken behavior.

---

## 6. TDD

**Test-Driven Development** — the red-green-refactor loop:

1. 🔴 **Red** — write a failing test for the next small behavior.
2. 🟢 **Green** — write the minimum code to pass.
3. 🔵 **Refactor** — clean up with the test as a safety net. Repeat.

What it buys: 100%-meaningful coverage by construction, testable design (you feel coupling immediately), and a tight feedback loop. Criticisms: it's a discipline that doesn't fit exploratory/spike work well, and dogmatic TDD can over-focus on unit tests. Pragmatic take: **invaluable for well-understood logic; relax it while exploring**, then backfill tests before merge.

> Note the resonance with [loop engineering](loop-engineering.md): "negotiate the contract first" is TDD's "write the test first" — agree on the observable criteria before building.

---

## 7. Coverage vs. confidence

Coverage measures *what code ran*, **not whether it was actually verified**:

- **Line coverage** — lines executed. Weak (a line can run with no assertion).
- **Branch coverage** — each `if`/`else` path taken. Better; ties to [cyclomatic complexity](cyclomatic-complexity.md) (≈ the number of paths, and roughly the minimum test count for full branch coverage).

**The core trap — coverage ≠ assertions.** A test that *executes* a function but asserts nothing counts as 100% coverage while verifying nothing. This is the exact **"coverage theater"** warning from the [CRAP-score](crap-score.md) and [agent-evaluator](building-agent-evaluators.md) notes: coverage is necessary, not sufficient.

**Mutation testing** is the honest measure of test *strength*: it introduces small faults ("mutants" — flip a `<` to `<=`, delete a line) and checks whether your tests **catch** them. The **mutation score** (mutants killed) tells you if your assertions actually assert. Tools: **Stryker** (JS/TS), **PIT** (Java). Slow, so run it periodically on critical modules — but it exposes the hollow tests coverage hides.

> Treat coverage as a **floor to spot untested code**, never a **target to hit**. "90% coverage" mandated as a KPI just breeds assertion-free tests. (Goodhart's law: a measure that becomes a target stops being a good measure.)

---

## 8. Flaky tests

A **flaky test** passes and fails on the same code without changes. They're corrosive out of proportion to their number: **a suite people don't trust is a suite people ignore** — one flaky test poisons confidence in all the green ones.

Common causes:
- **Async/timing** — waiting on a fixed `sleep` instead of a condition; race conditions (the [concurrency](concurrency-parallelism.md) note, in your test harness).
- **Shared state / test ordering** — one test leaks state into another (violates *Independent*).
- **Non-determinism** — real `Date.now()`, `Math.random()`, unstable map ordering.
- **External dependencies** — real network, real time, a shared DB.

Fixes: inject a **fake clock** and seed randomness; make each test set up and tear down its own state; wait on **conditions, not timeouts**; stub the network at the boundary; run tests in random order in CI to *surface* hidden coupling.

> Zero-tolerance policy that works: **quarantine** a newly-flaky test immediately (so it stops blocking merges) and **fix or delete** it fast — never leave a known-flaky test failing in the main suite, or people start ignoring red.

---

## 9. Best practices & anti-patterns

**Do**
- **Push tests to the lowest level that gives real confidence**; keep E2E few and focused on critical journeys.
- **Test behavior, not implementation** — assert outcomes, not internals.
- **Keep tests FIRST** — fast, independent, deterministic.
- **Prefer fakes over mocks**; double only at real boundaries (I/O, time, randomness).
- **Use coverage to find gaps**, and mutation testing to judge test strength.
- **Fix or quarantine flaky tests immediately.**
- **Run the fast tests on every save/PR**; gate merges on green.

**Avoid**
- **The ice-cream cone** — mostly slow E2E/manual tests.
- **Over-mocking** — tests that verify calls, not correctness, and break on every refactor.
- **Testing private implementation details** — brittle by construction.
- **Coverage as a KPI** — breeds assertion-free tests (Goodhart).
- **Brittle snapshot tests** rubber-stamped on every update (a snapshot nobody reads is coverage theater).
- **Tolerating flakiness** — it silently destroys trust in the whole suite.

---

## 10. Go deeper

Related material in this library:

- 📝 **[CRAP Score](crap-score.md)** — combines complexity and coverage; the "coverage ≠ assertions" caveat starts here.
- 📝 **[Cyclomatic Complexity](cyclomatic-complexity.md)** — complexity ≈ the minimum number of tests for full branch coverage.
- 📝 **[Building an Agent Evaluator](building-agent-evaluators.md)** — evals *are* tests for non-deterministic systems; deterministic gates first, and the same coverage-theater trap.
- 📝 **[API Design](api-design.md)** — contract testing (Pact) solves the versioning/compatibility problem.
- 📗 **[Working Effectively with Legacy Code — Michael Feathers](../books/)** — how to get untested code under test so you can change it.
- 📗 **[Clean Code — Robert C. Martin](../books/)** — the FIRST principles and clean-test discipline.

### Primary references

- Mike Cohn, *Succeeding with Agile* (2009) — the Test Pyramid.
- Martin Fowler, ["Mocks Aren't Stubs"](https://martinfowler.com/articles/mocksArentStubs.html) — the test-double taxonomy and the two schools.
- Kent Beck, *Test-Driven Development: By Example* (2002).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
