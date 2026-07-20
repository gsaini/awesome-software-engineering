# ✂️ Knip — A Study Note

> **Level:** 🟢 Beginner–Intermediate · **Reading time:** ~12 min · **Scope:** Knip **v6** (works with npm, pnpm, Yarn, and Bun). Pairs with the [pnpm](pnpm-tips.md) and [Security Fundamentals](security-fundamentals.md) notes.

**Knip** ("to cut" in Dutch — *"knip it before you ship it"*) finds and fixes **unused files, dependencies, and exports** in JavaScript/TypeScript projects. It's a linter for your *project structure* rather than your syntax: instead of "this line is wrong," it answers "this whole file / package / export is dead — delete it."

> **Why it matters:** dead code and unused dependencies accumulate silently. Every unused dependency is install weight, a slower CI, and — post OWASP 2025 — **attack surface** (supply-chain risk, A03). Knip is the tool that tells you what you can safely remove. It consolidates what used to take three tools (`depcheck` + `ts-prune` + `unimported`) into one.

## Table of contents

- [1. What it finds](#1-what-it-finds)
- [2. How it works](#2-how-it-works)
- [3. Getting started](#3-getting-started)
- [4. Configuration](#4-configuration)
- [5. Plugins](#5-plugins)
- [6. Auto-fix](#6-auto-fix)
- [7. Production mode & CI](#7-production-mode--ci)
- [8. Handling false positives](#8-handling-false-positives)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. What it finds

A single run surfaces a broad set of "dead weight" issue types:

- **Unused files** — not reachable from any entry point.
- **Unused dependencies** — in `package.json` but never imported.
- **Unlisted (undeclared) dependencies** — imported but *not* in `package.json` (phantom deps — the exact class the [pnpm](pnpm-tips.md) strict `node_modules` also guards against).
- **Unused exports** — exported functions/types/classes/enums nobody imports.
- **Unused exported members** — e.g. an enum member or class method never used.
- **Unresolved imports** — imports pointing at nothing.
- **Duplicate exports** — the same thing exported twice.
- **Unused binaries / listed-but-missing binaries** in scripts.

> The mental model: **it's ESLint's `no-unused-vars`, but for your whole repo** — across file, package, and export boundaries that a per-file linter can't see.

---

## 2. How it works

Knip builds a **module graph** starting from your **entry files** (the roots — `main`, CLI bins, test files, framework pages) and walks every import. Anything in your **project files** that the graph never reaches is, by definition, unused.

Two key concepts you configure:

- **`entry`** — the roots execution starts from (what the outside world calls).
- **`project`** — the full set of files to analyze for reachability.

It combines **static analysis** (import/export statements) with **dynamic** heuristics and **compilers** for non-JS files, plus **plugins** that teach it each framework's implicit entry points (§5). That plugin knowledge is what makes it accurate where naïve tools produce noise.

---

## 3. Getting started

Zero-config first run — Knip infers sensible defaults from your `package.json` and tsconfig:

```bash
# one-off, no install
pnpm dlx knip            # (or npx knip)

# or add it as a dev dependency
pnpm add -D knip
pnpm knip
```

Requirements: a modern Node and, ideally, a `tsconfig.json` for TS projects. The first run often surfaces a surprising amount — treat it as a discovery pass, not a to-do list to blindly action (§8).

---

## 4. Configuration

Add `knip.json`, `knip.jsonc`, or a typed `knip.ts` when the defaults need tuning:

```jsonc
// knip.json
{
  "$schema": "https://unpkg.com/knip@6/schema.json",
  "entry": ["src/index.ts", "src/cli.ts"],
  "project": ["src/**/*.ts"],
  "ignore": ["**/*.generated.ts"],
  "ignoreDependencies": ["some-peer-only-pkg"],
  "ignoreBinaries": ["docker"]
}
```

- **`entry` / `project`** — the two roots above; most tuning is here.
- **`ignore*`** — escape hatches for files, dependencies, or binaries Knip can't see are used (e.g. loaded by config magic).
- **Workspaces/monorepos** — first-class: configure per-workspace `entry`/`project`, and Knip resolves cross-package usage so a package isn't flagged when a sibling uses it.

---

## 5. Plugins

Plugins are Knip's accuracy engine. Frameworks and tools have **implicit entry points** a generic analyzer would miss — Next.js routes under `app/`, a `jest.config.js`, ESLint's config, Storybook stories, GitHub Actions workflows. Knip ships **150+ plugins** (Astro, Cypress, ESLint, Jest, Vite, Vitest, Next.js, Nx, Remix, Storybook, Svelte, Webpack, and many more) that register those entry points automatically.

Most are **auto-enabled** when Knip detects the dependency, so you usually get their benefit for free. Without the right plugin, framework files look "unused" and you get false positives — so the fix for noise is often "is the plugin active?", not "add an ignore."

---

## 6. Auto-fix

`--fix` makes Knip a *remediation* tool, not just a reporter:

```bash
pnpm knip --fix                 # remove unused exports, deps, and files
pnpm knip --fix --allow-remove-files   # also delete unused files
pnpm knip --fix-type exports    # limit the fix to one issue type
```

It rewrites source to drop unused exports, removes unused entries from `package.json`, and (with the flag) deletes unused files.

> ⚠️ **Always run `--fix` on a clean git tree and review the diff.** Autofix is aggressive by design; dynamic usage it can't see (§8) may get "fixed" incorrectly. Commit first, fix, then read the diff like any refactor.

---

## 7. Production mode & CI

**Production mode** (`--production`) analyzes only **production** entry points — it *excludes* test files, dev configs, and `devDependencies`. This is stricter and answers a different question: "what's dead in the code I actually ship?" Great for trimming the shipped bundle and surface area.

**In CI**, Knip exits non-zero when it finds issues, so it gates merges:

```yaml
# CI step
- run: pnpm knip           # fails the build on new dead code
```

Introduce it gradually on an existing repo — clean up the current findings first, *then* turn on the gate, so the baseline is zero and every new violation is caught at the PR that introduces it. (This is the same "logging & alerting" discipline as the [observability](observability.md) note: make the signal actionable, not noisy.)

---

## 8. Handling false positives

Knip can't see *every* form of dynamic use, so a few real-but-flagged items are normal. Resolve them in priority order:

1. **Check the plugin** — a missing/disabled plugin is the #1 cause of framework false positives.
2. **Fix `entry`** — if a legitimate root isn't listed, everything it uses looks dead.
3. **Mark intentional public API** — use a JSDoc **`@public`** tag on exports that are part of your library's surface but have no internal importer, so Knip treats them as used.
4. **Ignore as a last resort** — `ignore`, `ignoreDependencies`, `ignoreBinaries` for the genuinely un-seeable.

> Order matters: reaching for `ignore` first hides the problem; fixing the plugin/entry first makes Knip *more* accurate everywhere.

---

## 9. Best practices & anti-patterns

**Do**
- **Run it early** on a project and clean the baseline to zero before gating CI.
- **Use `--production`** to trim what you actually ship.
- **Run `--fix` on a clean tree** and review the diff as a refactor.
- **Prefer fixing plugins/`entry`** over adding ignores.
- **Mark public API with `@public`** rather than ignoring it.
- **Pair with [pnpm](pnpm-tips.md)** — unused-dep removal + strict `node_modules` + SBOM shrink your dependency footprint and supply-chain surface together.

**Avoid**
- **Blindly running `--fix`** on a dirty tree — you can't review or revert cleanly.
- **Drowning in ignores** — each ignore is accuracy you gave up; fix the root cause.
- **Treating the first run as a mandate** — some "unused" exports are dynamic/public; verify before deleting.
- **Skipping it in monorepos** — that's exactly where dead cross-package code hides.

---

## 10. Go deeper

Related material in this library:

- 📝 **[pnpm Tips (v11)](pnpm-tips.md)** — the companion: pnpm keeps installs lean and strict; Knip tells you *which* deps and files to drop.
- 📝 **[Security Fundamentals](security-fundamentals.md)** — fewer dependencies = smaller supply-chain attack surface (OWASP A03).
- 📝 **[Cyclomatic Complexity](cyclomatic-complexity.md)** & **[CRAP Score](crap-score.md)** — other "measure the health of the codebase" tooling; Knip is the dead-weight axis.
- 📗 **[Refactoring — Martin Fowler](../books/)** — deleting dead code is the safest refactor there is; Knip finds it for you.

### Primary references

- [knip.dev](https://knip.dev/) — official docs.
- [webpro-nl/knip on GitHub](https://github.com/webpro-nl/knip).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
