# 🔭 Observability — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~20 min · **Prerequisites:** basic services/HTTP; helpful to have seen a dashboard or a log file in anger.

**Observability** is the ability to understand a system's internal state from its external outputs — specifically, to **ask new questions about your system without shipping new code**. It's what lets you debug a failure you never anticipated.

> **Observability ≠ monitoring.** *Monitoring* answers **known unknowns**: you predicted a failure mode, so you built a dashboard and an alert for it. *Observability* handles **unknown unknowns**: the weird, novel outage nobody thought to instrument for. Monitoring tells you *that* something is wrong; observability lets you find out *why*.

## Table of contents

- [1. The three (and a half) pillars](#1-the-three-and-a-half-pillars)
- [2. Metrics](#2-metrics)
- [3. Logs](#3-logs)
- [4. Traces](#4-traces)
- [5. Cardinality — the central constraint](#5-cardinality--the-central-constraint)
- [6. The methods: RED, USE & Golden Signals](#6-the-methods-red-use--golden-signals)
- [7. SLIs, SLOs & error budgets](#7-slis-slos--error-budgets)
- [8. OpenTelemetry & correlation](#8-opentelemetry--correlation)
- [9. Sampling & cost](#9-sampling--cost)
- [10. Best practices & anti-patterns](#10-best-practices--anti-patterns)
- [11. Go deeper](#11-go-deeper)

---

## 1. The three (and a half) pillars

| Signal | What it is | Best at | Weak at |
| ------ | ---------- | ------- | ------- |
| **Metrics** | Aggregated numeric time series | Dashboards, alerts, trends. Cheap at scale. | Explaining *why*; high-cardinality detail |
| **Logs** | Discrete timestamped events | Rich detail about a specific moment | Cost/volume; aggregation |
| **Traces** | One request's path across services | "Where did the time go? Which hop broke?" | Overhead; needs sampling |
| **Profiles** *(the ½)* | Continuous CPU/memory profiling | "Which *function* is burning the CPU?" | Newer, less universal |

> ⚠️ "Three pillars" is a useful intro but a slightly misleading model. They're not separate foundations — they're **three views over the same underlying events**. The modern framing is **wide, structured events**: emit one rich event per unit of work with many attributes, then derive metrics, logs, and traces from it. That's what makes arbitrary new questions answerable after the fact.

---

## 2. Metrics

Numbers over time, aggregated. Cheap, compact, and the backbone of alerting. Standard types (Prometheus vocabulary):

- **Counter** — monotonically increasing (requests served, errors). You query its *rate*.
- **Gauge** — a value that goes up and down (queue depth, memory in use, temperature).
- **Histogram** — bucketed distribution (request duration). Lets you compute **percentiles**.
- **Summary** — client-side computed quantiles.

> **Always look at percentiles, never just averages.** A 200 ms *mean* latency can hide a p99 of 8 s — and the p99 is somebody's real experience. Averages lie about tails; tails are where users churn.

---

## 3. Logs

Discrete records of what happened. The single highest-leverage upgrade: **structured logging**.

```jsonc
// ❌ Unstructured — greppable at best, not queryable
"User 42 checkout failed after 1200ms: card declined"

// ✅ Structured — queryable, aggregatable, correlatable
{"event":"checkout_failed","user_id":42,"duration_ms":1200,
 "reason":"card_declined","trace_id":"a3f9...","service":"payments"}
```

Structured logs let you ask *"p95 duration of checkout_failed, grouped by reason, for the last hour"* — which is impossible with prose. Always include a **`trace_id`** (see §8) so a log line links to its trace.

Logs are the most expensive signal at volume — sample aggressively, and prefer a few wide events over many thin ones.

---

## 4. Traces

A **trace** follows one request across every service it touches. It's a tree of **spans**:

```
Trace: POST /checkout                      [850ms]
├── span: api-gateway                      [845ms]
│   ├── span: auth-service                 [ 30ms]
│   ├── span: inventory-service            [120ms]
│   │   └── span: db.query SELECT stock    [110ms]  ← the real cost
│   └── span: payment-service              [680ms]
│       └── span: http POST stripe.com     [670ms]  ← and here
```

Each span has a name, start/end time, parent, and attributes. Traces answer the question metrics can't: **where did the time actually go, and which hop failed?**

The magic is **context propagation** — a trace ID passed along every hop (the W3C `traceparent` header), so independent services stitch into one picture. This idea comes from Google's **[Dapper](../papers/)** paper (2010), the ancestor of every tracing system today.

---

## 5. Cardinality — the central constraint

**Cardinality** = the number of unique values a dimension can take. It's *the* thing that governs observability cost and design.

- `status_code` → ~10 values. **Low cardinality.** Cheap as a metric label.
- `user_id` → millions. **High cardinality.** Adding it as a metric label creates millions of time series and will blow up your metrics backend (a "cardinality explosion").

The tension: **high-cardinality fields are exactly the ones you need to debug** ("which *user*? which *build*? which *request*?"), but metrics systems can't hold them.

**The resolution:** don't force high-cardinality data into metrics. Put it in **wide structured events / traces**, which are designed for it, and keep metrics low-cardinality for alerting. This is precisely why the "three pillars" model gives way to "wide events."

---

## 6. The methods: RED, USE & Golden Signals

Don't instrument randomly — use a checklist.

**RED** — for **request-driven services** (Tom Wilkie):
- **R**ate — requests per second
- **E**rrors — failed requests per second
- **D**uration — latency distribution

**USE** — for **resources** (CPU, disk, queues) (Brendan Gregg):
- **U**tilization — % time busy
- **S**aturation — queued work waiting
- **E**rrors — error events

**Four Golden Signals** — from Google's SRE book:
- **Latency**, **Traffic**, **Errors**, **Saturation**
  (Crucially: measure the latency of *successful* and *failed* requests **separately** — fast failures otherwise flatter your latency graph.)

> Rule of thumb: **RED for your services, USE for your resources.** Between them you'll catch most of what matters.

---

## 7. SLIs, SLOs & error budgets

Observability becomes *decision-making* when you attach targets:

- **SLI (Indicator)** — the actual measurement. *"% of requests served < 300 ms."*
- **SLO (Objective)** — your internal target. *"99.9% of requests < 300 ms over 30 days."*
- **SLA (Agreement)** — the contractual promise to customers, with penalties. (Always looser than your SLO.)
- **Error budget** — `100% − SLO`. At 99.9%, you may be "unhealthy" **0.1%** of 30 days ≈ **43 minutes**.

The error budget is the point: it turns reliability into a **currency**. Budget remaining → ship features fast. Budget exhausted → freeze features and fix reliability. It replaces arguing about "how stable is stable enough" with a number both engineers and product agree on up front.

> **100% is the wrong target.** It's infinitely expensive and users can't tell the difference — their ISP is flakier than your service.

---

## 8. OpenTelemetry & correlation

**OpenTelemetry (OTel)** is the CNCF vendor-neutral standard (the merger of OpenTracing + OpenCensus) — now the default choice. It gives you:

- **API + SDKs** — instrument once, in any language.
- **Signals** — traces, metrics, logs (and profiles maturing).
- **Collector** — a pipeline to receive, process, and export telemetry.
- **Context propagation** — the W3C `traceparent` standard.

The strategic win: **instrumentation is decoupled from your vendor.** Swap backends without re-instrumenting.

**Correlation is what makes the whole thing work** — tie the signals together:

- **`trace_id` in every log line** → jump from a log to the full request trace.
- **Exemplars** → a metric data point links to an example trace ("show me a trace from that p99 spike").
- Consistent **resource attributes** (`service.name`, `deployment.environment`, `version`) across all signals.

The debugging loop this enables: **alert (metric) → find an exemplar trace → see the slow span → read that span's logs.** From "something's wrong" to root cause in three hops.

---

## 9. Sampling & cost

Telemetry can easily cost more than the system it observes. Sampling is the control:

- **Head-based sampling** — decide at the start of the request (e.g. keep 1%). Simple and cheap, but you'll usually *miss the rare error* you actually wanted.
- **Tail-based sampling** — buffer the trace, decide *after* it completes. Keep 100% of errors and slow requests, 1% of boring successes. Far better signal-per-dollar; needs a collector with memory.
- **Dynamic sampling** — sample by frequency: keep everything rare, downsample the common.

> Rule of thumb: **sample the boring, keep the interesting.** Never sample errors away.

---

## 10. Best practices & anti-patterns

**Do**
- Emit **wide, structured events** with rich attributes — favor one fat event per unit of work over many thin logs.
- Put a **`trace_id` on everything** and propagate context across every hop.
- Instrument by checklist (**RED** for services, **USE** for resources).
- Alert on **symptoms users feel** (SLO burn), not on causes (CPU is 90%).
- Track **percentiles** (p95/p99), never averages alone.
- Use **OpenTelemetry** to stay vendor-neutral.
- Separate latency of successful vs. failed requests.

**Avoid**
- **Cardinality explosions** — never put `user_id`/`request_id` in a metric label.
- **Alerting on everything** — noisy pages train people to ignore pages (alert fatigue).
- **Unstructured prose logs** — unqueryable and unaggregatable.
- **Averages** — they hide the tail where your users actually suffer.
- **Head-sampling away your errors.**
- Treating dashboards as observability — a fixed dashboard only answers questions you *already* thought of.

---

## 11. Go deeper

Related material in this library:

- 📄 **[Dapper — Google](../papers/)** — the origin of distributed tracing; short and very readable.
- 📗 **[Site Reliability Engineering — Google](../books/)** — the Four Golden Signals, SLOs, and error budgets, straight from the source. Free online.
- 📗 **[Release It! — Michael Nygard](../books/)** — stability patterns and what production failure actually looks like.
- 📝 **[Loop Engineering](loop-engineering.md)** — *"read the traces"* is the same instinct applied to agents: debug from the transcript, not by re-running experiments.
- 📝 **[Caching Strategies](caching-strategies.md)** — hit rate and eviction rate are exactly the kind of metrics this note is about.

### Primary references

- Sigelman et al., *"Dapper, a Large-Scale Distributed Systems Tracing Infrastructure,"* Google, 2010.
- Beyer et al., *Site Reliability Engineering*, O'Reilly/Google, 2016 — Ch. 6 "Monitoring Distributed Systems."
- [OpenTelemetry documentation](https://opentelemetry.io/docs/).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
