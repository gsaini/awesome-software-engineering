# 📄 Papers

Classic and modern academic papers that shaped how we build software. Links go to the canonical source. Levels: 🟢 Approachable · 🟡 Intermediate · 🔴 Dense.

> These are foundational reads. Don't aim to understand everything on the first pass — revisit as you grow.

## 🌐 Distributed Systems

| Paper | Year | Level | Why it matters |
| ----- | ---- | ----- | -------------- |
| [The Google File System](https://research.google/pubs/the-google-file-system/) | 2003 | 🟡 | Blueprint for distributed storage at scale. |
| [MapReduce](https://research.google/pubs/mapreduce-simplified-data-processing-on-large-clusters/) | 2004 | 🟡 | Programming model that launched big data. |
| [Dynamo: Amazon's Highly Available Key-value Store](https://www.allthingsdistributed.com/files/amazon-dynamo-sosp2007.pdf) | 2007 | 🔴 | Eventual consistency, quorums, and AP systems. |
| [Spanner: Google's Globally-Distributed Database](https://research.google/pubs/spanner-googles-globally-distributed-database/) | 2012 | 🔴 | TrueTime and external consistency across the globe. |
| [In Search of an Understandable Consensus Algorithm (Raft)](https://raft.github.io/raft.pdf) | 2014 | 🟡 | Consensus you can actually understand and implement. |
| [Paxos Made Simple](https://lamport.azurewebsites.net/pubs/paxos-simple.pdf) | 2001 | 🔴 | The foundational (if famously tricky) consensus protocol. |
| [Time, Clocks, and the Ordering of Events](https://lamport.azurewebsites.net/pubs/time-clocks.pdf) | 1978 | 🟡 | Lamport clocks — how to reason about order without a global clock. |

## 🗄️ Databases & Data

| Paper | Year | Level | Why it matters |
| ----- | ---- | ----- | -------------- |
| [The Log: What every software engineer should know about real-time data](https://engineering.linkedin.com/distributed-systems/log-what-every-software-engineer-should-know-about-real-time-datas-unifying) | 2013 | 🟡 | The log as the backbone of data systems. |
| [Bigtable: A Distributed Storage System](https://research.google/pubs/bigtable-a-distributed-storage-system-for-structured-data/) | 2006 | 🔴 | Wide-column storage at planet scale. |
| [Kafka: a Distributed Messaging System](https://notes.stephenholiday.com/Kafka.pdf) | 2011 | 🟡 | Design of the now-ubiquitous event log. |

## ⚙️ Systems & Infrastructure

| Paper | Year | Level | Why it matters |
| ----- | ---- | ----- | -------------- |
| [Large-scale cluster management at Google with Borg](https://research.google/pubs/large-scale-cluster-management-at-google-with-borg/) | 2015 | 🔴 | The ancestor of Kubernetes. |
| [Dapper, a Large-Scale Distributed Systems Tracing Infrastructure](https://research.google/pubs/dapper-a-large-scale-distributed-systems-tracing-infrastructure/) | 2010 | 🟡 | The origin of distributed tracing. |

## 🤖 AI & Machine Learning

| Paper | Year | Level | Why it matters |
| ----- | ---- | ----- | -------------- |
| [Attention Is All You Need](https://arxiv.org/abs/1706.03762) | 2017 | 🔴 | The Transformer — foundation of modern LLMs. |
| [Language Models are Few-Shot Learners (GPT-3)](https://arxiv.org/abs/2005.14165) | 2020 | 🔴 | Scaling laws and in-context learning. |
| [Chain-of-Thought Prompting](https://arxiv.org/abs/2201.11903) | 2022 | 🟡 | Reasoning by prompting — practical and influential. |
| [ReAct: Synergizing Reasoning and Acting in LLMs](https://arxiv.org/abs/2210.03629) | 2022 | 🟡 | The pattern behind modern tool-using agents. |

---

### Adding a paper

Add a row under the right topic: title (linked to canonical source), year, level badge, and a one-line *why it matters*. Prefer the authoritative URL (arXiv, ACM, or the lab's publication page).
