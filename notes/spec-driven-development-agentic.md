# 📐 Why Spec-Driven Development Is Becoming Essential for Agentic Software Engineering

> **Level:** 🟡 Intermediate · **Reading time:** ~18 min · **Prerequisites:** the [Constraint-Driven Development](constraint-driven-development.md) note (SDD is one *form* of CDD — this note is the *why now*), plus [Loop Engineering](loop-engineering.md) and [Building an Agent Evaluator](building-agent-evaluators.md). Practical how-to: [OpenSpec Across Multiple Repos](openspec-multi-repo.md).

**Spec-Driven Development (SDD)** makes a structured, machine-readable **specification** the primary artifact of delivery: the thing from which implementation is derived, against which it is verified, and by which it is governed. It went from a niche practice to an industry default in about eighteen months — and the reason is not that specs got better. It's that **who writes the code changed.**

> **The thesis in one line:** *AI agents are excellent at writing code and terrible at guessing what you meant.* When generating code was expensive, the specification could live in a senior engineer's head and leak out through code review. Now generation is nearly free, **the scarce resources are intent and verification** — and a spec is the artifact that carries both. SDD isn't ceremony being re-imposed on engineering; it's the bottleneck moving, and the practice following it.

## Table of contents

- [1. What actually changed](#1-what-actually-changed)
- [2. The failure mode it replaces](#2-the-failure-mode-it-replaces)
- [3. Four forces that make specs necessary](#3-four-forces-that-make-specs-necessary)
- [4. What an agent-ready spec contains](#4-what-an-agent-ready-spec-contains)
- [5. Spec-anchored, not spec-as-source](#5-spec-anchored-not-spec-as-source)
- [6. The 2026 landscape](#6-the-2026-landscape)
- [7. The honest counterarguments](#7-the-honest-counterarguments)
- [8. Best practices & anti-patterns](#8-best-practices--anti-patterns)
- [9. Go deeper](#9-go-deeper)

---

## 1. What actually changed

Software engineering has always had three costs: **deciding what to build**, **writing it**, and **checking it**. For fifty years the middle one dominated, so methodology optimised for it — and the specification was allowed to be informal, because the same humans who held the intent also typed the code, and review caught the drift.

Agentic coding collapsed the middle cost. The consequences are structural, not stylistic:

| | **Before** | **With coding agents** |
| - | ---------- | ---------------------- |
| Cost of writing code | High | **Near zero** |
| Scarce resource | Implementation capacity | **Intent + verification capacity** |
| Where intent lived | A senior engineer's head; PR review | Must be **written down** — the agent can't read minds |
| Review load | Matched human output | **Far exceeds** what humans can read line-by-line |
| What limits throughput | How fast you can build | **How fast you can specify and verify** |

> When the bottleneck moves, the artifact that resolves the bottleneck becomes the centre of the process. That artifact is now the **spec**. This is the same displacement the [loop-engineering](loop-engineering.md) note described for prompts — *"the unit of leverage moved from the prompt to the loop"* — one level up: from the loop to the **specification the loop is held to**.

---

## 2. The failure mode it replaces

Teams rarely adopt SDD from theory. They adopt it **reactively**, after living the loop it fixes:

```
prompt → output → spot what's wrong → patch → prompt again → …
```

This works beautifully on a greenfield toy and **degrades as complexity grows**, because each cycle re-states intent informally, incompletely, and slightly differently. The characteristic symptom is the one that makes it dangerous:

> **AI-generated code passes the unit tests, but violates the architecture, breaks a cross-service API contract, or introduces a security anti-pattern that only surfaces in production.**

That's not a model-quality problem you can prompt your way out of. Unit tests check *this function does what this test says*. They **structurally cannot** check "the domain layer must not import the UI layer," "this response still satisfies the consumer's contract," or "authorisation is enforced server-side." Those are **cross-cutting invariants**, and they need an artifact that states them — which is exactly what [architecture rules](dependency-cruiser.md), [contract tests](testing-strategy.md), and specs provide.

---

## 3. Four forces that make specs necessary

**① Agents fill silence with guesses.** A model completes whatever you leave unspecified — plausibly, confidently, and not necessarily the way you'd have chosen. Every gap in the spec becomes a decision the agent makes for you, and you find out in review. A spec's job is to **remove ambiguity before generation**, not to explain it afterwards.

**② Context doesn't persist; specs do.** Sessions end, context windows compact, agents get replaced by other agents. Nothing an agent "learned" in a conversation survives. A committed spec is **durable, versioned context** that any agent — today's, tomorrow's, a teammate's — can load and be bound by. It's the memory the model doesn't have. (Your [context-management](building-agent-evaluators.md) instinct: *write to disk, not to context.*)

**③ Verification must be separate and automatic.** Volume makes line-by-line human review untenable, and an agent that checks its own work is [sycophantic](building-agent-evaluators.md). SDD's real payoff is that the spec becomes an **executable gate** — acceptance criteria that CI runs — so the loop is: *spec → generate → **verify against the spec** → repeat*. This is the **implementer/verifier separation** from the agent-evaluator note, applied to the whole SDLC rather than one agent loop.

**④ Parallel agents need a shared source of truth.** The moment more than one agent works at once — or one agent works across [several repos](openspec-multi-repo.md) — they need something authoritative to agree on. Without it they make locally-reasonable, mutually-incompatible choices. **The spec is the coordination substrate**, and it's why cross-repo contracts matter more than ever.

---

## 4. What an agent-ready spec contains

The 2026 distinction that matters: **a traditional spec is read by humans; an SDD spec executes as a validation gate.** A good one fixes:

- **Outcomes** — what must be true when this is done.
- **Scope boundaries** — explicitly including what's *out* of scope (agents over-reach into adjacent code otherwise).
- **Constraints** — the invariants the implementation must respect.
- **Prior decisions** — what's already settled and must not be relitigated (saves the agent re-deriving, badly).
- **Acceptance criteria** — the definition of done, **testable**.
- **Conventions** — the team's actual norms for structure, error handling, testing.

Then the agent fills in whatever the spec deliberately leaves open.

**Write acceptance criteria in a constrained syntax.** **EARS** (Easy Approach to Requirements Syntax) is the common choice because its templates are unambiguous and mechanically checkable:

```
Ubiquitous:     The <system> shall <response>.
Event-driven:   When <trigger>, the <system> shall <response>.
State-driven:   While <state>, the <system> shall <response>.
Unwanted:       If <condition>, then the <system> shall <response>.
Optional:       Where <feature is included>, the <system> shall <response>.
```

Prose invites interpretation; EARS-style criteria don't. The same reason [schemas beat "please output JSON"](serialization-schema-evolution.md).

---

## 5. Spec-anchored, not spec-as-source

The single most important caveat, and the thing that separates teams who benefit from SDD from teams who drown in it:

> **Target *spec-anchored*, not *spec-as-source*. Code remains the source of truth; tests remain the enforcer; the spec anchors intent and gates the change.**

The seductive version of SDD — *the spec is the real program, code is a compilation artifact you never read* — does not survive contact with a real codebase. It creates a second system to maintain, and the moment the spec and the code disagree, **the spec is the one that's wrong**, because reality is what runs.

So the spec's job is narrower and more durable: **state intent, bound the agent, and gate the merge.** It is not a replacement for reading code, and a spec with no enforcement is just documentation that rots — **a spec that drifts from code is worse than no spec**, because it's confidently wrong. (Same warning as the [Store-that-drifts](openspec-multi-repo.md) in the cross-repo note.)

---

## 6. The 2026 landscape

SDD went mainstream fast. **GitHub Spec Kit** shipped mid-2025; by Q2 2026 essentially every major coding-agent platform had a spec-aware mode — **Claude Code**, **Cursor**, **AWS Kiro**, **Google Antigravity** — with open-source frameworks layered on the same primitive: **[OpenSpec](openspec-multi-repo.md)**, **BMAD**, **Tessl**, `cc-sdd`.

That convergence is itself the evidence worth noting: independent teams, different products, **the same primitive** — a structured spec that bounds and verifies agent work. When that many actors land on one abstraction in eighteen months, it's usually because the underlying constraint is real.

---

## 7. The honest counterarguments

An argued note owes you the other side:

- **"Specs are documentation, and documentation rots."** True *unless the spec is enforced*. An unenforced spec absolutely rots — which is why the gate matters more than the document (§5).
- **"This is waterfall in new clothes."** The difference is the **loop length and the artifact's role**: SDD specs are small, per-change, versioned deltas that execute as gates — not a 200-page up-front document that's obsolete on contact. (OpenSpec's per-change delta model is the concrete answer here.)
- **"It's ceremony that slows small work."** Correct — and it *is* overkill for a one-line fix or a spike. Reach for it when the change is non-trivial, crosses a boundary, or will be implemented by an agent you won't line-by-line review.
- **"Writing a good spec is as hard as writing the code."** Sometimes. But the spec is **reusable, reviewable, and durable** in a way a prompt isn't — and it's the thing a human can actually own when the code volume exceeds what they can read.
- **The evidence is still young.** Adoption is broad; rigorous outcome data is thin. Treat SDD as a strong, well-reasoned practice with fast-converging industry support — not a proven result.

---

## 8. Best practices & anti-patterns

**Do**
- **Spec the *change*, not the whole system** — small, per-change delta specs beat a monolithic document.
- **Make acceptance criteria executable** (EARS-style, then CI-checked) — a gate, not prose.
- **Write down scope boundaries and prior decisions** — the two things agents most often get wrong.
- **Keep code as source of truth; let the spec anchor and gate.**
- **Put cross-boundary contracts in one enforced place** ([cross-repo note](openspec-multi-repo.md)).
- **Archive/merge the spec when the work lands** so `specs/` reflects reality.
- **Scale ceremony to risk** — trivial change, no spec.

**Avoid**
- **Spec-as-source** — a second system to maintain, and it loses to reality.
- **An unenforced spec** — confidently-wrong documentation.
- **Spec bloat** — a spec nobody reads bounds nothing.
- **Relying on unit tests alone** to catch architectural or contract violations — they structurally can't.
- **Letting the agent both implement and judge** its own conformance.
- **Treating tooling as the practice** — Spec Kit/Kiro/OpenSpec enforce the loop; they don't do the thinking.

---

## 9. Go deeper

Related material in this library:

- 📝 **[Constraint-Driven Development](constraint-driven-development.md)** — the general practice; SDD is one form of constraint. Read for the constraint toolbox and cost ladder.
- 📝 **[OpenSpec Across Multiple Repos](openspec-multi-repo.md)** — the concrete how: spec structure, cross-repo changes, archiving.
- 📝 **[Loop Engineering](loop-engineering.md)** — "negotiate the contract first"; the unit of leverage moving up a level.
- 📝 **[Building an Agent Evaluator](building-agent-evaluators.md)** — implementer/verifier separation and evals as versioned datasets.
- 📝 **[Testing Strategy](testing-strategy.md)** & **[dependency-cruiser](dependency-cruiser.md)** — why unit tests can't catch architectural/contract drift, and what does.
- 📝 **[API Design](api-design.md)** & **[Serialization & Schema Evolution](serialization-schema-evolution.md)** — contract drift, the failure SDD is most often adopted to stop.

### Primary references

- [GitHub Spec Kit](https://github.com/github/spec-kit) · [OpenSpec](https://github.com/Fission-AI/OpenSpec) · [AWS Kiro](https://kiro.dev/) — the tooling that standardised the primitive.
- [Spec-Driven Development — Microsoft for Developers](https://developer.microsoft.com/blog/spec-driven-development-ai-native-engineering/).
- [DeepLearning.AI — *Spec-Driven Development with Coding Agents*](https://www.deeplearning.ai/courses/spec-driven-development-with-coding-agents).
- Alistair Mavin et al., *"Easy Approach to Requirements Syntax (EARS)"* (2009) — the acceptance-criteria templates.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
