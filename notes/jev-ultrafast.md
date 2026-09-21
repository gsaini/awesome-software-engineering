# ⚡ Jev Ultrafast — A Browser Agent That Chooses Instead of Generating

> **Level:** 🟡 Intermediate · **Reading time:** ~14 min · **Source:** [browser-use/jev-ultrafast](https://github.com/browser-use/jev-ultrafast) (MIT, Python, v0.1.0 — created 2026-09-16) · Prerequisites: [Loop Engineering](loop-engineering.md), [Constraint-Driven Development](constraint-driven-development.md).

**Jev Ultrafast** is a small, readable browser agent from **Browser Use** that completes real web tasks from a single natural-language goal — for example, a Zürich → London search on Google Flights in **~7.1 seconds**. What makes it worth studying isn't the speed number; it's the architectural idea underneath it, stated in its own `pyproject.toml`:

> ***"A browser agent that chooses instead of generating."***

Most LLM browser agents ask a model to **generate** the next action — a CSS selector, click coordinates, a JSON tool call, sometimes raw JavaScript — and then hope the output is valid. Jev Ultrafast reframes each step as a **choice from an enumerated, indexed menu** of what's actually on the page. A generative model is invoked for exactly one thing that genuinely needs generating: **the text to type into a field**.

## Table of contents

- [1. The core idea: choose, don't generate](#1-the-core-idea-choose-dont-generate)
- [2. The dynamic, indexed action space](#2-the-dynamic-indexed-action-space)
- [3. Two decisions, one round trip (speculative fan-out)](#3-two-decisions-one-round-trip-speculative-fan-out)
- [4. System One chooses, a small LLM writes](#4-system-one-chooses-a-small-llm-writes)
- [5. Why it's fast](#5-why-its-fast)
- [6. Safety by construction](#6-safety-by-construction)
- [7. The evidence — and its honest limits](#7-the-evidence--and-its-honest-limits)
- [8. What it teaches](#8-what-it-teaches)
- [9. Go deeper](#9-go-deeper)

---

## 1. The core idea: choose, don't generate

| | **Typical LLM browser agent** | **Jev Ultrafast** |
| - | ----------------------------- | ----------------- |
| Per step, the model… | **Generates** an action (selector / coords / JSON / JS) | **Chooses** an operation + an element index from a menu |
| Output space | Unbounded text | A finite set of *observed, valid* options |
| Invalid output | Possible — must be parsed & validated after the fact | **Impossible by construction** — only offered indices exist |
| Model input | Often screenshots (vision) | **Structured DOM state** (no screenshots in the loop) |
| Text generation | Every step | **Only** when the operation is `TYPE_TEXT` |

This is the most literal application of *"make illegal states unrepresentable"* in the library. The model **cannot** produce a malformed selector or an off-page coordinate, because it never produces selectors or coordinates at all — it picks a number from a list the code built.

---

## 2. The dynamic, indexed action space

On every observation, one atomic browser call builds a fresh **element table** — each actionable node gets one index, with its role, accessible name, and current value:

```text
[1] button    Change ticket type · Round trip
[2] combobox  Where from?        · San Francisco
[3] combobox  Where to?          · empty
[4] textbox   Departure          · empty
...
```

The operations are a fixed vocabulary: **`CLICK`, `TYPE_TEXT`, `SELECT`, `SCROLL_UP`, `SCROLL_DOWN`, `WAIT`, `DONE`, `BLOCKED`**. Only operations an element actually supports are offered for it — a checkbox isn't offered `TYPE_TEXT` (the project's own audit caught exactly that misclassification). Native dropdown options get a code-owned `index:option` identifier.

Two details matter:
- **Each node gets a code-owned identity** (a `WeakMap` of live DOM references), not a model-invented handle. Replaced elements get new identities; disconnected ones are pruned; navigation starts fresh.
- **The action space is rebuilt every step** — "dynamic" means the menu always reflects the page as it is *now*, not a stale plan.

> Note what this depends on: **roles, names, and values** — i.e. the page's **accessibility tree**. A well-labelled, [accessible](web-accessibility.md) page is, quite literally, an agent-operable page. a11y turns out to be machine-operability.

---

## 3. Two decisions, one round trip (speculative fan-out)

Each step needs two decisions: *which operation?* and *on which element?* Asked serially, that's two network round trips.

Instead, **one request asks the operation question *and* every operation's target question at once**:

```text
                      one TypeSafe request
                     ┌───────────────────────────┐
page → element table → operation                 │
                     │ click_target              │
                     │ type_text_target          │
                     │ select_target, if present │
                     └─────────────┬─────────────┘
                     use only the target matching the chosen operation
```

The target questions are **speculative**: if the chosen operation is `CLICK`, only `click_target` is consumed and the others are discarded. **Two decisions, one round trip.** Each target head contains only elements *compatible* with its operation, and because the questions are evaluated independently, each target question explicitly names the operation it assumes.

> This is **speculative execution** — compute every branch in parallel, keep the one you need — the same trade CPUs make with branch prediction. It's also the [Diamond / fan-out pattern](graph-engineering.md) from the graph-engineering note, applied to a single decision instead of a multi-agent workflow.

---

## 4. System One chooses, a small LLM writes

The two halves use two different kinds of model — deliberately:

- **Jev** (TypeSafe) — described as *"the first System One model."* It **doesn't generate text**: it evaluates **typed questions against a state** and returns a **choice, a probability distribution, and a confidence score** that code can act on directly. TypeSafe's pitch: adding questions barely changes latency, and because each question is evaluated independently, **adding questions doesn't cause context rot**.
- **A small generative LLM** (the demo uses `inception/mercury-2.5`, reasoning disabled; Gemini/GLM/DeepSeek also work) — called **only** for `TYPE_TEXT`. It must return **exactly** `{"text": "…"}` as JSON (or `{"text": null}` if the value is missing); commentary or anything else is rejected, and the prompt says *"Never invent personal information."* The code never extracts quoted literals from the goal — the value is genuinely generated.

The naming is Kahneman's: **System 1** (fast, intuitive, pattern-matching) picks the action; **System 2** (slower, generative) is reserved for the one sub-task that needs it. The inspector UI even shows the operation and target **probability distributions** at each step — the model's uncertainty is inspectable, not hidden inside prose.

---

## 5. Why it's fast

The README's own list, and each item is a transferable lesson:

- **One request per decision cycle** — operation and target heads share one observed state.
- **No screenshots in the loop** — the model consumes structured state; vision is optional (inspector/recording only).
- **One browser call per snapshot** — read visible controls, names, values, and text *atomically*, keeping live node references.
- **Validate the selected target, not the whole page** — scoped freshness guards compare the document, URL, form values, the target, and its nearby context. Animation alone no longer forces a re-prediction.
- **Wait for *useful* state** — after typing into a combobox, wait for suggestions (capped at **200 ms**); otherwise at most two animation frames or **50 ms**. Don't pay for a prediction before the autocomplete arrives.
- **Keep hidden tabs rendering** — focus emulation prevents background animation throttling.
- **Send only visible text** — offscreen article bodies and footers don't bloat the model's context.
- **Reuse an interrupted text request** — but only if the helper's *entire* input is byte-identical.

The single biggest measured win was mechanical, not model-side: **browser protocol calls fell from a median of 1,092 → 101** per run, because the old loop re-read the accessibility tree repeatedly and invalidated decisions on *every* DOM mutation, including animations.

---

## 6. Safety by construction

The project's `AGENTS.md` reads like a checklist for trustworthy agents:

- **Model output never becomes selectors, coordinates, shell commands, or executable JavaScript.** Every executed target resolves from an *observed* node — an **allowlist**, not validation-after-the-fact.
- **Page text is untrusted data, never instructions** — stated in the prompt itself. That's the standard [prompt-injection](security-fundamentals.md) defence, and the indexed action space makes it far stronger: even a successful injection can only pick from the offered menu.
- **Never retry a browser mutation.** A click or a form submit is **not idempotent**; retrying it after an ambiguous failure could double-submit. Instead: **log execution *before* observing its result**, and stop on uncertainty.
- **Re-check before acting** — target visibility, enabled state, current geometry, and **click occlusion** are verified immediately before input.
- **`DONE` is not proof of success.** Completion is judged by an **independent verifier**, never by the model's own claim.
- **Bounded runs** — at most **60** browser actions and **120** decision requests; up to **250** action candidates are kept, and truncated ones simply can't be selected.

> These map almost one-to-one onto earlier notes: the allowlist is [constraint-driven development](constraint-driven-development.md); "never retry a mutation" is the [idempotency](message-queues-event-driven.md) lesson inverted (*if you can't make it idempotent, don't retry it*); "log before observing" is a write-ahead log; and "DONE ≠ success" is the [implementer-vs-verifier](building-agent-evaluators.md) separation.

---

## 7. The evidence — and its honest limits

What's reported, from the repo's [performance doc](https://github.com/browser-use/jev-ultrafast/blob/main/docs/performance.md):

| Measure | Result |
| ------- | ------ |
| Google Flights, Zürich → London (recorded, 1× speed) | **7.073 s** — 17 Jev requests, 10 interactions + 1 WAIT, 2 text-helper calls |
| Matched comparison, 3 alternating pairs, same models | median **9.450 s → 7.092 s** (−25%); 3/3 verified each |
| Median TypeSafe requests / browser protocol calls | **22 → 17** / **1,092 → 101** |
| Median Jev latency per request | **178 ms** |
| Text generation (Mercury) | Zurich in **581 ms**, London in **346 ms**; **$0.00006272** for both calls (helper cost only, not total task cost) |
| Wikipedia: open a specific article | **2.798 s** (smoke check) |
| Local hotel search + 3 filters | **1.896 s** (smoke check) |

Now the part worth admiring — the authors are unusually explicit about what this *doesn't* show:

- **Three pairs is not a statistical result** — they report the two-sided sign test themselves: **p = 0.25**.
- It is **three repeats of one task on one browser profile, not a general reliability benchmark**; Google, the network, and caches stay live.
- All six matched attempts are included, and the failed **development attempts are retained** in the write-up — including a faster candidate that *failed* independent verification, and helper models rejected for swapping origin/destination or emitting commentary instead of JSON.
- The design doc admits the **first demo used five prepared steps and copied strings** — and that shortcut was removed so the policy works from the goal alone.
- **Unsupported (MVP scope):** shadow roots, iframes, canvas, file uploads, pop-up tabs, nested scrolling, arbitrary keyboard widgets; it reads common HTML/ARIA, not the full accessible-name algorithm.
- *"A valid action can still be wrong."*

> Treat it as a **compelling architectural demonstration**, not a proven production agent. That calibration is itself the lesson: this is how to publish a speed claim — medians, matched arms, raw measurements, a significance test, and a stated scope. Compare the [observability](observability.md) note's "percentiles, not averages" discipline.

---

## 8. What it teaches

- **Don't let a model generate what it can choose.** Replacing an open generation surface with a finite, code-built menu buys correctness, safety, and latency at once. The generative model shrinks to the one job that truly needs it.
- **The action space is the constraint.** The most robust agent guardrail isn't a filter on output — it's an output space where invalid actions can't be expressed. [Constraint-driven development](constraint-driven-development.md), at its purest.
- **Structured state beats pixels when you have it.** Screenshots are expensive, slow, and ambiguous; the DOM already *knows* what's clickable. Vision is for when structure isn't available.
- **Speed came from the mechanics, not the model.** The biggest win was cutting browser calls 10×, plus smarter waits and scoped freshness checks — ordinary systems engineering applied to an agent loop.
- **Small enough to read.** Six core files; the whole loop is `agent.py`. That's a feature — it's a reference implementation you can actually understand in an afternoon.

---

## 9. Go deeper

Related material in this library:

- 📝 **[Constraint-Driven Development](constraint-driven-development.md)** — "make illegal states unrepresentable"; the indexed action space is the textbook case.
- 📝 **[Why Spec-Driven Development Is Essential for Agentic SE](spec-driven-development-agentic.md)** — agents are good at generating, bad at guessing; Jev removes most of the generation surface entirely.
- 📝 **[Loop Engineering](loop-engineering.md)** — a tiny, explicit loop: page → indexed elements → operation + target → execute.
- 📝 **[Graph Engineering](graph-engineering.md)** — speculative fan-out as the single-decision Diamond pattern.
- 📝 **[Building an Agent Evaluator](building-agent-evaluators.md)** — DONE is a claim, not evidence; verify independently.
- 📝 **[Security Fundamentals](security-fundamentals.md)** & **[Web Accessibility](web-accessibility.md)** — page text as untrusted data; the accessibility tree as the machine-readable page.

### Primary references

- [browser-use/jev-ultrafast](https://github.com/browser-use/jev-ultrafast) — README, [`docs/design.md`](https://github.com/browser-use/jev-ultrafast/blob/main/docs/design.md), [`docs/performance.md`](https://github.com/browser-use/jev-ultrafast/blob/main/docs/performance.md), and [`agent.py`](https://github.com/browser-use/jev-ultrafast/blob/main/jev_ultrafast/agent.py).
- [TypeSafe — Jev & the Choice/Score/Noul primitives](https://docs.typesafe.ai/introduction) and [speculative fan-out](https://docs.typesafe.ai/patterns/fan-out).
- [Browser Use](https://github.com/browser-use/browser-use) and [Browser Harness](https://github.com/browser-use/browser-harness).

*Summary note of a third-party project (v0.1.0) — numbers are as reported by its authors; see the linked measurement docs for their exact boundaries.*
