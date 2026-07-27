# 🧮 Data Structures & Algorithms — A Detailed Study Note

> **Level:** 🟢 Beginner–Intermediate · **Reading time:** ~24 min · **Prerequisites:** you can read code with loops and arrays. The foundation the rest of the library quietly stands on.

Data structures are **how you organize data**; algorithms are **how you operate on it**. The whole subject is one question in disguise: *given what you do most often (read? insert? search? order?), which arrangement makes that operation cheap?* Every "fast" system you've studied — [indexes](database-indexing.md), [caches](caching-strategies.md), [consistent hashing](load-balancing-rate-limiting.md) — is a data-structure choice wearing a bigger hat.

> **The core idea:** there is no universally "best" structure — only the best *for your access pattern*. A hash map is unbeatable for lookups and useless for "give me the smallest." Choosing a data structure **is** choosing which operations you've decided to make fast, and which you've accepted will be slow.

## Table of contents

- [1. Big-O: the language of cost](#1-big-o-the-language-of-cost)
- [2. The core data structures](#2-the-core-data-structures)
- [3. Trees & heaps](#3-trees--heaps)
- [4. Hashing](#4-hashing)
- [5. Graphs](#5-graphs)
- [6. Algorithmic paradigms](#6-algorithmic-paradigms)
- [7. Sorting & searching](#7-sorting--searching)
- [8. How to pick (and solve)](#8-how-to-pick-and-solve)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. Big-O: the language of cost

**Big-O** describes how an algorithm's cost **grows** as input size `n` grows — ignoring constants and lower-order terms, because at scale the growth *rate* dominates everything else.

| Notation | Name | Feel | Example |
| -------- | ---- | ---- | ------- |
| `O(1)` | constant | best | hash lookup, array index |
| `O(log n)` | logarithmic | excellent | binary search, balanced-tree op |
| `O(n)` | linear | fine | scan a list |
| `O(n log n)` | linearithmic | good | best general sort |
| `O(n²)` | quadratic | painful | nested loops over the same data |
| `O(2ⁿ)` / `O(n!)` | exponential/factorial | intractable | brute-force subsets/permutations |

Three things people forget:
- **Constants can matter in practice.** `O(n)` with a huge constant can lose to `O(n log n)` at real sizes — Big-O is asymptotic, benchmarks are concrete.
- **Analyze time *and* space.** A faster algorithm that needs `O(n)` extra memory may not fit. This is the recurring **[read-vs-write / space-vs-time trade-off](database-indexing.md)** — you often buy speed with memory (a cache, an index, a hash set).
- **Amortized vs. worst-case.** A dynamic array's `push` is `O(1)` *amortized* (occasional `O(n)` resize spread over many cheap ops) — the same amortization idea as a [B-tree](database-indexing.md) or hash-map resize.

---

## 2. The core data structures

| Structure | Access | Search | Insert | Delete | Notes |
| --------- | :----: | :----: | :----: | :----: | ----- |
| **Array** | O(1) | O(n) | O(n) | O(n) | Contiguous; cache-friendly; fixed/append-cheap |
| **Dynamic array** (list, vector) | O(1) | O(n) | O(1)* | O(n) | *amortized append; the workhorse |
| **Linked list** | O(n) | O(n) | O(1) | O(1) | O(1) insert *if* you hold the node; poor cache locality |
| **Hash map** | — | O(1)* | O(1)* | O(1)* | *average; no ordering (§4) |
| **Stack** (LIFO) | O(1) top | — | O(1) | O(1) | Undo, call stack, DFS, backtracking |
| **Queue** (FIFO) | O(1) ends | — | O(1) | O(1) | Scheduling, BFS, buffering ([message queues](message-queues-event-driven.md)) |
| **Balanced BST** | — | O(log n) | O(log n) | O(log n) | **Ordered**: range queries, min/max, floor/ceil |
| **Heap** | O(1) peek | — | O(log n) | O(log n) | Priority queue; top-k |

The two you'll reach for constantly are the **dynamic array** (default container) and the **hash map** (default lookup). Most of the rest earn their place by giving you an operation those two can't do cheaply — **ordering** (BST), **priority** (heap), or **cheap ends** (stack/queue).

> **Arrays beat linked lists more often than theory suggests** — contiguous memory means the CPU cache prefetches the next elements, while a linked list chases pointers all over the heap. `O(1)` insertion rarely beats cache locality in practice (the [CS:APP](../books/) lesson).

---

## 3. Trees & heaps

**Binary Search Tree (BST)** — left < node < right, so an in-order walk yields sorted data and search is `O(log n)`… **if balanced.** An unbalanced BST degrades to a linked list (`O(n)`) — which is why real systems use **self-balancing** trees:

- **Red-black tree** — most in-memory ordered maps (Java `TreeMap`, C++ `std::map`).
- **AVL tree** — more strictly balanced, faster reads, more rebalancing on writes.
- **B-tree / B+ tree** — high fan-out, disk-optimized; **the database index structure** from your [indexing note](database-indexing.md).
- **Trie (prefix tree)** — keyed by string prefixes; autocomplete, routers, dictionaries.

**Heap** — a complete binary tree with the **heap property** (parent ≤ children for a min-heap). It doesn't fully sort — it just guarantees **`O(1)` access to the min/max** and `O(log n)` insert/extract. It's the natural **priority queue**, and the engine behind top-k, Dijkstra, and scheduling.

> BST vs. heap in one line: a **BST keeps everything ordered** (range queries, predecessors); a **heap only tracks the extreme** (fast min/max, nothing else). Pick by whether you need full order or just the top.

---

## 4. Hashing

A **hash map** turns a key into an array index via a **hash function**, giving `O(1)` average lookup/insert/delete. The catch is **collisions** (two keys → one bucket), resolved by:
- **Chaining** — each bucket holds a list of entries.
- **Open addressing** — probe for the next free slot (linear/quadratic probing).

Key realities:
- **Worst case is `O(n)`** — if everything collides (bad hash, or adversarial input → a real DoS vector, "hash flooding"). Good hash functions and randomized seeds defend against it.
- **Load factor** triggers **resize/rehash** (all keys re-placed) — an `O(n)` op amortized to `O(1)` per insert. Same amortization as the dynamic array.
- **No ordering** — you cannot ask a hash map for "the smallest key" or a range. Need order? Use a tree map.

> Hashing is *everywhere* upstream in this library: [database hash indexes](database-indexing.md), [consistent hashing](load-balancing-rate-limiting.md) for sharding/LB, [content-addressable stores](pnpm-tips.md) (pnpm, Git), Bloom filters, and dedup sets. It's the single most leveraged idea in the subject.

---

## 5. Graphs

A **graph** = nodes + edges; models anything relational (networks, dependencies, social graphs, maps, the [call graph a linter walks](knip.md)). Represented as an **adjacency list** (sparse, common) or **adjacency matrix** (dense, `O(1)` edge check, `O(V²)` space).

Core traversals:
- **BFS (breadth-first)** — a **queue**; explores level by level. Finds **shortest path in unweighted** graphs.
- **DFS (depth-first)** — a **stack**/recursion; goes deep. Cycle detection, topological sort, connectivity.

Classic algorithms worth recognizing:
- **Dijkstra** — shortest path, weighted (non-negative), via a **heap**. (Networks, maps, routing.)
- **Topological sort** — order a DAG by dependency (build systems, task scheduling, [module graphs](knip.md)).
- **Union-Find (disjoint set)** — near-`O(1)` "are these connected?"; used in Kruskal's MST and [consistent-hashing](load-balancing-rate-limiting.md) grouping.

> Notice the composition: graph algorithms are built *out of* the earlier structures — BFS is a queue, DFS is a stack, Dijkstra is a heap. The structures are the alphabet; the algorithms are the words.

---

## 6. Algorithmic paradigms

Recognizing the *pattern* matters more than memorizing solutions:

- **Two pointers / sliding window** — one pass over a sequence with two indices. Turns many `O(n²)` scans into `O(n)`.
- **Binary search** — halve the search space each step, `O(log n)`. Works on any *monotonic* predicate, not just sorted arrays ("smallest x that works").
- **Recursion & divide-and-conquer** — split into subproblems, solve, combine (merge sort, quicksort, binary search).
- **Dynamic programming (DP)** — when subproblems **overlap**, cache their results (memoization) instead of recomputing. Turns exponential into polynomial. The signal: "optimal substructure + overlapping subproblems."
- **Greedy** — take the locally best choice each step. Fast and simple, but only correct when local optimum ⇒ global (Dijkstra, Huffman, interval scheduling). Prove it before trusting it.
- **Backtracking** — try, recurse, undo on failure (a **stack**). Permutations, N-queens, sudoku.

> DP vs. greedy is the classic fork: **greedy commits and never looks back** (fast, sometimes wrong); **DP explores all subproblem combinations** (slower, always optimal when it applies). Reach for greedy only when you can *prove* the local choice is safe.

---

## 7. Sorting & searching

**Sorting** — know these three and why:

| Sort | Time | Space | Stable? | When |
| ---- | ---- | ----- | :-----: | ---- |
| **Merge sort** | O(n log n) | O(n) | ✅ | Guaranteed bound; stable; external/linked data |
| **Quicksort** | O(n log n) avg, O(n²) worst | O(log n) | ❌ | Fast in practice; in-place; the usual default |
| **Heap sort** | O(n log n) | O(1) | ❌ | In-place with guaranteed bound |

Real-world sorts are **hybrids** — e.g. **Timsort** (Python, Java objects) blends merge + insertion sort and exploits already-ordered runs. **`O(n log n)` is the comparison-sort lower bound**; you only beat it with non-comparison sorts (counting/radix) on constrained keys.

**Searching** — **binary search** (`O(log n)`) on sorted data is the workhorse, and its "halve the space" idea generalizes far beyond arrays (it's the shape of a [B-tree](database-indexing.md) descent and of `git bisect`).

---

## 8. How to pick (and solve)

**Choosing a structure** — ask what you do *most*:

- Lookup by key → **hash map**. Need it *ordered* too → **tree map**.
- Always want the min/max/top-k → **heap**.
- LIFO → **stack**; FIFO → **queue**.
- Index by position, iterate a lot → **dynamic array** (cache-friendly).
- Relationships/paths → **graph**.
- Prefix/autocomplete → **trie**.

**Solving a problem** — a reliable loop:
1. **Clarify** inputs, outputs, constraints, edge cases (empty, one element, duplicates, overflow).
2. **Brute force first** — get *a* correct answer and its Big-O.
3. **Find the bottleneck**, then reach for the pattern that removes it (a hash set to kill an `O(n²)` scan; sorting to enable two-pointers; DP for overlapping subproblems).
4. **Analyze time and space**, and **test the edges**.

> This mirrors the [debugging discipline](observability.md) elsewhere in the library: measure first, find the bottleneck, then fix the *actual* one — don't optimize by guess.

---

## 9. Best practices & anti-patterns

**Do**
- **Pick the structure from the access pattern**, not habit.
- **Reach for a hash map to break `O(n²)`** — trading space for time is usually the move.
- **Prefer arrays for iteration-heavy work** (cache locality beats theoretical `O(1)` insert).
- **Know your language's built-ins** — the standard library's sort/map/heap is battle-tested; use it.
- **State the Big-O** (time *and* space) of what you write, and **test edge cases**.

**Avoid**
- **Premature optimization** — clarity first; optimize the *measured* bottleneck ("the root of all evil" — Knuth).
- **Reinventing standard structures** — hand-rolled hash maps/sorts are a bug farm.
- **Ignoring worst cases** — quicksort's `O(n²)`, hash flooding, unbalanced BSTs bite in production.
- **Optimizing Big-O while ignoring constants/memory** — the asymptotically-better option can lose on real hardware.
- **Choosing by familiarity** — a linked list "because O(1) insert" that then gets iterated a million times.

---

## 10. Go deeper

This is the foundation under much of the library:

- 📝 **[Database Indexing](database-indexing.md)** — B-trees, hash indexes, and amortization applied to storage.
- 📝 **[Caching](caching-strategies.md)** & **[Load Balancing](load-balancing-rate-limiting.md)** — LRU (hash map + linked list), consistent hashing, Bloom filters in the wild.
- 📝 **[Concurrency](concurrency-parallelism.md)** — lock-free structures and why memory layout matters.
- 📗 **[Structure and Interpretation of Computer Programs (SICP)](../books/)** & **[CS:APP](../books/)** — the foundations, including why cache locality beats pointer-chasing.
- 🎓 **[CS50](../courses/)** — a superb first pass at arrays, hashing, trees, and Big-O.
- 📄 Practice: LeetCode/NeetCode patterns — but learn the **paradigms** (§6), not individual answers.

### Primary references

- Cormen, Leiserson, Rivest & Stein, *Introduction to Algorithms (CLRS)* — the canonical reference.
- Sedgewick & Wayne, *Algorithms* (4th ed.) — more approachable, with great visualizations.
- D. Knuth, *The Art of Computer Programming* — the deep well.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
