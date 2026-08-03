# 🧩 Exposing a React App/Component to Other Apps — Integration Options

> **Level:** 🟡 Intermediate · **Reading time:** ~20 min · **Prerequisites:** the [React Flow](react-flow.md) note (basic React), helpful: [API Design](api-design.md), [Load Balancing](load-balancing-rate-limiting.md) (L7 path routing). Landscape current as of 2026 (Module Federation 3.0 / Native ESM Federation, Next.js Multi-Zones).

You've built something in React and another application needs to use it. There are **more ways to do this than people realize**, and they differ enormously in coupling, isolation, and deployment independence. This note enumerates every real option, gives the trade-offs, and — per your question — **confirms that "route mapping" is a legitimate, named pattern** (reverse-proxy / routing composition).

> **The three questions that decide everything:**
> 1. **Granularity** — are you sharing a **component** (composed *into* someone else's page) or a **whole page/app** (they hand off a route to you)?
> 2. **Integration time** — **build-time** (compiled together), **run-time** (loaded in the browser), or **server/edge-time** (composed before it reaches the browser)?
> 3. **Origin & coupling** — same-origin or cross-origin? Must the consumer use React (and a matching version), or should it be framework-agnostic?
>
> Every option below is just a different answer to those three.

## Table of contents

- [1. The options at a glance](#1-the-options-at-a-glance)
- [2. Shared component library (npm)](#2-shared-component-library-npm)
- [3. Module Federation](#3-module-federation)
- [4. Web Component](#4-web-component)
- [5. Embeddable JS widget](#5-embeddable-js-widget)
- [6. iframe](#6-iframe)
- [7. Route mapping / reverse-proxy composition ✅](#7-route-mapping--reverse-proxy-composition-)
- [8. Orchestrator frameworks & server-side composition](#8-orchestrator-frameworks--server-side-composition)
- [9. How host & guest talk to each other](#9-how-host--guest-talk-to-each-other)
- [10. Decision guide](#10-decision-guide)
- [11. Cross-cutting concerns](#11-cross-cutting-concerns)
- [12. Go deeper](#12-go-deeper)

---

## 1. The options at a glance

| # | Option | Granularity | Integration time | Consumer needs React? | Isolation | Independent deploy |
| - | ------ | ----------- | ---------------- | :-------------------: | --------- | :----------------: |
| 1 | **npm component library** | Component | Build-time | ✅ (matching version) | ❌ (shared globals) | ❌ (consumer rebuilds) |
| 2 | **Module Federation** | Component/app | Run-time | ✅ (shared instance) | ⚠️ partial | ✅ |
| 3 | **Web Component** | Component | Run-time | ❌ (agnostic) | ✅ (Shadow DOM) | ✅ |
| 4 | **Embeddable JS widget** | Component | Run-time | ❌ | ⚠️ (unless shadow/iframe) | ✅ (CDN) |
| 5 | **iframe (URL)** | Page/widget | Run-time | ❌ | ✅✅ (strongest) | ✅ |
| 6 | **Route mapping / reverse proxy** | Whole page/route | Server/edge | ❌ | ✅ (separate app) | ✅ |
| 7 | **Orchestrator (single-spa/Piral)** | App | Run-time | ❌ (multi-framework) | ⚠️ | ✅ |
| 8 | **Server/edge composition (ESI/SSI)** | Fragment | Server-time | ❌ | ✅ | ✅ |

The spread runs from **tightest coupling** (npm — compiled together, shared React) to **loosest** (iframe / route mapping — fully separate apps).

---

## 2. Shared component library (npm)

Publish the component as a package (public npm, or private: GitHub Packages, Verdaccio, Artifactory). Consumers `import { Widget }` and compile it into their bundle.

- ✅ Best DX — typed props, tree-shaking, no runtime magic; the standard for **design systems** and sharing UI *within* a React org.
- ❌ **Tightest coupling:** consumer must use React, with a compatible version (declare it as a `peerDependency`); a change means the consumer **rebuilds and redeploys**. No independent deploy.
- **Use when:** everyone's on React, in one org/monorepo, and you want compile-time safety over deploy independence.

---

## 3. Module Federation

A Webpack 5 / **Rspack** / Vite plugin that lets separate builds **share code at runtime**: a *host* app dynamically loads modules a *remote* app **exposes**, without rebuilding the host. The flagship **micro-frontend** technique.

- Apps deploy **independently**; the host pulls the latest remote at load time.
- **Shares a single React instance** (declared `shared` singleton) so you don't ship React twice or break hooks.
- **2026 state:** *Module Federation 3.0* auto-dedupes shared deps in the browser; the trend is **Native ESM Federation** via **import maps** + top-level await — federating modules with little/no host build step.
- ✅ Independent deploys + real component sharing.
- ❌ Complexity; **version-skew** management; both sides must agree on shared-dependency contracts; largely bundler-specific.
- **Use when:** multiple teams ship parts of one React app independently and need to share live components, not just pages.

---

## 4. Web Component

Wrap your React app in a **Custom Element** and distribute it as a script; consumers use `<my-widget prop="x">` in **any framework** (or plain HTML). **Shadow DOM** encapsulates your CSS so it can't clash with the host.

- ✅ **Framework-agnostic**, strong style isolation, standards-based (long-lived).
- ❌ Usually **bundles its own React** (heavier unless the host also federates it); props cross the boundary as **attributes (strings) + DOM events**, so rich data needs care; SSR and some accessibility/focus behaviors are trickier across the shadow boundary.
- **Use when:** you must ship one widget to **unknown or mixed-framework** consumers with clean CSS isolation.

---

## 5. Embeddable JS widget

The **Intercom / Stripe / Disqus** model: the consumer drops a `<script src="https://cdn.you.com/widget.js">` and a mount point; your script boots the React app into that element (often inside its own **shadow root or iframe** for isolation) and exposes an API like `window.MyWidget.init({...})`.

- ✅ Dead-simple for consumers (one snippet), **you push updates centrally via CDN**, framework-agnostic.
- ❌ Runs **inside the host page** — CSS/JS can leak both ways unless you isolate; it's third-party code the host must **trust** (security); bundle size matters.
- **Use when:** distributing a **self-contained third-party widget** (chat, payments, reviews, analytics) to many external sites.

---

## 6. iframe

Host the app at a URL; consumers embed `<iframe src="https://you.com/widget">`. The embedded app runs in a **separate document** — the **strongest isolation** available, cross-origin, any stack on both sides.

- ✅ Bulletproof CSS/JS sandboxing; works across orgs and frameworks; quickest "just embed it."
- ❌ **Responsive sizing is awkward** (the host can't see content height → you postMessage the height or use a resizer); **auth/cookies** hit third-party-cookie / `SameSite` limits; **SEO** — content isn't in the host DOM; separate JS context = more memory; deep-linking/routing and focus/accessibility need extra work.
- **Use when:** you need **hard isolation** or are embedding into **untrusted/third-party** sites and can live with the sizing/auth friction.

---

## 7. Route mapping / reverse-proxy composition ✅

**Your proposed option — and yes, it's a real, widely-used pattern.** Deploy the React app standalone; the consuming site maps a **URL path under its own domain** to that app via a **reverse proxy / rewrite** at the server or edge. To the browser it's **same-origin** (`shop.com/reviews/*` served by a separate reviews app), so there are none of the iframe cookie/sizing headaches, yet the app deploys independently.

Names & implementations you'll see:
- **Reverse proxy:** nginx `location /reviews/ { proxy_pass https://reviews-app; }`, HAProxy, Envoy.
- **Next.js Multi-Zones / `rewrites`** — the framework-native version: route + `assetPrefix` composition (the 2026 go-to for this pattern).
- **Edge/CDN rewrites:** Cloudflare Workers, Fastly, Vercel/Netlify `rewrites`.
- **API-gateway L7 path routing** — the same **path-based routing** from your [Load Balancing note](load-balancing-rate-limiting.md), applied to whole frontends.

This is the **"vertical split" micro-frontend**: each app **owns a whole page/route**, rather than a component composed into a shared page.

- ✅ **Same-origin** (shared cookies/auth, no iframe friction), **independent deploy**, framework-agnostic, **SEO-friendly** (real URLs + server responses), simple mental model.
- ❌ It's **page-level, not component-level** — you don't compose a widget *into* another page, you hand off a route. A seamless feel needs a **shared shell/nav** and consistent design system; watch **asset paths** (set `assetPrefix`/`basePath`) and **auth/session** coordination across zones.
- **Use when:** different teams/apps should own **whole sections** of one domain (`/`, `/docs`, `/reviews`, `/dashboard`) and deploy them separately, under one URL — often the **simplest, most robust** choice when you don't need in-page component composition.

> **Confirmed:** route mapping is not a workaround — it's a first-class micro-frontend integration style (routing/vertical composition). It's frequently the *right default* because it sidesteps runtime coupling and iframe pain entirely.

---

## 8. Orchestrator frameworks & server-side composition

Two more families worth knowing:

- **Orchestrator frameworks — single-spa, Piral, Luigi.** A **shell** registers multiple micro-apps and mounts/unmounts them by route — even across **different frameworks** (React + Vue + Angular on one page). Run-time. Use when you have many teams/stacks sharing one SPA shell. (Module Federation often supplies the loading underneath.)
- **Server/edge composition — SSI, ESI, Podium, Tailor, "server-driven UI."** The server or CDN stitches **HTML fragments** from multiple services before the page reaches the browser. ✅ Great SEO/first-paint (server-rendered), ❌ more infra and weaker cross-fragment interactivity. Use for content-heavy, SEO-critical composition.

---

## 9. How host & guest talk to each other

Whatever you pick, they need to communicate. Options by mechanism:

- **Props / attributes** — npm, Module Federation, Web Components (attributes are strings; use properties/events for rich data).
- **Custom DOM events / a global API** (`window.MyWidget.on(...)`) — widgets, Web Components.
- **`postMessage`** — the **only** channel across an **iframe** (and cross-origin); define a small typed message protocol and **validate `event.origin`** (security — untrusted messages are an attack vector).
- **Shared store / event bus** — Module Federation / orchestrators can share a state singleton (use sparingly; it re-couples independently-deployed apps).
- **The URL** — route mapping and orchestrators coordinate largely through the address bar (deep links, query params). Underrated and robust.

---

## 10. Decision guide

```
Do consumers control the build AND all use React?
 ├─ yes, one org/monorepo, want type-safety      → npm library (2)
 └─ no / independent deploys needed
      Are you sharing a COMPONENT into their page?
       ├─ all React, need live sharing            → Module Federation (3)
       ├─ any framework, need CSS isolation        → Web Component (4)
       └─ external sites, one snippet              → Embeddable widget (5)
      Are you handing off a WHOLE page/route?
       ├─ need hard isolation / untrusted host      → iframe (6)
       ├─ same domain, independent deploy, SEO      → Route mapping / Multi-Zones (7)  ← often the best default
       └─ many frameworks on one shell              → Orchestrator: single-spa (8)
      SEO-critical, content-heavy composition       → Server/edge composition (8)
```

---

## 11. Cross-cutting concerns

Score every option against these — they're where integrations actually succeed or fail:

- **React version coupling** — npm & Module Federation must reconcile versions (shared singletons, `peerDependencies`); iframe/route-mapping/Web-Components don't care.
- **CSS isolation** — Shadow DOM (Web Component) and separate documents (iframe/route) isolate cleanly; npm/widget/federation can leak (scope with CSS Modules, a prefix, or shadow roots).
- **Auth & cookies** — same-origin (npm, federation, route mapping) shares session cleanly; **iframe cross-origin fights third-party-cookie/`SameSite`** rules — a frequent blocker.
- **SEO** — server-rendered options (route mapping, server composition) win; iframe content is invisible to the host's SEO.
- **Bundle size / performance** — build-time & federation can share deps (one React); iframe/widget/Web-Component often duplicate runtime.
- **Independent deployability** — the reason to leave npm behind: everything run-time or server-time lets you ship without a consumer rebuild.
- **Security & trust** — anything running **in the host page** (npm, federation, widget, Web Component) executes with the host's privileges; iframe/route-mapping contain the blast radius. Validate all `postMessage` origins.

> The recurring theme mirrors the rest of this library: **isolation vs. integration is a trade-off dial.** iframe/route-mapping maximize isolation and independence; npm/federation maximize integration and sharing. Pick the point that matches how much the two apps should actually know about each other — the [coupling decision](message-queues-event-driven.md), on the frontend.

---

## 12. Go deeper

Related material in this library:

- 📝 **[React Flow](react-flow.md)** — a component you might expose via any of the above.
- 📝 **[Load Balancing & Rate Limiting](load-balancing-rate-limiting.md)** — L7 **path-based routing** is the mechanism under route mapping (option 7).
- 📝 **[API Design](api-design.md)** — the guest usually needs a backend contract; the edge-vs-internal split rhymes with widget-vs-federation.
- 📝 **[Security Fundamentals](security-fundamentals.md)** — `postMessage` origin validation, third-party script trust, iframe sandboxing, `SameSite` cookies.
- 📝 **[Message Queues & Event-Driven](message-queues-event-driven.md)** — the coupling/decoupling trade-off, at the frontend layer.

### Primary references

- Cam Jackson, ["Micro Frontends"](https://martinfowler.com/articles/micro-frontends.html) — martinfowler.com; the canonical taxonomy (build-time / run-time / server-side composition).
- Luca Mezzalira, *Building Micro-Frontends* (O'Reilly) — the decision framework (horizontal vs. vertical split).
- [Module Federation](https://module-federation.io/) docs; [Next.js Multi-Zones](https://nextjs.org/docs/app/guides/multi-zones); [single-spa](https://single-spa.js.org/).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
