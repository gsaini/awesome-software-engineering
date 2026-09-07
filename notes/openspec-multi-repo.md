# 🗂️ Managing OpenSpec Across Multiple Related Repos — A Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~14 min · **Prerequisites:** the [Constraint-Driven Development](constraint-driven-development.md), [API Design](api-design.md), and [Serialization & Schema Evolution](serialization-schema-evolution.md) notes. Scope: **OpenSpec** (`@fission-ai/openspec`) spec-driven development across a service split (e.g. UI → UI Service/BFF → Functional Service).

When a product spans several related repos — a **UI**, a **UI Service (BFF)**, and a **Functional Service** — spec-driven development hits a coordination problem: a single feature touches all three, and the *interfaces between them* must stay in sync. This note is how to manage **OpenSpec** across those repos without the specs drifting from each other or from the code.

> **The reframe that unlocks it:** there are **two kinds of spec**, and they belong in different places. **Local specs** describe what *one* repo does internally — they live *in that repo*, with the code, owned by that team. **Cross-cutting specs and the contracts between repos** are shared and must not drift — they need a **single source of truth**. Nearly every failure here is putting a *shared* thing (a contract) in a *local* place (one repo) and watching it diverge.

## Table of contents

- [1. What OpenSpec gives you](#1-what-openspec-gives-you)
- [2. The two-kinds-of-spec split](#2-the-two-kinds-of-spec-split)
- [3. The recommended structure](#3-the-recommended-structure)
- [4. Contracts are the real artifact](#4-contracts-are-the-real-artifact)
- [5. Coordinating a cross-repo change](#5-coordinating-a-cross-repo-change)
- [6. Making it agent-ready](#6-making-it-agent-ready)
- [7. Monorepo vs. polyrepo decision](#7-monorepo-vs-polyrepo-decision)
- [8. Best practices & anti-patterns](#8-best-practices--anti-patterns)
- [9. Go deeper](#9-go-deeper)

---

## 1. What OpenSpec gives you

**OpenSpec** is a spec-driven-development convention + CLI (MIT, `@fission-ai/openspec`): plain-markdown specs in an `openspec/` folder, versioned in git like anything else. The shape:

- **`openspec/specs/`** — the current source of truth (what *is* built), per capability.
- **`openspec/changes/`** — in-flight **change proposals** (what *should* change): a proposal, a task plan, and delta specs. Approved changes are implemented, then archived into `specs/`.

Two features make it work across repos (both confirmed in its current docs):
- **A "Store"** — a *dedicated spec repo* with the same `openspec/` shape, acting as a **cross-repo source of truth** the whole team and every coding agent can read. A platform team owns it; product repos reference it **read-only**.
- **Cross-repo changes** — **one change, one plan, even when the code lands in three repos.**

---

## 2. The two-kinds-of-spec split

| | **Local specs** | **Cross-cutting specs & contracts** |
| - | --------------- | ----------------------------------- |
| Describe | What *one* repo does internally | End-to-end behavior + the interfaces *between* repos |
| Live in | That repo's `openspec/` | A shared **Store** (or shared contract package) |
| Owned by | That repo's team | Platform/architecture; product teams reference read-only |
| Drift risk | Low (next to its code) | **High** — this is where the discipline goes |

Get this split right and everything else follows.

---

## 3. The recommended structure

For UI / UI-Service / Functional-Service that **deploy independently** (the usual case):

```
functional-service/   openspec/   → its own domain capabilities        (local)
ui-service/  (BFF)     openspec/   → its own orchestration capabilities (local)
ui/                    openspec/   → its own UI capabilities            (local)

platform-spec/  ← the Store (its own repo)
   openspec/
   ├── specs/     → end-to-end product capabilities + the CONTRACTS between the services
   └── changes/   → cross-repo change proposals (one plan spanning the three repos)
```

- **Each repo keeps its own `openspec/`** for its local capabilities — team autonomy, specs next to the code the agent edits.
- **The Store owns the cross-cutting behavior and the inter-service contracts** — the one place the three agree. Product repos reference it read-only.

---

## 4. Contracts are the real artifact

OpenSpec coordinates the *plan*; **you** still have to own the *interface* so it can't drift. The contracts between UI↔UI-Service↔Functional-Service (OpenAPI / gRPC proto / GraphQL SDL) are the artifact that matters most:

- **Put the contract specs in the Store** (or a versioned shared package both provider and consumer depend on).
- **Enforce them, don't just document them** — **consumer-driven contract tests (Pact-style)** in each repo's CI, so a contract violation **fails the build**. This is the [constraint-driven-development](constraint-driven-development.md) / fitness-function idea: the spec is a *gate*, not a wiki page. **A Store that drifts from code is worse than no Store** — it becomes confident fiction.
- **Version the contract** and evolve it **additively / backward-compatibly** ([schema-evolution](serialization-schema-evolution.md)): additive is cheap; a breaking change needs a new version + a migration window, or you've forced a lockstep deploy across three repos — the exact thing independent repos exist to avoid.

---

## 5. Coordinating a cross-repo change

The hard case — a feature spanning all three:

1. **One cross-repo change proposal** in the Store — the end-to-end behavior + the contract delta + one plan.
2. **Contract-first, then in dependency order:** agree the contract → **Functional Service** (provider) → **UI Service** (BFF — consumer *and* provider) → **UI** (consumer).
3. **Additive/backward-compatible first**, so the three deploy independently rather than in lockstep.
4. **Shared change ID across repos** (`2026-09-saved-carts`) + an epic issue linking the per-repo PRs → the whole change is traceable.
5. **Contract tests gate each side** so provider and consumer can't drift apart mid-rollout.

> This is the [message-queue](message-queues-event-driven.md)/[API](api-design.md) rollout discipline applied to specs: agree the interface, roll it out provider-before-consumer, keep it compatible so nothing has to move in lockstep.

---

## 6. Making it agent-ready

The real payoff of doing this with OpenSpec is **AI coding agents that don't wander outside the contract**:

- Each repo's **`AGENTS.md` / `CLAUDE.md` points at the Store and the relevant contracts** — so an agent editing the **UI** *knows* the UI-Service contract it must honor, and an agent editing the **Functional Service** knows what its consumers expect.
- The spec/contract becomes **durable, cross-repo context that bounds what the agent generates** — [constraint-driven development](constraint-driven-development.md) across repo boundaries. The agent implements *against an agreed spec*, and contract tests verify it — the [implementer-vs-verifier separation](building-agent-evaluators.md) at the repo level.

---

## 7. Monorepo vs. polyrepo decision

The choice that sits above all of this:

| If the three… | Prefer |
| ------------- | ------ |
| Deploy together, one team | **A monorepo with one `openspec/`** — a cross-cutting change is one atomic PR (specs + all three services together). Simplest by far. |
| Deploy independently, separate teams | **Per-repo `openspec/` + a Store for cross-repo specs & contracts + cross-repo changes** (the structure above). |

Either way the invariant holds: **local specs live with their repo; the contracts between repos live in one enforced place.** (Same [monorepo-vs-polyrepo](git-internals.md) trade-off as code — atomic cross-cutting change vs. independent autonomy.)

---

## 8. Best practices & anti-patterns

**Do**
- **Split local vs. cross-cutting specs** — the foundational decision.
- **Own inter-service contracts in one place** (Store or shared package), **versioned** and **contract-tested in CI**.
- **One cross-repo change + shared change ID** for anything spanning repos; contract-first, provider→consumer, additive.
- **Point each repo's `AGENTS.md`/`CLAUDE.md` at the Store & contracts** so agents honor them.
- **Consider a monorepo** if the three deploy together — it makes cross-cutting spec changes trivial.

**Avoid**
- **Duplicating a contract spec across repos** → guaranteed drift.
- **A Store/spec repo with no enforcement** → it drifts from code into fiction.
- **Coordinating cross-repo changes only in chat** instead of one linked change.
- **Breaking a contract with no compatibility window** → forces a lockstep deploy across three repos.
- **Letting an agent change one side of a contract** without the other (or without a contract test catching it).

---

## 9. Go deeper

Related material in this library:

- 📝 **[Constraint-Driven Development](constraint-driven-development.md)** — specs/contracts as executable gates; this is that idea across repos.
- 📝 **[API Design](api-design.md)** — contract-first, versioning, the BFF (UI Service) role, provider→consumer rollout.
- 📝 **[Serialization & Schema Evolution](serialization-schema-evolution.md)** — backward/forward compatibility so services deploy independently.
- 📝 **[Testing Strategy](testing-strategy.md)** — consumer-driven contract testing (Pact) as the enforcement layer.
- 📝 **[Exposing React to Other Apps](exposing-react-to-other-apps.md)** — the BFF pattern the "UI Service" embodies.
- 📝 **[Git Internals](git-internals.md)** — the monorepo-vs-polyrepo trade-off underneath.

### Primary references

- [OpenSpec (Fission-AI/OpenSpec)](https://github.com/Fission-AI/OpenSpec) — the tool; check current docs for exact CLI, Stores, and cross-repo change commands (moving fast — v1.4+ as of mid-2026).
- [Spec-Driven Development — Microsoft for Developers](https://developer.microsoft.com/blog/spec-driven-development-ai-native-engineering/) and [Pact](https://pact.io/) (consumer-driven contract testing).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
