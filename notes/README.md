# 📝 Study Notes

Original, self-authored deep-dive notes on core software-engineering topics — written to be read on their own and to tie together the books, papers, and courses elsewhere in this library.

Levels: 🟢 Beginner · 🟡 Intermediate · 🔴 Advanced.

> 🗓️ Looking for the day-by-day journal of what I studied? See the **[Learning Log](learning-log/)**.

## 🤖 AI & Agentic Engineering

| Note | Level | Summary |
| ---- | ----- | ------- |
| [Loop Engineering](loop-engineering.md) | 🟡 | Designing the loop an agent runs inside — agent vs. loop, the core cycle, 20 loop design patterns across 5 families, Karpathy's LOOPS.md field notes, and production controls. |
| [Building an Agent Evaluator](building-agent-evaluators.md) | 🟡 | The verifier half of an agent loop — three levels of eval, outcome vs. trajectory, picking deterministic vs. LLM-as-judge, judge biases & calibration, and evals as versioned datasets. |
| [Model Context Protocol (MCP)](model-context-protocol.md) | 🟡 | The open standard connecting AI agents to tools & data — host/client/server, the three primitives (tools/resources/prompts), transports, the 2026-07-28 stateless spec, and the security model. |
| [Graph Engineering](graph-engineering.md) | 🟡 | Building AI systems as explicit graphs — execution/orchestration graphs (nodes/edges/state, LangGraph) vs. knowledge graphs (GraphRAG), graphs-vs-loops, and why it's durable execution for agents. |

## 🧠 Foundations & Systems

| Note | Level | Summary |
| ---- | ----- | ------- |
| [Data Structures & Algorithms](data-structures-algorithms.md) | 🟢 | Big-O, the core structures (array/list/hash map/stack/queue/tree/heap), hashing, graphs & traversals, algorithmic paradigms (two-pointer, binary search, DP, greedy), sorting & searching, and how to pick. |
| [Concurrency & Parallelism](concurrency-parallelism.md) | 🟡 | Concurrency vs. parallelism, shared mutable state & the bugs (races, deadlock, livelock), synchronization primitives, the Coffman conditions, threads/async/actors/CSP, CPU- vs. I/O-bound, Amdahl's Law, and memory models. |
| [Git Internals & Workflows](git-internals.md) | 🟡 | Git as content-addressable storage — the object model (blob/tree/commit/tag), refs & HEAD, the three areas, merge vs. rebase, reset/revert, the reflog safety net, and branching workflows. |
| [API Design (REST/gRPC/GraphQL)](api-design.md) | 🟡 | The three styles compared, REST resource/verb/status-code discipline, gRPC & protobuf, GraphQL over/under-fetching & N+1, choosing between them, versioning & evolution, and errors/idempotency/pagination. |

## 🗄️ Databases & Data

| Note | Level | Summary |
| ---- | ----- | ------- |
| [Database Indexing](database-indexing.md) | 🟡 | What indexes are, B+Trees vs. LSM-trees, clustered vs. non-clustered, composite & covering indexes, costs, and how to read `EXPLAIN`. |
| [Database Transactions & Isolation Levels](database-transactions-isolation.md) | 🟡 | ACID, the concurrency anomalies, the four isolation levels, locking vs. MVCC, snapshot isolation & write skew, and real-engine defaults. |
| [Caching Strategies & Invalidation](caching-strategies.md) | 🟡 | Where caches live, read/write patterns (cache-aside, write-through/back), eviction, invalidation approaches, the stampede/penetration/avalanche failure modes, and staleness. |
| [Database Sharding & Partitioning](database-sharding-partitioning.md) | 🔴 | Splitting data across nodes to scale — range/hash/consistent-hashing/directory strategies, choosing the shard key, hotspots & skew, rebalancing, request routing, and the cross-shard hard parts. |

## ⚙️ Systems, Reliability & Operations

| Note | Level | Summary |
| ---- | ----- | ------- |
| [Observability](observability.md) | 🟡 | Monitoring vs. observability, metrics/logs/traces (and wide events), cardinality, RED/USE/Golden Signals, SLOs & error budgets, OpenTelemetry, and sampling. |
| [Load Balancing & Rate Limiting](load-balancing-rate-limiting.md) | 🟡 | L4 vs. L7, balancing algorithms (least-connections, consistent hashing, power-of-two), health checks & sessions, rate-limiting algorithms (token/leaky bucket, sliding window), distributed limiting, and resilience patterns. |
| [Durable Execution](durable-execution.md) | 🟡 | Crash-proof long-running workflows as code (Temporal & friends) — event history & replay, workflows vs. activities, durable timers, sagas via try/except, the determinism constraint, and the landscape. |
| [Multi-Region Deployment](multi-region-deployment.md) | 🔴 | Running across cloud regions — why/why-not, RTO & RPO, the DR spectrum, active-passive vs. active-active, cross-region data replication, global routing, split-brain & blast radius, and failover testing. |
| [Message Queues & Event-Driven](message-queues-event-driven.md) | 🟡 | Queues vs. logs, messaging patterns, delivery guarantees (at-least-once + idempotency), ordering & partitioning, retries/DLQs/poison messages, events vs. commands, the dual-write problem (outbox & CDC), and backpressure. |
| [Serialization & Schema Evolution](serialization-schema-evolution.md) | 🟡 | Encoding objects to bytes and evolving their shape safely: text vs. binary (JSON/Protobuf/Avro), forward/backward compatibility, the add-and-deprecate rules, and schema registries. |
| [Consistency Models & CAP](consistency-models-cap.md) | 🔴 | Why replicas disagree, CAP & PACELC, the consistency spectrum (linearizable → eventual), linearizability vs. serializability, session guarantees, quorums (`W+R>N`), conflict resolution (LWW/vector clocks/CRDTs). The capstone tying the library together. |
| [Distributed Consensus (Raft/Paxos)](distributed-consensus.md) | 🔴 | How CP systems are built: safety vs. liveness, FLP & failure models, replicated state machines, Paxos & Multi-Paxos, Raft (election/replication/safety), majority quorums & odd clusters, split brain, and where consensus is used. |

## 🔐 Security

| Note | Level | Summary |
| ---- | ----- | ------- |
| [Security Fundamentals](security-fundamentals.md) | 🟢 | The attacker mindset, CIA triad, durable principles (least privilege, fail closed, defense in depth), STRIDE threat modeling, AuthN vs. AuthZ, the OWASP Top 10:2025, injection/XSS/CSRF/IDOR/SSRF, crypto basics, and secrets & supply chain. |
| [Service-to-Service Authentication](service-to-service-auth.md) | 🟡 | How machines prove identity to each other: the "secret zero" problem, mTLS, OAuth2 client credentials & JWT validation, workload identity (SPIFFE/cloud IAM), bearer vs. sender-constrained tokens, and per-service authz. |
| [OAuth 2.0, OIDC & SSO](oauth-oidc-sso.md) | 🟡 | User/browser auth: OAuth vs. OIDC, Authorization Code + PKCE, the three tokens, SSO via a shared IdP session, **silent SSO** (`prompt=none`) & its third-party-cookie demise, token storage & the BFF pattern, and logout. |

## 🎨 Frontend & UI

| Note | Level | Summary |
| ---- | ----- | ------- |
| [React Flow](react-flow.md) | 🟢 | Building node-based editors in React (`@xyflow/react`) — the controlled nodes/edges model, handles & viewport, custom nodes/edges, built-in components, auto-layout (Dagre/ELK), hooks, and performance. |
| [Exposing React to Other Apps](exposing-react-to-other-apps.md) | 🟡 | Every way to share a React component/app: npm library, Module Federation, Web Components, embeddable widgets, iframes, route mapping (reverse-proxy/Multi-Zones), orchestrators & server composition — with a decision guide. |

## 🌐 Web Performance & Quality

| Note | Level | Summary |
| ---- | ----- | ------- |
| [Web Quality & Lighthouse](web-quality-lighthouse.md) | 🟢 | The hub — the four pillars (perf/a11y/SEO/best-practices), Lighthouse 13, field vs. lab measurement, security, and the evidence-led audit workflow. |
| [Core Web Vitals & Performance](web-core-vitals-performance.md) | 🟡 | Measure-first performance — CrUX/RUM vs. lab, LCP/INP/CLS with causes & fixes, performance budgets, and the critical rendering path. |
| [Web Accessibility (a11y)](web-accessibility.md) | 🟢 | WCAG 2.2 & POUR, why automated ≠ conformance, the high-impact fixes, semantic HTML & the accessibility tree, ARIA, and keyboard/screen-reader testing. |
| [Technical SEO](web-seo.md) | 🟢 | Crawl → index → rank, crawlability & indexability, metadata & canonicals, structured data (JSON-LD), JS/SSR rendering, and page experience. |

## 🧰 Tooling & Developer Experience

| Note | Level | Summary |
| ---- | ----- | ------- |
| [pnpm Tips & Capabilities (v11)](pnpm-tips.md) | 🟢 | Why pnpm's store + strict `node_modules` are different, an everyday cheat sheet, workspaces & filtering, catalogs, overrides & patching, and what's new/secure-by-default in v11. |
| [Knip](knip.md) | 🟢 | Find (and fix) unused files, dependencies, and exports in JS/TS — how it works via entry/project graph analysis, plugins, `--fix`, production mode & CI, and taming false positives. |
| [dependency-cruiser](dependency-cruiser.md) | 🟢 | Validate & visualize the JS/TS dependency graph — rules (no-circular, layer boundaries), the recommended ruleset, Graphviz/Mermaid output, CI gating, and vs. Knip/Madge. |
| [commitlint](commitlint.md) | 🟢 | Lint commit messages to Conventional Commits — how it works (config + commit-msg hook + CI), the benefits (readable history, automated semver & changelogs), and the release-automation ecosystem it unlocks. |

## 🛠️ Software Craft & Code Quality

| Note | Level | Summary |
| ---- | ----- | ------- |
| [Cyclomatic Complexity](cyclomatic-complexity.md) | 🟢 | McCabe's metric — counting independent paths, how to calculate it, what the number means, and how to reduce it. |
| [CRAP Score](crap-score.md) | 🟡 | Change Risk Anti-Patterns — combining complexity and test coverage into one change-risk number, with the formula, thresholds, and the test-vs-refactor trade-off. |
| [Testing Strategy](testing-strategy.md) | 🟡 | The test pyramid (and rivals), the kinds of tests, test doubles & the two schools, FIRST/AAA, TDD, coverage vs. confidence & mutation testing, and taming flaky tests. |
| [Constraint-Driven Development](constraint-driven-development.md) | 🟡 | Letting machine-checked constraints (types, contracts, tests, schemas, arch rules, specs) drive & bound development — the cost ladder, CDD vs. TDD vs. spec-driven, why it's load-bearing for AI agents, and making illegal states unrepresentable. |

---

### Adding a note

1. Write a `kebab-case-title.md` file in this folder.
2. Start with a level, reading time, and a one-line definition.
3. Include a table of contents, worked examples, and a **Go deeper** section that links to related [books/](../books/), [papers/](../papers/), and [courses/](../courses/).
4. Add a row to the table above under the right topic.
