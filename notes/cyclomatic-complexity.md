# 🔀 Cyclomatic Complexity — A Detailed Study Note

> **Level:** 🟢 Beginner–Intermediate · **Reading time:** ~12 min · **Prerequisites:** you can read a function with `if`/`for`/`while`.

**Cyclomatic complexity** measures the number of **linearly independent paths** through a piece of code — in plain terms, *how many distinct routes execution can take*. It's a proxy for how hard code is to understand, test, and maintain. More branches ⇒ higher complexity ⇒ more ways to be wrong.

Introduced by **Thomas J. McCabe (1976)**, it's one of the oldest and most widely used software metrics, built into nearly every linter and code-quality tool.

## Table of contents

- [1. The intuition](#1-the-intuition)
- [2. How it's calculated](#2-how-its-calculated)
- [3. A worked example](#3-a-worked-example)
- [4. What the number means](#4-what-the-number-means)
- [5. Why it matters — the link to testing](#5-why-it-matters--the-link-to-testing)
- [6. Limitations](#6-limitations)
- [7. How to reduce it](#7-how-to-reduce-it)
- [8. Go deeper](#8-go-deeper)

---

## 1. The intuition

Straight-line code (no branches) has exactly **one** path — complexity **1**. Every decision point (a branch that can go two ways) adds another independent path:

```
no branches         one if              if + else-if
   │                  │                    │
   ▼                 ╱ ╲                  ╱│╲
  done              ▼   ▼               ▼ ▼ ▼
  CC = 1           CC = 2              CC = 3
```

So the metric is essentially: **count the decisions, add one.**

---

## 2. How it's calculated

Three equivalent ways:

### (a) Control-flow graph formula (McCabe's original)

Model the code as a graph where nodes are statements and edges are jumps between them:

```
M = E − N + 2P
```

- **E** = number of edges
- **N** = number of nodes
- **P** = number of connected components (a single function ⇒ `P = 1`, so `M = E − N + 2`)

### (b) The practical shortcut (decision points + 1)

Count each of these and add 1:

| Adds +1 to complexity |
| --------------------- |
| `if`, `else if` |
| `for`, `while`, `do-while` |
| `case` in a `switch` (each label) |
| `catch` clause |
| `&&`, `\|\|` (short-circuit boolean operators) |
| ternary `?:` |
| null-coalescing / optional chaining (language-dependent) |

> Note: an `else` (or the `default` of a switch) does **not** add complexity — it introduces no new *decision*, just the alternative of one already counted.

### (c) Regions of the graph

For a planar control-flow graph, cyclomatic complexity equals the number of enclosed **regions** + 1.

All three give the same number.

---

## 3. A worked example

```java
int classify(int n) {
    if (n < 0) {                 // +1
        return -1;
    }
    if (n == 0 || n == 100) {    // +1 for `if`, +1 for `||`
        return 0;
    }
    for (int i = 0; i < n; i++) {// +1
        if (i % 2 == 0) {        // +1
            doSomething(i);
        }
    }
    return 1;
}
```

Base 1 + (if) 1 + (if) 1 + (||) 1 + (for) 1 + (if) 1 = **6**.

This function needs roughly **6 test cases** to exercise every independent path.

---

## 4. What the number means

A widely used interpretation (McCabe / NIST guidance):

| Complexity | Risk & readability | Action |
| ---------: | ------------------ | ------ |
| **1–10**  | Simple, low risk | ✅ Fine |
| **11–20** | Moderate — getting complex | ⚠️ Watch it; consider refactoring |
| **21–50** | High risk, hard to test | 🔴 Refactor |
| **> 50**  | Untestable, unmaintainable | ⛔ Break it up now |

Many teams set a **CI gate** (e.g. fail the build if any method exceeds 10 or 15).

---

## 5. Why it matters — the link to testing

Cyclomatic complexity is (approximately) the **minimum number of test cases needed for full branch coverage**. That makes it a *budget*:

- A method with complexity 6 needs ~6 tests to cover every path.
- A method with complexity 40 needs ~40 — which almost never gets written, so those paths go untested and untested paths are where bugs live.

This direct tie between complexity and testability is exactly what the **[CRAP score](crap-score.md)** exploits: it combines complexity *and* actual test coverage into a single change-risk number.

---

## 6. Limitations

Cyclomatic complexity is useful but **narrow** — know what it doesn't capture:

- **Not the same as readability.** A long flat `switch` with 20 cases scores high but may be trivial to follow; deeply nested logic with the same score is far worse. See **cognitive complexity** (SonarSource) for a metric that weights nesting.
- **Ignores data complexity.** Pointer arithmetic, shared mutable state, and tricky types don't move the number.
- **Method-local.** It says nothing about coupling between modules or overall architecture.
- **Gameable.** Extracting a branch into a helper lowers one method's score without reducing total system complexity (though smaller methods are usually genuinely better).

Treat it as **one signal**, not a verdict.

---

## 7. How to reduce it

- **Extract methods** — pull a cohesive block of branches into a well-named helper.
- **Replace nested conditionals with guard clauses / early returns.**
- **Replace conditionals with polymorphism** — a strategy/state object instead of a big `switch`.
- **Use lookup tables / maps** instead of long `if-else` ladders.
- **Decompose boolean expressions** — extract `&&`/`||` chains into named predicates.
- **Apply "decompose conditional"** and other refactorings from Fowler's catalog.

---

## 8. Go deeper

- 📗 **[Refactoring (2nd Ed.) — Martin Fowler](../books/)** — the catalog of moves that lower complexity safely.
- 📗 **[Clean Code — Robert C. Martin](../books/)** — small functions and the "do one thing" rule.
- 📗 **[A Philosophy of Software Design — John Ousterhout](../books/)** — deep modules and managing complexity at the design level.
- 📝 **[CRAP Score](crap-score.md)** — the companion metric that pairs this with test coverage.

### Primary source

- T. J. McCabe, *"A Complexity Measure,"* IEEE Transactions on Software Engineering, 1976.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
