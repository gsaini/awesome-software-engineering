# 🗄️ Database Optimization & Performance Checklist

> **Level:** 🟡 Intermediate · **Reading time:** ~15 min · **Prerequisites:** SQL basics, schema design, and query profiling.

A database can be "correct" and still be slow. This checklist captures the common performance wins and the failure modes that quietly create latency, lock contention, and runaway cloud spend. Treat it as a practical audit for application databases before large-scale refactors or production tuning.

## Table of contents

- [1. Query and execution-plan review](#1-query-and-execution-plan-review)
- [2. Index design and maintenance](#2-index-design-and-maintenance)
- [3. Schema and data model checks](#3-schema-and-data-model-checks)
- [4. Transaction and concurrency checks](#4-transaction-and-concurrency-checks)
- [5. Caching and read-path optimization](#5-caching-and-read-path-optimization)
- [6. Capacity, storage, and scaling review](#6-capacity-storage-and-scaling-review)
- [7. Operational monitoring and alerting](#7-operational-monitoring-and-alerting)
- [8. Quick checklist](#8-quick-checklist)
- [9. Go deeper](#9-go-deeper)

---

## 1. Query and execution-plan review

### Check the actual plan, not the intuition

- Confirm the database is using the expected index for each critical query.
- Look for full table scans on high-cardinality filters or hot tables.
- Identify expensive `JOIN`s, nested loops, hash joins, or sorts that could be eliminated.
- Check whether the application is selecting too many columns or rows when a smaller projection would do.
- Review `LIMIT` usage and pagination patterns to ensure no unbounded scans happen.

### Red flags

- `Seq Scan` / full table scan on a frequently accessed table
- Large sort or hash operations in the plan
- Repeated index lookups for the same row set
- Missing `ORDER BY`/`WHERE` selectivity on big tables
- Queries that perform functions on indexed columns (`LOWER(email)`, `DATE(created_at)`) and defeat the index

### Typical fixes

- Add or reorder composite indexes to match the query shape.
- Use covering indexes when the query only needs a few columns.
- Rewrite predicates to avoid function calls on indexed fields.
- Split large queries into smaller, more targeted reads.

---

## 2. Index design and maintenance

### What to verify

- Every hot filter or join key is indexed.
- Composite indexes are ordered according to actual query patterns, not just theoretical correctness.
- There are not too many redundant indexes creating write overhead.
- Primary key and foreign key relationships are defined clearly.
- Indexes are checked regularly for bloat and unused coverage.

### A good index is not just present — it is purposeful

- For a query like `WHERE tenant_id = ? AND status = ? ORDER BY created_at`, the order may matter more than the individual columns.
- A broad index on `(tenant_id, status, created_at)` may outperform several single-column indexes.
- If a query never filters on the first column, a composite index may not help much.
- An index that is never used adds write amplification and storage cost.

### Maintenance actions

- Rebuild or reorganize fragmented indexes where supported.
- Remove duplicate or stale indexes discovered by query-analysis tooling.
- Check index cardinality and correlation when a table changes behavior.
- Review whether partial indexes or expression indexes would reduce index size and improve selectivity.

---

## 3. Schema and data model checks

### Database design issues are often the root cause

- Use the smallest practical data types for each column.
- Avoid storing large JSON blobs or free-form text in hot rows if they are queried often.
- Normalize carefully, but do not over-normalize if it creates expensive joins.
- Ensure foreign keys are indexed where joins occur frequently.
- Prefer surrogate keys when they improve join and lookup behavior, but keep them aligned with realistic access patterns.

### Watch for anti-patterns

- `TEXT` or `VARCHAR` columns without length discipline
- Large nullable columns in frequently accessed rows
- Eager denormalization in one place and expensive aggregation elsewhere
- Deep joins across many tables for a simple read
- Data models that force repeated `SELECT *` or large row reads on each request

### Practical reviews

- Check table sizes and row counts by growth pattern.
- Identify tables that are hot paths versus archival data.
- Confirm that the database schema reflects actual access patterns instead of historical design by committee.

---

## 4. Transaction and concurrency checks

### Slow queries often appear as lock contention

- Review transaction scope: keep transactions as short as possible.
- Reduce the number of rows touched inside long transactions.
- Avoid long-running `SELECT` statements that hold locks or create read anomalies.
- Watch for deadlock-prone access patterns with multiple updates on the same rows.
- Confirm isolation levels are appropriate for the workload; do not default to stronger isolation than needed.

### Review patterns

- Batch updates in the same logical boundary instead of many tiny writes.
- Use optimistic concurrency when conflicts are rare and pessimistic locking only when necessary.
- Identify hot spots where several workers modify the same resource in a cascade.
- Check whether the database is doing a lot of row locking on indexes with poor selectivity.

---

## 5. Caching and read-path optimization

### Not every read needs the database

- Cache the most frequent read results close to the application or in a dedicated cache layer.
- Distinguish between data freshness requirements and strict consistency requirements.
- Validate cache invalidation patterns to avoid stale reads or cache stampedes.
- Review whether expensive aggregation queries are repeated unnecessarily.

### Common hotspots

- Frequently repeated reads of the same lookup tables
- Expensive reports or dashboards re-running the same aggregates on every request
- High query churn caused by fan-out patterns or N+1 retrievals
- Unbounded revalidation when data changes frequently but read patterns remain bursty

### Good practice

- Cache at the right layer: application, CDN, reverse proxy, or database-level caching if the engine supports it.
- Provide TTLs that match freshness requirements.
- Use versioning or event-driven invalidation where correctness matters.

---

## 6. Capacity, storage, and scaling review

### Performance is often a capacity problem in disguise

- Review disk I/O, memory pressure, CPU saturation, and connection count.
- Check if the database is swapping or failing to keep working set memory hot.
- Confirm the working set fits in memory where the workload depends on it.
- Examine whether the database is under-provisioned for peak write traffic.
- Review replication lag and read-replica routing if the architecture has replicas.

### Structural optimization options

- Partition large tables by time or tenant when the workload naturally falls into segments.
- Offload archival or reporting workloads to read replicas or analytics systems.
- Tune batch sizes and throughput for bulk imports and large writes.
- Change storage or instance sizing only after verifying the true bottleneck.

---

## 7. Operational monitoring and alerting

### Without telemetry, optimization is guesswork

- Measure query latency, throughput, row count, and lock waits over time.
- Track slow query logs and P95/P99 response percentiles, not just averages.
- Watch for top offenders by execution count and total time spent.
- Alert on index bloat, replication lag, deadlocks, and connection saturation.
- Capture baseline metrics before and after each optimization.

### Key signals

- P95/P99 latency trends
- CPU / memory / IOPS saturation
- Query counts per endpoint or feature
- Lock waits and deadlock counters
- Database growth and vacuum/maintenance backlog

---

## 8. Quick checklist

Use this as a pre-flight audit for any optimization effort:

- [ ] Identify top slow queries and their actual execution plans.
- [ ] Confirm all hot filters and joins use the right indexes.
- [ ] Check for redundant, missing, or bloated indexes.
- [ ] Review composite index ordering against real query patterns.
- [ ] Eliminate function calls on indexed columns where possible.
- [ ] Inspect large scans, sorts, and hash operations in plans.
- [ ] Keep transactions short and avoid unnecessary lock scope.
- [ ] Validate data types and schema choices for frequent access paths.
- [ ] Review caching and invalidation for repeated reads.
- [ ] Confirm partitioning or sharding is justified before adding complexity.
- [ ] Monitor query latency, lock waits, and resource saturation regularly.
- [ ] Measure and compare before/after performance for each planned change.

---

## 9. Go deeper

- [Database Indexing](database-indexing.md) — indexes, B+Trees, composite keys, and query-plan reading.
- [Database Transactions & Isolation Levels](database-transactions-isolation.md) — locks, anomalies, isolation trade-offs, and concurrency.
- [Caching Strategies & Invalidation](caching-strategies.md) — read-path tuning and invalidation patterns.
- [Database Sharding & Partitioning](database-sharding-partitioning.md) — when horizontal scaling is the right answer.

### Related reading

- The practical cost model behind indexing, limits, and query selection: database fundamentals and engine docs.
- Query-optimizer behavior in PostgreSQL, MySQL, SQL Server, and SQLite.
- Benchmarking and profiling patterns for application systems under realistic load.

---

> Performance work is easiest to get right when it starts with the slowest query, the hottest table, and the actual execution plan — not a guess about what "should" be fast.
