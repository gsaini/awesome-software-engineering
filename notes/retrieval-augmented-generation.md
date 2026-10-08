# 📚 Retrieval-Augmented Generation (RAG) — A Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~20 min · **Prerequisites:** [Database Indexing](database-indexing.md) (the index ideas carry over), [Building an Agent Evaluator](building-agent-evaluators.md) (for §10). [Graph Engineering §6](graph-engineering.md#6-knowledge-graphs--graphrag) already covers GraphRAG; this note covers everything around it.

**Retrieval-augmented generation** answers a question by first **retrieving** relevant passages from a corpus and then **generating** an answer from them. The model's weights hold *parametric* knowledge, which is frozen at training time, unattributable, and public. Retrieval adds *non-parametric* knowledge that is current, private, and citable. The name and the original architecture come from [Lewis et al., 2020](https://arxiv.org/abs/2005.11401).

> **The one-line thesis:** *RAG is a search problem with a language model at the end.* When a RAG system gives a bad answer, the cause is usually that the right passage **wasn't retrieved**, not that the model wrote badly. So build and measure it like a search engine first.

## Table of contents

- [1. Why RAG exists, and what it doesn't fix](#1-why-rag-exists-and-what-it-doesnt-fix)
- [2. The pipeline at a glance](#2-the-pipeline-at-a-glance)
- [3. Chunking](#3-chunking)
- [4. Embeddings and vector indexes](#4-embeddings-and-vector-indexes)
- [5. Retrieval that works: hybrid, fusion, reranking](#5-retrieval-that-works-hybrid-fusion-reranking)
- [6. Query transformation](#6-query-transformation)
- [7. Generation: grounding and citations](#7-generation-grounding-and-citations)
- [8. RAG vs. long context vs. fine-tuning](#8-rag-vs-long-context-vs-fine-tuning)
- [9. Agentic RAG](#9-agentic-rag)
- [10. Evaluating RAG](#10-evaluating-rag)
- [11. Security and operations](#11-security-and-operations)
- [12. Best practices & anti-patterns](#12-best-practices--anti-patterns)
- [13. Go deeper](#13-go-deeper)

---

## 1. Why RAG exists, and what it doesn't fix

| Problem with a model alone | How retrieval helps |
| -------------------------- | ------------------- |
| **Stale knowledge.** Weights are frozen at a training cutoff. | Re-index today's documents; no retraining needed. |
| **Private knowledge.** The model never saw your wiki, tickets, or contracts. | Retrieve from your own corpus at query time. |
| **No attribution.** "Where did that come from?" has no answer. | Every claim can point to a retrieved passage. |
| **Access control.** Weights can't forget what one user may not see. | Filter retrieval by the asking user's permissions. |
| **Hallucination on specifics** (numbers, names, clauses) | The model copies from the source instead of recalling it. |

**What RAG doesn't fix:** questions that need reasoning across the *whole* corpus ("what are the main themes?"), which is GraphRAG's niche; a model that ignores the retrieved context; and a corpus that is itself wrong or contradictory. Retrieval reduces hallucination but doesn't eliminate it. A model can still misread or over-generalize from a correct passage.

---

## 2. The pipeline at a glance

```text
 OFFLINE (indexing)                                    ONLINE (per query)

 sources ─► parse ─► chunk ─► embed ─► index           question
 (PDF, HTML,  (text +   (passages  (vectors) (vector +     │
  tickets,     structure) + metadata)         keyword)     ▼
  code)                                          ▲      rewrite / expand        (§6)
                                                 │         │
                                                 └──── retrieve top-k (hybrid)  (§5)
                                                           │
                                                        rerank to top-n          (§5)
                                                           │
                                                        assemble prompt          (§7)
                                                           │
                                                        generate + cite ─► answer
```

The indexing side is a **derived-data pipeline**, just like a cache or a search index. It needs the same rebuild and freshness discipline (see [Caching Strategies](caching-strategies.md)), and every chunk should keep a pointer back to its source.

---

## 3. Chunking

Chunking decides what one retrievable unit is. It's the most underrated knob in the system: no amount of retrieval quality can recover a fact that chunking split across two halves.

| Strategy | How it works | Use when |
| -------- | ------------ | -------- |
| **Fixed size + overlap** | e.g. ~500 tokens, 10–20% overlap | A baseline for uniform prose. |
| **Structure-aware / recursive** | Split on headings, then paragraphs, then sentences | Docs, wikis, Markdown, HTML. Usually the best default. |
| **Code-aware** | Split by function or class (via a parser) | Source code. |
| **Semantic** | Split where embedding similarity between sentences drops | Long unstructured text. Costs more to index. |
| **Parent–child ("small-to-big")** | Retrieve small, precise chunks; give the model the parent section | When precise matching and enough context are both needed. |

**Rules that hold across strategies:**

- **Keep metadata on every chunk:** source, title, section path, date, owner, and the ACL. Filters and citations depend on it.
- **Prepend context.** A chunk like *"Revenue grew 3% over the previous quarter"* doesn't say which company or which quarter. Anthropic's [Contextual Retrieval](https://www.anthropic.com/news/contextual-retrieval) has a model write a short, chunk-specific context and prepends it before embedding and keyword indexing. On their benchmarks, contextual embeddings cut failed retrievals by 35%. Adding contextual BM25 brought that to 49%, and adding reranking brought it to 67%.
- **Tune on your questions, not on folklore.** The right chunk size depends on what people ask. Measure recall@k ([§10](#10-evaluating-rag)) at two or three sizes.

---

## 4. Embeddings and vector indexes

An **embedding model** maps text to a vector, so that texts with similar meaning land close together, usually measured by cosine similarity. A **vector index** finds the nearest vectors to a query vector quickly.

**Embeddings:**

- **Use the same model for queries and documents.** Some models expect a different prefix or instruction for each (for example `query:` vs. `passage:`). Use them as documented.
- **Changing the embedding model means re-embedding everything.** Vectors from different models aren't comparable. Version the index like a schema (see [Serialization & Schema Evolution](serialization-schema-evolution.md)).
- **Dense vectors blur exact tokens.** Error codes, SKUs, rare names, and version numbers get fuzzy. That's the main reason for hybrid search ([§5](#5-retrieval-that-works-hybrid-fusion-reranking)).

**Indexes.** Exact search compares the query with every vector. That is fine up to roughly a few hundred thousand vectors, and it's the baseline to beat. Beyond that, use **approximate nearest neighbor (ANN)** search:

| Index | Idea | Trade-off |
| ----- | ---- | --------- |
| **HNSW** ([Malkov & Yashunin](https://arxiv.org/abs/1603.09320)) | A layered proximity graph, searched greedily from the top layer down | Fast and high-recall, but memory-hungry. The usual default. |
| **IVF** | Cluster the vectors; search only the nearest clusters (`nprobe`) | Less memory. Recall depends on `nprobe`. |
| **PQ** (product quantization) | Compress vectors into short codes | Much smaller and a little less accurate. Often combined with IVF. |

Every ANN index trades **recall for latency** through a query-time knob (`ef_search` in HNSW, `nprobe` in IVF). **Metadata filters** interact with ANN: filtering *after* the search can leave too few results, while filtering during the search needs index support. You don't need a dedicated vector database to start. `pgvector` adds HNSW and IVFFlat indexes to Postgres, next to the data you already have.

---

## 5. Retrieval that works: hybrid, fusion, reranking

The production pattern is to **retrieve broadly with cheap methods, then rank precisely with an expensive one.**

**1. Hybrid search.** Run **keyword search (BM25)** and **vector search** in parallel. BM25 catches exact terms. Vectors catch paraphrases ("cancel my plan" matches "terminate subscription"). Each covers the other's blind spot.

**2. Fuse the result lists.** Scores from the two retrievers aren't on the same scale, so combine **ranks** instead of scores. The standard method is **Reciprocal Rank Fusion** ([Cormack et al., 2009](https://dl.acm.org/doi/10.1145/1571941.1572114)):

```python
def rrf(rankings: list[list[str]], k: int = 60) -> list[str]:
    """Fuse ranked lists of doc ids. A doc ranked high anywhere floats up."""
    score: dict[str, float] = {}
    for ranking in rankings:
        for rank, doc in enumerate(ranking, start=1):
            score[doc] = score.get(doc, 0.0) + 1 / (k + rank)
    return sorted(score, key=score.get, reverse=True)
```

**3. Rerank.** Take the top ~50–150 fused candidates and score each `(query, passage)` pair with a **cross-encoder** reranker. A cross-encoder reads the query and the passage together, so it's far more accurate than comparing two separately computed vectors, and far too slow to run over the whole corpus. Keep the top ~5–20.

| Stage | Method | Cost per query | Job |
| ----- | ------ | -------------- | --- |
| Recall | BM25 + vectors, fused | Cheap, runs over millions of chunks | Don't miss the right passage. |
| Precision | Cross-encoder rerank | Expensive, runs over ~100 candidates | Put the right passage first. |

Late-interaction models such as [ColBERT](https://arxiv.org/abs/2004.12832) sit between the two: they're more precise than single vectors and cheaper than a full cross-encoder.

---

## 6. Query transformation

Users rarely phrase questions the way documents are written. Fix the query before retrieving:

| Technique | What it does | Fixes |
| --------- | ------------ | ----- |
| **Conversational rewrite** | Rewrites "what about the second one?" into a standalone question using the chat history | Follow-ups that retrieve nothing |
| **Multi-query** | Generates 3–5 paraphrases, retrieves for each, and fuses the results | Vocabulary mismatch |
| **HyDE** ([Gao et al., 2022](https://arxiv.org/abs/2212.10496)) | Has the model draft a *hypothetical answer* and embeds that instead of the question | Questions that look nothing like their answers |
| **Decomposition** | Splits a multi-hop question into sub-questions and retrieves for each | "Compare X's 2025 policy with Y's" |
| **Routing / filter extraction** | Turns "incidents last March in the EU" into metadata filters, or picks an index | Pure-similarity search ignoring hard constraints |

Each technique adds latency and a model call. Add them one at a time, and keep each only if recall goes up.

---

## 7. Generation: grounding and citations

Retrieving the right passage isn't enough on its own. The model has to use it, and only it.

**Prompt assembly:**

- **Wrap each passage in tags with an id and its source**, e.g. `<doc id="3" source="refund-policy.md#limits">…</doc>`.
- **Put the long material first and the question last.** Anthropic's long-context guidance recommends this, and it tends to help with large inputs.
- **Mind the order.** Models use information at the start and end of a long context better than in the middle ([Liu et al., "Lost in the Middle"](https://arxiv.org/abs/2307.03172)). Put the strongest passages at the edges, and don't pad with weak ones. More context is not free.
- **Ask for quotes first.** For hard questions, have the model extract the relevant quotes and then answer from them.

**Grounding instructions that matter:**

```text
Answer only from the documents above. Cite the document id for every claim, like [3].
If the documents don't contain the answer, say so and stop. Don't fill gaps from memory.
If documents conflict, say which ones disagree and prefer the most recent.
```

**Structured citations.** Some APIs do this natively. With the Claude API you can pass the passages as `document` content blocks with `citations: {enabled: true}`. The response then carries citations that point to the exact cited text in each document, which is better than asking for `[3]` and hoping. (On the Claude API, citations can't be combined with structured outputs in the same request.)

**"I don't know" is a feature.** A RAG system that answers every question is hallucinating some of the time. Measure abstention: when the answer isn't in the corpus, the right output is a refusal with no citations.

---

## 8. RAG vs. long context vs. fine-tuning

With 1M-token context windows, "just put everything in the prompt" is a real option. It's often the right one for small corpora.

| Approach | Best when | Weak when |
| -------- | --------- | --------- |
| **Whole corpus in context** (plus prompt caching) | Small, mostly static corpus. Anthropic's Contextual Retrieval post suggests skipping RAG below ~200K tokens (~500 pages). | Large or fast-changing corpora, per-user permissions, high query volume, latency budgets |
| **RAG** | Large, changing, permissioned corpora where answers need citations | Questions that need the whole corpus at once |
| **Fine-tuning** | Teaching a *behavior*, format, or style; very high volume of narrow tasks | Teaching *facts*: they go stale, can't be cited, and can't be permissioned |
| **Agentic search** ([§9](#9-agentic-rag)) | Navigable, structured sources (code, file trees, APIs) | Latency-sensitive, high-volume Q&A |

Prompt caching changes the economics of the first row. A large, stable prefix is processed once, and later requests read it at a fraction of the normal input price. But it doesn't fix the middle-of-context problem, per-user permissions, or freshness. Rule of thumb: **fine-tune for form, retrieve for facts.**

---

## 9. Agentic RAG

Classic RAG retrieves once, then answers. **Agentic RAG** makes retrieval a **tool** the model calls as often as it needs: search, read, notice a gap, search again, then answer.

- **Iterative retrieval.** The model decides when it has enough. This handles multi-hop questions naturally.
- **Self-checking variants.** [Self-RAG](https://arxiv.org/abs/2310.11511) trains the model to decide when to retrieve and to critique its own use of passages. [Corrective RAG](https://arxiv.org/abs/2401.15884) grades the retrieved documents and falls back to other sources when they're poor. Both are the critique-loop idea from [Critique Agents as a Graph](critique-agent-graph.md), applied to retrieval.
- **Agentic search without vectors.** For code, `grep`, `glob`, and reading files often beat an embedding index. The structure (names, imports, call sites) *is* the index, and it is never stale. Claude Code's team has said publicly that early versions used a vector index and that agentic search worked better for code.
- **Retrieval over MCP.** Search becomes a tool any agent can call. See [Model Context Protocol](model-context-protocol.md).
- **As a graph:** `rewrite → retrieve → grade passages ⟲ (re-query | answer) → check citations`. This is an execution graph with a bounded loop (see [Graph Engineering](graph-engineering.md)).

The cost is latency and unpredictability, since each extra hop is another model call. Use a fixed pipeline for high-volume Q&A, and agentic retrieval where questions are open-ended.

---

## 10. Evaluating RAG

**Evaluate retrieval and generation separately.** Otherwise you can't tell which half failed.

| Layer | Metric | Question it answers |
| ----- | ------ | ------------------- |
| Retrieval | **Recall@k** | Is the right passage anywhere in the top k? (The metric that matters most.) |
| Retrieval | **MRR / nDCG** | How high is the right passage ranked? |
| Generation | **Faithfulness / groundedness** | Is every claim supported by the retrieved passages? |
| Generation | **Answer relevance** | Does the answer address the question? |
| Generation | **Citation correctness** | Does each cited passage actually support its claim? |
| End to end | **Correctness on a golden set** | Is the answer right? |
| End to end | **Abstention rate** | On unanswerable questions, does it refuse? |

**The golden set** is a few hundred real questions, each labeled with the passage(s) that answer it and a reference answer, plus deliberately *unanswerable* questions. Version it like code. Frameworks such as [RAGAS](https://arxiv.org/abs/2309.15217) automate faithfulness and relevance with LLM-as-judge. Calibrate those judges against human labels before trusting them (see [Building an Agent Evaluator](building-agent-evaluators.md)).

**Seven failure points** ([Barnett et al., 2024](https://arxiv.org/abs/2401.05856)), which make a useful triage list:

1. **Missing content.** The answer isn't in the corpus.
2. **Missed the top-ranked documents.** It's in the corpus but wasn't retrieved high enough.
3. **Not in context.** It was retrieved but dropped during prompt assembly.
4. **Not extracted.** It was in context, but the model missed it.
5. **Wrong format.** The answer ignores the requested format.
6. **Incorrect specificity.** The answer is too vague or too detailed.
7. **Incomplete.** Only part of the answer was given.

Points 1–3 happen before the model sees anything (corpus, retrieval, prompt assembly). Points 4–7 happen in generation. Check them in that order.

---

## 11. Security and operations

**Security:**

- **Enforce permissions at retrieval time, inside the query.** Filter by the *asking user's* ACL before ranking. Never retrieve everything and ask the model to hide what the user shouldn't see.
- **Retrieved text is untrusted input.** A document can contain instructions ("ignore previous instructions and …"). This is **indirect prompt injection** ([Greshake et al., 2023](https://arxiv.org/abs/2302.12173)). Mark passages clearly as data, don't let retrieved text trigger privileged tools, and treat any write-capable agent that reads untrusted content as high-risk. See [Security Fundamentals](security-fundamentals.md).
- **The index is a copy of your data,** with PII and secrets included. It needs the same retention, deletion (right-to-erasure), and encryption controls as the source.

**Operations:**

- **Freshness.** Use incremental indexing driven by change events (CDC or webhooks), with deletes as well as upserts. A deleted document that still gets retrieved is a correctness bug *and* a compliance bug. This is the same outbox and CDC pattern as [Message Queues & Event-Driven](message-queues-event-driven.md).
- **Index versioning.** Build the new index alongside the old one, evaluate it on the golden set, then switch an alias. This is blue-green for search (see [Feature Flags & Progressive Delivery](feature-flags-progressive-delivery.md)).
- **Observability.** Log the query, the rewritten query, the retrieved ids and scores, the reranked ids, and the cited ids for every request. Without that trace you can't tell which of the seven failure points you hit.
- **Cost and latency budget.** Embedding a query costs milliseconds and little money. Reranking takes tens of milliseconds. Query rewrites and agentic hops are full model calls. Spend where recall@k actually improves.

---

## 12. Best practices & anti-patterns

**Do**

- ✅ Start with **structure-aware chunking + hybrid search + a reranker**. That baseline beats most clever additions.
- ✅ Build the **golden set first**, including unanswerable questions, and track recall@k on every change.
- ✅ Keep **metadata and source pointers** on every chunk, for filters, citations, and deletion.
- ✅ **Prepend context to chunks** (contextual retrieval) when chunks don't make sense on their own.
- ✅ Require **citations** and allow **"I don't know"**.
- ✅ Filter by **permissions in the retrieval query**.
- ✅ Try **whole-corpus-in-context with caching** first when the corpus is small.

**Avoid**

- ❌ **Vector-only search** on content full of identifiers, codes, and names.
- ❌ **Tuning the prompt when retrieval is the problem.** Check recall@k first.
- ❌ **Stuffing in the top 50 chunks "just in case."** Weak passages dilute strong ones and push them into the middle.
- ❌ **Fine-tuning to teach facts.**
- ❌ **Post-hoc permission filtering** by the model.
- ❌ **Re-embedding with a new model in place,** which leaves vectors from two models mixed in one index.
- ❌ **Judging quality by demo questions** instead of a labeled set.

---

## 13. Go deeper

Related material in this library:

- 📝 **[Graph Engineering §6](graph-engineering.md#6-knowledge-graphs--graphrag)**: GraphRAG, for global and multi-hop questions where vector RAG struggles.
- 📝 **[Database Indexing](database-indexing.md)**: B-trees vs. ANN graphs. Same goal (avoid a full scan), different geometry.
- 📝 **[Building an Agent Evaluator](building-agent-evaluators.md)**: LLM-as-judge for faithfulness, and its biases.
- 📝 **[Critique Agents as a Graph](critique-agent-graph.md)**: Self-RAG and CRAG are critique loops over retrieval.
- 📝 **[Model Context Protocol](model-context-protocol.md)**: retrieval as a tool any agent can call.
- 📝 **[Caching Strategies](caching-strategies.md)** · **[Message Queues & Event-Driven](message-queues-event-driven.md)**: the index as derived data, kept fresh by CDC.
- 📝 **[Security Fundamentals](security-fundamentals.md)**: prompt injection and least privilege.

### Primary references

- Lewis et al., [*Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks*](https://arxiv.org/abs/2005.11401) (2020): the original RAG paper.
- Anthropic, [*Introducing Contextual Retrieval*](https://www.anthropic.com/news/contextual-retrieval) (2024): contextual embeddings and BM25, reranking, and the whole-corpus-in-context threshold.
- Liu et al., [*Lost in the Middle: How Language Models Use Long Contexts*](https://arxiv.org/abs/2307.03172) (2023).
- Cormack, Clarke & Büttcher, [*Reciprocal Rank Fusion Outperforms Condorcet and Individual Rank Learning Methods*](https://dl.acm.org/doi/10.1145/1571941.1572114) (SIGIR 2009).
- Gao et al., [*Precise Zero-Shot Dense Retrieval without Relevance Labels*](https://arxiv.org/abs/2212.10496) (HyDE, 2022).
- Khattab & Zaharia, [*ColBERT*](https://arxiv.org/abs/2004.12832) (2020) · Malkov & Yashunin, [*HNSW*](https://arxiv.org/abs/1603.09320) (2016).
- Asai et al., [*Self-RAG*](https://arxiv.org/abs/2310.11511) (2023) · Yan et al., [*Corrective RAG*](https://arxiv.org/abs/2401.15884) (2024).
- Es et al., [*RAGAS: Automated Evaluation of Retrieval Augmented Generation*](https://arxiv.org/abs/2309.15217) (2023).
- Barnett et al., [*Seven Failure Points When Engineering a Retrieval Augmented Generation System*](https://arxiv.org/abs/2401.05856) (2024).
- Greshake et al., [*Not What You've Signed Up For: Compromising Real-World LLM-Integrated Applications with Indirect Prompt Injection*](https://arxiv.org/abs/2302.12173) (2023).
- Edge et al., [*From Local to Global: A Graph RAG Approach to Query-Focused Summarization*](https://arxiv.org/abs/2404.16130) (2024): Microsoft GraphRAG.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
