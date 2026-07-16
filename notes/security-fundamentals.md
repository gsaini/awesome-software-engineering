# 🔐 Security Fundamentals — A Detailed Study Note

> **Level:** 🟢 Beginner–Intermediate · **Reading time:** ~22 min · **Prerequisites:** you build software that touches a network. That's it — this one is for everyone.

Security isn't a feature you add at the end; it's a **property of how you build**. This note covers the mindset, the durable principles, a structured way to find threats (threat modeling), the vulnerability classes that actually cause breaches (**OWASP Top 10:2025**), and the practices that prevent them.

> **The core reframe:** every other part of engineering asks *"does it work?"* Security asks *"can it be made to do something it shouldn't?"* You're not designing for the average user — you're designing against a motivated adversary who reads your source code.

## Table of contents

- [1. The mindset](#1-the-mindset)
- [2. The durable principles](#2-the-durable-principles)
- [3. Threat modeling with STRIDE](#3-threat-modeling-with-stride)
- [4. AuthN vs. AuthZ](#4-authn-vs-authz)
- [5. The OWASP Top 10:2025](#5-the-owasp-top-102025)
- [6. The vulnerability classes to know cold](#6-the-vulnerability-classes-to-know-cold)
- [7. Crypto basics without footguns](#7-crypto-basics-without-footguns)
- [8. Secrets & supply chain](#8-secrets--supply-chain)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. The mindset

- **Security is risk management, not perfection.** You cannot be "100% secure" any more than you can have a 100% SLO. You reduce risk to an acceptable level for the assets you hold.
- **Assume breach.** Design so that one compromised component doesn't hand over everything.
- **All input is hostile** — including input from *your own* other services. Trust is a decision, not a default.
- **The attacker reads your code.** Assume they know your design (Kerckhoffs's principle). Obscurity is not a control.

The classic frame is the **CIA triad**:

| Property | Question it protects |
| -------- | -------------------- |
| **Confidentiality** | Can only authorized parties *read* it? |
| **Integrity** | Can only authorized parties *change* it (and can you detect tampering)? |
| **Availability** | Is it *there* when legitimate users need it? |

---

## 2. The durable principles

These outlive any specific vulnerability list:

- **Defense in depth** — layer controls; assume any single one fails. (Same shape as the layered evaluator in [agent evals](building-agent-evaluators.md): cheap gates first, deeper checks behind.)
- **Least privilege** — every user, service, and token gets the *minimum* access needed, for the *minimum* time.
- **Fail securely (fail closed)** — on error, **deny**. A crashed authorization check must not mean "allow."
  > ⚠️ Note the deliberate contrast with *fail open* for caches in the [caching note](caching-strategies.md). Availability components fail open; **security controls fail closed**. Knowing which you're building is the whole point.
- **Secure by default** — the out-of-the-box config should be the safe one. Users don't harden.
- **Minimize attack surface** — every endpoint, port, feature, and dependency is a door. Fewer doors, fewer locks to pick.
- **Complete mediation** — check authorization on **every** request, not once at login.
- **Don't roll your own crypto** — you will get it wrong. So would I. Use vetted libraries.
- **Separation of duties** — no single actor can complete a sensitive action alone.
- **Zero trust** — the network is not a security boundary. "Inside the VPC" ≠ trusted.

---

## 3. Threat modeling with STRIDE

Threat modeling is just structured paranoia, applied early (design time), when fixes are cheap. Adam Shostack's **four questions**:

1. **What are we building?** (a diagram — data flows, trust boundaries)
2. **What can go wrong?** (← STRIDE helps here)
3. **What are we going to do about it?**
4. **Did we do a good job?**

**STRIDE** is a checklist for question 2 — each letter maps to the property it violates:

| Threat | Means | Violates | Typical control |
| ------ | ----- | -------- | --------------- |
| **S**poofing | Pretending to be someone else | Authentication | Strong authn, MFA, signed tokens |
| **T**ampering | Modifying data or code | Integrity | Signatures, checksums, access control |
| **R**epudiation | "I never did that" | Non-repudiation | Audit logs, signing |
| **I**nformation disclosure | Leaking data | Confidentiality | Encryption, access control |
| **D**enial of service | Making it unavailable | Availability | Rate limits, quotas, timeouts |
| **E**levation of privilege | Gaining rights you shouldn't have | Authorization | Least privilege, authz checks |

> The highest-leverage habit here: **draw your trust boundaries.** Most real vulnerabilities live exactly where data crosses one.

---

## 4. AuthN vs. AuthZ

Constantly conflated; completely different:

- **Authentication (AuthN)** — *who are you?* Passwords, MFA, passkeys, OIDC.
- **Authorization (AuthZ)** — *what are you allowed to do?* Roles, permissions, ownership checks.

**Broken access control is the #1 risk on the OWASP list** — and it's almost always an AuthZ failure: the app authenticates you correctly, then forgets to check whether *this* user may touch *that* record.

Practical rules:
- **Enforce authz server-side on every request.** Hiding a button is UI, not security.
- **Deny by default** — allowlist what's permitted, don't blocklist what isn't.
- **Check ownership, not just role** — "is this invoice *yours*?" (see IDOR in §6).
- **Passwords:** hash with **argon2id** (or bcrypt/scrypt) — *never* MD5/SHA-1/SHA-256 alone, always salted (the library does this). Support MFA; prefer **passkeys** where you can.
- **Tokens:** short-lived access tokens, rotate refresh tokens, validate signature **and** `alg`/issuer/audience. Never accept `alg: none`.

---

## 5. The OWASP Top 10:2025

The industry-standard list of most critical web application risks, revised roughly every 4 years. The **2025 edition** (announced Nov 2025, finalized January 2026) is current — note it differs from the widely-memorized 2021 list:

| # | Category | Note |
| - | -------- | ---- |
| **A01** | **Broken Access Control** | Still #1. The authz failures above. |
| **A02** | **Security Misconfiguration** | ⬆️ Rose from A05 — defaults, verbose errors, open buckets, stale permissions. |
| **A03** | **Software Supply Chain Failures** | 🆕 **New and straight to #3** — expands the old "Vulnerable & Outdated Components" to the whole chain: deps, build systems, CI/CD, distribution. |
| **A04** | **Cryptographic Failures** | Weak/missing encryption, bad key handling, data in transit/at rest. |
| **A05** | **Injection** | ⬇️ Fell from A03 — SQLi, XSS, command injection (see §6). |
| **A06** | **Insecure Design** | Flaws you can't patch away — the design itself is wrong. Fix by threat modeling (§3). |
| **A07** | **Authentication Failures** | Weak credentials, broken session handling. |
| **A08** | **Software or Data Integrity Failures** | Unverified updates, insecure deserialization, unsigned artifacts. |
| **A09** | **Security Logging & Alerting Failures** | You can't respond to what you can't see (→ [observability](observability.md)). |
| **A10** | **Mishandling of Exceptional Conditions** | 🆕 **New** — improper error handling, logic errors, and **failing open**. |

Two things worth internalizing about the 2025 revision:

- **Supply chain jumped to #3.** Your dependencies are now a top-tier attack vector — which is exactly why [pnpm v11](pnpm-tips.md) blocks lifecycle scripts by default (`allowBuilds`), gates on `minimumReleaseAge`, and ships `pnpm sbom`. That note and this one are describing the same threat from two directions.
- **"Mishandling of Exceptional Conditions" is new** — and it's the *fail closed* principle promoted to a top-10 risk. Error paths are security-critical code.

---

## 6. The vulnerability classes to know cold

**Injection** — untrusted input interpreted as *code/commands*.

```python
# ❌ SQL injection: input becomes SQL
db.execute("SELECT * FROM users WHERE email = '" + email + "'")
#   email = "' OR '1'='1"  → returns every user

# ✅ Parameterized query: input is always DATA, never code
db.execute("SELECT * FROM users WHERE email = ?", [email])
```
The fix generalizes: **never build interpreter strings by concatenation** — parameterize (SQL), use `exec` arrays not shell strings (commands), and never `eval` user input.

**XSS (Cross-Site Scripting)** — attacker's JavaScript runs in your users' browsers. Fix: **context-aware output encoding** (frameworks do this — don't defeat it with `dangerouslySetInnerHTML`/`v-html`), plus a **Content Security Policy** as defense in depth.

**CSRF** — a third-party site makes an authenticated request as your user. Fix: `SameSite=Lax/Strict` cookies + anti-CSRF tokens for state-changing requests.

**IDOR / Broken Object-Level Authorization** — `GET /invoices/1234` returns someone else's invoice because you checked *authn* but not *ownership*. Fix: always scope the query by the caller (`WHERE id=? AND owner_id=?`). Unguessable IDs help but are **not** an authz control.

**SSRF** — you fetch a user-supplied URL and the attacker points it at internal services or cloud metadata endpoints. Fix: allowlist destinations; block private/link-local ranges; no redirects.

**Path traversal** — `../../etc/passwd`. Fix: resolve and confirm the path stays inside the intended root.

**Insecure deserialization** — deserializing untrusted bytes into objects → RCE. Fix: don't; use data-only formats (JSON) with schema validation.

---

## 7. Crypto basics without footguns

Three things people constantly conflate:

| | Purpose | Reversible? | Example |
| - | ------- | ----------- | ------- |
| **Encoding** | Data *representation* | Yes, trivially | Base64, URL-encoding — **not security at all** |
| **Hashing** | One-way fingerprint | No | SHA-256 (data integrity), argon2id (passwords) |
| **Encryption** | Confidentiality | Yes, **with the key** | AES-GCM, TLS |

Rules that will save you:

- **Base64 is not encryption.** It's an alphabet change.
- **Password hashing ≠ general hashing.** Use a **slow, salted** KDF (**argon2id**, bcrypt, scrypt). SHA-256 is *too fast* — GPUs try billions/sec.
- **Symmetric** (AES — one shared key, fast, bulk data) vs. **asymmetric** (RSA/ECC — keypair, used to exchange keys/sign). TLS uses asymmetric to bootstrap, then symmetric.
- **Use authenticated encryption** (AES-**GCM**, ChaCha20-Poly1305) so tampering is detected — encryption alone doesn't give you integrity.
- **TLS everywhere**, including internal traffic (zero trust).
- **Never invent crypto or reuse a nonce/IV.** Use libsodium / your platform's vetted library.

---

## 8. Secrets & supply chain

**Secrets**
- **Never commit secrets to git.** Once pushed, treat as compromised — **rotate**, don't just delete the commit (history and forks persist).
- Use a secret manager (Vault, cloud KMS/Secrets Manager) or at minimum env vars — never source code, never client-side.
- **Rotate regularly** and on any suspicion. Scan repos (`gitleaks`, `trufflehog`) in CI.

**Supply chain** (now OWASP **A03** — take it seriously)
- **Commit your lockfile** and install with a frozen lockfile in CI (`pnpm ci`).
- **Block dependency lifecycle scripts by default** — the single highest-leverage npm-ecosystem control ([pnpm v11 does this](pnpm-tips.md) via `allowBuilds` + `pnpm approve-builds`).
- **Gate on release age** — malicious versions are usually caught within hours (pnpm's `minimumReleaseAge`).
- **Generate an SBOM** (`pnpm sbom`) so you can answer "are we affected?" within minutes of the next CVE.
- **Scan dependencies** continuously (Dependabot/Renovate + audit).
- **Pin and verify your CI actions/images** — your build system is part of the attack surface.

---

## 9. Best practices & anti-patterns

**Do**
- **Validate input, encode output** — and validate on an **allowlist** ("what's permitted"), not a blocklist.
- **Parameterize every query.** Always.
- **Authorize server-side on every request**, scoped to the caller.
- **Fail closed** on any security decision.
- **Threat model at design time** — draw the trust boundaries.
- **Log security events** (authn failures, authz denials, admin actions) — and *alert* on them (A09).
- **Patch dependencies** on a schedule, not on panic.

**Avoid**
- **Rolling your own crypto or auth.** Use a battle-tested library/provider.
- **Trusting the client** — validation in the browser is UX; the server must re-check everything.
- **Security through obscurity** as a control (unguessable URLs are not authorization).
- **Blocklists** for injection ("strip `<script>`") — attackers have infinite encodings; use encoding/parameterization.
- **Secrets in code, logs, or error messages.**
- **Verbose errors in production** — stack traces are free recon (A02).
- Treating security as a **pre-launch gate** instead of a design input (A06 exists because of this).

---

## 10. Go deeper

Related material in this library:

- 📝 **[pnpm Tips (v11)](pnpm-tips.md)** — the supply-chain controls (A03) in practice: `allowBuilds`, `minimumReleaseAge`, `pnpm sbom`.
- 📝 **[Observability](observability.md)** — A09 is a logging/alerting failure; you can't respond to what you can't see.
- 📝 **[Building an Agent Evaluator](building-agent-evaluators.md)** — the same defense-in-depth shape: cheap deterministic gates first.
- 📗 **[Site Reliability Engineering](../books/)** & **[Release It!](../books/)** — availability is the "A" in CIA; failure handling is now OWASP A10.
- 📗 **[Designing Data-Intensive Applications](../books/)** — encryption, integrity, and trust boundaries in data systems.

### Primary references

- [OWASP Top 10:2025](https://owasp.org/Top10/2025/) — the authoritative list (finalized January 2026).
- [OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org/) — the single most practical security resource on the internet; there's a cheat sheet for nearly every item above.
- Adam Shostack, *Threat Modeling: Designing for Security* — the four questions and STRIDE.
- Saltzer & Schroeder, *"The Protection of Information in Computer Systems,"* 1975 — where least privilege, complete mediation, and fail-safe defaults come from.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
