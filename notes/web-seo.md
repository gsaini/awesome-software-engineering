# 🔍 Technical SEO for Developers — A Detailed Study Note

> **Level:** 🟢 Beginner–Intermediate · **Reading time:** ~16 min · **Prerequisites:** HTTP + HTML basics. Part of the web-quality cluster; reference: Addy Osmani's **[web-quality-skills](https://github.com/addyosmani/web-quality-skills)**.

**SEO** (Search Engine Optimization) is making a site discoverable and understandable to search engines. This note is the **technical, developer-owned** half — crawlability, indexability, metadata, and structured data — *not* content strategy or link building. It's the part you can actually verify and fix in code.

> **The honest framing:** technical SEO gets a page **crawled, indexed, and correctly represented**. It does **not** guarantee rankings — those depend on content quality, authority, and competition you don't control. A **Lighthouse SEO score covers a useful *subset* of technical checks; it is not a ranking prediction.** Separate *technical* findings (fixable, verifiable) from *content/authority* (not something you promise with a percentage).

## Table of contents

- [1. The pipeline: crawl → index → rank](#1-the-pipeline-crawl--index--rank)
- [2. Crawlability](#2-crawlability)
- [3. Indexability](#3-indexability)
- [4. On-page metadata & semantics](#4-on-page-metadata--semantics)
- [5. Structured data](#5-structured-data)
- [6. Rendering & JavaScript SEO](#6-rendering--javascript-seo)
- [7. Page experience & Core Web Vitals](#7-page-experience--core-web-vitals)
- [8. Best practices & anti-patterns](#8-best-practices--anti-patterns)
- [9. Go deeper](#9-go-deeper)

---

## 1. The pipeline: crawl → index → rank

Three stages, and technical SEO owns the first two:

1. **Crawl** — a bot (Googlebot) discovers and fetches your URLs. Blocked here → invisible.
2. **Index** — the engine renders, understands, and stores the page. Not indexed → can't rank.
3. **Rank** — the engine orders indexed pages for a query (content, authority, relevance, page experience).

> Get a page crawled and indexed correctly, and represented with accurate metadata — that's the developer's job. Ranking is a *content + authority* game on top.

---

## 2. Crawlability

Can bots find and fetch your pages?

- **`robots.txt`** — tells crawlers what they may/may not fetch. A misconfigured `Disallow: /` silently deindexes a whole site. Don't block resources (CSS/JS) the page needs to render.
- **XML `sitemap.xml`** — lists your canonical URLs to help discovery; reference it from `robots.txt` and submit in Search Console. Keep it current (no dead/`noindex` URLs).
- **Internal linking** — bots follow links; orphaned pages (no inbound links) may never be found.
- **HTTP status & redirects** — serve `200` for real pages, `301` for permanent moves (preserves signals), `404`/`410` for gone. Avoid redirect chains.

---

## 3. Indexability

Once crawled, may the page be indexed, and which version is canonical?

- **`<meta name="robots" content="index,follow">`** (or `noindex` to keep a page out). The `X-Robots-Tag` HTTP header does the same for non-HTML.
- **Canonical tags** — `<link rel="canonical" href="…">` tells the engine the *preferred* URL when duplicates exist (tracking params, http/https, trailing slashes). **Canonical consistency across templates** is a frequent bug — verify it, don't assume.
- **One page, one canonical** — conflicting canonicals or self-referencing errors confuse indexing.

---

## 4. On-page metadata & semantics

What the engine (and social previews) show:

- **`<title>`** — unique, descriptive, keyword-relevant, ~50–60 chars. The single most important on-page tag.
- **`<meta name="description">`** — the snippet; doesn't affect ranking directly but drives click-through.
- **Heading structure** — one `<h1>` describing the page; logical `<h2>`/`<h3>` outline (also an [accessibility](web-accessibility.md) win).
- **Semantic HTML** — helps engines parse meaning (the same semantic markup that helps a11y).
- **Open Graph / Twitter Cards** — control link previews on social/messaging.
- **Descriptive, stable URLs** and `hreflang` for internationalized pages.

---

## 5. Structured data

**Structured data** (schema.org, usually as **JSON-LD**) annotates page meaning — products, articles, recipes, FAQs, breadcrumbs — so engines can understand it and *potentially* show **rich results** (stars, prices, FAQ accordions).

- **JSON-LD** in a `<script type="application/ld+json">` is Google's preferred format.
- Structured data gives **eligibility** for rich results, **not a guarantee** — and it must **match the visible page** (marking up content that isn't there is a violation).
- Validate with the Rich Results Test / schema validator; Lighthouse checks *syntax/eligibility signals*, not guaranteed results.

---

## 6. Rendering & JavaScript SEO

Modern SPAs create a real risk: **if content only exists after JavaScript runs, can the crawler see it?**

- **SSR / SSG** (server-side rendering / static generation) → HTML arrives complete → best for SEO and reliable.
- **CSR** (client-side rendering) → the crawler must render JS to see content; Google can, but it's slower, budget-limited, and less reliable for other engines/social scrapers.
- **Rule:** for content that must be indexed, **render it server-side** (or pre-render). Don't gate critical content or metadata behind client-only JS.

> This is the [exposing-React / rendering](exposing-react-to-other-apps.md) trade-off through an SEO lens: route-level SSR (Next.js, Astro, etc.) makes content crawler-visible *and* fast.

---

## 7. Page experience & Core Web Vitals

**Core Web Vitals are a ranking signal** (part of Google's "page experience"). They're a *tiebreaker*, not the dominant factor — content relevance and authority still lead — but a slow, unstable page is a real disadvantage.

- The **[Core Web Vitals & Performance note](web-core-vitals-performance.md)** is the deep dive; SEO just links to that measured evidence rather than re-deriving it.
- Also table stakes: **HTTPS**, **mobile-friendliness** (responsive, mobile-first indexing), and no intrusive interstitials.

---

## 8. Best practices & anti-patterns

**Do**
- **Server-render (or pre-render) content that must be indexed.**
- **Give every page a unique `<title>` + description** and one clear `<h1>`.
- **Set correct canonicals** and verify consistency across templates.
- **Keep `robots.txt`/sitemap accurate**; don't block render-critical resources.
- **Add valid JSON-LD** that matches visible content.
- **Separate technical fixes from content/authority** — and don't promise rankings.

**Avoid**
- **Blocking CSS/JS in `robots.txt`** (the page renders wrong for the bot).
- **Content/metadata only available after client-side JS.**
- **Duplicate content with no canonical.**
- **Structured data that doesn't match the page** (a manual-action risk).
- **Treating the Lighthouse SEO score as a ranking guarantee.**

---

## 9. Go deeper

Related material in this library:

- 📝 **[Core Web Vitals & Performance](web-core-vitals-performance.md)** — the measured page-experience signal SEO depends on.
- 📝 **[Web Accessibility](web-accessibility.md)** — semantic markup helps both a11y and SEO.
- 📝 **[Web Quality & Lighthouse](web-quality-lighthouse.md)** — the umbrella (SEO is one Lighthouse category).
- 📝 **[Exposing React to Other Apps](exposing-react-to-other-apps.md)** — SSR vs. CSR and route-level rendering.

### Primary references

- Addy Osmani, **[web-quality-skills](https://github.com/addyosmani/web-quality-skills)** — the SEO skill this note distills.
- [Google Search Central — SEO docs](https://developers.google.com/search/docs) · [schema.org](https://schema.org/) · [Rich Results Test](https://search.google.com/test/rich-results).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
