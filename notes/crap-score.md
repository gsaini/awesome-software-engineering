# 💩 CRAP Score — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~12 min · **Prerequisites:** [Cyclomatic Complexity](cyclomatic-complexity.md) and the idea of test coverage.

**CRAP** stands for **Change Risk Anti-Patterns** (also "Change Risk Analysis and Predictions"). The CRAP score estimates **how risky a method is to change** by combining two things a single metric alone misses: **how complex the code is** and **how well it's tested**.

The insight: complex code isn't automatically dangerous — complex code *without tests* is. Simple, well-covered code is safe to change; complex, uncovered code is a minefield. CRAP puts both on one axis. It was introduced by **Alberto Savoia and Bob Evans (2007)** in the `crap4j` tool.

## Table of contents

- [1. The formula](#1-the-formula)
- [2. Why it's shaped this way](#2-why-its-shaped-this-way)
- [3. Worked examples](#3-worked-examples)
- [4. The threshold: 30](#4-the-threshold-30)
- [5. The two ways to lower CRAP](#5-the-two-ways-to-lower-crap)
- [6. Limitations](#6-limitations)
- [7. Tooling](#7-tooling)
- [8. Go deeper](#8-go-deeper)

---

## 1. The formula

For a method `m`:

```
CRAP(m) = comp(m)²  ×  (1 − cov(m))³  +  comp(m)
```

- **`comp(m)`** = the method's [cyclomatic complexity](cyclomatic-complexity.md).
- **`cov(m)`** = automated-test code coverage, as a fraction from **0** (untested) to **1** (fully covered). *(If you use percentages, it's `(1 − cov/100)³`.)*

The score has no upper bound; **lower is better**.

---

## 2. Why it's shaped this way

Read the formula as two terms:

- **`comp(m)`** (the trailing term) — a **floor**. Even with 100% coverage, a method's CRAP can never drop below its own complexity. Complexity always carries *some* irreducible risk.
- **`comp(m)² × (1 − cov)³`** — the **penalty** for missing tests. It's:
  - **quadratic in complexity** — risk from complexity grows *fast*;
  - **cubic in the coverage gap** — being untested is punished *hard*, and each additional bit of coverage buys a lot of forgiveness.

At full coverage (`cov = 1`), the penalty term is `× 0³ = 0`, so **`CRAP = comp`**. At zero coverage (`cov = 0`), the penalty is `× 1³ = comp²`, so **`CRAP = comp² + comp`** — the worst case.

---

## 3. Worked examples

| Complexity | Coverage | Penalty term | CRAP | Verdict |
| ---------: | -------: | ------------ | ---: | ------- |
| 5  | 100% | 25 × 0    = 0    | **5**    | ✅ great |
| 5  | 0%   | 25 × 1    = 25   | **30**   | ⚠️ at the limit |
| 10 | 100% | 100 × 0   = 0    | **10**   | ✅ fine |
| 10 | 80%  | 100 × 0.008 = 0.8 | **10.8** | ✅ fine |
| 10 | 50%  | 100 × 0.125 = 12.5| **22.5** | ⚠️ okay-ish |
| 10 | 0%   | 100 × 1   = 100  | **110**  | 🔴 dangerous |
| 20 | 100% | 400 × 0   = 0    | **20**   | ✅ (but complex) |
| 30 | 100% | 900 × 0   = 0    | **30**   | ⚠️ exactly at limit |
| 40 | 100% | 1600 × 0  = 0    | **40**   | 🔴 fails on complexity alone |

Notice the last rows: once complexity exceeds ~30, **no amount of testing** brings CRAP under the threshold. That's deliberate — the metric is telling you to *refactor*, not just test harder.

---

## 4. The threshold: 30

`crap4j`'s default acceptable ceiling is **CRAP ≤ 30**. What it takes to stay under 30 by coverage alone:

| Complexity | Coverage needed to hit CRAP ≤ 30 |
| ---------: | -------------------------------- |
| ≤ 5   | 0% (already ≤ 30) |
| 10    | ~42% |
| 15    | ~60% |
| 20    | ~71% |
| 25    | ~80% |
| 30    | 100% |
| ≥ 31  | **impossible** — must reduce complexity |

The curve makes the point: the more complex a method, the closer to 100% coverage you need just to keep it in bounds — until complexity gets so high that testing can't save it.

---

## 5. The two ways to lower CRAP

There are exactly two levers, and CRAP makes the trade-off explicit:

1. **Write tests** — raise `cov(m)`. Cheap and effective for low-to-moderate complexity. Going from 0% → 80% coverage on a complexity-10 method drops CRAP from 110 to ~10.8.
2. **Refactor** — lower `comp(m)`. The only option once complexity is high, and it lowers *both* terms at once (it shrinks the floor **and** the penalty). See [reducing cyclomatic complexity](cyclomatic-complexity.md#7-how-to-reduce-it).

> Rule of thumb: **test simple code, refactor complex code.** CRAP tells you which method you're looking at.

---

## 6. Limitations

- **Only as good as its inputs.** Coverage measures *lines/branches executed*, not *assertions made*. A test that runs a method but asserts nothing still counts as coverage — so CRAP can be gamed with hollow tests.
- **Inherits cyclomatic complexity's blind spots** — it doesn't see nesting depth, data complexity, or coupling (see [that note's limitations](cyclomatic-complexity.md#6-limitations)).
- **Method-level.** It flags risky methods, not risky architectures.
- **Coverage type matters.** Branch coverage is a more honest input than line coverage.

Use CRAP to **prioritize** where to spend refactoring/testing effort — not as an absolute quality verdict.

---

## 7. Tooling

- **`crap4j`** — the original Java tool (now largely historical).
- **PHPUnit** — reports CRAP per method in its coverage HTML output.
- **OpenClover**, **Clover** — surface CRAP for Java/Groovy.
- Many coverage tools expose complexity + coverage so you can compute CRAP yourself or via plugins.

Practical workflow: sort methods by CRAP descending, and attack the top of the list first — that's your highest change-risk surface.

---

## 8. Go deeper

- 📝 **[Cyclomatic Complexity](cyclomatic-complexity.md)** — the `comp(m)` half of the formula; read it first.
- 📗 **[Working Effectively with Legacy Code — Michael Feathers](../books/)** — how to add tests to scary, high-CRAP code before changing it.
- 📗 **[Refactoring (2nd Ed.) — Martin Fowler](../books/)** — the moves that lower complexity safely.
- 📗 **[Clean Code — Robert C. Martin](../books/)** — small, single-purpose functions keep both inputs low.

### Primary source

- Alberto Savoia & Bob Evans, *"CRAP — Change Risk Analysis and Predictions"* / the `crap4j` project (2007).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
