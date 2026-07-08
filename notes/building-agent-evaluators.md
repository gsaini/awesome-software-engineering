# 🧪 Building an Agent Evaluator — A Detailed Study Note

> **Level:** 🟡 Intermediate–Advanced · **Reading time:** ~20 min · **Prerequisites:** [Loop Engineering](loop-engineering.md) and a working idea of LLM agents (tool calls, multi-step runs).

An **agent evaluator** (or *verifier*) is the separate system that decides whether an agent's work is actually good. It's the single most important — and most skipped — component of a reliable agent. Yesterday's loop-engineering note said it in one line: *never let a loop grade its own work.* This note is about how to build the thing that grades it instead.

> **The core principle:** a generator that scores its own output becomes sycophantic and converges on **slop**. Quality comes from an **independent evaluator** with its own criteria — the same reason we separate authors from reviewers, and code from its tests.

## Table of contents

- [1. Why evaluators matter](#1-why-evaluators-matter)
- [2. The three levels of agent evaluation](#2-the-three-levels-of-agent-evaluation)
- [3. Outcome vs. trajectory](#3-outcome-vs-trajectory)
- [4. Pick the right judge](#4-pick-the-right-judge)
- [5. LLM-as-judge, done well](#5-llm-as-judge-done-well)
- [6. Agent-specific metrics](#6-agent-specific-metrics)
- [7. Evals as versioned datasets](#7-evals-as-versioned-datasets)
- [8. A concrete recipe](#8-a-concrete-recipe)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. Why evaluators matter

An agent loop is only as good as its stop condition, and the stop condition is only as good as the evaluator behind it. Without a trustworthy verifier you get:

- **Silent regressions** — a prompt or model change quietly makes things worse and nothing catches it.
- **Slop convergence** — the loop optimizes toward whatever the (self-)grader rewards, which drifts from what you wanted.
- **No way to ship confidently** — "seems fine when I tried it" doesn't scale past a demo.

The evaluator turns "vibes" into a **number you can regression-test**. It's the contract from loop engineering made executable.

---

## 2. The three levels of agent evaluation

Modern agent runs chain planning, tool calls, retrieval, and sub-agent handoffs across long trajectories. Evaluate at **three levels** — each answers a different question:

| Level | Question | Example checks |
| ----- | -------- | -------------- |
| **End-to-end (outcome)** | Did the full run accomplish the user's goal? | Task success, final-answer correctness, user satisfaction |
| **Trajectory (process)** | Was the *path* sound and efficient? | Right tools in the right order, no wasted loops, no dead ends |
| **Component** | Which piece broke? | This retriever's recall, that tool's arg formatting, a sub-agent's output |

> You need all three. End-to-end tells you *that* it failed; trajectory and component tell you *why* — which is what you actually fix.

---

## 3. Outcome vs. trajectory

Two independent axes that are easy to conflate:

- **An agent can call every tool correctly and still fail the task** (right steps, wrong result).
- **An agent can reach the right answer via a terrible path** (lucky guess, 40 wasted tool calls, huge cost).

So evaluate both:

- **Outcome eval** — did it achieve the goal? The bottom line.
- **Trajectory eval** — was the reasoning path efficient, safe, and correct? This is where cost, latency, and reliability live, and it's what predicts whether success *repeats*.

For anything irreversible or expensive, a good trajectory matters as much as a good outcome.

---

## 4. Pick the right judge

The first design decision: **deterministic check or LLM judge?** Match the tool to what you're measuring.

### Deterministic / rule-based (use whenever possible)

Cheap, fast, 100% reproducible — run these on **every** output:

- Schema / JSON validation, output-length bounds
- Tool-call **format** and argument-type correctness
- Exact / regex match against a known answer
- **Code execution**: does the generated code compile, run, and pass unit tests?
- Safety-filter and policy passes

> These catch the most common failures at essentially zero cost. Reach for an LLM judge only for what deterministic checks *can't* express.

### Reference-based metrics

Exact match, F1, BLEU/ROUGE — useful when there's a canonical answer, but weak for open-ended generation (they punish valid paraphrases). Use sparingly.

### LLM-as-judge

For anything subjective or output-dependent — helpfulness, coherence, groundedness, "does this answer the question." Powerful and flexible, but has failure modes (§5).

### Human evaluation

The gold standard and the **calibration anchor** for your automated judge — not something you run on every output, but the ground truth you measure the judge against.

**Rule of thumb:** *deterministic for objective, LLM-as-judge for subjective, human to calibrate the judge.*

---

## 5. LLM-as-judge, done well

Using a strong model to grade outputs. Three modes:

- **Pointwise (scoring)** — grade one output against a rubric (e.g. 1–5, or pass/fail per criterion).
- **Pairwise** — "is A or B better?" More reliable than absolute scores because relative judgments are easier.
- **Reference-guided** — grade against a gold answer or rubric.

### Techniques worth knowing

- **G-Eval** — express custom criteria in natural language; the judge reasons step-by-step (chain-of-thought) before scoring. Best for subjective, hard-to-formalize quality.
- **DAG (decision-tree judge)** — deterministic branches, hard gates, and multi-step scoring logic. Best when grading needs "if X fails, stop and fail."
- **QAG (question-answer generation)** — decompose evaluation into closed-ended yes/no questions and aggregate. Makes fuzzy criteria more objective.

### The biases (and how to fight them)

LLM judges are not neutral. Know these:

| Bias | What it is | Mitigation |
| ---- | ---------- | ---------- |
| **Position bias** | Prefers whichever answer comes first (or last) | Run both orders, average; or randomize |
| **Verbosity bias** | Rates longer answers higher regardless of quality | Rubric anchoring; penalize unwarranted length |
| **Self-preference** | Favors text from its own model family | Use a *different* model as judge |
| **Leniency / narrow range** | Everything gets a 4/5 | Force a rubric with explicit anchors + require justification |

**Always:** give the judge a **specific rubric with anchored levels**, require a **written justification** before the score (not after), and **calibrate against human labels** — measure agreement (e.g. Cohen's κ) and tune the prompt until the judge tracks humans on a labeled set. An uncalibrated judge is just a second opinion you can't trust.

---

## 6. Agent-specific metrics

Beyond generic output quality, agents need metrics for *acting*:

- **Task Completion / Success Rate** — the headline outcome metric.
- **Tool Correctness** — did it select the right tool with the right arguments? (Often deterministic.)
- **Trajectory Efficiency** — steps, tool calls, tokens, latency, and cost vs. an ideal path.
- **Groundedness / Faithfulness** (RAG) — are claims supported by retrieved context, or hallucinated?
- **Context Relevance / Retrieval quality** — did retrieval surface the right material?
- **Reasoning quality** — is the plan coherent and are the steps justified?
- **Safety / policy adherence** — no unsafe actions, no policy violations.

---

## 7. Evals as versioned datasets

The evaluator is only half the system; the other half is the **eval set** it runs against. Treat evals like a test suite:

- **Build a golden dataset** — representative inputs with expected outcomes/criteria. Mine real traces and failure cases, not just happy paths.
- **Cover edge cases deliberately** — the tail is where agents break.
- **Volume matters** — a common 2026 guideline is **≥500 cases before trusting aggregate metrics**; smaller sets are fine for smoke tests but noisy for decisions.
- **Version them** — an eval set is a spec. Changing it changes what "good" means, so track it in git alongside code.
- **Offline + online** — offline evals gate releases (run in CI); online evals (sampled production traffic, user feedback, guardrails) catch what offline missed.

> This closes the loop with loop engineering's *"eval integration"* pattern: verification becomes a **versioned dataset and rubric the loop must satisfy** — not a vibe check.

---

## 8. A concrete recipe

A minimal, layered evaluator for an agent:

```python
def evaluate(run) -> Result:
    # Layer 1 — deterministic gates (fast, 100% of runs, zero cost)
    assert valid_schema(run.output)              # structure
    assert all(valid_tool_call(c) for c in run.tool_calls)
    if run.code:
        assert run.code_tests_pass               # execute + test

    # Layer 2 — trajectory checks
    traj = score_trajectory(run)                 # steps, cost, tool correctness
    if traj.wasted_steps > BUDGET: flag("inefficient")

    # Layer 3 — LLM-as-judge for subjective quality (only if gates pass)
    judged = llm_judge(
        rubric=RUBRIC,                           # anchored 1–5 per criterion
        require_justification=True,
        swap_order=True,                          # kill position bias
        judge_model=DIFFERENT_MODEL,             # kill self-preference
    )

    return Result(outcome=judged.pass_, trajectory=traj, detail=judged.reasons)
```

Run this over the versioned eval set, aggregate, and gate the release on it. Wire the same evaluator into the agent's **loop** as the verifier/stop condition.

---

## 9. Best practices & anti-patterns

**Do**
- Separate the evaluator from the generator — always.
- Layer it: deterministic gates first, LLM-judge only for what they can't cover.
- Anchor LLM judges with explicit rubrics and require justification-before-score.
- Calibrate the judge against human labels; measure agreement.
- Evaluate outcome *and* trajectory.
- Version the eval set like code; grow it from real failures.

**Avoid**
- Letting the agent grade itself (slop).
- LLM-judge for things a `==` or a unit test could check (slow, costly, flaky).
- Trusting an uncalibrated judge, or one from the same model family as the generator.
- A single aggregate number with no component/trajectory breakdown — you won't know what to fix.
- Coverage theater: an eval that runs the agent but asserts nothing meaningful (echoes the [CRAP-score](crap-score.md) warning that coverage ≠ assertions).

---

## 10. Go deeper

Related material in this library:

- 📝 **[Loop Engineering](loop-engineering.md)** — the *why*: separate roles, contract-first, eval integration. This note is the *how* for the verifier half.
- 📗 **[AI Engineering — Chip Huyen](../books/)** — production evaluation of foundation-model apps, end to end.
- 📗 **[Designing Machine Learning Systems — Chip Huyen](../books/)** — evaluation, monitoring, and data flywheels.
- 📘 **[The New SDLC With Vibe Coding](../whitepapers/ai-agentic-engineering/)** — where verification fits in agentic engineering.
- 📄 **[ReAct](../papers/)** — the reasoning-and-acting loop these evaluators grade.

### Primary sources

- [LLM-as-a-Judge in 2026 — DeepEval](https://deepeval.com/blog/llm-as-a-judge)
- [LLM Agent Evaluation Metrics in 2026 — Confident AI](https://www.confident-ai.com/blog/llm-agent-evaluation-complete-guide)
- Zheng et al., *Judging LLM-as-a-Judge (MT-Bench / Chatbot Arena)*, 2023 — foundational study of LLM judges and their biases.
- Liu et al., *G-Eval: NLG Evaluation using GPT-4 with Better Human Alignment*, 2023.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
