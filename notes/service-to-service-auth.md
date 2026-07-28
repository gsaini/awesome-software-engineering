# 🔑 Service-to-Service Authentication — A Detailed Study Note

> **Level:** 🟡 Intermediate–Advanced · **Reading time:** ~22 min · **Prerequisites:** the [Security Fundamentals](security-fundamentals.md) and [API Design](api-design.md) notes; helpful to have the [Consensus](distributed-consensus.md)/distributed context.

When a browser logs in, a *human* proves who they are with a password + MFA. But when **service A calls service B**, there's no human, no login prompt, no password to type. **Service-to-service (S2S) authentication** — also *machine-to-machine (M2M)* or *workload identity* — is how one non-human component proves its identity to another. Get it wrong and your "internal" network becomes a single compromised service away from total takeover.

> **The core reframe:** user auth optimizes for a human who authenticates *occasionally* and interactively. S2S auth is for a machine calling *thousands of times a second*, non-interactively, with credentials that live in config/memory — so the whole game is **short-lived, automatically-rotated, cryptographically-verifiable identity with no human in the loop.** And per [zero trust](security-fundamentals.md), being "inside the network" proves nothing.

## Table of contents

- [1. Why S2S is a different problem](#1-why-s2s-is-a-different-problem)
- [2. The threat model](#2-the-threat-model)
- [3. The approaches](#3-the-approaches)
- [4. mTLS](#4-mtls)
- [5. OAuth2 client credentials & JWTs](#5-oauth2-client-credentials--jwts)
- [6. Workload identity (SPIFFE, cloud IAM)](#6-workload-identity-spiffe-cloud-iam)
- [7. Bearer vs. sender-constrained tokens](#7-bearer-vs-sender-constrained-tokens)
- [8. AuthN vs. AuthZ for services](#8-authn-vs-authz-for-services)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. Why S2S is a different problem

| | User auth | Service auth |
| - | --------- | ------------ |
| **Who** | A human | A workload/process |
| **How often** | Occasionally, interactively | Constantly, automatically |
| **Factor** | Password + MFA, passkey | A key/cert/token, no interaction |
| **Credential lifetime** | A session | Ideally minutes (short-lived, rotated) |
| **Hard part** | Phishing, password reuse | **Secret distribution & rotation at scale** ("secret zero") |

The defining challenge is **the bootstrap problem**: to get a credential you usually need a credential. Where does the *first* secret come from, and how do you rotate thousands of them without downtime? Most of the sophistication below exists to answer that.

---

## 2. The threat model

Design against these, not just "is the caller who they say":

- **Network is not a boundary** ([zero trust](security-fundamentals.md)) — "inside the VPC" ≠ trusted. A foothold in one service must not grant the identity of others.
- **Credential theft & replay** — if a token leaks (logs, memory dump, SSRF), can an attacker *reuse* it? (→ sender-constrained tokens, §7.)
- **Impersonation** — can service A forge a request that looks like it came from B?
- **Lateral movement** — the whole point of per-service identity + least privilege is to *contain* a breach to one blast radius.
- **Long-lived secrets** — a static API key in a repo/config is the classic catastrophe (OWASP secrets exposure; supply-chain adjacency).

The goal: **mutual authentication** (both sides verify each other) with **short-lived, verifiable, per-workload identity**.

---

## 3. The approaches

From weakest to strongest:

| Approach | Identity basis | Rotation | Mutual? | Verdict |
| -------- | -------------- | -------- | :-----: | ------- |
| **Shared API key / static secret** | A bearer string | Manual, painful | ❌ | Baseline only; avoid internally |
| **Signed request (HMAC)** | Shared secret signs the request | Manual | ❌ | Better than raw keys (AWS SigV4 style) |
| **OAuth2 client credentials + JWT** | Token from an auth server | Short-lived tokens | ❌ (bearer) | The M2M standard |
| **mTLS** | X.509 certificates | Automatable (mesh) | ✅ | Strong cryptographic identity |
| **Workload identity (SPIFFE / cloud IAM)** | Platform-attested identity | Automatic, minutes | ✅ | Best — solves "secret zero" |

The trajectory of the field is clear: **away from shared static secrets, toward short-lived, platform-issued, cryptographic identity** that no human ever handles.

---

## 4. mTLS

Ordinary TLS authenticates the **server** to the client (the padlock). **Mutual TLS** adds the reverse: the **client also presents a certificate**, so both sides cryptographically prove identity — and the channel is encrypted, satisfying [zero trust](security-fundamentals.md) even inside the network.

How it works:
- A private **CA** issues each service a certificate whose identity (e.g. `spiffe://…/payments` or a SAN) names the workload.
- On connect, each side validates the other's cert against the trusted CA. No valid cert → no connection.
- Identity is **the key pair**, not a bearer string — you can't "replay" a cert without its private key.

The catch is **operational**: issuing, distributing, and *rotating* certs across hundreds of services by hand is infeasible. This is exactly what a **service mesh** (Istio, Linkerd) automates — sidecar proxies transparently establish mTLS and **rotate certs every few hours**, so application code does nothing. mTLS is the de-facto backbone of modern internal S2S auth.

---

## 5. OAuth2 client credentials & JWTs

The application-layer standard for M2M, and the S2S cousin of the user-facing OAuth you know.

**The Client Credentials grant** (no user involved):

```
1. Service A → Authorization Server:  "here's my client_id + credential, give me a token"
2. Auth Server → Service A:           access_token (a short-lived JWT), scope=..., exp=...
3. Service A → Service B:             Authorization: Bearer <token>
4. Service B validates the token's signature, issuer, audience, expiry, scope — locally.
```

**How the client authenticates to the auth server** matters — weakest to strongest:
- `client_secret` — a shared secret. Simple, leakable.
- **`private_key_jwt`** — the client signs a JWT assertion with its *private* key; the server verifies with the public key. **No shared secret in transit.** Preferred.
- **mTLS client auth** — authenticate with a certificate (and get a cert-bound token, §7).

**Validating the JWT** (service B's job, every request) — this is where bugs live:
- Verify the **signature** using the issuer's public key (fetched from a **JWKS** endpoint, cached).
- Check **`iss`** (trusted issuer), **`aud`** (this token is *for me*), **`exp`/`nbf`** (not expired).
- Check **`scope`/roles** for [authorization](#8-authn-vs-authz-for-services).
- **Pin the algorithm** — reject `alg: none` and never let the token pick the verification algorithm (the classic JWT vuln from the [security note](security-fundamentals.md)).

JWTs are **self-contained** (stateless verification, no callback to the auth server — fast, but revocation is hard → keep them short-lived).

---

## 6. Workload identity (SPIFFE, cloud IAM)

The modern answer to "secret zero": stop distributing secrets at all. Let the **platform attest** each workload and hand it an identity.

**SPIFFE / SPIRE** (CNCF):
- **SPIFFE ID** — a URI naming a workload: `spiffe://acme.com/ns/prod/sa/payments`.
- **SVID** — a **SPIFFE Verifiable Identity Document**, delivered as an X.509 cert or a JWT, that proves that ID.
- **SPIRE** — the runtime that **attests** a workload (using node + process properties — "is this really the payments pod on this trusted node?") and issues short-lived SVIDs, **auto-rotated**. No developer ever handles a secret.

**Cloud IAM** does the same, managed:
- **AWS IAM roles for tasks/IRSA**, **GCP Workload Identity**, **Azure Managed Identities** — the platform gives each workload an identity and hands its code **short-lived credentials** via the metadata service. There is **no secret to store, leak, or rotate**.

> This is the endgame: identity derived from *what the workload is and where it runs*, attested by the platform, expressed as a **minutes-lived** credential. It dissolves the bootstrap problem instead of managing it.

---

## 7. Bearer vs. sender-constrained tokens

A crucial distinction that decides what happens when a token leaks:

- **Bearer token** — "whoever *bears* it may use it." Simple, but if stolen (logs, [SSRF](security-fundamentals.md), a memory dump), the attacker can **replay** it until it expires. Most OAuth tokens are bearer.
- **Sender-constrained (proof-of-possession) token** — bound to the client's key, so presenting it also requires *proving* you hold that key. A stolen token is **useless** to anyone else. Two mechanisms:
  - **mTLS-bound tokens (RFC 8705)** — the token is tied to the client's TLS certificate.
  - **DPoP (RFC 9449)** — the client signs each request with a key the token is bound to (for non-mTLS/HTTP contexts).

> Rule of thumb: bearer + **short expiry** is acceptable for many internal calls; for high-value paths, **sender-constrain** the token so theft ≠ compromise. This is the token-layer version of "assume breach."

---

## 8. AuthN vs. AuthZ for services

Authenticating *who is calling* is only half the job — you still must decide *what they're allowed to do* (the [AuthN vs. AuthZ](security-fundamentals.md) split, now between machines):

- **Scopes / roles in the token** — `scope: orders:read` — coarse-grained, carried in the JWT.
- **Policy engines** (**OPA/Rego**, Cerbos) — externalize authorization: "may `payments` call `POST /refunds` on `ledger`?" decided by centralized, versioned policy rather than scattered `if` checks.
- **Least privilege per service** — each workload's identity is granted the *minimum* it needs, so a compromised service can reach little. This is what makes per-workload identity worth the effort — it **caps the blast radius**.

Enforce authz on **every call, server-side** (complete mediation) — a valid identity is not a blank check.

---

## 9. Best practices & anti-patterns

**Do**
- **Prefer platform workload identity** (SPIFFE/SPIRE, cloud IAM) — no secrets to manage.
- **Use mTLS for internal S2S**, ideally via a service mesh that auto-rotates certs.
- **Make credentials short-lived and auto-rotated** — minutes, not months.
- **Validate JWTs fully** — signature, `iss`, `aud`, `exp`, and a **pinned algorithm**; reject `alg: none`.
- **Sender-constrain tokens** (mTLS-bound / DPoP) for high-value paths.
- **Least privilege per service** + centralized policy (OPA) enforced on every call.
- **Store any unavoidable secret in a manager** (Vault, cloud KMS), never in code/env/images.

**Avoid**
- **Shared static API keys** for internal auth — unrotatable, leaky, no mutual auth.
- **Long-lived secrets** in config, env vars, images, or git (rotate on any exposure — deleting the commit doesn't help).
- **Trusting the network** — "it came from inside" is not authentication.
- **Skipping `aud`/`iss`/signature validation**, or accepting `alg: none` (token forgery).
- **One over-privileged identity shared by many services** — destroys blast-radius containment.
- **Treating authentication as authorization** — verify the caller *and* check what it may do.

---

## 10. Go deeper

Related material in this library:

- 📝 **[Security Fundamentals](security-fundamentals.md)** — zero trust, AuthN vs. AuthZ, least privilege, secrets, the JWT `alg` trap. This note is the S2S deep-dive of those principles.
- 📝 **[API Design](api-design.md)** — bearer tokens, `401/403`, and authorizing every request; S2S is the machine caller of your APIs.
- 📝 **[Message Queues & Event-Driven](message-queues-event-driven.md)** & **[Consensus](distributed-consensus.md)** — the distributed systems whose components need to trust each other.
- 📝 **[Observability](observability.md)** — log authn failures and authz denials (OWASP A09); trace identity across service hops.
- 📗 **[Designing Data-Intensive Applications](../books/)** — trust boundaries in distributed data systems.

### Primary references

- OAuth 2.0 — [RFC 6749 (Client Credentials grant)](https://datatracker.ietf.org/doc/html/rfc6749#section-4.4), [RFC 8705 (mTLS / certificate-bound tokens)](https://datatracker.ietf.org/doc/html/rfc8705), [RFC 9449 (DPoP)](https://datatracker.ietf.org/doc/html/rfc9449).
- [SPIFFE / SPIRE](https://spiffe.io/) — workload identity standard and runtime.
- NIST SP 800-207, *Zero Trust Architecture*.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
