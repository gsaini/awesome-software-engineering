# 📦 Containers & Orchestration — A Detailed Study Note

> **Level:** 🟡 Intermediate–Advanced · **Reading time:** ~24 min · **Prerequisites:** Linux/process basics; the [Infrastructure as Code](infrastructure-as-code-terraform.md), [Multi-Region](multi-region-deployment.md), and [Distributed Consensus](distributed-consensus.md) notes. The capstone of the infrastructure arc.

A **container** packages an application with *everything it needs to run* — code, runtime, libraries, config — into one portable, isolated unit that behaves the same on a laptop, in CI, and in production. **Orchestration** (Kubernetes) runs thousands of those containers across a fleet: scheduling, scaling, healing, networking, and rolling them out. This note covers both — how a container actually works under the hood, and how Kubernetes manages them at scale.

> **The core reframe, twice over:** (1) A container is *not* a lightweight VM — it's **just a Linux process** with its own isolated view (namespaces) and resource limits (cgroups), sharing the host kernel. There's no magic box. (2) Kubernetes is *not* a deploy script — it's a **desired-state reconcile loop**: you declare what you want, and controllers continuously converge reality to it. Both ideas — process isolation and declarative reconciliation — are things you've already met elsewhere in this library, now applied to running software.

## Table of contents

- [1. Containers vs. VMs](#1-containers-vs-vms)
- [2. How a container actually works](#2-how-a-container-actually-works)
- [3. Images & layers](#3-images--layers)
- [4. Docker & the OCI standard](#4-docker--the-oci-standard)
- [5. Why orchestration](#5-why-orchestration)
- [6. Kubernetes: the reconcile loop](#6-kubernetes-the-reconcile-loop)
- [7. Kubernetes core objects](#7-kubernetes-core-objects)
- [8. The ecosystem](#8-the-ecosystem)
- [9. When not to reach for Kubernetes](#9-when-not-to-reach-for-kubernetes)
- [10. Best practices & anti-patterns](#10-best-practices--anti-patterns)
- [11. Go deeper](#11-go-deeper)

---

## 1. Containers vs. VMs

Both isolate workloads, at different layers:

| | **Virtual Machine** | **Container** |
| - | ------------------- | ------------- |
| Virtualizes | **Hardware** (via a hypervisor) | The **OS** (shares the host kernel) |
| Contains | A full **guest OS** per VM | Just the app + its user-space deps |
| Size / start | GBs / minutes | MBs / milliseconds |
| Isolation | **Stronger** (separate kernel) | Weaker (shared kernel) |
| Density | Fewer per host | Many per host |

**Containers win on speed, size, and density; VMs win on isolation.** They're often combined — containers running *inside* VMs in the cloud (each cloud node is a VM; you pack many containers onto it).

> The key trade-off: a container shares the host kernel, so a kernel-level escape is a bigger deal than in a VM. For hostile multi-tenant workloads, people add a VM-like boundary (gVisor, Firecracker microVMs, Kata) to get "container speed, VM-ish isolation."

---

## 2. How a container actually works

A container is **three Linux features composed** — that's the whole trick:

- **Namespaces** — control what a process can *see* (isolation). Each container gets its own:
  - **PID** (its own process tree — PID 1 inside), **net** (own network interfaces/ports), **mount** (own filesystem view), **UTS** (own hostname), **IPC**, **user** (map container root to an unprivileged host user), **cgroup**.
- **cgroups (control groups)** — control what a process can *use* (resource limits): CPU shares, memory caps, I/O. This is what stops one container starving the others.
- **Union filesystem (overlayfs)** — stacks read-only image layers + a writable top layer into one filesystem view (§3).

> Put together: **a container = a normal process, given its own namespaced view of the system, capped by cgroups, rooted in a layered filesystem.** "Containerization" is Linux isolation primitives with good ergonomics — not virtualization. (This is the same "look under the hood and the magic disappears" payoff as the [git internals](git-internals.md) note.)

---

## 3. Images & layers

- **Image** = a read-only template (app + deps + runtime) — what you ship. Built from a **Dockerfile**, one instruction at a time, each producing a **layer**.
- **Layers are stacked and cached.** Each layer is content-addressed (a hash of its contents); unchanged layers are **reused across builds and shared between images**. Change one line late in the Dockerfile and only layers from there down rebuild.
- **Container** = a running instance of an image = the image's read-only layers + a thin **writable layer** on top (discarded when the container dies — containers are ephemeral).
- **Registries** (Docker Hub, GHCR, ECR, GCR) store and distribute images by name+tag (or by digest).

```dockerfile
# multi-stage build: heavy build tools stay out of the final image
FROM node:22 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci                 # cached unless package files change (order matters!)
COPY . .
RUN npm run build

FROM node:22-slim          # small runtime base
WORKDIR /app
COPY --from=build /app/dist ./dist
USER node                  # don't run as root
CMD ["node", "dist/server.js"]
```

> **Layers are content-addressed storage** — hash the content, key by hash, dedupe and cache. The *same idea* as [pnpm's store](pnpm-tips.md) and [git objects](git-internals.md), a fourth home for it. Dockerfile best practices all flow from the layer/cache model: order least-changing steps first, use small/multi-stage bases, add a `.dockerignore`, run as non-root, and pin versions. And an image is a supply-chain artifact — **scan it, generate an SBOM, sign it** ([security](security-fundamentals.md), OWASP A03).

---

## 4. Docker & the OCI standard

- **Docker** popularized containers with great tooling: `docker build` (image from Dockerfile), `docker run` (start a container), `docker push/pull` (registry), and **Docker Compose** (define & run a multi-container app locally with one YAML — great for dev).
- **OCI (Open Container Initiative)** standardized the **image format** and **runtime**, so containers aren't Docker-locked. Under the hood: a high-level runtime (**containerd**) manages images/lifecycle and calls a low-level runtime (**runc**) that actually sets up the namespaces/cgroups. **Podman** is a popular daemonless, rootless alternative to the Docker CLI.
- Kubernetes talks to runtimes via the **CRI** (Container Runtime Interface) — it dropped the Docker shim years ago and uses containerd/CRI-O directly.

---

## 5. Why orchestration

Running a few containers on one host: easy (`docker run`, Compose). Running **hundreds across a fleet of nodes** raises problems a single host can't answer:

- **Scheduling** — which node has room for this container?
- **Scaling** — add/remove replicas with load.
- **Self-healing** — a container crashes or a node dies → replace it.
- **Networking & discovery** — containers move; how do they find each other and receive traffic?
- **Rollouts** — deploy a new version with zero downtime, and roll back on failure.
- **Config & secrets**, storage, resource bin-packing.

An **orchestrator** automates all of it. **Kubernetes (K8s)** is the de-facto standard — descended from Google's **[Borg](../papers/)** (in your papers index).

---

## 6. Kubernetes: the reconcile loop

The single most important mental model: **Kubernetes is declarative and self-healing via control loops.**

- You submit **desired state** (YAML: "I want 3 replicas of this image, exposed on port 80").
- **Controllers** continuously compare **desired vs. actual** and take action to converge them. A pod died and now there are 2? The controller starts a 3rd. Forever.

```
You declare desired state ──► stored in etcd
        Controllers watch:  actual ≠ desired?  ──► act to converge  ──► (repeat forever)
```

This is **exactly the declarative, idempotent, desired-state model** of [Terraform](infrastructure-as-code-terraform.md) and [durable execution](durable-execution.md) — re-submitting the same manifest is a no-op; the system *continuously* reconciles rather than running once. Self-healing is just "the loop noticing actual drifted from desired."

**Architecture:**
- **Control plane** — the brain: **API server** (the front door, everything goes through it), **etcd** (the cluster's state store), **scheduler** (assigns pods to nodes), **controller manager** (runs the reconcile loops).
- **Worker nodes** — run the workloads: **kubelet** (agent that runs pods on the node), **kube-proxy** (networking), and a **container runtime** (containerd).

> **etcd is a [Raft](distributed-consensus.md) consensus store** — which is *why* a production control plane wants an **odd number (3/5) of members** to keep a quorum through a failure. Your consensus note is running inside every Kubernetes cluster.

---

## 7. Kubernetes core objects

The vocabulary you actually need:

| Object | What it is |
| ------ | ---------- |
| **Pod** | The smallest deployable unit — **one or more containers** sharing network (localhost) & storage. Ephemeral; you rarely create them directly. |
| **ReplicaSet** | Keeps *N* identical pods running (self-healing). |
| **Deployment** | Manages ReplicaSets → **rolling updates & rollbacks** of stateless apps. The workhorse you'll use most. |
| **Service** | A **stable virtual IP + DNS name** load-balancing across a set of pods (pods come and go; the Service endpoint stays). L4. |
| **Ingress** (/ Gateway API) | **L7 HTTP routing** into the cluster — host/path rules, TLS. |
| **ConfigMap / Secret** | Externalized config / sensitive values injected into pods. |
| **Namespace** | Virtual cluster partition for isolation & quotas. |
| **StatefulSet** | Pods with **stable identity & storage** (databases, queues). |
| **DaemonSet** | One pod per node (log/metrics agents). |
| **Job / CronJob** | Run-to-completion / scheduled tasks. |
| **PersistentVolume / Claim** | Durable storage decoupled from pod lifecycle. |

Two glue concepts: **labels & selectors** (key-value tags; a Service finds its pods by label selector — loose coupling), and **HPA** (Horizontal Pod Autoscaler — scale replicas on CPU/metrics). **Stateless pods behind a Service** is the [stateless-fleet + load-balancing](load-balancing-rate-limiting.md) principle, made native.

---

## 8. The ecosystem

Kubernetes is a platform to build on:

- **Helm** — the package manager ("charts" = templated, versioned K8s manifests).
- **Operators / CRDs** — extend K8s with your own resource types + controllers, applying the **reconcile loop to anything** (databases, certificates). Custom Resource Definitions + a controller = a domain-specific automation.
- **Service mesh** (Istio, Linkerd) — sidecar proxies add **mTLS, retries, traffic shaping, and observability** without app changes (ties to [service-to-service auth](service-to-service-auth.md) and [observability](observability.md)).
- **GitOps** (Argo CD, Flux) — the cluster's desired state lives in **git**; a controller continuously syncs the cluster to it. Declarative reconciliation + version control — [IaC](infrastructure-as-code-terraform.md) meets [git](git-internals.md), for what runs *in* the cluster.
- **Managed Kubernetes** (EKS, GKE, AKS) — the cloud runs the control plane; you get worker nodes. Usually **provisioned by [Terraform](infrastructure-as-code-terraform.md)** — closing the infra arc.

---

## 9. When not to reach for Kubernetes

K8s is powerful and **genuinely complex** — a real operational commitment. It's over-engineering for many apps:

- **A simple app / small team** → a PaaS (Fly.io, Render, App Runner, Cloud Run), or a single container on a VM, is far less to operate.
- **Event-driven / bursty** → serverless (Lambda, Cloud Functions) may fit better.
- **You don't need multi-service orchestration, autoscaling, or self-healing at fleet scale** → you probably don't need K8s yet.

> Same "use the simplest thing that works" instinct as [loop vs. graph](graph-engineering.md) and *don't-shard-prematurely*: adopt Kubernetes when you have the scale/complexity that justifies its overhead — not by default.

---

## 10. Best practices & anti-patterns

**Do**
- **Small, multi-stage images**, non-root user, pinned versions, `.dockerignore`; order Dockerfile steps for cache reuse.
- **Scan/sign images & track an SBOM** — the image is a supply-chain surface.
- **Set resource requests/limits** (cgroups) so pods don't starve each other; add liveness/readiness probes for self-healing.
- **Keep app pods stateless**; put state in StatefulSets/managed data stores.
- **Declare everything (GitOps)** — no `kubectl edit` in prod (that's ClickOps drift, K8s edition).
- **3/5-member control plane / etcd** for quorum; back up etcd.
- **Least privilege** — RBAC, network policies, non-root, drop capabilities.

**Avoid**
- **Treating a container as a VM** (SSHing in, running many services, mutable state in the writable layer).
- **`latest` tags** — unpinned, unreproducible deploys.
- **Running as root / huge base images** — attack surface + slow pulls.
- **No resource limits** → a noisy pod takes down a node.
- **Reaching for Kubernetes by default** for a simple app.
- **Imperative changes in prod** (`kubectl edit`) that git doesn't know about → drift.

---

## 11. Go deeper

This note ties the whole infrastructure arc together:

- 📝 **[Infrastructure as Code (Terraform)](infrastructure-as-code-terraform.md)** — provisions the cluster; K8s's reconcile loop is IaC's declarative model, continuous.
- 📝 **[Multi-Region](multi-region-deployment.md)** & **[Sharding](database-sharding-partitioning.md)** — what you deploy across; K8s is the substrate per region.
- 📝 **[Distributed Consensus](distributed-consensus.md)** — etcd is Raft; the control plane needs a quorum (odd members).
- 📝 **[Load Balancing](load-balancing-rate-limiting.md)** — Services (L4) & Ingress (L7); stateless pods behind a stable endpoint.
- 📝 **[Service-to-Service Auth](service-to-service-auth.md)** & **[Security](security-fundamentals.md)** — mTLS via mesh, image supply chain, RBAC, network policies.
- 📝 **[Git Internals](git-internals.md)** & **[pnpm](pnpm-tips.md)** — content-addressed image layers (hashing, 4th context); GitOps.
- 📝 **[Durable Execution](durable-execution.md)** — the reconcile loop's declarative-idempotent-converge model.
- 📄 **[Borg](../papers/)** — Kubernetes' ancestor, in your papers index.

### Primary references

- [Kubernetes documentation](https://kubernetes.io/docs/) — concepts & the object model.
- [Docker docs](https://docs.docker.com/) and the [OCI](https://opencontainers.org/) specs.
- Burns, Beda & Hightower, *Kubernetes: Up & Running*; the [Borg paper](../papers/).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
