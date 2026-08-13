# 🔓 OAuth 2.0, OIDC & SSO (incl. Silent SSO) — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~22 min · **Prerequisites:** the [Security Fundamentals](security-fundamentals.md) and [Service-to-Service Auth](service-to-service-auth.md) notes. This is the **user/browser** side of the same auth world (S2S covers *machine* identity).

When a user "Signs in with Google" or logs into one company app and is silently signed into all the others, a small stack of protocols is doing the work: **OAuth 2.0** (delegated authorization), **OIDC** (authentication on top of it), and **SSO** (one login → many apps). This note is that stack, ending on the specific thing you asked about — **silent SSO**.

> **The one distinction that unlocks everything:** **OAuth 2.0 is about *authorization*** (can this app access this resource on the user's behalf?), while **OIDC is about *authentication*** (who is this user?). OAuth was borrowed for login for years before OIDC standardized it — and conflating the two is the root of most auth confusion. Access token = "you may call this API." ID token = "this user is Alice."

## Table of contents

- [1. The landscape](#1-the-landscape)
- [2. OAuth 2.0 core](#2-oauth-20-core)
- [3. The Authorization Code flow + PKCE](#3-the-authorization-code-flow--pkce)
- [4. The tokens](#4-the-tokens)
- [5. OIDC — the identity layer](#5-oidc--the-identity-layer)
- [6. SSO — one login, many apps](#6-sso--one-login-many-apps)
- [7. Silent SSO](#7-silent-sso)
- [8. Where to keep tokens (the SPA problem & BFF)](#8-where-to-keep-tokens-the-spa-problem--bff)
- [9. Logout & session management](#9-logout--session-management)
- [10. Best practices & anti-patterns](#10-best-practices--anti-patterns)
- [11. Go deeper](#11-go-deeper)

---

## 1. The landscape

| Protocol | Answers | Format | Where |
| -------- | ------- | ------ | ----- |
| **OAuth 2.0** | *Authorization* — "may this app do X on the user's behalf?" | Tokens (JSON/JWT) | The delegation framework everything builds on |
| **OIDC** (OpenID Connect) | *Authentication* — "who is this user?" | ID token (JWT) | A thin identity layer **on top of** OAuth 2.0 |
| **SAML 2.0** | Authentication + SSO (enterprise) | XML assertions | Older, still dominant in enterprise SSO |
| **SSO** | "log in once, use many apps" | (a pattern, via the above) | Enabled by a shared IdP session |

Modern web/mobile → **OIDC**. Enterprise/legacy → often **SAML**. **OAuth 2.1** is the in-progress consolidation that folds in a decade of security best practices (PKCE everywhere; implicit & password grants removed).

---

## 2. OAuth 2.0 core

Four roles:

- **Resource Owner** — the **user** who owns the data.
- **Client** — the **application** wanting access (a SPA, mobile app, backend).
- **Authorization Server (IdP)** — authenticates the user and **issues tokens** (Auth0, Okta, Entra ID, Google, Keycloak…).
- **Resource Server** — the **API** that accepts access tokens.

**Grant types** (how a client gets tokens) — know which is current:

| Grant | For | Status |
| ----- | --- | ------ |
| **Authorization Code + PKCE** | Web apps, SPAs, mobile — **everything user-facing** | ✅ The one to use |
| **Client Credentials** | Machine-to-machine, no user | ✅ (see [S2S auth](service-to-service-auth.md)) |
| **Device Code** | TVs, CLIs, input-constrained devices | ✅ |
| **Implicit** | (old SPA flow — tokens in the URL) | ❌ Deprecated (insecure) |
| **Resource Owner Password (ROPC)** | (app collects the password directly) | ❌ Deprecated (defeats the point) |

> Rule: **Authorization Code + PKCE for users, Client Credentials for machines.** If a design uses implicit or password grant, it's out of date.

---

## 3. The Authorization Code flow + PKCE

The canonical user-login flow, with **PKCE** (Proof Key for Code Exchange, RFC 7636) — mandatory for public clients, recommended for all:

```
1. Client makes a random `code_verifier`, hashes it → `code_challenge`.
2. Redirect user to IdP /authorize?response_type=code
      &code_challenge=...  &state=...  (+ &nonce=... for OIDC)
3. User authenticates at the IdP (or already has a session → SSO).
4. IdP redirects back with an authorization CODE (not tokens) + state.
5. Client POSTs code + `code_verifier` to /token.
6. IdP verifies the verifier matches the challenge → returns
      access_token (+ refresh_token) (+ id_token for OIDC).
```

Why the extra dance:
- **The code is exchanged server-to-server-style with a secret proof**, so intercepting the redirect (which is visible) doesn't let an attacker redeem it — **PKCE** binds the code to the client that started the flow.
- **`state`** — a random value echoed back to defeat **CSRF** on the callback.
- **`nonce`** (OIDC) — binds the ID token to this request to defeat **replay**.

---

## 4. The tokens

Three tokens, three jobs — **do not mix them up** (a common exam/interview trap):

| Token | Purpose | Audience | Lifetime |
| ----- | ------- | -------- | -------- |
| **Access token** | Call APIs (a bearer credential) | the **Resource Server** | short (minutes) |
| **Refresh token** | Get new access tokens without re-login | the **Auth Server** | long, **rotated** |
| **ID token** (OIDC) | *Prove the user authenticated* | the **Client** | short |

Critical distinctions:
- **The access token is for the API; the ID token is for the app.** Never send an ID token to an API as authorization, and never use an access token to identify the user in your UI.
- **Access tokens may be opaque or JWT.** If JWT, the API validates signature + `iss`/`aud`/`exp` + **pins the alg** (the [JWT trap](security-fundamentals.md); never `alg:none`).
- **Refresh tokens should rotate** (each use issues a new one and invalidates the old) so a stolen one has a short window — the "keep the window small" instinct again.

---

## 5. OIDC — the identity layer

**OpenID Connect** adds authentication to OAuth 2.0 with three things:

- **The `openid` scope** — request it and the IdP returns an **ID token**.
- **The ID token** — a **JWT** with identity **claims**: `sub` (the stable user id), `iss`, `aud`, `exp`, `iat`, `nonce`, and profile claims (`name`, `email`) per the `profile`/`email` scopes.
- **Standard endpoints** — the **UserInfo** endpoint (fetch more claims), and **Discovery** (`/.well-known/openid-configuration`) + **JWKS** (public keys to verify ID tokens). Discovery is why "Sign in with X" is so uniform across providers.

> OIDC is what makes "Sign in with Google/Microsoft/Apple" work the same everywhere — it standardized the identity layer that everyone was hand-rolling on raw OAuth.

---

## 6. SSO — one login, many apps

**Single Sign-On:** authenticate **once** and access many applications without logging in again. The mechanism is simple once you have the above:

- The **IdP maintains its own session** with the user (a cookie on the IdP's domain, set when they first log in).
- When App B redirects the user to the IdP's `/authorize`, the IdP **sees that existing session** and issues tokens **without prompting** — the user just bounces through and lands logged in.
- So "SSO" isn't a separate protocol; it's **the natural consequence of a shared IdP session** across apps that all federate to it (via OIDC or SAML).

```
App A login → IdP sets its session cookie
App B → redirect to IdP → IdP sees session → back to App B, logged in (no prompt)
App C → same → logged in
```

Enterprise variant: **SAML SSO** does the same with XML assertions and IdP/SP roles — common with corporate IdPs; conceptually identical (shared IdP session, federated apps).

---

## 7. Silent SSO

**Silent SSO** (a.k.a. *silent authentication*) authenticates the user **with no visible prompt** by quietly checking for that existing IdP session — used for **seamless cross-app SSO** and **background token/session renewal** in SPAs.

### The classic mechanism: `prompt=none`

```
1. SPA opens a hidden <iframe> → IdP /authorize with  prompt=none
      (= "authenticate ONLY if a session already exists; never show UI")
2. IdP checks its session cookie:
      ├─ session exists → returns a code/tokens silently
      └─ no session     → returns error  login_required / interaction_required
3. iframe posts the result back (postMessage):
      ├─ success → app has fresh tokens, user noticed nothing
      └─ error   → app starts a normal interactive login
```

`prompt=none` is the core: **"succeed silently or fail fast."** Useful for renewing a short-lived access token without redirecting the whole page.

### ⚠️ The third-party-cookie problem (the thing to know in 2026)

The hidden iframe only works if the browser sends the IdP's session cookie **inside that iframe** — a **third-party cookie**. Modern browsers (Safari ITP, Chrome's phase-out) **block third-party cookies by default**, so **iframe-based silent SSO is now unreliable or broken.** Don't design new systems around it.

### The modern replacements

| Approach | How | Verdict |
| -------- | --- | ------- |
| **Refresh token rotation** | SPA holds a short-lived, rotating refresh token and exchanges it directly at the IdP — no iframe, no third-party cookie | ✅ Standard for SPAs (OAuth 2.1 + PKCE) |
| **BFF (Backend-for-Frontend)** | A server-side component holds tokens; the browser gets a **same-site**, HttpOnly session cookie and talks only to your backend | ✅✅ Most secure & future-proof — see §8 |

> **Bottom line on silent SSO:** the *concept* (authenticate only if a session already exists, invisibly) is alive and well; the *hidden-iframe implementation* is dying with third-party cookies. Use refresh-token rotation, or better, the BFF pattern.

---

## 8. Where to keep tokens (the SPA problem & BFF)

Browser token storage is a genuine hazard:

- **`localStorage`** → readable by any JavaScript → **XSS steals your tokens**. Avoid for tokens.
- **Non-HttpOnly cookies** → same XSS exposure.
- **In-memory** → safer from XSS, but lost on refresh (needs silent renewal).

**The BFF (Backend-for-Frontend) pattern** is the current best answer: a lightweight server component does the OAuth flow, **holds the tokens server-side**, and gives the browser only a **same-site, HttpOnly session cookie**. The SPA calls *your* BFF, which attaches the real token to downstream API calls.

- ✅ **No tokens in the browser** (XSS can't steal what isn't there), **no third-party-cookie dependence** (the session cookie is first-party), and silent SSO becomes a non-issue.
- The recommended architecture for new browser apps handling anything sensitive.

---

## 9. Logout & session management

Logout is deceptively hard in SSO — there are multiple sessions (the app's *and* the IdP's, plus every other federated app):

- **Local logout** — clear the app's own session. Easy, but the IdP session persists → the user is silently logged back in on the next visit.
- **Single Logout (SLO)** — log out of the IdP *and* propagate to all apps. **Front-channel** (redirects/iframes — fragile, third-party-cookie-dependent) vs. **back-channel** (server-to-server logout tokens — more reliable). SLO is notoriously finicky; know it's a design concern, not an afterthought.
- **Session checking** — OIDC session-management specs let an app detect when the IdP session ended.

---

## 10. Best practices & anti-patterns

**Do**
- **Authorization Code + PKCE** for all user-facing clients; **Client Credentials** for machines.
- Use **`state` (CSRF)** and **`nonce` (replay)** on every flow.
- **Short-lived access tokens + rotating refresh tokens.**
- **Validate tokens** fully (signature, `iss`, `aud`, `exp`, pinned alg); use **Discovery + JWKS**.
- Prefer the **BFF pattern** (tokens server-side, HttpOnly same-site cookie) for browser apps.
- Treat the **ID token as identity, the access token as API authorization** — never swap them.
- Validate `postMessage` **origins** in any iframe flow.

**Avoid**
- **Implicit or password (ROPC) grants** — deprecated and insecure.
- **Tokens in `localStorage`** — XSS-lootable.
- **New designs relying on iframe silent SSO / third-party cookies** — use refresh rotation or BFF.
- **Using an access token to identify the user**, or an ID token to call an API.
- **Skipping `state`/`nonce`**, or accepting `alg:none`.
- **Forgetting IdP-session logout** — "logged out" that silently logs back in.

---

## 11. Go deeper

Related material in this library:

- 📝 **[Service-to-Service Auth](service-to-service-auth.md)** — the *machine* side of the same OAuth world (Client Credentials, mTLS, workload identity). This note is the *user/browser* side.
- 📝 **[Security Fundamentals](security-fundamentals.md)** — authN vs. authZ, the JWT `alg` trap, XSS/CSRF, least privilege.
- 📝 **[Exposing React to Other Apps](exposing-react-to-other-apps.md)** — the iframe `postMessage` + origin-validation mechanics silent SSO relies on.
- 📝 **[API Design](api-design.md)** — bearer tokens, `401` vs `403`, and authorizing every request.

### Primary references

- [OAuth 2.0 (RFC 6749)](https://datatracker.ietf.org/doc/html/rfc6749) · [PKCE (RFC 7636)](https://datatracker.ietf.org/doc/html/rfc7636) · [OAuth 2.0 for Browser-Based Apps (BFF guidance)](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps) · [OAuth 2.1 draft](https://oauth.net/2.1/).
- [OpenID Connect Core](https://openid.net/specs/openid-connect-core-1_0.html) and [Session Management](https://openid.net/specs/openid-connect-session-1_0.html).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
