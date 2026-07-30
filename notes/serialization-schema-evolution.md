# 🔄 Serialization & Schema Evolution — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~22 min · **Prerequisites:** the [API Design](api-design.md) and [Message Queues](message-queues-event-driven.md) notes (this deepens both). [DDIA Ch. 4](../books/) is the canonical companion.

**Serialization** (a.k.a. encoding, marshalling) turns in-memory objects into **bytes** for storage or transmission; **deserialization** turns them back. **Schema evolution** is the harder half: how you change that data's *shape over time* without breaking the code that reads old bytes or the code that reads new ones. Every network call, queue message, database row, and cache entry is serialized — and every long-lived system eventually needs to change the shape. Getting evolution right is what lets you deploy services independently.

> **The core reframe:** in a single program, changing a struct is free — you recompile everything at once. In a distributed system, **you can never upgrade everyone simultaneously.** Old and new code, old and new data, coexist for hours or years. So the real question isn't "what format?" but "**can new readers understand old data, and old readers understand new data?**" That's forward/backward compatibility, and it's the whole game.

## Table of contents

- [1. Why encoding matters](#1-why-encoding-matters)
- [2. Text vs. binary formats](#2-text-vs-binary-formats)
- [3. The contenders](#3-the-contenders)
- [4. Compatibility: the core concept](#4-compatibility-the-core-concept)
- [5. Evolution rules that actually work](#5-evolution-rules-that-actually-work)
- [6. Schema registries](#6-schema-registries)
- [7. Choosing a format](#7-choosing-a-format)
- [8. Best practices & anti-patterns](#8-best-practices--anti-patterns)
- [9. Go deeper](#9-go-deeper)

---

## 1. Why encoding matters

The encoding you pick decides four things at once:

- **Size** on the wire/disk (bandwidth and storage cost).
- **Speed** of encode/decode (CPU — often the bottleneck at scale).
- **Interoperability** — can other languages/tools read it?
- **Evolvability** — can you change the shape without a lockstep deploy?

> ⚠️ **Never use language-native serialization** (Java `Serializable`, Python `pickle`, Ruby `Marshal`) for anything crossing a boundary. It's tied to one language, versions badly, and — critically — **deserializing untrusted native-serialized data is remote code execution** (the [insecure-deserialization](security-fundamentals.md) vuln). Use a cross-language format.

---

## 2. Text vs. binary formats

| | **Text (JSON, XML, YAML)** | **Binary (Protobuf, Avro, MessagePack)** |
| - | -------------------------- | ---------------------------------------- |
| **Human-readable** | ✅ | ❌ (needs the schema/tooling) |
| **Size** | Large (field names repeated as strings) | Small (field tags/positional) |
| **Speed** | Slower parse | Fast |
| **Schema** | Optional/implicit | Usually explicit & enforced |
| **Best for** | Public APIs, config, debuggability | Internal S2S, high-throughput, storage |

The trade-off mirrors the rest of the library: **JSON optimizes for humans and ubiquity; binary optimizes for machines and scale.** Public [REST APIs](api-design.md) lean JSON; internal high-volume [service-to-service](service-to-service-auth.md) and [event streams](message-queues-event-driven.md) lean binary.

> The hidden cost of JSON at scale: it repeats every **field name** as a string in every record. Binary formats replace names with small numeric **field tags** defined once in the schema — which is also *why* they evolve well (§5).

---

## 3. The contenders

- **JSON** — ubiquitous, human-readable, schemaless by default. Evolution is "be liberal in what you accept" (ignore unknown fields, tolerate missing ones). Optionally add **JSON Schema** for validation. Verbose and untyped, but unbeatable for reach.
- **Protocol Buffers (Protobuf)** — Google's binary format; **schema-first** (`.proto`), compact, fast, great codegen in many languages. Compatibility hinges on **never reusing a field number**. The default for [gRPC](api-design.md).
- **Apache Avro** — binary, schema-first, designed for data pipelines/Hadoop/Kafka. Distinctive: **no field tags at all** — it relies on the **writer's schema being available to the reader** (via a registry), which makes it very compact and dynamic-schema-friendly. Uses field **names** for matching + defaults.
- **MessagePack / CBOR** — "binary JSON": smaller/faster than JSON, same schemaless model. A drop-in when you want JSON's flexibility with less overhead.
- **FlatBuffers / Cap'n Proto** — zero-copy formats: read fields **without** a parse step (access directly in the buffer). For latency-critical/game/embedded paths.
- **XML** — legacy/enterprise; verbose, powerful schema (XSD). Mostly displaced by JSON.

---

## 4. Compatibility: the core concept

Schema evolution is defined by **who can read what**. Two directions, and they're independent:

- **Backward compatibility** — **new** code can read **old** data. (You upgraded the reader; old records/messages still parse.)
- **Forward compatibility** — **old** code can read **new** data. (A producer upgraded first; readers that haven't upgraded must not choke on the new fields.)

```
                writer (producer)
                 old ──────── new
   reader   old  ✓ (same)    FORWARD compat needed
 (consumer) new  BACKWARD    ✓ (same)
                 compat needed
```

**Full compatibility** = both. Why you need both: in a real deploy, **producers and consumers upgrade in some arbitrary order**, and both old→new and new→old data flow *simultaneously* during the rollout. This is the exact independent-deploy problem from the [API versioning](api-design.md) and [message queue](message-queues-event-driven.md) notes — now with precise names.

> Avro registries make you *declare* which compatibility mode you want (`BACKWARD`, `FORWARD`, `FULL`, or `NONE`) and **reject** schema changes that would violate it — turning "hope it's compatible" into an enforced check.

---

## 5. Evolution rules that actually work

The golden rule across every good format: **add and deprecate, never mutate or reuse.**

**✅ Safe (compatible) changes**
- **Add an optional field with a default** — old readers ignore it (forward); new readers use the default when it's absent from old data (backward). The workhorse of evolution.
- **Remove an optional field** — as long as it was optional and you don't reuse its identity.
- **Add a new enum value** — *if* readers tolerate unknowns (design for this from day one).

**❌ Breaking changes (need a new version / major bump)**
- **Renaming a field**, **changing its type**, or **making an optional field required**.
- **Reusing a Protobuf field number** — silent, dangerous data corruption (old data's bytes get read as the new field). **Reserve** removed numbers so they can't be reused.
- **Removing a required field** — old writers won't send it; new readers may break.

**Format-specific mechanics:**
- **Protobuf** — identity is the **field number**, not the name; so you can *rename* freely but must **never renumber or reuse**. In proto3, fields are effectively optional. `reserved` the tags and names you retire.
- **Avro** — matches fields by **name** and fills gaps with **defaults**, comparing the writer's and reader's schemas. Adding/removing fields **with defaults** is safe; the default is what makes both directions work.
- **JSON** — no enforcement; *you* implement tolerance: ignore unknown keys, treat missing keys as defaults, and never repurpose a key's meaning.

> This is [Postel's Law](api-design.md) as an engineering discipline: **be liberal in what you accept, conservative in what you send.** Design fields to be optional and additive so you rarely need a breaking v2.

---

## 6. Schema registries

In an [event-driven system](message-queues-event-driven.md), a message outlives the code that wrote it and is read by many consumers — so where does the schema live? A **schema registry** (Confluent Schema Registry for Kafka, AWS Glue) is the answer:

- Producers **register** the schema; the message carries a tiny **schema ID**, not the whole schema (keeps records small — especially Avro, which needs the writer schema to decode).
- Consumers **fetch** the schema by ID to deserialize.
- The registry **enforces a compatibility policy** on every new schema version — rejecting a change that would break existing consumers *before* it ships. Compatibility becomes a CI-gated contract, not a prayer.

> This is the [contract-testing](testing-strategy.md) idea for data: the registry is the enforced, versioned contract between producers and consumers — the data equivalent of an [OpenAPI/`.proto`](api-design.md) spec.

---

## 7. Choosing a format

| Need | Reach for |
| ---- | --------- |
| Public API, debuggability, max reach | **JSON** (+ JSON Schema) |
| Internal RPC, typed contract, speed | **Protobuf** (+ [gRPC](api-design.md)) |
| Kafka / data pipelines, dynamic schemas, registry | **Avro** |
| JSON flexibility, smaller/faster | **MessagePack / CBOR** |
| Ultra-low-latency, zero-copy reads | **FlatBuffers / Cap'n Proto** |

The common architecture: **JSON at the public edge** (humans, browsers, third parties) and **binary + a schema registry internally** (services and streams) — the same edge-vs-internal split as your [API](api-design.md) and [auth](service-to-service-auth.md) notes.

---

## 8. Best practices & anti-patterns

**Do**
- **Add optional fields with defaults**; deprecate rather than delete; **reserve** retired Protobuf tags.
- **Design readers to tolerate unknown fields and missing fields** from day one (forward compatibility isn't free later).
- **Use a schema registry** with an enforced compatibility policy for event streams.
- **Version your schemas** and treat a schema change like an [API change](api-design.md) — additive is cheap, breaking needs a plan.
- **Pick binary + schema for internal high-volume paths; JSON for the public edge.**
- **Test round-trips across versions** (old→new and new→old) in CI.

**Avoid**
- **Language-native serialization across boundaries** (lock-in + RCE risk).
- **Reusing a Protobuf field number** — corrupts data silently.
- **Renaming/retyping fields or making optional→required** without a version bump.
- **Repurposing a field's meaning** ("`status` used to be a bool, now it's an enum") — the sneakiest break.
- **Putting the full schema in every message** when a registry + ID is far cheaper.
- **Assuming JSON "just evolves"** — it only does if your readers are deliberately tolerant.

---

## 9. Go deeper

Related material in this library:

- 📝 **[API Design](api-design.md)** — versioning & evolution (§7 there) is this note applied to request/response contracts; Postel's Law lives in both.
- 📝 **[Message Queues & Event-Driven](message-queues-event-driven.md)** — events outlive their producers, so schema evolution + registries are essential for streams.
- 📝 **[Service-to-Service Auth](service-to-service-auth.md)** & **[gRPC in API Design](api-design.md)** — Protobuf is the wire format under both.
- 📝 **[Testing Strategy](testing-strategy.md)** — a schema registry is contract-testing for data; round-trip tests catch evolution breaks.
- 📝 **[Security Fundamentals](security-fundamentals.md)** — insecure deserialization (why native formats are dangerous).
- 📗 **[Designing Data-Intensive Applications — Kleppmann](../books/)** — **Ch. 4 "Encoding and Evolution"** is *the* definitive treatment of everything here.

### Primary references

- Kleppmann, *Designing Data-Intensive Applications*, Ch. 4.
- [Protocol Buffers](https://protobuf.dev/) and [Apache Avro](https://avro.apache.org/docs/) specs.
- [Confluent Schema Registry & compatibility types](https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
