# 🕸️ Graph Engineering in AI — A Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~18 min · **Prerequisites:** the [Loop Engineering](loop-engineering.md), [Durable Execution](durable-execution.md), and [Data Structures & Algorithms](data-structures-algorithms.md) notes. The term went viral in **July 2026**; the practice is older.

**Graph engineering** is the discipline of building AI systems around **explicit graphs** — designing the application as a graph of nodes and edges rather than one opaque, do-everything agent. It's the natural successor framing to "loop engineering": where a loop is a single agent iterating until done, a **graph makes the whole workflow's topology explicit** — which node runs next, what state it gets, where it branches, where a human steps in.

> **The one-line thesis:** *graph engineering is about modeling **execution**, not just data.* A graph says **which agent/tool/validator/human runs next and what state it receives.** That's the primary sense. A close cousin — knowledge graphs / GraphRAG — models the *data* as a graph. Both live under "graph engineering," but the headline meaning is **orchestration as an explicit graph**.

## Table of contents

- [1. Two senses of "graph"](#1-two-senses-of-graph)
- [2. Execution graphs: the core idea](#2-execution-graphs-the-core-idea)
- [3. What a graph buys you](#3-what-a-graph-buys-you)
- [4. Graphs vs. loops — the 2026 debate](#4-graphs-vs-loops--the-2026-debate)
- [5. LangGraph, the reference implementation](#5-langgraph-the-reference-implementation)
- [6. Knowledge graphs & GraphRAG](#6-knowledge-graphs--graphrag)
- [7. Why it's really durable execution for agents](#7-why-its-really-durable-execution-for-agents)
- [8. When the graph earns its cost](#8-when-the-graph-earns-its-cost)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. Two senses of "graph"

The term covers two related-but-distinct things — keep them straight:

| | **Execution / orchestration graph** | **Knowledge graph (GraphRAG)** |
| - | ----------------------------------- | ------------------------------ |
| **Models** | *Control flow* — which step runs next | *Data* — entities & their relationships |
| **Nodes are** | agents, tools, functions, validators, humans | entities (people, docs, concepts) |
| **Edges are** | flow of computation / transitions | typed relationships ("works-for", "cites") |
| **Example** | LangGraph, LlamaIndex Workflows | Microsoft GraphRAG, a Neo4j-backed retriever |
| **Answers** | "how does the work get done?" | "what does the agent know?" |

This note leads with **execution graphs** (the headline meaning) and covers knowledge graphs in §6. They compose: an execution-graph node can *query* a knowledge graph.

---

## 2. Execution graphs: the core idea

Instead of one agent looping freely, you **decompose the task into specialized nodes and wire them into a directed graph**:

```
            ┌─────────────┐
   input ──►│  router     │──► classify the request
            └─────┬───────┘
        ┌─────────┼──────────┐
        ▼         ▼          ▼
   ┌────────┐ ┌────────┐ ┌────────┐
   │ search │ │ analyze│ │ handle │   ← nodes: agents / tools / functions
   └───┬────┘ └───┬────┘ └───┬────┘
       └──────────┼──────────┘
                  ▼
            ┌───────────┐
            │ validate  │──► fail? ──► loop back (a cycle)
            └─────┬─────┘
                  ▼  pass
            ┌───────────┐
            │ human gate│──► interrupt for approval (durable pause)
            └─────┬─────┘
                  ▼
                output
```

Three primitives:
- **Nodes** — callable units: an agent, an LLM call, a deterministic function, a validator, a tool, a human step.
- **Edges** — the flow between nodes. **Conditional edges** route based on state ("if low confidence → escalate").
- **Shared state** — a state object passed through and updated by each node, the way nodes communicate (not free-form chat history).

> The mental model: it's the [orchestration patterns](loop-engineering.md) from your loop note — routing, parallelization, orchestrator–workers, evaluator–optimizer — made into a **first-class, inspectable graph** instead of implicit control flow buried in prompts and `if` statements.

---

## 3. What a graph buys you

Making the topology explicit unlocks capabilities a free-form loop makes hard:

- **Conditional routing** — branch on state (route to the right specialist; escalate on low confidence).
- **Parallel fan-out / fan-in** — run independent nodes concurrently, then merge (the `parallel()` shape from your [concurrency note](concurrency-parallelism.md), across agents).
- **Cycles** — a node can loop back (retry, refine) — graphs here are *not* strictly DAGs; controlled cycles are the point (evaluator → generator → evaluator).
- **Human-in-the-loop interrupts** — pause at a node for approval and resume later — a **durable checkpoint**, not a blocking wait.
- **Durable state & persistence** — state survives across steps and sessions; a crashed run resumes from its last checkpoint.
- **Observability** — you can *see* the graph, trace which path a request took, and debug a node in isolation (the [trajectory](building-agent-evaluators.md) is literally a path through the graph).

---

## 4. Graphs vs. loops — the 2026 debate

The live argument (Andrew Ng and others): **do you even need a graph?** The honest answer is *it depends on the shape of the work* — and over-reaching for a graph is a real failure mode.

| Use a **loop** ([loop engineering](loop-engineering.md)) when… | Use a **graph** when… |
| ------------------------------------------------------------- | --------------------- |
| A single, well-scoped, retryable task | Multiple specialized agents / steps |
| The agent iterates until a stop condition | Branching logic — different paths for different inputs |
| State fits in the context/loop | **State must persist across sessions** |
| Simplicity and speed matter most | You need parallelism, human checkpoints, durability |

> The rule mirrors your loop note's *"use the simplest thing that works"*: **start with a loop; graduate to a graph when branching, parallelism, durable state, or human gates make the loop's implicit control flow unmanageable.** A graph is more powerful *and* more overhead — don't pay for it until the workflow's complexity demands it.

---

## 5. LangGraph, the reference implementation

**LangGraph** is the flagship execution-graph framework (open-source, Python/JS). Its model *is* the concepts above:

- A **`StateGraph`** with a typed **state** object (fields updated by nodes, often via reducers so parallel updates merge).
- **Nodes** = functions `(state) -> partial state update`.
- **Edges** = normal transitions, plus **conditional edges** (a function picks the next node from state).
- **Compile** the graph to a runnable; **cycles allowed**.
- **Checkpointing / persistence** — a checkpointer saves state after each node, enabling **durable execution, resume-after-crash, and human-in-the-loop interrupts** (pause → get approval → resume).

Alternatives in the space: **LlamaIndex Workflows**, **Pydantic AI graphs**, **Burr**, **CrewAI** (role-based), **AutoGen / Semantic Kernel**, **OpenAI Agents SDK**, and **Temporal-backed** agent frameworks. The *pattern* — explicit nodes + edges + shared durable state — is converging across all of them.

---

## 6. Knowledge graphs & GraphRAG

The data-side sense: model knowledge as **nodes (entities) + typed edges (relationships)** an agent can **traverse**, instead of flat documents searched by vector similarity.

**GraphRAG** = retrieval-augmented generation where the retrieval step uses a graph. Microsoft's GraphRAG builds a knowledge graph from a corpus, clusters it into communities, and summarizes them — which beats plain vector RAG on:

- **Multi-hop questions** — "which of Alice's reports also worked on project X?" (follow edges, not similarity).
- **Global / holistic questions** — "what are the main themes across all these documents?" (community summaries).
- **Relationship-heavy domains** — org charts, citations, dependencies, supply chains.

Plain **vector RAG** still wins for **local, specific lookups** ("what does the refund policy say?") and is cheaper to build. Many systems go **hybrid**: vector search for recall + graph traversal for relationships. (This is the retrieval side of the RAG design your [CCAR-P Integration domain](api-design.md) drills.)

> Contrast with your [caching](caching-strategies.md)/[serialization](serialization-schema-evolution.md) notes: this is *derived data* again — a graph index built from source documents, needing the same freshness/rebuild discipline as any cache.

---

## 7. Why it's really durable execution for agents

Here's the connection that makes graph engineering click: **LangGraph's checkpointer + resume + human interrupts is [durable execution](durable-execution.md) applied to agents.**

- **Checkpoint after each node** = the durable-execution **event history**.
- **Resume a crashed run from its last checkpoint** = **replay** to reconstruct state.
- **Human-in-the-loop interrupt** = a durable pause (the `sleep`/signal pattern) — the workflow waits, consuming nothing, until a human resumes it.
- **Nodes should be idempotent** — because a resumed run may re-enter a node — the same **at-least-once + idempotency** rule (its ~9th appearance in this library).

So the "new" discipline of graph engineering is your existing notes recomposed: **orchestration patterns (loop note) + a graph data structure (DSA note) + durable execution + human-in-the-loop.** Recognizing that is the payoff.

---

## 8. When the graph earns its cost

Graphs add real overhead — a framework, a state schema, more moving parts. It earns that cost when the workflow has:

- **Genuine branching** (distinct paths per input), not one linear/looping flow.
- **Parallelism** worth exploiting.
- **State that must survive** crashes or span sessions.
- **Human checkpoints** or long waits.
- **Multiple specialized agents** that need explicit coordination.

If none of those hold — a single retryable task, state fitting in one loop — **the graph is over-engineering**; a loop (or even a plain function) is the right call.

---

## 9. Best practices & anti-patterns

**Do**
- **Start with a loop; graduate to a graph** only when branching/parallelism/durability/human-gates demand it.
- **Keep nodes small, single-purpose, and idempotent** (resumable).
- **Model state explicitly** (a typed schema with clear reducers), not free-form chat history.
- **Use checkpointing** for durability and human-in-the-loop.
- **Make the graph observable** — trace the path each request took; evaluate the [trajectory](building-agent-evaluators.md), not just the outcome.
- **Go hybrid on retrieval** — vector RAG for lookups, graph traversal for relationships.

**Avoid**
- **Reaching for a graph when a loop would do** — the #1 anti-pattern of 2026.
- **A giant god-node** that does everything — defeats the point of the topology.
- **Unbounded cycles** with no stop condition — same runaway risk as any agent loop.
- **Non-idempotent nodes** — break resume/replay.
- **Treating a knowledge graph as a silver bullet** — it's costly to build and maintain; justify it against vector RAG.

---

## 10. Go deeper

This note sits at the intersection of much of the library:

- 📝 **[Loop Engineering](loop-engineering.md)** — the counterpoint; graphs are where you go when loops stop scaling. The orchestration patterns are the graph structures.
- 📝 **[Durable Execution](durable-execution.md)** — checkpoints/resume/interrupts are durable execution for agents; the determinism/idempotency rules carry over.
- 📝 **[Data Structures & Algorithms](data-structures-algorithms.md)** — nodes + edges + traversal; an execution graph is *the* graph structure, made runnable.
- 📝 **[React Flow](react-flow.md)** — the same node/edge model, made *visible* — a natural way to render/inspect an agent graph.
- 📝 **[Building an Agent Evaluator](building-agent-evaluators.md)** — a trajectory is a path through the graph; validators are nodes.
- 📝 **[Model Context Protocol](model-context-protocol.md)** — the tools that graph nodes call.

### Primary references

- [Towards AI — *Graph Engineering* article](https://x.com/towards_AI/article/2078892237287801283) (X Article, 2026) — the piece that prompted this note.
- [LangGraph documentation](https://langchain-ai.github.io/langgraph/) — the reference execution-graph framework.
- [Microsoft GraphRAG](https://microsoft.github.io/graphrag/) — knowledge-graph-based retrieval.
- Analytics Vidhya / AI Builder Club — *"Graph Engineering for AI Agents"* guides (2026), where the term was popularized.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
