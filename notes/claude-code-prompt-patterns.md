# 🧭 Claude Code Prompt Patterns — Getting More From the Agent

> **Level:** 🟢 Beginner–🟡 Intermediate · **Reading time:** ~10 min · **Prerequisites:** none. [Critique Agents as a Graph](critique-agent-graph.md) explains *why* the critique patterns work · **As of:** October 2026. Claude Code changes fast, so check the [docs](https://code.claude.com/docs/en/cli-reference) when a command doesn't behave as described.

Most weak results from a coding agent don't come from the model. They come from prompts that leave out three things: **what "done" means**, **how to prove it**, and **who checks the work**. The patterns below fill those gaps. Most of them are a single line you add to the end of an ordinary prompt.

> **The one rule:** ask for **evidence**, not claims. A test run, command output, or a `file:line` reference is evidence. "I fixed it" is a claim.

## Table of contents

- [1. Weak prompt → strong prompt](#1-weak-prompt--strong-prompt)
- [2. Pin down the goal before any code](#2-pin-down-the-goal-before-any-code)
- [3. Make it prove things](#3-make-it-prove-things)
- [4. Critique loops](#4-critique-loops)
- [5. Fan out](#5-fan-out)
- [6. Memory and context](#6-memory-and-context)
- [7. Long-running and recurring work](#7-long-running-and-recurring-work)
- [8. Shape the output](#8-shape-the-output)
- [9. Templates that combine them](#9-templates-that-combine-them)
- [10. Anti-patterns](#10-anti-patterns)
- [11. Go deeper](#11-go-deeper)

---

## 1. Weak prompt → strong prompt

| Weak | Strong | What changed |
| ---- | ------ | ------------ |
| "Critique it to improve the output." | "Have a fresh subagent review this against the goal. Every issue needs evidence; drop the rest. Fix what's left and show me before and after." | The reviewer is independent, and issues need evidence. |
| "Fan out the agents." | "Fan out one agent per dimension (bugs, performance, security). Each returns findings with `file:line`. Verify every finding before reporting." | Each agent has a clear scope and output, and findings are checked. |
| "Fix the bug." | "Reproduce it with a failing test first, then fix the root cause and show the test passing." | Proof that the bug existed and is now gone. |
| "Make it better." | "Make it faster: the p95 of `bench.py` should drop below 200 ms. Keep the public API unchanged." | "Better" becomes something measurable. |
| "Add tests." | "Add tests for the inputs most likely to break this, and run them. Tell me which ones failed before your fix." | Tests aimed at real risk, with results. |

## 2. Pin down the goal before any code

| Add this | Why it helps |
| -------- | ------------ |
| "Ask me up to 3 questions before you start." | Unclear points come back as multiple-choice questions now, instead of wrong guesses later. |
| "Plan first. Don't edit anything until I approve." | You review the approach while it's still cheap to change. Plan mode does the same. |
| "Read X and Y and explain how they work before changing them." | It learns the code before touching it, and you can check that understanding. |
| "Done means: …" (a command that passes, a behavior, an output) | It gets a finish line it can check itself against. |
| "…because <reason>" after a constraint | Knowing the reason, it makes better calls on cases you didn't list. |

For anything bigger than a single change, write the goal down as a spec first. See [Why Spec-Driven Development Is Essential for Agentic SE](spec-driven-development-agentic.md).

## 3. Make it prove things

Of all these patterns, this one helps the most.

| Say | Effect |
| --- | ------ |
| "Prove it works: run it and show me the output." | You get evidence instead of a claim. |
| "Write a failing test first, show it failing, then fix it." | The fix is tied to the actual bug. |
| "Run tests, lint and typecheck before you say done." | Nothing is reported as done until those pass. |
| "Run the app and check the change in the real app." | It starts and uses the app, not just the tests. |
| "What didn't you verify?" | Gaps come out that would otherwise be glossed over. |

## 4. Critique loops

Plain "critique it" in the same conversation is the weakest version, because the reviewer is the author and can still see its own reasoning. Research agrees: self-correction without outside feedback often doesn't help ([Huang et al., 2023](https://arxiv.org/abs/2310.01798)). These versions are stronger:

| Say | Effect |
| --- | ------ |
| "Have a fresh subagent review this against <goal>. It shouldn't see your reasoning." | You get an independent reviewer with its own context. |
| "Every issue needs evidence: a failing test, command output, or `file:line`. Drop the rest." | Vague nitpicks get dropped. |
| "What inputs would break this? Write them as tests and run them." | Suspicions become facts. These are the *probes* from the critique-graph note. |
| "Critique the critique: which findings are wrong or not worth fixing?" | Reviewer noise gets filtered out. |
| "Argue for the opposite approach, then pick one and say why." | It doesn't lock in on its first idea. |

Some built-in commands do this job too:

- **`/code-review`** reviews your changes. Levels go from `low` to `max`, and `--fix` applies the findings.
- **`/simplify`** cleans up changed code.
- **`/security-review`** checks your pending changes for security problems.
- **`/code-review ultra`** runs a deep multi-agent review of the branch in the cloud. It's billed and you start it yourself.

## 5. Fan out

The phrases **"fan out agents"** and **"use a workflow"**, and the keyword **`ultracode`**, explicitly opt in to a multi-agent workflow. Workflows are powerful but use a lot of tokens. By default they stay under about 10 agents; you can change that with "Dynamic workflow size" in `/config`.

| Say | Effect |
| --- | ------ |
| "Use subagents to explore A, B and C in parallel; bring back conclusions, not file dumps." | Runs in parallel and keeps the main conversation's context small. |
| "Fan out one agent per dimension (bugs, performance, security), then verify each finding before reporting." | A review-then-verify pipeline that removes false positives. |
| "Try 3 approaches in parallel, each in its own git worktree, then compare them against <criteria>." | Competing implementations that don't overwrite each other. |
| "Research X from 3 angles with sources; then have one agent fact-check the claims." | A cited research report. |

**Don't fan out** for one-file changes, or when step 2 depends on what step 1 finds. Parallel agents only help when the pieces are independent.

## 6. Memory and context

| Say | Effect |
| --- | ------ |
| "Remember that …" | Saved to persistent memory for future sessions. |
| "From now on, whenever X happens, do Y." | Becomes a [hook](https://code.claude.com/docs/en/hooks) in `settings.json`. Claude Code runs it automatically, so it doesn't rely on Claude remembering. |
| `/init`, or "add this rule to CLAUDE.md" | Rules that apply to every session in the project ([memory docs](https://code.claude.com/docs/en/memory)). |
| `@file`, or select code in the editor | Exact context, with no guessing which file you mean. |
| `/clear` between unrelated tasks | Old context stops steering new work. |

A rule written in prose is a request. A hook, a permission rule, or a CI check enforces it. Put anything that must always happen into one of those.

## 7. Long-running and recurring work

- "Run it in the background and tell me when it finishes."
- "Watch the logs until X appears."
- `/loop 5m check CI on this PR` checks every 5 minutes. Plain `/loop` lets Claude choose the pace.
- `/schedule` runs a cloud agent on a schedule, for example a weekly dependency check.

## 8. Shape the output

- "Recommend one option and say why. Don't list every option."
- "This is for <audience>." Length, terms and detail change to fit the reader.
- "Keep the diff minimal; don't touch unrelated code."
- "Publish it as an artifact" turns the result into a shareable page.
- "How do I … in Claude Code?" sends the question to a docs-aware guide agent.
- `/fewer-permission-prompts` allow-lists the safe read-only commands you approve often.

---

## 9. Templates that combine them

**Feature**

```text
Goal: <what>. Done means: <check>. Constraints: <...> because <why>.
Read <files> first and ask me up to 3 questions. Then plan and wait for my OK.
Implement test-first. Before you say done: run tests and lint, then have a fresh
subagent review the diff against the goal; fix only findings with evidence.
Show me the final test output.
```

**Bug**

```text
Bug: <symptom>. Reproduce it with a failing test before changing any code.
Find the root cause, not just the symptom. Fix it, show the test passing,
and tell me where else the same cause could bite.
```

**Review or research fan-out**

```text
Fan out agents: one per <dimension>. Each returns findings with file:line and
evidence. Then verify every finding adversarially and report only the confirmed
ones, most severe first.
```

**Refactor**

```text
Refactor <area> to <target shape>. Behavior must not change: run the full test
suite before and after and show both results. Keep the diff to <area>.
Then run /simplify and /code-review high on the result.
```

## 10. Anti-patterns

- ❌ **"Critique it" in the same conversation, round after round.** The author grades itself, and the loop drifts into rewording.
- ❌ **Critique without evidence.** This turns into bikeshedding. "Evidence or drop it" is the rule that matters most.
- ❌ **Fanning out by default.** Every agent costs tokens. Save fan-outs for work where a mistake would be expensive or the pieces are truly independent.
- ❌ **"Make it better"** with no measure of better.
- ❌ **Accepting "done" without output.** Ask for the test run.
- ❌ **Rules only in prose** ("never touch prod") that nothing enforces. Use a hook, permissions, or CI.
- ❌ **One endless session for unrelated tasks.** Old context leaks into new work. Use `/clear`.

## 11. Go deeper

Related material in this library:

- 📝 **[Critique Agents as a Graph](critique-agent-graph.md)**: why independent, evidence-backed critique beats self-critique, built as a graph.
- 📝 **[Loop Engineering](loop-engineering.md)**: the loop every one of these prompts runs inside.
- 📝 **[Building an Agent Evaluator](building-agent-evaluators.md)**: the reviewer as an evaluator, and its biases.
- 📝 **[Why Spec-Driven Development Is Essential for Agentic SE](spec-driven-development-agentic.md)**: "done means" grown into a spec.
- 📝 **[Graph Engineering](graph-engineering.md)**: what a fan-out-then-verify workflow is underneath.

### Primary references

- Claude Code docs: [subagents](https://code.claude.com/docs/en/sub-agents), [hooks](https://code.claude.com/docs/en/hooks), [memory](https://code.claude.com/docs/en/memory), [skills](https://code.claude.com/docs/en/skills), [CLI reference](https://code.claude.com/docs/en/cli-reference).
- Huang et al., [*Large Language Models Cannot Self-Correct Reasoning Yet*](https://arxiv.org/abs/2310.01798) (2023).
- Gou et al., [*CRITIC: Large Language Models Can Self-Correct with Tool-Interactive Critiquing*](https://arxiv.org/abs/2305.11738) (2023).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
