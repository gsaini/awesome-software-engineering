# ♿ Web Accessibility (a11y) — A Detailed Study Note

> **Level:** 🟢 Beginner–Intermediate · **Reading time:** ~16 min · **Prerequisites:** HTML/CSS basics. Part of the web-quality cluster; reference: Addy Osmani's **[web-quality-skills](https://github.com/addyosmani/web-quality-skills)**.

**Accessibility (a11y)** means building web content that **everyone can use**, including people with disabilities — visual, motor, auditory, cognitive. It's both the right thing to do and, at **WCAG 2.2 Level AA**, a **legal requirement** in many jurisdictions. Good a11y also improves usability and SEO for everyone.

> **The reframe most developers need:** accessibility is not a feature you bolt on — it's mostly **using the platform correctly**. Semantic HTML, real buttons, labeled inputs, and logical headings get you most of the way *for free*. Accessibility problems are usually the result of *fighting* the platform (a `<div>` pretending to be a button), not a lack of special APIs.

## Table of contents

- [1. WCAG: POUR & conformance levels](#1-wcag-pour--conformance-levels)
- [2. Automated ≠ conformance](#2-automated--conformance)
- [3. The high-impact fixes](#3-the-high-impact-fixes)
- [4. Semantic HTML & the accessibility tree](#4-semantic-html--the-accessibility-tree)
- [5. ARIA — and the first rule of ARIA](#5-aria--and-the-first-rule-of-aria)
- [6. Keyboard & screen-reader support](#6-keyboard--screen-reader-support)
- [7. Best practices & anti-patterns](#7-best-practices--anti-patterns)
- [8. Go deeper](#8-go-deeper)

---

## 1. WCAG: POUR & conformance levels

**WCAG** (Web Content Accessibility Guidelines, current: **2.2**) is organized by four principles — **POUR**:

| Principle | Means |
| --------- | ----- |
| **P**erceivable | Content can be perceived through different senses (text alternatives, contrast, captions) |
| **O**perable | The interface works for all input methods (keyboard, not just mouse) |
| **U**nderstandable | Content and behavior are predictable and clear |
| **R**obust | Works with assistive technologies (screen readers), now and as tech evolves |

**Conformance levels:**

| Level | Requirement | Target |
| ----- | ----------- | ------ |
| **A** | Minimum | Must pass |
| **AA** | Standard | **Should pass — the legal bar in many jurisdictions** (ADA, EN 301 549, European Accessibility Act) |
| **AAA** | Enhanced | Nice to have; rarely required wholesale |

**AA is the practical target.** Design and test to it.

---

## 2. Automated ≠ conformance

The most important caveat: **automated tools (Lighthouse, axe) catch only a *subset* of accessibility barriers** — roughly a third.

- A **Lighthouse accessibility score of 100 is *not* WCAG conformance.**
- A **low score doesn't replace issue-level evidence** either.
- Automated tools find *machine-detectable* issues (missing alt, low contrast, unlabeled inputs). They **cannot** judge whether alt text is *meaningful*, whether focus order is *logical*, or whether a custom widget is *actually usable* with a screen reader.

**So the workflow is: automated audit to localize issues → then manual checks** (keyboard-only navigation, a screen-reader pass, inspecting the accessibility tree). Automation narrows where to look; humans confirm usability.

---

## 3. The high-impact fixes

The issues that account for most real-world failures (WebAIM's annual analysis finds the same handful dominate):

- **Missing/poor image `alt` text** — every meaningful image needs a text alternative; decorative images get `alt=""`.
- **Low color contrast** — text vs. background must meet **4.5:1** (normal text) / **3:1** (large text) for AA.
- **Missing form labels** — every input needs a programmatically associated `<label>` (not just a placeholder).
- **Missing document language** — `<html lang="en">`.
- **Empty links/buttons** — links and buttons need discernible text (an icon button needs an accessible name).
- **Broken heading/landmark structure** — one `<h1>`, logical nesting, real landmarks (`<nav>`, `<main>`, `<header>`).

---

## 4. Semantic HTML & the accessibility tree

The browser builds an **accessibility tree** from your DOM — the structured representation (names, **roles**, **states**, landmarks, headings) that assistive tech consumes. Semantic HTML populates it correctly *automatically*:

- `<button>` → role "button", focusable, keyboard-activatable, announced correctly. A `<div onclick>` gets **none** of that.
- `<nav>`, `<main>`, `<h1>`–`<h6>`, `<label>`, `<table>` → landmarks, headings, associations for free.
- **Focus order** should follow reading order; don't break it with positive `tabindex`.

> Inspect the **accessibility tree** (DevTools) — names/roles/states — rather than eyeballing the visual UI. That's what a screen-reader user actually experiences.

---

## 5. ARIA — and the first rule of ARIA

**ARIA** (Accessible Rich Internet Applications) adds roles/states/properties for cases native HTML can't express (custom widgets, live regions). But:

> **The first rule of ARIA: don't use ARIA.** If a native HTML element does the job, use it — native semantics are more robust and less error-prone. **Bad ARIA is worse than no ARIA** (a wrong `role` actively misleads assistive tech).

When you *do* need it (a custom combobox, tabs, a live region announcing async updates), follow the **ARIA Authoring Practices** patterns exactly — including the keyboard interactions they specify.

---

## 6. Keyboard & screen-reader support

Two manual checks that catch what automation can't:

- **Keyboard-only:** unplug the mouse. Can you reach and operate *everything* with Tab/Shift-Tab/Enter/Space/arrows? Is there a **visible focus indicator**? Can you escape modals? Is there a **skip-to-content** link? No keyboard trap?
- **Screen reader:** run VoiceOver (macOS), NVDA (Windows), or TalkBack (Android). Are names/roles/states announced sensibly? Do dynamic updates get announced (live regions)? Does the reading order make sense?

These two passes find the majority of issues a score of 100 misses.

---

## 7. Best practices & anti-patterns

**Do**
- **Use semantic HTML first** — real buttons, labels, headings, landmarks.
- **Target WCAG 2.2 AA**; treat it as the baseline, not the ceiling.
- **Automate to localize, then verify manually** (keyboard + screen reader).
- **Meet contrast ratios** and provide meaningful alt text.
- **Maintain a visible focus indicator** and logical focus order.

**Avoid**
- **`<div>`/`<span>` as interactive controls** — you re-implement (badly) what `<button>`/`<a>` give free.
- **Treating a 100 score as "accessible"** — it covers ~a third of barriers.
- **ARIA when native HTML would do**, or wrong ARIA roles.
- **Placeholder-as-label** — placeholders vanish and aren't reliable labels.
- **Removing focus outlines** (`outline: none`) with no replacement — a keyboard user is now lost.

---

## 8. Go deeper

Related material in this library:

- 📝 **[Web Quality & Lighthouse](web-quality-lighthouse.md)** — the umbrella (accessibility is one Lighthouse category).
- 📝 **[Core Web Vitals & Performance](web-core-vitals-performance.md)** · **[Technical SEO](web-seo.md)** — the rest of the cluster (accessible, semantic markup also helps SEO).

### Primary references

- Addy Osmani, **[web-quality-skills](https://github.com/addyosmani/web-quality-skills)** — the accessibility skill this note distills.
- [WCAG 2.2](https://www.w3.org/TR/WCAG22/) · [ARIA Authoring Practices Guide (APG)](https://www.w3.org/WAI/ARIA/apg/) · [WebAIM](https://webaim.org/) · [The A11Y Project](https://www.a11yproject.com/).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
