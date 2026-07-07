# 📝 Study Notes

Original, self-authored deep-dive notes on core software-engineering topics — written to be read on their own and to tie together the books, papers, and courses elsewhere in this library.

Levels: 🟢 Beginner · 🟡 Intermediate · 🔴 Advanced.

> 🗓️ Looking for the day-by-day journal of what I studied? See the **[Learning Log](learning-log/)**.

## 🤖 AI & Agentic Engineering

| Note | Level | Summary |
| ---- | ----- | ------- |
| [Loop Engineering](loop-engineering.md) | 🟡 | Designing the loop an agent runs inside — agent vs. loop, the core cycle, 20 loop design patterns across 5 families, Karpathy's LOOPS.md field notes, and production controls. |

## 🗄️ Databases & Data

| Note | Level | Summary |
| ---- | ----- | ------- |
| [Database Indexing](database-indexing.md) | 🟡 | What indexes are, B+Trees vs. LSM-trees, clustered vs. non-clustered, composite & covering indexes, costs, and how to read `EXPLAIN`. |

## 🛠️ Software Craft & Code Quality

| Note | Level | Summary |
| ---- | ----- | ------- |
| [Cyclomatic Complexity](cyclomatic-complexity.md) | 🟢 | McCabe's metric — counting independent paths, how to calculate it, what the number means, and how to reduce it. |
| [CRAP Score](crap-score.md) | 🟡 | Change Risk Anti-Patterns — combining complexity and test coverage into one change-risk number, with the formula, thresholds, and the test-vs-refactor trade-off. |

---

### Adding a note

1. Write a `kebab-case-title.md` file in this folder.
2. Start with a level, reading time, and a one-line definition.
3. Include a table of contents, worked examples, and a **Go deeper** section that links to related [books/](../books/), [papers/](../papers/), and [courses/](../courses/).
4. Add a row to the table above under the right topic.
