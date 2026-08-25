# 🌐 Web Quality & Lighthouse — A Study Note

> **Level:** 🟢 Beginner–Intermediate · **Reading time:** ~15 min · **The hub of the web-quality cluster.** Reference: Addy Osmani's **[web-quality-skills](https://github.com/addyosmani/web-quality-skills)** — a measurement-first collection of agent skills for web quality.

**Web quality** is the umbrella over the properties that make a site *good* beyond "it works": **fast** (performance), **usable by everyone** (accessibility), **discoverable** (SEO), and **secure/correct** (best practices). This note is the entry point — the measurement philosophy, the tool (Lighthouse), the best-practices/security pillar, and how the cluster fits together.

> **The one principle that runs through all of it: evidence over aggregate scores.** *"While interface guidelines tell you what to build, web quality tells you how to build it performantly, accessibly, and optimally for search."* And crucially — **an aggregate score is not proof of quality.** A green Lighthouse number is a lab proxy; real quality is measured, issue by issue, against real users.

## Table of contents

- [1. The four pillars](#1-the-four-pillars)
- [2. Lighthouse — the tool](#2-lighthouse--the-tool)
- [3. Field vs. lab measurement](#3-field-vs-lab-measurement)
- [4. Best practices & security](#4-best-practices--security)
- [5. The audit workflow](#5-the-audit-workflow)
- [6. Best practices & anti-patterns](#6-best-practices--anti-patterns)
- [7. Go deeper](#7-go-deeper)

---

## 1. The four pillars

| Pillar | Question | Deep-dive note |
| ------ | -------- | -------------- |
| ⚡ **Performance** | Is it fast and smooth? (LCP/INP/CLS) | [Core Web Vitals & Performance](web-core-vitals-performance.md) |
| ♿ **Accessibility** | Can everyone use it? (WCAG 2.2) | [Web Accessibility](web-accessibility.md) |
| 🔍 **SEO** | Can search engines find & understand it? | [Technical SEO](web-seo.md) |
| 🔒 **Best Practices** | Is it secure, modern, correct? | *this note, §4* |

They reinforce each other: **semantic HTML** helps a11y *and* SEO; **fast pages** help UX, SEO (page experience), and conversions; **HTTPS** is security *and* an SEO signal. Web quality is one discipline with four lenses.

---

## 2. Lighthouse — the tool

**[Google Lighthouse](https://developer.chrome.com/docs/lighthouse/overview/)** is the standard automated auditor. It runs a page and scores it across **Performance, Accessibility, SEO, and Best Practices** (the PWA category was retired). Run it from Chrome DevTools, the CLI (great for CI), or PageSpeed Insights.

Two things to know in 2026:
- **Lighthouse 13+ uses shared "Performance Insights"** with the DevTools Performance panel — the old performance audit IDs were **retired** (some were noisy or inactionable). Follow current insight names; don't recreate removed audits.
- **`lighthouse_audit` intentionally excludes performance.** Diagnose performance via a **DevTools performance trace + focused insights**, and use Lighthouse for the *other* categories (accessibility, SEO, best practices). Stack-agnostic — works with any framework.

> **A Lighthouse score is a lab signal, not a verdict.** 100 on accessibility ≠ WCAG conformance; a high SEO score ≠ good rankings; a high best-practices score ≠ secure. Scores *localize* issues; humans and field data confirm impact.

---

## 3. Field vs. lab measurement

The measurement model that separates real quality work from guessing (the [performance note](web-core-vitals-performance.md) goes deep):

| Evidence | Answers | Type |
| -------- | ------- | ---- |
| **CrUX field data** | How real Chrome users experienced the URL/origin | **Field** — decides impact |
| **First-party RUM** | How *your* users experience it | **Field** |
| **Lighthouse / DevTools trace** | What happened in one controlled session, and why | **Lab** — diagnoses |
| **Static source inspection** | What *might* break when the page can't run | **Hypothesis** |

**Field data decides what matters; lab traces diagnose and verify; source inspection only yields hypotheses.** Never present a lab score as real-user truth, or claim a field win before new user data arrives.

---

## 4. Best practices & security

The "Best Practices" pillar — modern standards, correctness, and **security** (this overlaps heavily with the [Security Fundamentals note](security-fundamentals.md), applied to the browser):

- **HTTPS everywhere, no mixed content.** Add **HSTS** only after every subdomain supports HTTPS.
- **Content Security Policy (CSP)** as defense-in-depth — prefer **nonces/hashes**, test in report-only before enforcing. (Trusted Types for DOM-XSS sinks.)
- **Sanitize untrusted HTML**; prefer text APIs over `innerHTML`; protect DOM-XSS sinks — the [XSS](security-fundamentals.md) rule, client-side.
- **Subresource Integrity (SRI)** + pinned, patched third-party code — the [supply-chain](security-fundamentals.md) surface.
- **Verify response headers at runtime** — source config doesn't prove what the deployed page actually sends.
- **No console errors, valid HTML/doctype, correct charset, no deprecated APIs.**

> A high Lighthouse Best-Practices score is **never proof the app is secure** — it's a smoke test. Real security needs the dependency/header/config review from the [security note](security-fundamentals.md).

---

## 5. The audit workflow

The evidence-led loop the skills encode:

1. **Scope the target** — representative URLs, key journeys, public vs. authenticated, mobile vs. desktop.
2. **Collect a live baseline** *before* searching the codebase — field (CrUX/RUM) + a lab trace/audit.
3. **Localize from runtime failures** — let failed audit nodes point you at the component; don't grep the whole repo for generic patterns.
4. **Categorize by user impact & confidence** — measured findings separate from code-only hypotheses.
5. **Fix the implicated source, then re-run the same checks.** Report what's verified vs. still pending field/human validation.

> This mirrors the debugging discipline elsewhere in the library ([observability](observability.md), [DSA](data-structures-algorithms.md)): **measure → localize the real bottleneck → fix it → verify.** Don't optimize by guess, and don't trust a single aggregate number.

---

## 6. Best practices & anti-patterns

**Do**
- **Treat quality as measured evidence**, not a score to game.
- **Use Lighthouse to localize**, then verify with field data + manual checks.
- **CI-gate** budgets and category audits (Lighthouse CI) so regressions fail the build.
- **Address all four pillars** — they reinforce each other.
- **Keep security findings separate from style preferences.**

**Avoid**
- **"We hit 100" as done** — scores cover subsets; users are the truth.
- **Routing performance through `lighthouse_audit`** — use a performance trace + insights.
- **Treating a Best-Practices score as a security assessment.**
- **Optimizing code the audit never implicated** — chase measured impact.

---

## 7. Go deeper

The web-quality cluster:

- 📝 **[Core Web Vitals & Performance](web-core-vitals-performance.md)** — LCP/INP/CLS and the fixes.
- 📝 **[Web Accessibility](web-accessibility.md)** — WCAG 2.2, POUR, semantic HTML, ARIA.
- 📝 **[Technical SEO](web-seo.md)** — crawl/index/render, metadata, structured data.

And across the library:

- 📝 **[Security Fundamentals](security-fundamentals.md)** — the deep version of the best-practices/security pillar (HTTPS, CSP, XSS, SRI, supply chain).
- 📝 **[Caching](caching-strategies.md)** — CDN/HTTP caching underpins performance.
- 📗 **[Image Optimization — Addy Osmani](../books/)** & the ["How modern browsers work" article](../resources/) — same author, deeper dives.

### Primary references

- Addy Osmani, **[web-quality-skills](https://github.com/addyosmani/web-quality-skills)** — the measurement-first skills this cluster distills.
- [Lighthouse](https://developer.chrome.com/docs/lighthouse/overview/) · [web.dev](https://web.dev/) · [Chrome DevTools](https://developer.chrome.com/docs/devtools/).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
