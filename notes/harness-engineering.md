# 🧰 Harness Engineering — The System Around the Model

> **Level:** 🟡 Intermediate · **Reading time:** ~16 min · **Prerequisites:** [Loop Engineering](loop-engineering.md), [Building an Agent Evaluator](building-agent-evaluators.md) · **Companion repo:** [harness-engineering-getting-started](https://github.com/gsaini/harness-engineering-getting-started) (one failure, one fix, versions v0–v5) · **Prompted by:** Lunar, [*Harness Engineering: The Complete Guide to Building AI Agents That Don't Fall Apart*](https://x.com/LunarResearcher/status/2096570562625655088) (X article, 2026-09-06)

**Agent = Model + Harness.** The harness is everything in an agent that isn't the model: the loop, the tools and how they're exposed, what goes into context, what persists between sessions, what the agent may touch, how "done" is decided, and how a failed run is understood. **Harness engineering** is designing that system on purpose.

> **The one-line thesis**, in Lunar's words: *"A prompt changes one attempt. A harness changes every attempt."* When an agent keeps failing, the fix is usually in the environment, not in more adjectives in the prompt.

## Table of contents

- [1. Where the term comes from](#1-where-the-term-comes-from)
- [2. Most agent failures are environment failures](#2-most-agent-failures-are-environment-failures)
- [3. The twelve parts of a harness](#3-the-twelve-parts-of-a-harness)
- [4. The flywheel, and harness decay](#4-the-flywheel-and-harness-decay)
- [5. The minimum viable harness](#5-the-minimum-viable-harness)
- [6. What practitioners report](#6-what-practitioners-report)
- [7. Measuring a harness](#7-measuring-a-harness)
- [8. When you don't need one](#8-when-you-dont-need-one)
- [9. A harness spec template](#9-a-harness-spec-template)
- [10. How it fits with the rest of this library](#10-how-it-fits-with-the-rest-of-this-library)
- [11. Caveats](#11-caveats)
- [12. Go deeper](#12-go-deeper)

---

## 1. Where the term comes from

| When | Who | What |
|---|---|---|
| 2025-11-26 | Anthropic, [*Effective harnesses for long-running agents*](https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents) | An initializer agent sets up the environment. A coding agent then makes progress one feature at a time across many context windows. |
| 2026-02-05 | Mitchell Hashimoto, [*My AI Adoption Journey*](https://mitchellh.com/writing/my-ai-adoption-journey), "Step 5: Engineer the Harness" | Names the practice. *"Anytime you find an agent makes a mistake, you take the time to engineer a solution such that the agent never makes that mistake again."* |
| 2026-02-11 | OpenAI (Ryan Lopopolo), [*Harness engineering: leveraging Codex in an agent-first world*](https://openai.com/index/harness-engineering/) | A small team whose job shifted from writing code to designing the environment, intent, and feedback loops for Codex agents. |
| 2026-09-06 | Lunar, [the X article](https://x.com/LunarResearcher/status/2096570562625655088) | A 19-section practitioner synthesis. Its structure is the backbone of this note. |
| 2026-09-24 | marmelab, [*The State of AI Harness Engineering 2026*](https://marmelab.com/blog/2026/09/24/the-state-of-ai-harness-engineering-2026.html) | A survey of the field. |

The progression is **prompt engineering** (one call), then **context engineering** (what the model sees), then **harness engineering** (the whole system that runs, constrains, verifies, and remembers).

## 2. Most agent failures are environment failures

Lunar opens with six failures that look like reasoning problems but aren't. Each one has a harness fix, and the [companion repo](https://github.com/gsaini/harness-engineering-getting-started) reproduces several of them:

| The agent… | What was missing | Harness fix | In the companion repo |
|---|---|---|---|
| didn't know which files mattered | a map | progressive context (§3.2) | — |
| used the right tool in the wrong place | tool boundaries | a tool gateway (§3.3) | v1 (actionable tool errors), v2 (workspace boundary) |
| lost the previous session's decisions | durable state | memory as state (§3.5) | v4 (project memory) |
| **claimed success without running the checks** | an evidence gate | completion by evidence (§3.6) | **v3** (`finish` runs the tests), **v5** (every test checked off) |
| repeated an action after a partial failure | failure classification | recovery by class (§3.9) | v1 |
| had permission it shouldn't have had | policy outside the loop | authorization (§3.8) | v2 (read-only tests, no edits to the test) |

The repo's scoreboard is the argument in miniature: the same model on the same tasks goes from **1/7 to 7/7**, and only the harness changes. (It uses a scripted model that reproduces each failure, so it shows each fix works, not how often a real model fails.)

## 3. The twelve parts of a harness

### 3.1 A task contract

*"Improve the onboarding flow"* is fine in a conversation but too vague for autonomous work. Before the agent acts, turn the request into five answers:

1. What outcome must exist?
2. What's in scope?
3. What must not change?
4. What evidence proves completion?
5. Which actions need a human?

*"Without a contract, the agent optimizes for plausible activity."* This is [spec-driven development](spec-driven-development-agentic.md) at the scale of one task.

### 3.2 A map, not a manual

Dumping the whole repository into context is *context flooding*. Give the agent a small map first, and let it retrieve details as the task needs them (progressive disclosure). A good context compiler decides what is always needed, what can be fetched later, what has gone stale, and what must stay verbatim. Aim for maximum signal per token, not maximum context.

### 3.3 A tool gateway, not a tool pile

Twenty tools give an agent twenty ways to make mistakes. A gateway between the model and the tools can:

- hide irrelevant tools;
- validate arguments and restrict paths and domains;
- attach timeouts and make retries idempotent;
- normalize outputs and return **evidence** rather than "success";
- require confirmation for risky actions.

*"The model can propose an action. The tool gateway decides whether that action is valid enough to execute."*

### 3.4 Separate the brain, the hands, and the history

A fragile agent piles reasoning, tool calls, files, and old observations into one growing transcript. A sturdier one separates three things:

- the **model**, which needs the right *current* state;
- the **sandbox**, which needs to execute one bounded action safely;
- the **session log**, which needs to preserve what happened after the context is gone.

That separation makes runs resumable and inspectable, and lets you replace one part without rebuilding the others.

### 3.5 Memory is durable state, not transcript

*"Conversation history is not reliable memory. It is an event stream."* Compile the parts that matter into explicit state: decisions, constraints, progress, and open risks. Keep the raw history for audit, and use the compiled state for execution. (This is [event sourcing](event-sourcing-cqrs.md) applied to agent sessions: the transcript is the log, and the state file is a projection of it.)

### 3.6 Completion requires evidence

*"An agent saying 'done' is not evidence that the task is done."* Decide completion from observable changes in the environment, and run the cheapest deterministic checks first. *"Do not use another model where a compiler, schema, checksum, query, or test can answer the question. Use models for ambiguity. Use code for plumbing."*

### 3.7 Verification should try to disprove the result

The worker builds the strongest solution. The evaluator looks for the reason to reject it. Asking the same agent, in the same context, to "double-check" tends to keep the assumptions that caused the mistake. A real verifier has a rejection rubric, the artifact, the contract, fresh context or independent tools, and permission to reject without repairing. *"Verification is not a second opinion. It is an attempted disproof."* Two worked examples: [Critique agents as a graph](critique-agent-graph.md), and EGM's Goldfish reviewers in [egm-getting-started](https://github.com/gsaini/egm-getting-started).

### 3.8 The model proposes, the policy authorizes

Rules that matter shouldn't depend on the model remembering them. Keep policy outside the reasoning loop, and make the gate harder as the consequence grows. *"Autonomy is not the absence of control. It is the ability to operate freely inside a clearly enforced boundary."* In Claude Code terms, these are permission rules and hooks, not lines in `CLAUDE.md`. The [Jev guard](jev-claude-code.md) is one example: a hook that may deny or prompt, and never approves.

### 3.9 Recovery should target the failure class

*"Something failed. Try again"* is repetition, not recovery. Classify the failure before choosing the next action, and make sure every retry **changes at least one relevant condition**. Every loop needs a budget: attempts, time, spend, destructive scope, and an escalation condition.

### 3.10 Instructions should become infrastructure

Instructions explain local reality, but they're weak enforcement. If a rule matters repeatedly, move it down the stack:

```text
prompt  →  instructions file  →  tool contract  →  hook / permission  →  test / CI gate
(advice)                                                        (enforced, every run)
```

*"The prompt should explain judgment. The harness should enforce invariants."* (The article's own ladder diagram is an image that couldn't be retrieved. This one is mine and follows its text.)

### 3.11 Observe the run, not just the answer

A clean final artifact can hide a bad process: wrong data accessed, a failed command ignored, an external action done twice, ten times the budget spent. Trace state transitions, context sources, tool inputs and outputs, environment changes, verification results, retry reasons, approvals, cost, and latency. The point is local repair: restart from a trustworthy checkpoint at step 18, not from zero. This is the same reasoning as [durable execution](durable-execution.md).

### 3.12 Every run ends with a change receipt

A long transcript is a poor review surface. End each run with a short receipt of what the system can *prove*: what changed, the evidence, and the unresolved risk. That's what the human reviews, and what the next session starts from.

## 4. The flywheel, and harness decay

**The flywheel** (Hashimoto's rule, scaled up): *"The weakest teams fix the failed output. The strongest teams also fix the system that allowed it."* Each failure should leave infrastructure behind, such as a check, a gate, a tool contract, or a memory entry. A corrected answer helps one run. A corrected harness helps every later run.

**Harness decay** is the warning in the other direction. Models and tools improve, and a workaround for last year's model can stop this year's model from using a better strategy. Treat harness components like production code, and ask of each one:

- Which failure does this prevent?
- How often does that failure still happen?
- What latency and complexity does it add?
- What happens if we remove it?

*"The best harness is not the largest one. It is the smallest system that reliably closes the gap between intent and evidence. Build to delete."* This connects to Browser Use's [*bitter lesson of agent harnesses*](browser-harness.md), which argues for a thin harness the agent extends itself. Both views agree on removing scaffolding that no longer earns its place.

## 5. The minimum viable harness

Build in layers, and add a layer only when an observed failure calls for it:

| Level | Adds | Companion repo |
|---|---|---|
| 1 · Bounded task | objective, scope, constraints, acceptance checks | the eval tasks' prompts and checks |
| 2 · Legible environment | project map, commands, local instructions, dependencies | — |
| 3 · Controlled actions | typed tools, argument validation, path and permission boundaries, structured results | v1, v2 |
| 4 · Durable execution | run state, checkpoints, decisions, lessons | v4 (memory), JSONL traces |
| 5 · Evidence | deterministic checks, adversarial verification, change receipt | v3, v5 |
| 6 · Recovery and learning | failure classes, bounded retries, escalation, harness updates | the version history itself |

*"Do not begin with a multi-agent architecture because a single prompt occasionally needs clarification. Complexity should be earned by observed failure."*

## 6. What practitioners report

**Anthropic** ([2025-11-26](https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents)) describes long-running coding across many context windows:

- An **initializer agent** writes `init.sh`, a `claude-progress.txt` log, a structured JSON **feature list** of end-to-end features, and an initial git commit.
- Each later **coding agent** session reads the progress file and the feature list, works on **one feature at a time**, and leaves the environment clean.
- The fix for *"declares victory on the entire project too early"* was the feature list.
- Agents would pass unit tests or curl checks but miss that a feature didn't work **end to end**. Prompting them to test through browser automation, as a user would, fixed most of that.

**OpenAI** ([2026-02-11](https://openai.com/index/harness-engineering/), as summarized in the post's coverage, e.g. [InfoQ](https://www.infoq.com/news/2026/02/openai-harness-engineering-codex)) reports:

- **Early slowness came from the environment.** Progress was slow at first "not because Codex was incapable, but because the environment was underspecified".
- **The app was made legible to the agent.** The team wired the Chrome DevTools Protocol into the agent runtime, with skills for DOM snapshots, screenshots, and navigation.
- **Rules became mechanical.** Architectural boundaries were enforced by structural tests rather than by prose.
- **Review moved agent-to-agent.** Most review effort shifted there over time.
- **The reported scale** was about 1,500 merged pull requests driven by three engineers. That is the post's own claim, not an independent measurement.

**Hashimoto** ([2026-02-05](https://mitchellh.com/writing/my-ai-adoption-journey)) puts it plainly: give the agent *"fast, high quality tools to automatically tell it when it is wrong."*

## 7. Measuring a harness

Token counts and "tasks attempted" are the wrong unit. **The useful unit is accepted work: trusted outcomes per unit of human attention.** Lunar's list of metrics:

- first-pass acceptance rate;
- recovery rate after a tool failure;
- repeated-failure rate;
- human interventions per task;
- **unsupported completion claims**;
- time from request to verified outcome;
- harness overhead per component.

The illusion this guards against: *"An agent can look highly productive while creating expensive review work."* (The article's metric formula is an image that couldn't be retrieved, so it isn't reproduced here.)

## 8. When you don't need one

| A plain prompt is enough when | Add a harness when |
|---|---|
| the task is short | work spans tools or sessions |
| the output is easy to inspect | the environment can change under the agent |
| failure is cheap | actions have real consequences |
| nothing external changes | "done" is hard to judge by eye |
| a human stays in the loop | the same failure keeps coming back |
| | human review has become the bottleneck |

*"The purpose of a harness is not to make a demo look sophisticated. It is to make real work dependable."*

## 9. A harness spec template

Lunar's §17 says to define a harness spec before granting autonomy, but the spec itself is an image that couldn't be retrieved. This template is mine. It follows the article's sections, and each field points to the part above that it fills in:

```yaml
task:          # §3.1 contract
  outcome:     "..."
  scope:       [paths, services]
  must_not_change: [...]
  done_when:   [commands whose exit codes decide, artifacts that must exist]
  needs_human: [deploy, delete, spend > $X, external messages]
context:       # §3.2
  always:      [map file, conventions]
  on_demand:   [docs, code search]
tools:         # §3.3 — each with input schema, timeout, idempotency, evidence it returns
policy:        # §3.8 — enforced outside the model: permissions, hooks, sandbox, network
state:         # §3.5 — decisions, constraints, progress, open risks; where they persist
verification:  # §3.6–3.7 — deterministic checks first, then an independent reviewer with a rubric
recovery:      # §3.9 — failure classes → actions; budgets: attempts, time, spend, scope; escalation
trace:         # §3.11 — what is recorded, where, and how to resume from a checkpoint
receipt:       # §3.12 — what changed, evidence, unresolved risk
```

*"If these fields are undefined, the agent is not autonomous. It is improvising."*

## 10. How it fits with the rest of this library

- **[Loop Engineering](loop-engineering.md)**: the loop is one part of the harness. Harness engineering is the whole system around it.
- **[Graph Engineering](graph-engineering.md)**: an explicit graph is one way to build the harness's control flow. Its checkpoints are §3.11.
- **[Building an Agent Evaluator](building-agent-evaluators.md)** and **[Critique Agents as a Graph](critique-agent-graph.md)**: §3.6–3.7 in depth.
- **[Why Spec-Driven Development Is Essential for Agentic SE](spec-driven-development-agentic.md)**: §3.1's contract, at feature scale.
- **[Browser Harness](browser-harness.md)**: the opposite philosophy, a thin harness the agent extends. Read it alongside §4's decay argument.
- **[Jev + Claude Code](jev-claude-code.md)**: a policy gate (§3.8) built as a Claude Code hook.
- **[Claude Code Prompt Patterns](claude-code-prompt-patterns.md)**: the user-side version, where "make it prove it" and "rules in hooks, not prose" are §3.6 and §3.10.
- **[Durable Execution](durable-execution.md)** and **[Event Sourcing & CQRS](event-sourcing-cqrs.md)**: the machinery behind resumable runs and memory as state.

## 11. Caveats

- **The X article is a synthesis, not a study.** It contains no measurements, and its framework (contract, gateway, evidence, policy, recovery, trace) is presented as best practice, not tested. The numbers in this note come from Anthropic's and OpenAI's own posts, and from the companion repo's scripted evals, each labelled as such.
- **Some of the article's diagrams are images**, including the task-contract example, the tool contract, the memory categories, the policy table, the loop, the ladder, the metric, and the spec. A text mirror couldn't retrieve them. Where this note fills those gaps, it says so.
- **A harness can overfit to one model.** That's §4's decay point, and the strongest argument for keeping every component justified by a failure you can still reproduce.

## 12. Go deeper

- 🔧 **[harness-engineering-getting-started](https://github.com/gsaini/harness-engineering-getting-started)**: a harness built from scratch on the Claude API, one failure and one fix per version, with offline evals.
- 🔧 **[egm-getting-started](https://github.com/gsaini/egm-getting-started)**: §3.7 in practice, where fresh-context reviewers caught what the author missed.
- 🔧 **[jev-claude-code-case-study](https://github.com/gsaini/jev-claude-code-case-study)**: §3.8 in practice, a policy hook that only ever tightens.

### Primary references

- Lunar, [*Harness Engineering: The Complete Guide to Building AI Agents That Don't Fall Apart*](https://x.com/LunarResearcher/status/2096570562625655088) (X article, 2026-09-06).
- Mitchell Hashimoto, [*My AI Adoption Journey*](https://mitchellh.com/writing/my-ai-adoption-journey) (2026-02-05).
- Anthropic, [*Effective harnesses for long-running agents*](https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents) (2025-11-26).
- OpenAI (Ryan Lopopolo), [*Harness engineering: leveraging Codex in an agent-first world*](https://openai.com/index/harness-engineering/) (2026-02-11), and [InfoQ's coverage](https://www.infoq.com/news/2026/02/openai-harness-engineering-codex).
- marmelab, [*The State of AI Harness Engineering 2026*](https://marmelab.com/blog/2026/09/24/the-state-of-ai-harness-engineering-2026.html) · [awesome-harness-engineering](https://github.com/ai-boost/awesome-harness-engineering).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
