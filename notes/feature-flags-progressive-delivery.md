# 🚦 Feature Flags & Progressive Delivery — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~20 min · **Prerequisites:** the [Observability](observability.md), [Containers & Orchestration](containers-orchestration.md), and [Serialization & Schema Evolution](serialization-schema-evolution.md) notes.

**Progressive delivery** is the practice of releasing change **gradually and reversibly** — to a slice of traffic, a set of users, or one region at a time — watching real signals, and widening only when it's safe. **Feature flags** are its core mechanism: a runtime switch that turns a code path on or off **without a redeploy**. Together they turn every release from a big-bang event into a controlled, observable, undoable experiment.

> **The core reframe: *deploy* and *release* are two different things.** **Deploying** puts code on production servers — a *technical* event. **Releasing** exposes that behaviour to users — a *business* decision. Most outages come from fusing the two, so that shipping code *is* turning it on for everyone. Separate them and you can deploy continuously (dark, behind a flag) and release deliberately (to 1%, then 10%, then everyone) — with a kill switch the whole way.

## Table of contents

- [1. Deploy ≠ release](#1-deploy--release)
- [2. Feature flags](#2-feature-flags)
- [3. The four kinds of flag](#3-the-four-kinds-of-flag)
- [4. Progressive delivery strategies](#4-progressive-delivery-strategies)
- [5. The feedback loop: gate on signals](#5-the-feedback-loop-gate-on-signals)
- [6. Database & schema changes — the hard part](#6-database--schema-changes--the-hard-part)
- [7. Flag debt — the dark side](#7-flag-debt--the-dark-side)
- [8. Tooling & the OpenFeature standard](#8-tooling--the-openfeature-standard)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. Deploy ≠ release

| | **Deploy** | **Release** |
| - | ---------- | ----------- |
| Is | Code reaches production | Users experience the new behaviour |
| Owned by | Engineering | Product / business |
| Reversal | Redeploy / roll back (minutes) | Flip a flag (**seconds**) |
| Risk | Infra-level | User-facing |

Decoupling them is what enables **trunk-based development**: merge unfinished work to `main` behind an *off* flag, deploy continuously, and never keep a long-lived feature branch ([git internals](git-internals.md) — long branches compound merge pain). It also turns **rollback** from "revert, rebuild, redeploy" (minutes, risky) into "flip a switch" (seconds, safe).

---

## 2. Feature flags

A feature flag is a conditional evaluated at runtime:

```ts
if (flags.isEnabled('new-checkout', { userId, region, plan })) {
  return renderNewCheckout();
}
return renderOldCheckout();
```

What makes it more than an `if`:

- **Targeting rules** — enable by user, segment, plan, region, device, or a **percentage** of traffic.
- **Sticky bucketing** — percentage rollouts hash the user (`hash(userId) % 100 < 10`) so the **same user consistently** sees the same variant. Random-per-request would flip a user between old and new on every click.
- **Local evaluation** — good SDKs fetch rules once, cache them, and evaluate **in-process**. A flag check must never be a network call on your hot path.
- **Server-side vs. client-side** — server-side flags keep rules private; client-side flags ship rules/values to the browser, so **never put secrets or entitlement enforcement in a client-side flag** (it's UI, not security — see [authz](security-fundamentals.md)).

> ⚠️ **Decide the default for when the flag service is unreachable.** That default *is* your behaviour during an outage. For a new risky feature: **fail safe → off**. For a kill switch protecting a dependency: the default should be the *safe* state. This is the [fail-open vs. fail-closed](security-fundamentals.md) decision — make it deliberately, per flag.

---

## 3. The four kinds of flag

Pete Hodgson's taxonomy (martinfowler.com) is the one to know — flags differ hugely in **lifespan** and **dynamism**, and treating them the same is how flag debt starts:

| Kind | Purpose | Lifespan | Example |
| ---- | ------- | -------- | ------- |
| **Release** | Hide incomplete/unreleased work; enable trunk-based dev | **Short** (days–weeks) | `new-checkout` |
| **Experiment** | A/B & multivariate tests | Short–medium (the test's duration) | `pricing-page-v2` |
| **Ops** | Kill switches, circuit breakers, graceful degradation | **Can be long-lived** | `disable-recommendations` |
| **Permission** | Entitlements, beta access, premium tiers | **Long-lived** | `beta-users`, `enterprise-sso` |

The key discipline: **release flags must die.** Ops and permission flags may legitimately live for years; release flags left in place are pure liability (§7).

---

## 4. Progressive delivery strategies

Several complementary techniques — some move **infrastructure**, some move **features**:

**Rolling update** — replace instances a few at a time (the Kubernetes Deployment default). Cheap, but old and new versions run **simultaneously**, and rollback is another rollout.

**Blue-green** — run two identical environments; switch the router from blue (old) to green (new) at once.
- ✅ **Instant rollback** (switch back). ❌ **2× infrastructure** during cutover, and the **database is shared**, so the schema must work for both (§6).

**Canary** — route a **small percentage** of traffic (1% → 5% → 25% → 100%) to the new version, compare its metrics against the baseline, and promote or abort.
- ✅ Limits blast radius to the canary slice. Automated with **Argo Rollouts**, **Flagger** (K8s), or **Kayenta**-style automated canary analysis.

**Dark launch / shadow traffic** — run the new code path on real traffic (or mirror requests to it) but **discard its results**. Tests performance and correctness under real load with zero user impact.

**Ring deployments** — widen in concentric rings: internal staff → early adopters → one region → everyone. The [multi-region](multi-region-deployment.md) "deploy region-by-region, never globally at once" rule, generalised.

**Percentage rollout via flags** — the feature-level twin of canary: the code is already deployed everywhere; the *flag* widens exposure.

> **Canary vs. flag rollout:** canary moves a whole **version** across **traffic**; a flag moves a single **feature** across **users**. They compose — canary the deploy for infra safety, then flag-roll the feature for product safety.

---

## 5. The feedback loop: gate on signals

Progressive delivery is **only as good as the signals that gate it.** Widening a rollout without watching metrics isn't progressive delivery — it's a slow big-bang.

- **Define promotion criteria up front** — error rate, latency p95/p99, saturation, *and* a business metric (conversion, checkout success).
- **Compare canary vs. baseline**, not canary vs. an absolute number — the baseline absorbs normal daily variation.
- **Automate the rollback** — abort when the canary burns its [SLO / error budget](observability.md) faster than the baseline. A human watching a dashboard at 2am is not a control.
- **Bake time** — hold each stage long enough to catch slow failures (memory leaks, cache warm-up, daily batch jobs).

> This is the [observability](observability.md) note's SLOs and error budgets turned into a **deployment gate**: the error budget doesn't just describe reliability, it decides whether the rollout continues.

---

## 6. Database & schema changes — the hard part

Code rolls back in seconds. **Data doesn't.** And every strategy above has old and new code running **at the same time against the same database**, so a schema change must be compatible with *both*.

The answer is **expand / contract** (a.k.a. *parallel change*):

```
1. EXPAND    add the new column/table (nullable, additive) — old code ignores it
2. MIGRATE   new code writes BOTH old and new; backfill existing rows
3. SWITCH    new code reads from the new shape (behind a flag → reversible)
4. CONTRACT  once nothing reads the old shape, remove it — the only irreversible step, done last
```

Every step is independently deployable and reversible until step 4. This is the [schema-evolution](serialization-schema-evolution.md) rule — **additive first, remove last** — applied to databases, and the same provider-before-consumer discipline as the [OpenSpec cross-repo rollout](openspec-multi-repo.md).

---

## 7. Flag debt — the dark side

Flags are cheap to add and easy to forget. Left unchecked they become a serious liability:

- **Combinatorial explosion** — *n* flags ⇒ up to **2ⁿ** code paths. You cannot test them all; untested combinations are where production surprises live.
- **Dead code** — once a flag is at 100% forever, the old branch is dead weight.
- **Cognitive load** — every `if (flag)` makes code harder to read and reason about.

**The cautionary tale — Knight Capital (2012):** a deployment reached 7 of 8 servers; a **repurposed flag** then reactivated long-dead code ("Power Peg") on the eighth. The firm lost **more than $460 million in about 45 minutes** and did not survive as an independent company. Two lessons: **never reuse a flag name**, and **delete dead code paths** rather than leaving them one flag-flip away from execution.

Hygiene that works:
- **Every flag has an owner and an expiry date** at creation.
- **Remove release flags promptly** after reaching 100% — make "delete the flag" part of the feature's definition of done.
- **Alert on stale flags** (unchanged for N weeks, or evaluating to one value everywhere); dead-code tooling like [Knip](knip.md) helps with the leftovers.
- **Test the default path and the realistic combinations**, not all 2ⁿ.

---

## 8. Tooling & the OpenFeature standard

- **Flag platforms:** LaunchDarkly, Statsig, Split, Flagsmith, GrowthBook, ConfigCat; **Unleash** and Flagsmith/GrowthBook are open source; cloud-native options like AWS AppConfig.
- **OpenFeature** (CNCF) — a **vendor-neutral flag API**. Code against the OpenFeature SDK, plug in any provider behind it — the same decouple-from-vendor move as [OpenTelemetry](observability.md) for telemetry.
- **Progressive rollout controllers:** **Argo Rollouts** and **Flagger** (Kubernetes canary/blue-green with metric analysis), Spinnaker/Kayenta.
- **Build vs. buy:** a config-file boolean is fine for a handful of flags. Once you need targeting, percentage rollouts, audit trails, and a kill switch that works during an incident, a real platform pays for itself.

---

## 9. Best practices & anti-patterns

**Do**
- **Separate deploy from release**; deploy dark, release deliberately.
- **Classify every flag** (release / experiment / ops / permission) and set lifespan accordingly.
- **Sticky, hashed bucketing** for percentage rollouts.
- **Evaluate locally**; choose a safe default for flag-service outages.
- **Gate every rollout stage on metrics**, compare against baseline, and **auto-roll back**.
- **Expand / contract** for schema changes; keep every step reversible until the last.
- **Owner + expiry on every flag**; delete release flags when done.

**Avoid**
- **Big-bang releases** where deploy = release for 100% of users.
- **Reusing or repurposing a flag name** (Knight Capital).
- **Leaving release flags at 100% forever** — flag debt and dead code.
- **Security or entitlement checks in client-side flags.**
- **A flag check that makes a network call** on the request path.
- **Widening a rollout without watching signals.**
- **Breaking schema changes deployed alongside the code** — the old version, still running, will fail.

---

## 10. Go deeper

Related material in this library:

- 📝 **[Observability](observability.md)** — SLOs & error budgets become the rollout gate; OpenTelemetry is OpenFeature's sibling standard.
- 📝 **[Multi-Region Deployment](multi-region-deployment.md)** — region-by-region deploys and cell-based blast-radius control are ring deployments.
- 📝 **[Containers & Orchestration](containers-orchestration.md)** — rolling updates, and Argo Rollouts/Flagger on Kubernetes.
- 📝 **[Serialization & Schema Evolution](serialization-schema-evolution.md)** — additive-first compatibility, the basis of expand/contract.
- 📝 **[Testing Strategy](testing-strategy.md)** — testing flagged paths without testing every combination.
- 📝 **[Security Fundamentals](security-fundamentals.md)** — fail-open vs. fail-closed defaults; why client-side flags aren't authorization.
- 📝 **[Knip](knip.md)** & **[Git Internals](git-internals.md)** — dead-code cleanup, and the trunk-based development flags enable.

### Primary references

- Pete Hodgson, [*"Feature Toggles (aka Feature Flags)"*](https://martinfowler.com/articles/feature-toggles.html), martinfowler.com — the four-category taxonomy.
- Danilo Sato, [*"Parallel Change"*](https://martinfowler.com/bliki/ParallelChange.html) — expand/contract.
- [OpenFeature](https://openfeature.dev/) and [Argo Rollouts](https://argoproj.github.io/rollouts/) documentation.
- SEC, *In the Matter of Knight Capital Americas LLC* (2013) — the Knight Capital post-mortem.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
