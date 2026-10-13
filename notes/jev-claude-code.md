# 🧠 Jev + Claude Code — System One Inside a System Two Agent

> **Level:** 🟡 Intermediate · **Reading time:** ~12 min · **Companion repo:** [gsaini/jev-claude-code-case-study](https://github.com/gsaini/jev-claude-code-case-study) (runs offline; `--live` for Jev) · **Prerequisites:** [Jev Ultrafast](jev-ultrafast.md), [Building an Agent Evaluator](building-agent-evaluators.md) · **Researched:** 2026-10-07. Jev is weeks old, so re-check prices and limits.

In late September 2026, TypeSafe released **Jev**, which it calls the first *System One* model. Within days, "Jev + Claude Code" was all over developer feeds: plugins, MCP servers, hook kits, setup guides. This note covers what the combination actually is, the four ways people use it, what the headline numbers do and don't measure, and what building two working examples taught.

> **The one-line thesis:** *Jev isn't a model for Claude Code to run on. It's Claude Code's System One.* Claude plans, writes, and handles exceptions (System Two). Jev makes the fast, bounded, typed decisions inside the loop. Code owns everything else.

## Table of contents

- [1. What Jev is](#1-what-jev-is)
- [2. What it isn't](#2-what-it-isnt)
- [3. Four integration patterns](#3-four-integration-patterns)
- [4. Claims vs. independent evidence](#4-claims-vs-independent-evidence)
- [5. Case study A: an app Claude Code builds with Jev](#5-case-study-a-an-app-claude-code-builds-with-jev)
- [6. Case study B: Jev guarding Claude Code's own shell](#6-case-study-b-jev-guarding-claude-codes-own-shell)
- [7. Design rules that fell out](#7-design-rules-that-fell-out)
- [8. Anti-patterns](#8-anti-patterns)
- [9. Go deeper](#9-go-deeper)

---

## 1. What Jev is

Jev writes no text. You send a **state** (text or JSON) and named, typed **questions**, and you get typed answers back with probabilities:

| Primitive | Question | Answer |
|---|---|---|
| **Choice** | pick one option from a set | the option, a probability per option, `confidence` |
| **Score** | where does this sit on ordered, described levels? | an expected level, probabilities, `confidence` |
| **Noul** | is this statement true? | P(yes) |

```python
from typesafe_sdk import Choice, Noul, TypeSafeClient   # pip install typesafe-sdk; reads TYPESAFE_API_KEY

with TypeSafeClient() as client:                          # model defaults to "jev-latest"
    r = client.system_one(
        state={"ticket": "I was charged twice. Fix it ASAP."},
        questions={
            "team": Choice(instructions="Which team handles `ticket`?",
                           criteria={"billing": "Charges, refunds", "technical": "Bugs", "other": None}),
            "urgent": Noul(instructions="Is `ticket` time-sensitive?"),
        },
    )
r.choices["team"].choice, r.choices["team"].confidence, r.nouls["urgent"].noul
```

The questions in a request are evaluated **in parallel against one state**, so ten questions cost about the same latency as one ([speculative fan-out](https://docs.typesafe.ai/patterns/fan-out)). The current model, `jev-1.13.0` ([models](https://docs.typesafe.ai/models)), costs **$42 per billion input tokens; output is free**. Typical latency is 70–500 ms, the context limit is 64K tokens, input is text only, and rate limits "adjust dynamically".

`confidence` measures how concentrated the distribution is, not whether the answer is right. For a Choice it is `(n·peak − 1)/(n − 1)` ([confidence](https://docs.typesafe.ai/confidence)).

TypeSafe publishes a page of **known weak spots** ([Jev 1.13 jaggedness](https://docs.typesafe.ai/model-jaggedness/jev-1.13)), and it's the most useful page in the docs:

- it reads instructions **literally**;
- it is unreliable at counting, math, and **comparing dates**;
- it is weak on indirection;
- extra state it doesn't need lowers accuracy (context rot);
- adversarial text in the state can move answers;
- it is biased toward a Choice's **first option**;
- it can't generate text.

## 2. What it isn't

TypeSafe says it plainly ([Jev with coding agents](https://docs.typesafe.ai/introduction/coding-agents)): there is no `model: "jev-latest"` that powers Claude Code. A coding agent needs a model that streams text, calls tools, and edits files. Jev does none of those. The combination is **two models with different jobs**, the same split as [Jev Ultrafast](jev-ultrafast.md) at browser scale: *choose, don't generate*.

## 3. Four integration patterns

| # | Pattern | Example | When it fits |
|---|---|---|---|
| **A** | **Claude Code builds an app that calls Jev** | the official [TypeSafe skill](https://github.com/typesafe-ai/skills): `claude plugin marketplace add typesafe-ai/skills`, then `claude plugin install typesafe@typesafe-ai` | your *product* has repeated, bounded judgments (routing, triage, moderation, reranking) |
| **B** | **Jev guards Claude Code's loop** | [jev-kit](https://github.com/jonathanavis96/jev-kit): *Airlock* on `PreToolUse`, plus a *tier guard* on subagent dispatch | you want a fast semantic check on the agent's own actions |
| **C** | **Jev as a tool Claude Code calls** | [jev-code](https://github.com/FrancoisChastel/jev-code): MCP tools `jev_classify`, `jev_check`, `jev_score`, `jev_rank`, returning `decision: auto \| review` | the agent keeps making small classifications by eye or with regex |
| **D** | **Jev drives a browser for Claude** | [jev-mcp](https://github.com/SAITS-Swiss-AI-Tech-Services/jev-mcp), [jev-reflex](https://github.com/MSalvalaggio/jev-reflex) ("Claude thinks, Jev reacts"), built on [Jev Ultrafast](https://github.com/browser-use/jev-ultrafast) | browser QA and form flows where each step is picking from what's on the page |

All four keep **planning, writing, and exceptions with Claude**, and send the narrow, high-volume calls to Jev.

---

## 4. Claims vs. independent evidence

TypeSafe's headline is **"193.6× faster, 444.6× cheaper"**, and its own post calls these "the higher end of real world gains". Independent write-ups add context:

| Finding | Source |
|---|---|
| The benchmark scores **agreement with the average answer of two frontier models**, not human-labeled correctness. Independent tests report roughly **5×–25× faster**, with accuracy on par or slightly worse | [DataSci Ocean](https://datasciocean.com/en/ai-concept/jev-overview/) |
| Phishing, asked as **one** question: Jev 62.6% vs Haiku 81.3%. Split into **five narrow signals** plus a logistic regression: Jev **95.0%** vs 93.2% | [beri.net](https://www.beri.net/article/typesafe-jev-typed-decision-model-calibration-decomposition-shadow-eval) (2026-09-20) |
| Out-of-distribution miscalibration: ECE 0.107; on one ticket-priority question, right 44.7% of the time at an average probability of 0.74 | same |
| 9,081 product-match pairs for **$0.32 in 13 minutes**, with correct published matches up from 78% to 90%, but *"when Jev is wrong, it is usually sure of itself"* | [paddo.dev](https://paddo.dev/blog/thirty-cent-judge) (2026-09-30) |

> **Takeaway:** the biggest lever isn't the model, it's **decomposition**, and the confidence number is **not** a correctness guarantee. The good news is the price: verifying Jev on your own labeled data costs fractions of a cent.

---

## 5. Case study A: an app Claude Code builds with Jev

**Triage Desk** ([docs](https://github.com/gsaini/jev-claude-code-case-study/blob/main/docs/triage-desk.md)) routes support tickets. Each ticket gets **one request with six questions**:

- `department`: a Choice with a no-match `other` option;
- `urgency`: a Score whose levels describe *situations*, not numbers;
- `security`, `legal`, and `churn`: Nouls;
- `refund`: a **speculative** Noul whose premise is stated up front ("Assuming … billing") and which is used only when the department is billing.

Code then decides:

```text
security ≥ 0.5           → security queue, P1, a person reads it
department = other       → review
confidence < 0.6         → review, with the two leading candidates shown
legal ≥ 0.5              → keep the team, but a person reads it first
SLA = received_at + {72, 24, 8, 1}h by urgency level    ← date arithmetic in code, never the model
```

The eval counts **wrong auto-routes**: tickets sent to the wrong team *with no human looking first*. A threshold sweep re-decides the same answers at τ = 0.0–0.9 with **no new calls**, because the policy is code.

The offline keyword baseline, the router most teams have before a model, sent **two security incidents to the wrong team without review**. One was *"it wasn't me"* about a strange sign-in. The other went to billing because "pri**vat**e" contains the keyword `vat`. Those are the failures a semantic judge is meant to fix; the live run is what checks whether it does.

## 6. Case study B: Jev guarding Claude Code's own shell

**Jev Guard** ([docs](https://github.com/gsaini/jev-claude-code-case-study/blob/main/docs/jev-guard.md)) is a `PreToolUse` hook:

```text
Bash command ─► hard deny? (rm -rf /, curl or wget into sh, mkfs, fork bomb)   → deny     [code]
             ─► secrets?   (.env, ~/.ssh, $..._TOKEN)                         → ask      [code]
             ─► plainly read-only? (ls, git status, pytest)                   → no opinion [code]
             ─► Jev: 5 Nouls on the REDACTED command                          → ask, or no opinion
                 destroys_data · leaves_machine · discards_work · system_wide · exposes_secrets
```

Three lessons from building it:

1. **It only tightens.** A `PreToolUse` hook's `allow` *skips the permission prompt* ([hooks docs](https://code.claude.com/docs/en/hooks)). A probabilistic judge must never be what approves a command, so the hook answers only `deny`, `ask`, or nothing, and a test checks that across every labeled command. If Jev is slow, down, or has no key, the hook stays silent and Claude Code's normal permission rules apply.
2. **Decide before any side effect.** The first version wrote its log line before printing its decision. On the first end-to-end run the log directory was missing, the hook crashed, and `rm -rf /` got **no deny**: a crashing hook is a *non-blocking* error. The fix is to print the decision first and log as a best effort, with a regression test.
3. **Keep the judged agent's words out of the judge's input.** The state is the redacted command only, never Claude's `description` of it, which could argue its own case. The key can live in `~/.config/jev-guard/key` instead of the agent's environment.

Offline results on 61 labeled commands: **rules alone** miss 22 of 31 risky commands (`git push`, `terraform apply`, `kubectl delete`) and never prompt on routine work. **Keywords** miss 2 but add 4 needless prompts (`rm -rf node_modules`, `git push --dry-run`, `docker run --rm`). Those are the boundary cases the Jev questions spell out in their criteria. A third of commands never reach a model. The live comparison of five questions against one is the experiment still to run.

---

## 7. Design rules that fell out

1. **Give Jev judgments, not jobs.** Repeated, bounded, cheap-to-check calls: routing, gating, triage, reranking.
2. **One judgment per question; boundary cases in the criteria.** Jev reads literally, and decomposition was worth 30+ points in independent tests.
3. **Code owns arithmetic, dates, policy, and action.** Jev picks a described level; code adds the hours.
4. **Gate on confidence, but choose the threshold from labeled data**, because the confidence number is not correctness.
5. **Ask everything in one request** (speculative fan-out), and use only the answers whose premise holds.
6. **Fail toward the human or toward the existing safety net**: a review queue, or Claude Code's own permissions.
7. **Shadow first, then enforce.** Log what the judge *would* do, read the log, then switch it on.
8. **Never send what the judged party wrote in its own defense**, and redact before anything leaves the machine.

## 8. Anti-patterns

- ❌ Looking for a setting that makes Jev Claude Code's model.
- ❌ One "is this dangerous / phishing / urgent?" question that hides five judgments.
- ❌ Asking Jev to count, compare dates, or do math.
- ❌ Treating `confidence` as the probability of being right.
- ❌ A guard hook that answers `allow`.
- ❌ Writing logs, metrics, or anything else *before* the guard's decision is out.
- ❌ Quoting "193×" without saying it measures agreement, not correctness.

## 9. Go deeper

Related material in this library:

- 📝 **[Jev Ultrafast](jev-ultrafast.md)**: the same "choose, don't generate" idea applied to browsing (pattern D).
- 📝 **[Browser Harness](browser-harness.md)**: the opposite philosophy, where the agent writes its own tools.
- 📝 **[Constraint-Driven Development](constraint-driven-development.md)**: typed answers as unrepresentable illegal states.
- 📝 **[Building an Agent Evaluator](building-agent-evaluators.md)**: judges, calibration, and labeled sets.
- 📝 **[Critique Agents as a Graph](critique-agent-graph.md)**: the same "judge sees artifacts, not the author's reasoning" rule.
- 📝 **[Claude Code Prompt Patterns](claude-code-prompt-patterns.md)**: hooks vs. prose rules; shadow, then enforce.

### Primary references

- TypeSafe: [docs index](https://docs.typesafe.ai/llms.txt) · [Jev with coding agents](https://docs.typesafe.ai/introduction/coding-agents) · [models & pricing](https://docs.typesafe.ai/models) · [jaggedness](https://docs.typesafe.ai/model-jaggedness/jev-1.13) · [confidence](https://docs.typesafe.ai/confidence) · [agent skill](https://github.com/typesafe-ai/skills) · [Python SDK](https://docs.typesafe.ai/sdk/python)
- Claude Code: [hooks](https://code.claude.com/docs/en/hooks) · [permissions](https://code.claude.com/docs/en/permissions)
- Community: [jev-kit](https://github.com/jonathanavis96/jev-kit) · [jev-code](https://github.com/FrancoisChastel/jev-code) · [jev-mcp](https://github.com/SAITS-Swiss-AI-Tech-Services/jev-mcp) · [jev-reflex](https://github.com/MSalvalaggio/jev-reflex) · [Flavio Copes' deep dive](https://flaviocopes.com/jev/) · [Charlie Hills' setup guide](https://charliehills.substack.com/p/jev-claude-code-the-setup-guide)
- Independent evaluations: [DataSci Ocean](https://datasciocean.com/en/ai-concept/jev-overview/) · [beri.net](https://www.beri.net/article/typesafe-jev-typed-decision-model-calibration-decomposition-shadow-eval) · [paddo.dev](https://paddo.dev/blog/thirty-cent-judge)
- Case study: [gsaini/jev-claude-code-case-study](https://github.com/gsaini/jev-claude-code-case-study)

*Original study note. Third-party numbers are as reported by their authors; the case study's offline numbers come from a keyword baseline, not Jev. Corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
