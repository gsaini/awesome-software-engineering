<div align="center">

# 📚 Awesome Software Engineering

**A curated, opinionated library for studying software engineering — whitepapers, books, papers, courses, and roadmaps, organized by topic so you always know what to read next.**

![Awesome](https://img.shields.io/badge/Awesome-curated-FC60A8?style=for-the-badge&logo=awesomelists&logoColor=white)
![Whitepapers](https://img.shields.io/badge/Whitepapers-collected-4F46E5?style=for-the-badge&logo=readthedocs&logoColor=white)
![Books](https://img.shields.io/badge/Books-recommended-0EA5E9?style=for-the-badge&logo=bookstack&logoColor=white)
![Papers](https://img.shields.io/badge/Papers-classic%20%26%20modern-16A34A?style=for-the-badge&logo=googlescholar&logoColor=white)
![PRs Welcome](https://img.shields.io/badge/PRs-welcome-22C55E?style=for-the-badge&logo=github&logoColor=white)
![License](https://img.shields.io/badge/License-CC%20BY%204.0-EAB308?style=for-the-badge&logo=creativecommons&logoColor=white)

</div>

---

## ✨ What this is

This repository is a **structured study path** for software engineers — from fundamentals to the frontier of agentic engineering. Instead of a flat dump of links, everything is:

- **Organized by topic** — find what's next without guessing.
- **Annotated** — every entry says *why* it's worth your time and *who* it's for.
- **Leveled** — 🟢 Beginner · 🟡 Intermediate · 🔴 Advanced.
- **Mixed media** — whitepapers (PDFs in-repo), books, academic papers, courses, and roadmaps.

> If you read one thing per week from the path below, you will have a genuinely strong foundation within a year.

## 🗂️ Repository layout

```
awesome-software-engineering/
├── whitepapers/          # Industry whitepapers (PDFs stored in-repo), grouped by domain
│   ├── ai-agentic-engineering/
│   ├── distributed-systems/
│   ├── databases-data/
│   └── systems-infra/
├── books/                # Curated, annotated book recommendations by topic
├── papers/               # Classic & modern academic papers (links + notes)
├── courses/              # Free & paid courses, lectures, video series
├── notes/                # Original, self-authored deep-dive study notes
├── roadmaps/             # Step-by-step learning paths from zero to senior
└── resources/            # Blogs, newsletters, podcasts, and reference sites
```

Each folder has its own `README.md` acting as a mini-index. Start there.

## 🚀 Start here

| If you are…                        | Begin with                                                            |
| ---------------------------------- | -------------------------------------------------------------------- |
| New to software engineering        | [roadmaps/](roadmaps/) → then [books/](books/) foundations           |
| A working dev leveling up          | [books/](books/) craft & system design → [papers/](papers/) classics |
| Curious about AI-assisted coding   | [whitepapers/ai-agentic-engineering/](whitepapers/ai-agentic-engineering/) |
| Preparing for system-design rounds | [books/](books/) architecture → [papers/](papers/) distributed systems |

## 📑 Table of contents

- [📘 Whitepapers](#-whitepapers)
- [📗 Books](books/)
- [📄 Papers](papers/)
- [🎓 Courses](courses/)
- [📝 Study Notes](notes/)
- [🗺️ Roadmaps](roadmaps/)
- [🔗 Resources](resources/)
- [🤝 Contributing](#-contributing)

## 📘 Whitepapers

In-repo PDFs you can read offline. See [whitepapers/](whitepapers/) for the full index.

| Title | Domain | Authors | Level |
| ----- | ------ | ------- | ----- |
| [The New SDLC With Vibe Coding](whitepapers/ai-agentic-engineering/the-new-sdlc-with-vibe-coding.pdf) | AI / Agentic Engineering | Addy Osmani, Shubham Saboo, Sokratis Kartakis (Google) | 🟡 |

## 🧭 Topic map

A high-level view of the domains this library covers. Click through to the curated lists.

- **Foundations** — data structures, algorithms, CS basics → [books/](books/) · [roadmaps/](roadmaps/) · [notes/data-structures-algorithms.md](notes/data-structures-algorithms.md) · [notes/concurrency-parallelism.md](notes/concurrency-parallelism.md)
- **System Design & Architecture** → [books/](books/) · [papers/](papers/)
- **Distributed Systems** → [whitepapers/distributed-systems/](whitepapers/distributed-systems/) · [papers/](papers/) · [notes/message-queues-event-driven.md](notes/message-queues-event-driven.md) · [notes/consistency-models-cap.md](notes/consistency-models-cap.md) · [notes/distributed-consensus.md](notes/distributed-consensus.md)
- **Databases & Data** → [whitepapers/databases-data/](whitepapers/databases-data/) · [books/](books/) · [notes/database-indexing.md](notes/database-indexing.md) · [notes/database-transactions-isolation.md](notes/database-transactions-isolation.md) · [notes/caching-strategies.md](notes/caching-strategies.md)
- **AI & Agentic Engineering** → [whitepapers/ai-agentic-engineering/](whitepapers/ai-agentic-engineering/) · [notes/loop-engineering.md](notes/loop-engineering.md) · [notes/building-agent-evaluators.md](notes/building-agent-evaluators.md)
- **DevOps, SRE & Reliability** → [books/](books/) · [resources/](resources/) · [notes/observability.md](notes/observability.md) · [notes/load-balancing-rate-limiting.md](notes/load-balancing-rate-limiting.md)
- **Security** → [books/](books/) · [papers/](papers/) · [notes/security-fundamentals.md](notes/security-fundamentals.md) · [notes/service-to-service-auth.md](notes/service-to-service-auth.md)
- **Software Craft & Practices** — clean code, testing, refactoring → [books/](books/) · [notes/cyclomatic-complexity.md](notes/cyclomatic-complexity.md) · [notes/crap-score.md](notes/crap-score.md) · [notes/testing-strategy.md](notes/testing-strategy.md)
- **API & Interface Design** → [notes/api-design.md](notes/api-design.md)
- **Career & Leadership** → [books/](books/) · [resources/](resources/)

## 🤝 Contributing

Suggestions are very welcome — a great link, a missing classic, a better explanation. See **[CONTRIBUTING.md](CONTRIBUTING.md)** for the format and quality bar. In short:

1. Add the resource to the right folder's `README.md` (or drop a PDF in the matching `whitepapers/` subfolder).
2. Include a one-line *why it matters* and a level badge.
3. Open a PR. Keep it focused.

## ⚖️ Disclaimer

> [!IMPORTANT]
> **This is a non-commercial collection for learning and educational purposes only.**
>
> - 📖 **Study only — not for sale.** Nothing here is sold, monetized, or redistributed for profit. No part of this repository may be used for commercial gain.
> - © **All rights belong to the original authors and publishers.** The whitepapers, books, and papers referenced or stored here are the intellectual property of their respective creators. Full credit goes to them.
> - 🔗 **Support the creators.** Where a resource is available for purchase (books, courses), please buy it from the official source. Links are provided for that purpose.
> - 🧹 **Takedown on request.** If you are a rights holder and want any material removed, open an issue and it will be taken down promptly, no questions asked.

## 📜 License

The **curation** in this repository (the lists, annotations, organization, and original writing) is licensed under [CC BY 4.0](LICENSE).

The **PDFs and linked third-party materials** remain the property of their respective authors and publishers and are **not** covered by that license — they are included strictly for personal study and reference under the disclaimer above.

---

<div align="center">
<sub>Built as a personal study library. ⭐ Star it to follow along.</sub>
</div>
