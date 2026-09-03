# 🏗️ Infrastructure as Code (Terraform) — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~20 min · **Prerequisites:** basic cloud (VMs, networks, managed services). Pairs with the [Multi-Region](multi-region-deployment.md), [Security Fundamentals](security-fundamentals.md), and [Constraint-Driven Development](constraint-driven-development.md) notes.

**Infrastructure as Code (IaC)** means provisioning and managing infrastructure — servers, networks, databases, DNS, IAM — through **version-controlled configuration files** instead of clicking around a console ("ClickOps"). **Terraform** (and its open-source fork **OpenTofu**) is the dominant tool: you *declare* the infrastructure you want, and it figures out how to make reality match. This note covers the model, the state file (where all the pain and power lives), the workflow, and the practices that keep it safe.

> **The core reframe:** ClickOps produces infrastructure nobody can reproduce, review, or explain — a snowflake you're afraid to touch. IaC makes infrastructure a **declarative artifact in git**: reviewable in a PR, reproducible in any environment, and diff-able before you change anything. The unit of work shifts from *"do these steps in the console"* to *"describe the desired end state; let the tool converge to it."* It's [constraint-driven development](constraint-driven-development.md) applied to infrastructure — the config *is* the spec, and it's enforced.

## Table of contents

- [1. Why IaC](#1-why-iac)
- [2. Declarative vs. imperative](#2-declarative-vs-imperative)
- [3. Core Terraform concepts](#3-core-terraform-concepts)
- [4. The workflow: write → plan → apply](#4-the-workflow-write--plan--apply)
- [5. State — where the power and pain live](#5-state--where-the-power-and-pain-live)
- [6. Drift](#6-drift)
- [7. Modules & environments](#7-modules--environments)
- [8. GitOps & policy as code](#8-gitops--policy-as-code)
- [9. The landscape](#9-the-landscape)
- [10. Best practices & anti-patterns](#10-best-practices--anti-patterns)
- [11. Go deeper](#11-go-deeper)

---

## 1. Why IaC

- **Reproducibility** — spin up an identical environment (dev/stage/prod, or a new [region](multi-region-deployment.md)) from the same code. No more "prod is subtly different and nobody knows why."
- **Version control & review** — infra changes go through git and PR review, with history and blame ([git internals](git-internals.md)).
- **Automation** — provision in CI/CD, not by hand.
- **Documentation as code** — the config *is* the accurate, current description of your infra.
- **Disaster recovery** — rebuild from code (the multi-region DR story depends on this).
- **Consistency** — the same module deploys the same way everywhere, eliminating config drift between environments.

---

## 2. Declarative vs. imperative

- **Declarative (Terraform):** you describe the **desired end state** ("I want 3 servers, this network, that database"); the tool computes the *diff* from current reality and the steps to get there. You don't write "how."
- **Imperative (shell scripts, partly Ansible):** you write the **steps** to execute in order.

Terraform is declarative and **idempotent** — running `apply` when reality already matches the config is a **no-op**. Re-applying converges to the desired state rather than duplicating resources. (Idempotency yet again — the recurring reliability principle across this library: apply-until-converged is safe to repeat.)

> A related distinction: **provisioning** (create the infra — Terraform) vs. **configuration management** (set up software *on* the infra — Ansible, Chef, Puppet). Terraform provisions; it's often paired with a config-management or image-baking (Packer) step, or immutable-infrastructure containers.

---

## 3. Core Terraform concepts

- **Providers** — plugins that talk to a platform's API (AWS, GCP, Azure, Kubernetes, Cloudflare, Datadog, …). You configure a provider, then declare its resources. **Pin provider versions** for reproducibility.
- **Resources** — the infra objects you manage (`aws_instance`, `aws_s3_bucket`, `google_sql_database`). The building blocks.
- **The dependency graph** — Terraform parses your config, infers dependencies (resource A references B's output → B first), and builds a **DAG**, then provisions in the right order and in parallel where possible. (It's the [graph](graph-engineering.md) again — a resource DAG, like a build system's.)
- **Variables / outputs / locals** — parameterize inputs, expose values (an output of one module feeds another), and name intermediate expressions.
- **Data sources** — *read* existing infra (a VPC you didn't create) to reference it without managing it.
- **State** — the map from your config to real-world resources (§5).

Config is written in **HCL** (HashiCorp Configuration Language):

```hcl
provider "aws" { region = "us-east-1" }

resource "aws_s3_bucket" "assets" {
  bucket = "myapp-assets-prod"
}

resource "aws_s3_bucket_versioning" "assets" {
  bucket = aws_s3_bucket.assets.id   # this reference creates a dependency edge
  versioning_configuration { status = "Enabled" }
}
```

---

## 4. The workflow: write → plan → apply

The loop that makes IaC safe:

```
terraform init      # download providers, configure the backend
terraform plan      # ⭐ preview the diff: what will be created / changed / destroyed
terraform apply     # converge reality to the config (after you review the plan)
terraform destroy   # tear it all down
```

**`plan` is the killer feature** — a **dry run** showing exactly what will change *before* it happens: `+ create`, `~ update`, `- destroy`. Reviewing the plan is where you catch "wait, why is it *destroying* the database?" — because some changes **force replacement** (a mutation the provider can't do in place recreates the resource). **Always read the plan**, especially for any `- destroy` / `-/+ replace` on stateful resources.

> The plan is a **constraint gate**: it turns "apply and hope" into "see the exact diff, approve it, then apply." Never `apply` a plan you haven't read.

---

## 5. State — where the power and pain live

Terraform keeps a **state file** (`terraform.tfstate`) — a JSON map linking your config's resources to their real-world IDs. It's how Terraform knows what it already manages, computes diffs, and tracks metadata. **Most Terraform trouble is state trouble.**

Key truths:
- **State is the source of truth for what Terraform manages.** If a resource isn't in state, Terraform doesn't know it exists (→ `terraform import` to adopt existing infra).
- **Use a remote backend for any team** — S3 + DynamoDB lock, GCS, Terraform Cloud, etc. Local state on one laptop doesn't work for collaboration.
- **State locking is essential** — two `apply`s at once corrupt state (a [concurrency](concurrency-parallelism.md) race on a shared file). Remote backends provide a lock.
- **Never hand-edit state** — use `terraform state` subcommands (`mv`, `rm`, `import`). Manual edits corrupt it.
- **State contains secrets** — resource attributes (DB passwords, keys) land in state in plaintext. **Encrypt the backend, restrict access, treat state as sensitive** ([security](security-fundamentals.md)).
- **Keep state files small / blast-radius-scoped** — split infra into multiple states (per environment/component) so one `apply` can't wreck everything, and plans stay fast.

---

## 6. Drift

**Drift** = the real infrastructure has diverged from what the state/config says — usually because someone made a manual change in the console (ClickOps), or an out-of-band process modified a resource.

- `terraform plan` **detects drift** by refreshing state against reality and showing the difference.
- `apply` then **reconciles** — reverting the manual change back to what the code declares (or you update the code to match, if the change was intended).
- **The rule that prevents drift: no ClickOps on Terraform-managed resources.** Once infra is IaC, *all* changes go through the code. A manual "quick fix" in the console is a future outage when the next `apply` silently reverts it.

---

## 7. Modules & environments

- **Modules** — reusable, parameterized bundles of resources (a "vpc" module, a "web-service" module). They're the DRY unit: write once, instantiate many times with different inputs. Compose small modules into larger ones; publish to a registry.
- **Environments (dev/stage/prod)** — keep them consistent by using the *same modules* with different variable values and **separate state** (separate backends/directories, or workspaces). Separate state per environment is safer than workspaces for prod isolation (a prod mistake can't touch dev's state and vice versa).

> The same module producing dev, staging, and prod is what *guarantees* they match — the reproducibility payoff. Divergence between environments is almost always a symptom of *not* sharing the module.

---

## 8. GitOps & policy as code

IaC's endgame is **infrastructure through pull requests**:

- **GitOps flow** — a PR changes the config → CI runs `terraform plan` and posts the diff on the PR → a human reviews the plan → merge triggers `apply`. Tools: Atlantis, Terraform Cloud/Enterprise, Spacelift, Env0.
- **Policy as code** — automated guardrails that **reject** non-compliant plans *before* apply: **Sentinel** (HashiCorp), **OPA/Conftest**, or `tflint`/`checkov`/`tfsec`. E.g. "no public S3 buckets," "all resources must be tagged," "no untagged prod changes."

> Policy-as-code is [constraint-driven development](constraint-driven-development.md) for infrastructure — the same *fitness-function / executable-constraint* idea as [dependency-cruiser](dependency-cruiser.md) for code architecture: encode the rule so a violation **fails the pipeline**, not "someone notices in review." And it's the same **shift-left** instinct as tests and [commit linting](commitlint.md).

---

## 9. The landscape

- **Terraform** — the incumbent. In **Aug 2023 HashiCorp relicensed it** from MPL to the **BUSL** (Business Source License); the community forked the last open version as **OpenTofu** (now under the Linux Foundation, MPL, largely drop-in compatible). Know both exist and why.
- **Pulumi** — IaC in **real programming languages** (TypeScript/Python/Go) instead of HCL — loops/abstractions in a familiar language, at the cost of more power to shoot yourself with.
- **AWS CloudFormation / CDK, Azure Bicep, Google Deployment Manager** — cloud-native, single-provider IaC (CDK/Bicep add real languages).
- **Ansible / Chef / Puppet** — configuration management (software *on* servers), often complementary to Terraform's provisioning.
- **Crossplane** — IaC via Kubernetes-style control loops (continuous reconciliation, not one-shot apply).

Terraform/OpenTofu win on **multi-cloud** breadth and maturity; the others trade breadth for native integration or a real language.

---

## 10. Best practices & anti-patterns

**Do**
- **Remote state + locking**, always, for teams; **encrypt** it (it holds secrets).
- **Split state by environment/component** to bound blast radius and keep plans fast.
- **Always read `plan`** before `apply` — watch for `destroy`/`replace` on stateful resources.
- **Modularize** and reuse; drive environments from the same modules + different variables.
- **Pin provider & module versions**; commit the lock file.
- **Policy-as-code in CI** (OPA/Sentinel/tfsec) + PR-based `plan`.
- **Least-privilege credentials** for the Terraform runner; **no secrets hardcoded** in config (use a secrets manager / vars).

**Avoid**
- **ClickOps on managed resources** → drift → a future `apply` reverts it.
- **Local state / no locking** on a team → corruption.
- **Hand-editing `tfstate`** → corruption (use `state` subcommands).
- **One giant state file** for everything → slow plans, huge blast radius.
- **Secrets in `.tf` files or committed state.**
- **`apply` without reading the plan** — especially anything that replaces a database.
- **Unpinned versions** → "worked yesterday, broke today."

---

## 11. Go deeper

Related material in this library:

- 📝 **[Multi-Region Deployment](multi-region-deployment.md)** & **[Database Sharding](database-sharding-partitioning.md)** — IaC is how you provision that infra reproducibly (and rebuild it for DR).
- 📝 **[Constraint-Driven Development](constraint-driven-development.md)** — policy-as-code is executable constraints for infra; the `plan` is a diff-gate.
- 📝 **[dependency-cruiser](dependency-cruiser.md)** — the fitness-function idea, for code; policy-as-code is its infra twin.
- 📝 **[Security Fundamentals](security-fundamentals.md)** — state holds secrets; least-privilege runner creds; no ClickOps.
- 📝 **[Git Internals](git-internals.md)** — infra in version control; GitOps.
- 📝 **[Graph Engineering / DSA](graph-engineering.md)** — Terraform builds a resource dependency DAG.
- 📄 **[Borg](../papers/)** — the ancestor of declarative, desired-state infra management.

### Primary references

- [Terraform docs](https://developer.hashicorp.com/terraform) and [OpenTofu](https://opentofu.org/) — the tool and its fork.
- Yevgeniy Brikman, *Terraform: Up & Running* — the standard book.
- [Open Policy Agent](https://www.openpolicyagent.org/) / Sentinel — policy as code.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
