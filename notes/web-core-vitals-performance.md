# ⚡ Core Web Vitals & Web Performance — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~20 min · **Prerequisites:** basic web (HTML/CSS/JS, HTTP). Pairs with the [Caching](caching-strategies.md) note and the ["How modern browsers work" resource](../resources/). Reference: Addy Osmani's **[web-quality-skills](https://github.com/addyosmani/web-quality-skills)**.

**Web performance** is how fast and smooth a page feels to real users; **Core Web Vitals (CWV)** are Google's three headline metrics for it. This note covers the metrics, the *measurement-first* discipline that separates real optimization from guessing, and the concrete fixes per metric.

> **The core discipline — measure before you optimize.** The single biggest mistake is "optimizing" from intuition. Real performance work is **evidence-led**: use **field data** (how real users actually experienced the page) to decide *what's worth fixing*, and **lab traces** to diagnose *why* and verify a fix. Guessing from source code alone produces *hypotheses*, not measured wins.

## Table of contents

- [1. Field vs. lab: the four evidence types](#1-field-vs-lab-the-four-evidence-types)
- [2. The three Core Web Vitals](#2-the-three-core-web-vitals)
- [3. LCP — loading](#3-lcp--loading)
- [4. INP — interactivity](#4-inp--interactivity)
- [5. CLS — visual stability](#5-cls--visual-stability)
- [6. Performance budgets](#6-performance-budgets)
- [7. The critical rendering path](#7-the-critical-rendering-path)
- [8. Best practices & anti-patterns](#8-best-practices--anti-patterns)
- [9. Go deeper](#9-go-deeper)

---

## 1. Field vs. lab: the four evidence types

Keep these **explicit** — conflating them is how bad performance decisions get made:

| Evidence | What it answers | Nature |
| -------- | --------------- | ------ |
| **CrUX field data** | How real Chrome users experienced the URL/origin (recent window) | **Field** — real users, decides impact |
| **First-party RUM** | How *your* users experience routes, releases, devices | **Field** — your real users |
| **Lighthouse / DevTools trace** | What happened in one controlled browser session, and why | **Lab** — reproduces & diagnoses |
| **Static source inspection** | What *might* be a problem when the page can't run | **Hypothesis** only |

> **Field data determines user impact; lab traces reproduce and diagnose it.** A `PerformanceObserver` snippet run once in your browser is a *lab* observation, not real-user data. And after a fix, you can verify it in the lab immediately — but **field improvement lags**, because CrUX/RUM need new user visits to reflect it. Never claim a field win the moment you ship.

---

## 2. The three Core Web Vitals

Measured at the **75th percentile** of page visits (75% must hit "Good"):

| Metric | Measures | 🟢 Good | 🟡 Needs work | 🔴 Poor |
| ------ | -------- | :-----: | :-----------: | :-----: |
| **LCP** (Largest Contentful Paint) | Loading | ≤ 2.5s | 2.5–4s | > 4s |
| **INP** (Interaction to Next Paint) | Interactivity | ≤ 200ms | 200–500ms | > 500ms |
| **CLS** (Cumulative Layout Shift) | Visual stability | ≤ 0.1 | 0.1–0.25 | > 0.25 |

> ⚠️ **INP replaced FID** (First Input Delay) as a Core Web Vital in **March 2024**. INP is stricter and more representative — it measures the latency of *all* interactions across the page's life, not just the first. If a resource still says "FID," it's dated.

---

## 3. LCP — loading

**What:** when the largest visible element (usually a hero image/video, or a big text block) finishes rendering. LCP is dominated by **how fast that one element arrives and paints**.

**Common causes → fixes:**
- **Slow server response (high TTFB)** → CDN, caching ([caching note](caching-strategies.md)), faster backend, edge rendering.
- **Render-blocking CSS/JS** → inline critical CSS, defer/async non-critical JS, minimize blocking resources.
- **The LCP resource loads late** → **`fetchpriority="high"`** and/or **`<link rel="preload">`** the LCP image; don't lazy-load it.
- **Heavy images** → modern formats (**AVIF/WebP**), responsive `srcset`/`sizes`, right-size for the viewport (Addy Osmani's *Image Optimization* book, in [books/](../books/), is the deep dive).
- **Client-side rendering delay** → SSR/streaming so content isn't gated on a JS bundle.

> Rule: **find the actual LCP element** (DevTools/Lighthouse names it), then make *its* delivery path fast — don't optimize images that aren't the LCP.

---

## 4. INP — interactivity

**What:** the latency from a user interaction (click/tap/keypress) to the next frame painting — i.e. **does the page respond snappily?** Poor INP = janky, laggy UI. It's almost always a **main-thread / JavaScript** problem.

**Common causes → fixes:**
- **Long tasks blocking the main thread** → break them up; **yield to the main thread** (`await scheduler.yield()` / `setTimeout`), so the browser can paint.
- **Too much JS executing on interaction** → reduce/defer JS, code-split, remove unused code ([Knip](knip.md) for dead code).
- **Expensive event handlers / re-renders** → debounce/throttle; avoid huge synchronous re-renders; memoize.
- **Heavy work that isn't UI** → move to a **Web Worker** (off the main thread) — the [concurrency](concurrency-parallelism.md) instinct, in the browser.

> INP is where the [concurrency note](concurrency-parallelism.md) meets the browser: the main thread is single-threaded, so a long task freezes *everything*. Keep tasks short; yield often.

---

## 5. CLS — visual stability

**What:** how much visible content **shifts unexpectedly** during load (the "I tapped the wrong button because it jumped" metric). A layout shift score = impact × distance.

**Common causes → fixes:**
- **Images/videos/iframes without dimensions** → always set `width`/`height` or CSS **`aspect-ratio`** so the browser reserves space.
- **Ads/embeds/injected content** → reserve a fixed slot; don't insert content *above* existing content.
- **Web fonts (FOIT/FOUT swap)** → `font-display: optional/swap`, preload key fonts, size-adjust fallbacks to match metrics.
- **Dynamically injected UI** (banners, cookie bars) → reserve space or overlay rather than push content down.

> Rule: **reserve space for anything that arrives late.** Most CLS is "the browser didn't know how big something would be."

---

## 6. Performance budgets

Budgets turn "make it fast" into an enforceable number. Starting guardrails for a typical content/commerce page (**calibrate to your target devices/networks** — these aren't universal pass/fail):

| Resource | Budget | Why |
| -------- | ------ | --- |
| Total page weight | < 1.5 MB | Bounds transfer time on constrained networks |
| JavaScript (compressed) | < 300 KB | Protect parse/execution cost (INP!) |
| CSS (compressed) | < 100 KB | Limit render-blocking work |
| Above-fold images | < 500 KB | Protect the likely LCP resource |
| Fonts | < 100 KB | Limit critical font transfer |
| Third-party | < 200 KB | Bound code outside your control |

Enforce budgets in **CI** (Lighthouse CI, bundlesize) so regressions fail the build — the shift-left / [testing](testing-strategy.md) instinct applied to performance.

---

## 7. The critical rendering path

Everything above ladders up to one idea: **minimize the work between "request the page" and "the user sees/uses content."**

1. **Server response (TTFB)** — fast backend + CDN + caching.
2. **Render-blocking resources** — CSS blocks rendering, synchronous JS blocks parsing → inline critical CSS, defer the rest.
3. **Resource loading order** — prioritize the LCP resource (`fetchpriority`, `preload`), lazy-load below-the-fold.
4. **Main-thread work** — keep JS lean and tasks short (INP).
5. **Layout stability** — reserve space (CLS).

> **Lighthouse 13+** now shares **Performance Insights** with the DevTools Performance panel (the old audit IDs were retired for noisier ones). Diagnose performance via a **DevTools performance trace + focused insights**, not the generic Lighthouse score — and don't route performance through `lighthouse_audit`, which covers the *other* categories (see the [Web Quality overview](web-quality-lighthouse.md)).

---

## 8. Best practices & anti-patterns

**Do**
- **Measure first** — field data to prioritize, lab traces to diagnose and verify.
- **Optimize the actual bottleneck** (the named LCP element, the specific long task) — not by guess.
- **Set and CI-enforce performance budgets.**
- **Reserve space** for late-arriving content (CLS); **prioritize** the LCP resource; **keep tasks short** (INP).
- **Use modern image formats + responsive images.**

**Avoid**
- **Optimizing from intuition** — "this feels slow" ≠ evidence.
- **Claiming a field win the moment you deploy** — field data lags.
- **Lazy-loading the LCP image** (delays the very thing being measured).
- **Treating a Lighthouse *score* as the goal** — it's a lab proxy; real users (CrUX/RUM) are the truth.
- **Shipping unbounded JS** — it's the usual INP and page-weight culprit.

---

## 9. Go deeper

Related material in this library:

- 📝 **[Caching Strategies](caching-strategies.md)** — CDN/HTTP caching cut TTFB (LCP) and repeat-visit cost.
- 📝 **[Concurrency & Parallelism](concurrency-parallelism.md)** — the main thread is single-threaded; long tasks freeze the UI (INP); Web Workers offload.
- 📝 **[Knip](knip.md)** — remove dead JS to shrink bundles.
- 📗 **[Image Optimization — Addy Osmani](../books/)** & the ["How modern browsers work" article](../resources/) — the deep dives behind LCP and the rendering pipeline.
- 📝 **[Web Quality & Lighthouse](web-quality-lighthouse.md)** · **[Web Accessibility](web-accessibility.md)** · **[Technical SEO](web-seo.md)** — the rest of the web-quality cluster.

### Primary references

- Addy Osmani, **[web-quality-skills](https://github.com/addyosmani/web-quality-skills)** — the measurement-first skills this note distills.
- [web.dev — Core Web Vitals](https://web.dev/vitals/) and [Learn Performance](https://web.dev/learn/performance/).
- [Chrome UX Report (CrUX)](https://developer.chrome.com/docs/crux/) — the field-data source.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
