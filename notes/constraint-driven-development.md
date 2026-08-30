# 🚧 Constraint-Driven Development — A Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~18 min · **Prerequisites:** [Testing Strategy](testing-strategy.md); helpful: [dependency-cruiser](dependency-cruiser.md), [Loop Engineering](loop-engineering.md), [Building an Agent Evaluator](building-agent-evaluators.md). *Emerging term — synthesized here from the classical practice and its 2026 AI-native framing.*

**Constraint-driven development (CDD)** is the practice of letting **explicit, machine-checkable constraints** — types, contracts, invariants, tests, schemas, architecture rules, specs, guardrails — **drive and bound** how software is built. Instead of relying on discipline and review to *not* do the wrong thing, you make the wrong thing **structurally impossible or automatically caught**. In the AI era this has surged in importance: constraints are what turn fast, fallible code generation (human or agent) into something trustworthy.

> **The core reframe:** most development pushes correctness into *code you write carefully*. Constraint-driven development pushes correctness into **boundaries the machine enforces** — so the compiler, the test suite, the linter, or the spec-gate rejects a violation before it ships. The unit of work shifts from *"write the right code"* to *"declare what must be true, and let the tools guarantee it."* Whoever fills in the implementation — you, a teammate, or an AI agent — is bounded by the same rails.

## Table of contents

- [1. The central idea: constrain the space](#1-the-central-idea-constrain-the-space)
- [2. The constraint toolbox](#2-the-constraint-toolbox)
- [3. The cost ladder: enforce early and cheap](#3-the-cost-ladder-enforce-early-and-cheap)
- [4. CDD vs. TDD vs. spec-driven development](#4-cdd-vs-tdd-vs-spec-driven-development)
- [5. Why it matters in the AI-agent era](#5-why-it-matters-in-the-ai-agent-era)
- [6. Making illegal states unrepresentable](#6-making-illegal-states-unrepresentable)
- [7. Best practices & anti-patterns](#7-best-practices--anti-patterns)
- [8. Go deeper](#8-go-deeper)

---

## 1. The central idea: constrain the space

Every system has a vast space of *possible* behaviors; only a tiny sub-region is *correct*. Constraints **shrink the space you (or an agent) can move in** so that most wrong states are unreachable:

- A **type** says "this value can only ever be one of these shapes" — the compiler rejects the rest.
- A **contract/invariant** says "this must always hold" — a violation throws immediately, at the source.
- A **test** says "this behavior must be true" — CI fails if it isn't.
- An **architecture rule** says "this layer must not depend on that one" — the build breaks on a violation ([dependency-cruiser](dependency-cruiser.md)).
- A **spec** says "these are the boundaries, acceptance criteria, and prior decisions" — and becomes an executable gate, not just a doc.

The more of "correct" you can express as an enforced constraint, the less you rely on **hope, memory, and review**.

---

## 2. The constraint toolbox

CDD isn't one tool — it's a *stack* of constraint mechanisms, strongest/earliest first:

- **Type systems** — the cheapest, earliest constraint. Rich types (sum types, branded/opaque types, non-empty lists) make whole classes of bugs *uncompilable* (§6).
- **Design by Contract (DbC)** — preconditions, postconditions, and invariants on functions/objects (Bertrand Meyer / Eiffel). "This function requires X, guarantees Y, and the object always satisfies Z." Assertions enforce them at runtime.
- **Schemas** — Zod/JSON-Schema/Protobuf validate data shape at boundaries; a malformed payload is rejected, not silently mishandled ([serialization note](serialization-schema-evolution.md)).
- **Tests as constraints** — especially **property-based tests** ("for *all* inputs, this invariant holds" — the constraint made executable over generated cases) and contract tests ([testing note](testing-strategy.md)).
- **Static analysis / lint / architecture rules** — ESLint, type-checkers, and [dependency-cruiser](dependency-cruiser.md) as **fitness functions** encoding "the architecture must stay this shape."
- **Runtime guardrails** — input validation, authorization checks, rate limits, output validation — constraints enforced on live data ([security](security-fundamentals.md)).
- **Specs as executable gates** — a machine-readable spec (acceptance criteria + constraints + conventions) that validation runs *against* — the spec-driven-development idea (§4).

> The mature move is to **layer** these — types catch the most at compile time, tests and lint catch more in CI, runtime guardrails catch the rest in production. Defense in depth, for correctness.

---

## 3. The cost ladder: enforce early and cheap

A constraint's value is inversely proportional to *how late* it catches a violation. The **shift-left ladder** — prefer the highest rung that can express your constraint:

```
cheapest / earliest ─────────────────────────────► most expensive / latest
compile-time types → static analysis/lint → unit/property tests
   → CI gates (arch rules, budgets) → runtime validation → human review → prod monitoring
```

- A bug the **type system** rejects costs seconds and never reaches a teammate.
- The same bug caught in **code review** costs a human's attention days later.
- Caught **in production**, it costs an incident.

> Same instinct as the [testing pyramid](testing-strategy.md), [performance budgets](web-core-vitals-performance.md), and [commit linting](commitlint.md): **move the check as early and as automated as possible.** CDD is that principle applied to *every* kind of correctness, not just tests.

---

## 4. CDD vs. TDD vs. spec-driven development

Closely related, often confused — the distinction is *what drives the work*:

| Approach | Driven by | Artifact |
| -------- | --------- | -------- |
| **TDD** | Write a failing **test** first, then code to pass it | Tests |
| **Spec-Driven Development (SDD)** | Write an **executable spec** (constraints + acceptance criteria + conventions) first; generate code/tests from it | A spec that acts as a validation gate |
| **Constraint-Driven Development** | Express correctness as **enforced constraints** (types, contracts, rules, tests, specs) that bound all implementation | The whole constraint stack |

They're complementary, not rival: **TDD and SDD are both *forms* of constraint-driven development** — tests and specs are two kinds of constraint. The 2026 distinction that matters: a traditional spec is *read by humans*; an **SDD spec executes as a validation gate**. A good spec fixes outcomes, scope boundaries, constraints, prior decisions, and verification criteria — and lets the implementer fill in the rest. *(Your repo's very first badge — "OpenSpec · spec-driven" — is exactly this lineage.)*

---

## 5. Why it matters in the AI-agent era

CDD went from "good practice" to "load-bearing" because of AI code generation:

- **Agents fill whatever the constraints leave unspecified.** Free-form prompting gives the agent a huge space to wander (and hallucinate) in. **Constraints bound the search space** so the agent's output is *checkable* and *correct-by-construction* where possible. This is [loop engineering](loop-engineering.md)'s "negotiate the contract first," generalized.
- **Constraints are the verification half.** An agent can write code fast; what makes it *trustworthy* is that types, tests, lint, arch rules, and spec-gates **automatically reject bad output** — run after *every* agent action, not just at the end. This is the [agent-evaluator](building-agent-evaluators.md) principle (separate the implementing role from the verifying one) applied to the whole SDLC.
- **They catch what unit tests structurally can't** — architectural violations and API-contract drift across a large, agent-touched codebase (where [dependency-cruiser](dependency-cruiser.md)-style rules and schema checks shine).
- **They scale across parallel agents** — many agents can work at once *because* a shared set of constraints keeps them all in bounds and catches regressions independently.

> The shift: as the cost of *writing* code collapses, the leverage moves to **specifying the constraints that make generated code correct.** You spend less time typing implementation and more time declaring what must be true — and letting the machine enforce it against whatever gets generated.

---

## 6. Making illegal states unrepresentable

The purest form of a constraint is one you *can't violate because the code won't compile*. Instead of validating-and-hoping, **model the domain so bad states can't be constructed**:

```ts
// ❌ Illegal states ARE representable — every field optional; you validate & pray
type Order = { status: string; paidAt?: Date; shippedAt?: Date };

// ✅ Illegal states UNrepresentable — the type only permits valid shapes
type Order =
  | { status: 'pending' }
  | { status: 'paid';    paidAt: Date }
  | { status: 'shipped'; paidAt: Date; shippedAt: Date };
// A "shipped order with no paidAt" now cannot be constructed — no runtime check needed.
```

Techniques: **sum types / discriminated unions**, **branded/opaque types** (`type Email = string & { __brand: 'Email' }` — can't pass a raw string), **non-empty collections**, **smart constructors** (the only way to build a value validates it). The constraint moves from a *runtime check you might forget* to a *compile-time guarantee you can't*.

> This is the highest-leverage CDD move: a bug that can't be *represented* can't be *written* — by you, a teammate, or an agent.

---

## 7. Best practices & anti-patterns

**Do**
- **Push correctness into the earliest/cheapest constraint** that can express it (types > lint > tests > runtime).
- **Make illegal states unrepresentable** before reaching for runtime validation.
- **Encode architecture as executable rules** ([dependency-cruiser](dependency-cruiser.md)) and data shape as schemas.
- **Write specs as gates, not docs** — acceptance criteria + constraints + conventions the tools can check.
- **For AI agents: give constraints, not just prompts** — types, tests, lint, and a spec bound and verify the output; run them after every action.
- **Layer constraints** (defense in depth for correctness).

**Avoid**
- **Constraints only in prose/comments** — un-enforced, they rot and get ignored (the [prompt-instruction-is-not-a-control](security-fundamentals.md) fallacy).
- **Over-constraining** — rigid rules that block legitimate work breed `--no-verify` workarounds; constrain the *invariants*, not every stylistic choice.
- **Runtime validation for what a type could guarantee** — later, slower, forgettable.
- **Letting agents run unconstrained** and reviewing output by eyeball — you've moved the bottleneck to human review with none of the guarantees.
- **A spec nobody executes** — if it's not a gate, it's just documentation.

---

## 8. Go deeper

Related material in this library:

- 📝 **[Testing Strategy](testing-strategy.md)** — tests (esp. property-based) are executable constraints; TDD is CDD with tests.
- 📝 **[dependency-cruiser](dependency-cruiser.md)** — architecture rules as executable fitness-function constraints.
- 📝 **[Serialization & Schema Evolution](serialization-schema-evolution.md)** — schemas as boundary constraints; evolving them safely.
- 📝 **[Loop Engineering](loop-engineering.md)** & **[Building an Agent Evaluator](building-agent-evaluators.md)** — "contract first" and separating implementer from verifier; CDD is how you bound and check agent output.
- 📝 **[Security Fundamentals](security-fundamentals.md)** — guardrails, validation, fail-closed: runtime constraints.
- 📝 **[CRAP Score](crap-score.md)** & **[Cyclomatic Complexity](cyclomatic-complexity.md)** — quality thresholds as CI-gated constraints.

### Primary references

- Bertrand Meyer, *Object-Oriented Software Construction* — **Design by Contract** (preconditions/postconditions/invariants).
- [Spec-Driven Development — Microsoft for Developers](https://developer.microsoft.com/blog/spec-driven-development-ai-native-engineering/) and [GitHub Spec Kit](https://github.com/github/spec-kit) — specs as executable gates for AI agents (2026).
- Scott Wlaschin, *"Domain Modeling Made Functional"* / *"Designing with types"* — making illegal states unrepresentable.
- Neal Ford et al., *Building Evolutionary Architectures* — architectural fitness functions.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
