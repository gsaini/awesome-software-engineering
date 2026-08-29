# 🧭 dependency-cruiser — A Study Note

> **Level:** 🟢 Beginner–Intermediate · **Reading time:** ~12 min · **Prerequisites:** JS/TS modules & imports. Pairs with the [Knip](knip.md), [Git Internals](git-internals.md), and [Graph Engineering](graph-engineering.md) notes — a dependency graph *is* a graph.

**dependency-cruiser** (by Sander Verweij) **validates and visualizes the dependency graph** of a JavaScript/TypeScript codebase. You write rules about what may depend on what — "no circular dependencies," "the domain layer must not import from the UI," "nothing imports test code" — and it **enforces them in CI**, failing the build on a violation. It also **draws the graph** so you can actually *see* your architecture.

> **The core idea:** your codebase is a **graph** — modules are nodes, `import`s are edges. Left unwatched, that graph rots: cycles creep in, layers leak into each other, dead files pile up. dependency-cruiser makes the graph a **first-class, rule-checked artifact** — turning "we have an architecture" (a diagram in someone's head) into an **executable constraint** the build verifies on every commit.

## Table of contents

- [1. The two jobs](#1-the-two-jobs)
- [2. Validation: rules over the graph](#2-validation-rules-over-the-graph)
- [3. The rules you'll actually write](#3-the-rules-youll-actually-write)
- [4. Visualization](#4-visualization)
- [5. Setup & CI](#5-setup--ci)
- [6. dependency-cruiser vs. Knip vs. Madge](#6-dependency-cruiser-vs-knip-vs-madge)
- [7. Best practices & anti-patterns](#7-best-practices--anti-patterns)
- [8. Go deeper](#8-go-deeper)

---

## 1. The two jobs

- **Validate** — check the dependency graph against **rules** you define; report (and fail CI on) violations.
- **Visualize** — render the graph to SVG/Graphviz/Mermaid so you can inspect structure, spot cycles, and onboard people.

It understands **ES modules, CommonJS, TypeScript (incl. types), AMD**, monorepos, and resolves **path aliases** (tsconfig `paths`, webpack resolve). Stack-agnostic within the JS/TS world.

---

## 2. Validation: rules over the graph

Rules live in `.dependency-cruiser.js` (scaffold with `depcruise --init`). The model is simple — a rule matches a **`from` → `to`** edge by path regex and assigns a **severity**:

```js
// .dependency-cruiser.js (excerpt)
module.exports = {
  forbidden: [
    {
      name: 'no-circular',
      severity: 'error',
      comment: 'Circular dependencies make code hard to reason about and test.',
      from: {},
      to: { circular: true },
    },
    {
      name: 'domain-not-to-ui',
      severity: 'error',
      comment: 'The domain layer must not depend on the UI layer.',
      from: { path: '^src/domain' },
      to:   { path: '^src/ui' },
    },
  ],
};
```

Three rule kinds:
- **`forbidden`** — this edge must **not** exist (the common case: cycles, layer violations, test-code leaks).
- **`allowed`** — a whitelist: anything *not* matching is flagged (strict allow-listing of dependencies).
- **`required`** — this module type **must** depend on something (e.g. every React component must import the design system).

**Severity** is `error` (fails CI), `warn`, or `info`. dependency-cruiser ships a **`--init` "recommended" ruleset** (no-circular, no-orphans, not-to-unresolvable, not-to-dev-dep, no-duplicate-dep-types, …) — a strong starting point.

---

## 3. The rules you'll actually write

The high-value ones:

- **No circular dependencies** (`to: { circular: true }`) — cycles cause init-order bugs, break tree-shaking, and make modules un-testable in isolation. The flagship check.
- **Layer / architecture boundaries** — enforce your intended direction of dependency: `domain` ↛ `ui`, `core` ↛ `features`, `shared` ↛ `app`. This is **"architecture as an executable rule"** (a *fitness function*) — the [dependency rule](../books/) of clean architecture, checked automatically.
- **No orphans** — modules nothing imports (dead files — overlaps with [Knip](knip.md)).
- **No prod → dev/test** — application code must not import test utilities or `devDependencies`.
- **No reaching into another module's internals** — force imports through a package's public entry (`index.ts`), not deep paths.
- **Not-to-deprecated / not-to-unresolvable** — catch imports of deprecated core modules or things that don't resolve.

> The pattern: encode the **architecture decisions that are otherwise just tribal knowledge** as rules, so a new contributor (or a rushed PR) *can't* violate them without the build going red.

---

## 4. Visualization

dependency-cruiser emits the graph in many formats; the classic path is **Graphviz dot → SVG**:

```bash
# whole-graph SVG
depcruise src --include-only "^src" --output-type dot | dot -T svg > dependency-graph.svg

# a high-level, folder-collapsed view (readable for big repos)
depcruise src --output-type ddot | dot -T svg > architecture.svg

# mermaid (renders in Markdown/GitHub)
depcruise src --output-type mermaid
```

- **`dot`** = module-level graph; **`ddot`/`archi`** = folder/area-level (essential — a per-file graph of a big repo is unreadable).
- **Cycles are highlighted** (usually red), so the picture doubles as a diagnosis.
- Great for **onboarding** ("here's how the codebase is shaped") and for **arguing about architecture** with a real picture instead of a whiteboard guess. (Pipe it into [React Flow](react-flow.md) or view the SVG — same node/edge model, made visible.)

---

## 5. Setup & CI

```bash
pnpm add -D dependency-cruiser        # any package manager (see pnpm note)
pnpm depcruise --init                 # interactive: scaffolds .dependency-cruiser.js with recommended rules

# validate (exits non-zero on an `error`-severity violation → fails CI)
pnpm depcruise src --config
```

Then gate it in CI (GitHub Actions et al.): a new circular dependency or a layer violation **fails the PR**, not "someone notices in review three weeks later." This is the same **shift-left, executable-constraint** instinct as [tests](testing-strategy.md), [commit linting](commitlint.md), and [Knip](knip.md) — catch the structural regression at the commit that introduces it.

You can also generate the visualization as a CI artifact so the architecture picture stays current.

---

## 6. dependency-cruiser vs. Knip vs. Madge

They overlap but answer different questions — often used together:

| Tool | Primary question | Superpower |
| ---- | ---------------- | ---------- |
| **dependency-cruiser** | "Does the dependency *structure* obey my rules?" | Custom **rule validation** (cycles, layers, boundaries) + visualization |
| **[Knip](knip.md)** | "What's **unused**?" | Dead files/deps/exports removal |
| **Madge** | "Show me the graph / any cycles?" | Lightweight visualization + circular-dep detection |

- **dependency-cruiser** is the most powerful for **enforcing architecture** (arbitrary rules).
- **Knip** is the go-to for **decluttering** (unused code).
- **Madge** is a lighter option if you only want a picture and cycle detection.

A common setup: **Knip** to keep the graph lean + **dependency-cruiser** to keep its *shape* legal — both in CI.

---

## 7. Best practices & anti-patterns

**Do**
- **Start from `--init` recommended rules**, then add your **layer/boundary** rules.
- **Enforce `no-circular` early** — cycles are far cheaper to prevent than to untangle later.
- **Run it in CI** at `error` severity so violations block the merge.
- **Encode architecture as rules** (the fitness-function idea) — make the intended dependency direction executable.
- **Use folder-level (`ddot`/archi) views** for big repos; module-level for a specific area.
- **Pair with Knip** — structure rules + dead-code removal.

**Avoid**
- **A per-file graph of a huge repo** — unreadable; collapse to folders.
- **Rules nobody agreed to** — align the team on the architecture *before* enforcing it, or the build just annoys people into `--no-verify`-style workarounds.
- **`warn`-only in CI** — a warning nobody must fix is decoration; gate the important rules at `error`.
- **Ignoring cycles** — they compound and quietly break tree-shaking and testability.
- **Treating it as a security or dead-code tool** — it validates *structure*; use [Knip](knip.md)/audit for those.

---

## 8. Go deeper

Related material in this library:

- 📝 **[Graph Engineering](graph-engineering.md)** & **[Data Structures & Algorithms](data-structures-algorithms.md)** — a dependency graph is a graph; circular deps are cycles; a clean architecture is a DAG.
- 📝 **[Knip](knip.md)** — the dead-code companion; both walk the module graph, different questions.
- 📝 **[pnpm Tips](pnpm-tips.md)** & **[commitlint](commitlint.md)** — the Tooling & DevEx family; shift-left, CI-enforced checks.
- 📝 **[Testing Strategy](testing-strategy.md)** — architecture rules are "fitness-function tests" for structure.
- 📝 **[React Flow](react-flow.md)** — visualizing a dependency graph as an interactive node/edge diagram.
- 📗 **[Software Architecture books](../books/)** — the *dependency rule* and layering these rules enforce.

### Primary references

- [dependency-cruiser on GitHub](https://github.com/sverweij/dependency-cruiser) and its docs (rules reference, output formats, `--init`).
- Neal Ford et al., *Building Evolutionary Architectures* — the "architectural fitness function" idea dependency-cruiser operationalizes.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
