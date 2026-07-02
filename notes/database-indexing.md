# 🗂️ Database Indexing — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~20 min · **Prerequisites:** basic SQL, a rough idea of how tables and rows work.

A database index is a separate, sorted data structure that lets the engine **find rows without scanning the whole table** — like the index at the back of a book lets you jump to a topic instead of reading every page. This note explains what indexes are, the data structures behind them, how to design them well, and how to avoid the common traps.

## Table of contents

- [1. Why indexes exist](#1-why-indexes-exist)
- [2. The B+Tree](#2-the-btree-the-workhorse)
- [3. What an index accelerates](#3-what-an-index-accelerates)
- [4. Clustered vs. non-clustered](#4-clustered-vs-non-clustered)
- [5. Composite & covering indexes](#5-composite--covering-indexes)
- [6. Other index types](#6-other-index-types)
- [7. The cost of indexes](#7-the-cost-of-indexes)
- [8. Reading the query plan (EXPLAIN)](#8-reading-the-query-plan-explain)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Quick reference](#10-quick-reference)
- [11. Go deeper](#11-go-deeper)

---

## 1. Why indexes exist

Consider a `users` table with 10 million rows:

```sql
SELECT * FROM users WHERE email = 'ada@example.com';
```

- **Without an index** → a **full table scan**: read all 10M rows, check each. Cost is `O(n)`.
- **With an index on `email`** → an **index seek**: a lookup in a sorted structure. Cost is `O(log n)`.

For 10M rows that's roughly **23 comparisons instead of 10,000,000**.

> **The core trade-off:** an index trades slower writes and extra storage for dramatically faster reads. Everything else in this note is a consequence of that trade-off.

---

## 2. The B+Tree (the workhorse)

Most relational indexes — PostgreSQL, MySQL/InnoDB, SQL Server, Oracle — use a **B+Tree** by default. It is a *balanced, high-fan-out* tree designed for disk (not a binary tree).

```
                 [ M ]                      ← root
              /        \
        [ D | H ]     [ R | W ]             ← internal nodes: keys → child pointers
        /   |   \      /   |   \
   [A B C][D..][H..] [..][R..][W..]         ← leaf nodes: keys + row pointers
     ↕       ↕      ↕     ↕      ↕          ← leaves linked in sorted order
```

Why this shape wins:

- **Balanced** — every leaf is at the same depth, so *every* lookup costs the same (~3–4 disk reads even for billions of rows).
- **High fan-out** — each node holds hundreds of keys, keeping the tree shallow. Fewer levels = fewer disk seeks, and disk seeks are the real cost.
- **Linked, sorted leaves** — the B+Tree superpower. Because leaves form a sorted chain, **range scans and ordering are cheap**:

```sql
WHERE age BETWEEN 25 AND 40      -- seek to 25, then walk the leaf chain
ORDER BY created_at              -- read leaves in order — no separate sort step
```

---

## 3. What an index accelerates

| Operation        | Example                       | Helped? |
| ---------------- | ----------------------------- | :-----: |
| Equality         | `WHERE email = ?`             | ✅ |
| Range            | `WHERE price > 100`           | ✅ |
| Prefix match     | `WHERE name LIKE 'Ad%'`       | ✅ |
| Sorting          | `ORDER BY created_at`         | ✅ (avoids a sort) |
| Min / Max        | `MAX(salary)`                 | ✅ (jump to one end) |
| Joins            | joining on an indexed key     | ✅ |
| Suffix / contains| `LIKE '%son'`                 | ❌ (sort order is useless here) |
| Function on col  | `WHERE LOWER(email) = ?`      | ❌ unless you build an **expression index** |

> A plain B+Tree index is sorted **left-to-right**. Anything that doesn't anchor on the left of that sort order (a leading wildcard, a function wrapping the column) can't use it.

---

## 4. Clustered vs. non-clustered

This distinction trips people up constantly — internalize it.

**Clustered index** — the table rows are *physically stored in index order*. The leaf nodes **are** the data. You get **one per table** (data can only be physically sorted one way).
- InnoDB (MySQL): the **primary key is always the clustered index**.
- SQL Server: the primary key clusters by default.

**Non-clustered (secondary) index** — a separate structure whose leaves hold the indexed column(s) plus a **pointer back to the row** (in InnoDB, that pointer is the primary key value).

Why it matters — a secondary lookup can cost **two** traversals:

```sql
-- Secondary index on email; table clustered on id (PK).
SELECT name FROM users WHERE email = 'ada@example.com';
--  1. Seek the email index      → returns the row's PK (id = 42)
--  2. Seek the PK/clustered index with id = 42 → returns the full row   ← "bookmark lookup"
```

That second hop is the motivation for the next section.

---

## 5. Composite & covering indexes

This is where most real-world performance is won.

### Composite index (multiple columns)

An index on `(last_name, first_name)` is sorted by `last_name` first, then `first_name` within each. The **left-prefix rule** governs what it can serve:

| Query | Uses the index? | Why |
| ----- | :-------------: | --- |
| `WHERE last_name = 'Lovelace'` | ✅ | leading column |
| `WHERE last_name = 'Lovelace' AND first_name = 'Ada'` | ✅ | full prefix |
| `WHERE first_name = 'Ada'` | ❌ | skips the leading column — like searching a phone book by first name |

👉 **Column order is a design decision.** General guidance: equality-filtered columns first, then the range/sort column last.

```sql
-- Good for:  WHERE tenant_id = ? AND status = ? ORDER BY created_at
CREATE INDEX idx_orders ON orders (tenant_id, status, created_at);
```

### Covering index (the query never touches the table)

If the index contains **every column the query needs**, the engine answers entirely from the index and **skips the bookmark lookup**:

```sql
CREATE INDEX idx_cover ON users (email, name);
SELECT name FROM users WHERE email = ?;   -- `name` is in the index → no table hit
```

In plans this shows as **"Index Only Scan"** (Postgres) or **"Using index"** (MySQL `EXPLAIN`). One of the highest-leverage tuning tricks there is. Postgres also supports `INCLUDE` columns to add payload without affecting sort order:

```sql
CREATE INDEX idx_cover ON users (email) INCLUDE (name);
```

---

## 6. Other index types

| Type | Best for | Notes |
| ---- | -------- | ----- |
| **B+Tree** | equality, range, sorting | The default. Use it unless you have a specific reason not to. |
| **Hash** | equality only (`=`) | `O(1)` lookups, but no ranges and no ordering. |
| **Bitmap** | low-cardinality columns (status, gender) | Excellent for analytics with `AND`/`OR` filters; poor for high-write OLTP. |
| **GIN** (Postgres) | "many values per row": JSONB, arrays, full-text | Inverted structure: value → list of rows. |
| **GiST** (Postgres) | geospatial, ranges, nearest-neighbor | Extensible; powers PostGIS. |
| **Inverted index** | search engines (Elasticsearch, Lucene) | Maps *term → documents*; the basis of full-text search. |
| **LSM-tree** | write-heavy stores (Cassandra, RocksDB, LevelDB) | Buffers writes in memory, flushes sorted files; trades read amplification for write throughput. |

> **B+Tree vs. LSM-tree** is the classic storage-engine dichotomy: B+Trees favor reads and in-place updates; LSM-trees favor high write throughput. See *Designing Data-Intensive Applications*, Ch. 3.

---

## 7. The cost of indexes

Indexes are not free — this is why "just index everything" is wrong:

1. **Slower writes.** Every `INSERT`/`UPDATE`/`DELETE` must update *every* affected index. 10 indexes ⇒ up to 10 extra structures maintained per write.
2. **Storage & memory.** Indexes can rival or exceed the table's own size, and they compete for the buffer/page cache.
3. **Planner overhead.** More indexes ⇒ more candidate plans for the optimizer to weigh.
4. **Dead weight.** An unused index is pure cost. Track usage (`pg_stat_user_indexes` in Postgres; `sys.dm_db_index_usage_stats` in SQL Server) and drop indexes with zero scans.

**Rule of thumb:** index the columns used in `WHERE`, `JOIN`, and `ORDER BY` on your *hot* queries — not every column, and not blindly.

---

## 8. Reading the query plan (EXPLAIN)

Never guess whether an index is used — verify with the planner:

```sql
EXPLAIN ANALYZE SELECT * FROM users WHERE email = 'ada@example.com';
```

What to look for:

- ✅ `Index Scan` / `Index Seek` / `Index Only Scan` — the index is doing its job.
- ⚠️ `Seq Scan` / `Full Table Scan` on a large table with a selective filter — a missing or unused index.
- **Selectivity rules everything.** If a filter matches most rows (say 90%), the planner will *correctly* ignore the index and scan — random-access via an index would be slower than a sequential read. An index only helps when it eliminates *most* rows.
- **Stale statistics** cause bad plans. Keep them fresh (`ANALYZE` in Postgres, `UPDATE STATISTICS` in SQL Server).

---

## 9. Best practices & anti-patterns

**Do**
- Index foreign keys used in joins.
- Order composite index columns as *equality → range/sort*.
- Prefer **covering indexes** for hot read paths.
- Match the index to your **real query patterns** — read your slow-query log first.
- Use **partial/filtered indexes** for skewed data: `CREATE INDEX ... WHERE status = 'active'`.
- Re-check indexes after query patterns change; drop the unused ones.

**Avoid**
- Indexing every column "just in case" — it taxes every write.
- Wrapping the indexed column in a function (`WHERE LOWER(email) = ?`) unless you create a matching **expression index**.
- Leading wildcards (`LIKE '%foo'`) and expecting index help — use full-text/trigram indexes instead.
- Redundant indexes: `(a)` is already covered by the prefix of `(a, b)`.
- Very wide composite indexes with a low-selectivity leading column.

---

## 10. Quick reference

```sql
-- Create
CREATE INDEX idx_users_email ON users (email);
CREATE UNIQUE INDEX idx_users_email_u ON users (email);          -- also enforces uniqueness
CREATE INDEX idx_orders_lookup ON orders (tenant_id, status, created_at);   -- composite
CREATE INDEX idx_active ON orders (created_at) WHERE status = 'active';      -- partial (Postgres)
CREATE INDEX idx_lower_email ON users (LOWER(email));            -- expression index

-- Inspect / drop
EXPLAIN ANALYZE SELECT ...;      -- did it use the index?
DROP INDEX idx_users_email;
```

**Mental model:** an index is a *sorted copy of some columns + pointers to the rows*, kept up to date on every write, that turns "read everything and filter" into "jump straight there." You pay on writes and storage; you win on reads. Design around actual queries and confirm with `EXPLAIN`.

---

## 11. Go deeper

Resources already curated in this library:

- 📗 **[Database Internals](../books/) — Alex Petrov** — Chapters 1–4 are a deep dive into B-Trees, LSM-trees, and storage engines.
- 📗 **[Designing Data-Intensive Applications](../books/) — Martin Kleppmann** — Chapter 3, "Storage and Retrieval," covers the B+Tree vs. LSM-tree trade-off superbly.
- 🎓 **[CMU 15-445: Database Systems](../courses/)** — lectures on index data structures and query execution.
- 📄 **[The Log](../papers/)** and **[Bigtable](../papers/)** in the papers index for how large-scale systems handle indexing and storage.

*This is an original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
