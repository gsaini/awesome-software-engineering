# 🔌 Model Context Protocol (MCP) — A Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~20 min · **Prerequisites:** the [Loop Engineering](loop-engineering.md) and [Building an Agent Evaluator](building-agent-evaluators.md) notes (this is how agents *get* their tools). Landscape current as of the **2026-07-28 spec**.

**Model Context Protocol (MCP)** is an open standard — introduced by Anthropic in late 2024, now the de-facto substrate for agentic apps — that standardizes **how AI applications connect to external tools, data, and systems**. It's often called the **"USB-C for AI"**: one protocol so any model can plug into any tool, instead of every app hand-rolling every integration. By 2026 its Tier-1 SDKs see ~half a billion downloads a month.

> **The problem it solves — M×N → M+N.** Before MCP, connecting *M* AI apps to *N* tools meant building *M×N* bespoke integrations (every app writes its own GitHub connector, its own Postgres connector…). MCP defines **one standard interface**, so a tool author writes **one** MCP server and every MCP-capable app can use it — turning M×N into **M+N**. It's the same leverage a standard [API contract](api-design.md) gives, applied to the messy space of LLM tool-use.

## Table of contents

- [1. Why MCP exists](#1-why-mcp-exists)
- [2. Architecture: host, client, server](#2-architecture-host-client-server)
- [3. The three server primitives](#3-the-three-server-primitives)
- [4. Client-side features](#4-client-side-features)
- [5. Transports](#5-transports)
- [6. What's new in the 2026-07-28 spec](#6-whats-new-in-the-2026-07-28-spec)
- [7. Security model](#7-security-model)
- [8. How it fits agents](#8-how-it-fits-agents)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. Why MCP exists

An LLM on its own is a text predictor with a knowledge cutoff — it can't read *your* files, query *your* database, or take actions. "Tool use" fixes that, but before MCP every framework invented its own plugin format, so integrations didn't transfer. MCP standardizes:

- **The interface** — how a model discovers and calls a tool, reads a resource, or fetches a prompt.
- **The transport** — how messages flow (local subprocess or HTTP).
- **The message format** — **JSON-RPC 2.0** requests/responses/notifications.

Write an MCP server once (for Slack, GitHub, Postgres, your internal service) and *every* MCP host — Claude, IDEs like Cursor/VS Code/Zed, other agents — can use it unchanged.

---

## 2. Architecture: host, client, server

Three roles, with a clean 1:1 client↔server rule:

```
┌─────────────── HOST (the AI app: Claude, an IDE, an agent) ───────────────┐
│   contains the LLM + orchestration                                        │
│   ┌── MCP Client A ──┐   ┌── MCP Client B ──┐   ┌── MCP Client C ──┐      │
└───────┼──────────────────────┼──────────────────────┼──────────────────────┘
        │ JSON-RPC             │                      │
   ┌────▼─────┐          ┌─────▼─────┐          ┌─────▼──────┐
   │ MCP Server│         │ MCP Server │         │ MCP Server  │
   │ (GitHub)  │         │ (Postgres) │         │ (your API)  │
   └───────────┘         └────────────┘         └─────────────┘
```

- **Host** — the AI application; holds the model and decides what to do. Contains one or more **clients**.
- **Client** — a connector inside the host, maintaining a **dedicated 1:1 connection** to a single server (isolation — a misbehaving server can't reach into others; the [least-privilege / blast-radius](service-to-service-auth.md) instinct).
- **Server** — a lightweight program exposing capabilities (tools/resources/prompts) for a specific system. Can run locally or remotely.

---

## 3. The three server primitives

The heart of MCP — and the crucial idea is **who controls each one**:

| Primitive | What it is | Controlled by | Analogy |
| --------- | ---------- | ------------- | ------- |
| **Tools** | Callable functions the model can invoke (query a DB, send a message) — declared with a name, description, and **JSON Schema** input | **Model** (the LLM decides to call) | A `POST` endpoint / an action |
| **Resources** | Readable context identified by a **URI** (files, records, docs) the app can load into context | **Application** | A `GET` / read-only data |
| **Prompts** | Reusable prompt templates/workflows the server offers | **User** (invokes deliberately) | A slash-command / saved query |

That control split is the design's elegance: **tools are model-driven, resources are app-driven, prompts are user-driven** — three different trust levels, kept distinct. (Since 2025-06-18, tools can also declare **structured output** and return **resource links**, so a tool result can point at further context.)

---

## 4. Client-side features

MCP is bidirectional — a **server can also ask things of the host**:

- **Sampling** — the server requests a completion from the host's LLM (so a server can use the model without holding its own API key). Human-in-the-loop approval is expected.
- **Roots** — the host tells the server which filesystem/URI **boundaries** it may operate within (a sandbox).
- **Elicitation** — the server requests **structured input from the user** mid-operation (e.g. "which repo?"), enabling interactive flows.

> Sampling is the clever inversion: instead of only the host calling out, the *server* can borrow the host's model — but always mediated by the host, so trust and cost stay with the app.

---

## 5. Transports

Two official transports, both carrying **JSON-RPC 2.0**:

- **stdio** — the server runs as a **local subprocess**; messages flow over stdin/stdout. Simplest option for local tools (filesystem, git, a local DB). No network, no auth needed.
- **Streamable HTTP** — for **remote** servers: a single MCP endpoint handling `POST` (and `GET`), with **optional Server-Sent Events** for server→client streaming. (Introduced 2025-03-26, replacing the older, clunkier HTTP+SSE two-endpoint transport.)

Choose stdio for local/desktop integrations, Streamable HTTP for hosted/shared servers.

---

## 6. What's new in the 2026-07-28 spec

The latest revision (six days old at the time of writing) is a notable modernization — **it makes the protocol stateless**, which is a big deal for scaling:

- **Stateless core** — **removes the `initialize`/`initialized` handshake and the `Mcp-Session-Id` header.** Servers no longer need to hold per-connection session state, so any instance can serve any request — the [stateless-fleet](load-balancing-rate-limiting.md) principle from your load-balancing note, enabling easy horizontal scaling and load balancing of MCP servers.
- **Multi round-trip requests** & **header-based routing** — more flexible request patterns and routing at the edge.
- **Cacheable results** — `list` and resource responses now carry **`ttlMs`** and **`cacheScope`** fields, **modeled directly on HTTP's `Cache-Control`**. Your [caching note](caching-strategies.md), verbatim: a TTL as an explicit staleness bound, now baked into the protocol.
- **Traceability** — **W3C Trace Context** is propagated through fixed keys in `_meta`, giving **OpenTelemetry-compatible distributed tracing** across SDKs and gateways out of the box — the [observability note](observability.md)'s trace-ID-everywhere, standardized for agent tool calls.
- **Authorization hardening** + a formal **extensions framework** for evolving the protocol without breaking clients (schema evolution, [done right](serialization-schema-evolution.md)).

> Notice how much of this is *your own library's greatest hits*: stateless scaling, TTL-based caching, distributed tracing, backward-compatible evolution. MCP maturing means adopting the same distributed-systems lessons everything else did.

---

## 7. Security model

Powerful, and therefore dangerous — MCP hands a model the ability to *act*, so treat it with the [security mindset](security-fundamentals.md):

- **Untrusted servers** — an MCP server is third-party code that can return arbitrary content. A malicious tool description or result is a **prompt-injection** vector (the model reads it as instructions). Vet servers; sandbox them.
- **User consent / human-in-the-loop** — hosts should require **explicit approval** before a tool takes a consequential action (the "never let the loop act unsupervised on irreversible ops" rule from [loop engineering](loop-engineering.md)).
- **Authorization** — remote servers use **OAuth 2.1**; the 2026-07-28 spec **hardens** this. Scope tokens tightly (least privilege), validate them ([S2S auth](service-to-service-auth.md)).
- **Least privilege & isolation** — the 1:1 client↔server model and `roots` sandboxing cap what any one server can reach.

> The governing rule: **an MCP tool result is untrusted input.** Everything in the [security note](security-fundamentals.md) about validating input and least privilege applies — now with the twist that the "input" can try to *reprogram the agent*.

---

## 8. How it fits agents

MCP is the **tool-and-context layer** under the agent loop you already studied:

- [Loop engineering](loop-engineering.md)'s loop is *gather → reason → **act** → verify → repeat* — MCP is **how the "act" and "gather" happen**: tools for acting, resources for gathering, standardized so the agent isn't hard-wired to specific integrations.
- [Agent evaluators](building-agent-evaluators.md) grade tool **trajectories** — MCP's structured tool calls (name + JSON-Schema args + structured output) make those calls **inspectable and verifiable**, exactly what a deterministic evaluator gate needs.
- Adding a capability to an agent becomes "**point it at another MCP server**" — no code change to the agent, the same decoupling payoff as [event-driven consumers](message-queues-event-driven.md).

MCP is, in effect, the **standardized nervous system** that connects the agent brain (the loop) to the outside world.

---

## 9. Best practices & anti-patterns

**Do**
- **Build small, focused servers** (one system each) — composability over monoliths.
- **Write precise tool descriptions & JSON Schemas** — the model calls tools based on them; vague descriptions cause wrong calls (it's prompt engineering *for the model's benefit*).
- **Require human approval for consequential actions**; return structured, minimal results.
- **Scope OAuth tokens tightly** and validate them; treat every server as untrusted.
- **Prefer stdio for local, Streamable HTTP for remote**; lean into the new **stateless** model for scalable remote servers.
- **Set sensible `ttlMs`/`cacheScope`** on list/resource results; propagate trace context.

**Avoid**
- **Trusting tool results blindly** — they're untrusted input and a prompt-injection vector.
- **Over-broad servers/tokens** — a giant do-everything server is a fat blast radius.
- **Unbounded/irreversible actions with no consent gate.**
- **Leaking secrets into tool results or descriptions.**
- **Reinventing integrations** when a maintained MCP server already exists.

---

## 10. Go deeper

Related material in this library — MCP connects to an unusual number of them:

- 📝 **[Loop Engineering](loop-engineering.md)** — MCP is the tool/context layer under the agent loop.
- 📝 **[Building an Agent Evaluator](building-agent-evaluators.md)** — structured MCP tool calls are what evaluators inspect.
- 📝 **[Caching](caching-strategies.md)** — the new `ttlMs`/`cacheScope` fields *are* HTTP-style TTL caching.
- 📝 **[Observability](observability.md)** — W3C Trace Context / OpenTelemetry across tool calls.
- 📝 **[Service-to-Service Auth](service-to-service-auth.md)** & **[Security](security-fundamentals.md)** — OAuth 2.1, least privilege, untrusted-input handling.
- 📝 **[Serialization & Schema Evolution](serialization-schema-evolution.md)** — JSON-RPC + the extensions framework for evolving the protocol.
- 📝 **[Load Balancing](load-balancing-rate-limiting.md)** — the 2026 stateless core is what lets you scale MCP servers horizontally.

### Primary references

- [Model Context Protocol — official site & spec](https://modelcontextprotocol.io/) and the [2026-07-28 specification](https://modelcontextprotocol.io/specification/2026-07-28).
- [MCP 2026 roadmap](https://blog.modelcontextprotocol.io/posts/2026-mcp-roadmap/) and the [2026-07-28 release notes](https://blog.modelcontextprotocol.io/posts/2026-07-28/).
- [Introducing MCP — Anthropic](https://www.anthropic.com/news/model-context-protocol) (the original 2024 announcement).

*Original study note (MCP 2026-07-28 spec) — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
