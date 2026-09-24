# 📜 Single-Axis Scroll Containers — One Axis Scrolls, the Other Clips

> **Level:** 🟡 Intermediate · **Reading time:** ~11 min · **Prerequisites:** `overflow` basics, `position: sticky`.
> **Status when written:** 2026-09-23 — in pre-stable Chrome only. See [§7](#7-availability-and-status).

CSS has never had a way to say *"scroll horizontally, and genuinely don't scroll vertically."* Ask for one scrollable axis and the browser quietly made the other one scrollable too. **Single-axis scroll containers** fix that: pair a scrollable value with `clip`, and only one axis becomes a scroller.

```css
.table-wrapper {
  overflow-x: auto;
  overflow-y: clip; /* not "hidden" — clip means: this axis is not a scroller at all */
}
```

> The interesting part isn't the new syntax — `overflow: auto clip` has parsed for years. It's that **the two axes finally get independent answers to "who is my scroll container?"**, which is what `position: sticky` has to ask.

## Table of contents

- [1. The quirk: one scrollable axis infected the other](#1-the-quirk-one-scrollable-axis-infected-the-other)
- [2. The case everyone hits: a table with a sticky row *and* column](#2-the-case-everyone-hits-a-table-with-a-sticky-row-and-column)
- [3. What actually changed](#3-what-actually-changed)
- [4. Side effects worth testing](#4-side-effects-worth-testing)
- [5. Feature detection: the obvious way is wrong](#5-feature-detection-the-obvious-way-is-wrong)
- [6. Check it yourself in 20 lines](#6-check-it-yourself-in-20-lines)
- [7. Availability and status](#7-availability-and-status)
- [8. Best practices & anti-patterns](#8-best-practices--anti-patterns)
- [9. Go deeper](#9-go-deeper)

---

## 1. The quirk: one scrollable axis infected the other

[CSS Overflow 3](https://drafts.csswg.org/css-overflow-3/) splits `overflow` values in two groups:

- **Scrollable values** — `scroll`, `auto`, `hidden`. Each *"cause[s] the box to be a scroll container and the affected axis to be a scrollable axis."* Note that `hidden` is in this group: no scrollbar, but scripts can still scroll it.
- **Non-scrollable values** — `visible` and `clip`.

Two propagation rules then get in the way:

1. **`visible` becomes `auto`** when the other axis is scrollable: *"if the other axis specifies a scrollable value, a specified value of `visible` computes to `auto`."* This one is unavoidable and **isn't changing** — `visible` content paints outside the box, which is incompatible with a scrollport that has to clip.
2. **`clip` became `hidden`** under the same condition. This was the blocker, and it is what the new feature removes.

Rule 2 meant that *every* attempt at a one-axis scroller produced a two-axis one:

| You wrote | Computed (before) | What you got |
| --------- | ----------------- | ------------ |
| `overflow: auto visible` | `auto` / `auto` | Two-dimensional scroller |
| `overflow: auto hidden` | `auto` / `hidden` | Two-dimensional scroller; y scrollable by script only |
| `overflow: auto clip` | `auto` / **`hidden`** | Two-dimensional scroller — the `clip` was thrown away |

I measured that last row in a Chrome build without the feature: `overflow: auto clip` computed to `auto` / `hidden`, and setting `scrollTop = 200` on the supposedly clipped axis **moved it to 200**. The axis was a scroller all along.

## 2. The case everyone hits: a table with a sticky row *and* column

A wide table that scrolls sideways, with a header row that sticks to the page and a first column that sticks to the wrapper:

```html
<div class="table-wrapper"><table>…</table></div>
```

```css
.table-wrapper { overflow-x: auto; }
.table-wrapper thead { position: sticky; top: 0; }       /* should stick to the page */
.table-wrapper td:first-child { position: sticky; left: 0; } /* should stick to the wrapper */
```

This doesn't work, and the reason is rule 2. `position: sticky` resolves **per axis** against the nearest ancestor scroller for that axis — but because the wrapper became a scroller on *both* axes, both lookups stop at the wrapper. The header sticks to a box that never scrolls vertically, so it never appears to stick at all.

Adding one line fixes it:

```css
.table-wrapper {
  overflow-x: auto;
  overflow-y: clip; /* or the shorthand: overflow: auto clip */
}
```

Now the vertical lookup skips the wrapper and finds the document scroller, while the horizontal lookup still finds the wrapper. Chrome's post has [live](https://codepen.io/web-dot-dev/pen/VYmjePe) [demos](https://codepen.io/web-dot-dev/pen/ZYeQryE) of both states.

## 3. What actually changed

The editor's draft of CSS Overflow 3 now says it outright:

> *"If neither axis computes to a scrollable value, the box is not a scroll container. If only one axis computes to a scrollable value (i.e. the other axis is `clip`), the box is a **single-axis scroll container**."*

So the spec work is done ([CSSWG discussion #8286](https://github.com/w3c/csswg-drafts/issues/8286)); Chrome is now asking the web to test the behaviour change before it reaches stable.

**`clip` is not a quieter `hidden`** — the difference is the whole mechanism:

| | `hidden` | `clip` |
| - | -------- | ------ |
| Scroll container on that axis? | **Yes** | **No** |
| User can scroll it | No | No |
| Script can scroll it (`scrollTo`, `scrollIntoView`) | **Yes** | **No** — *"forbids scrolling entirely, through any mechanism"* |
| Establishes a formatting context | Yes | **No** (add `display: flow-root` if you need one) |
| Can extend the clip edge | — | Yes, with `overflow-clip-margin` |

## 4. Side effects worth testing

This is a behaviour change to something as load-bearing as "what is a scroll container", so Chrome flags four knock-on effects:

| Behaviour | What changes |
| --------- | ------------ |
| **`position: sticky`** | Now resolves per axis, so elements stick where you meant. Sticky things that accidentally "worked" against the wrong scroller can move. |
| **`overscroll-behavior`** | No longer fires on a clipped axis, so custom pull-to-refresh or bounce effects tied to it stop triggering. Chrome notes this **matches Firefox and Safari**. |
| **Programmatic scrolling** | `Element.scrollTo()` and friends now **refuse to move** the clipped axis — it stays at 0. Code that nudged that axis silently becomes a no-op. |
| **Flex & grid minimum sizing** | ⚠️ The sleeper. Scroll containers normally have their automatic minimum size (`min-width: auto`) ignored so cells can shrink. On an axis set to `clip`, that minimum **starts applying again**, so a flex or grid item can grow and blow out your layout. |

Chrome tested 190 sites and found no significant breakage, but the ask is explicit: run your app on Beta/Dev/Canary and [file a bug](https://crbug.com/new) if scrolling misbehaves.

## 5. Feature detection: the obvious way is wrong

```css
/* ❌ Lies. This only asks "can you parse these two keywords?" */
@supports (overflow: scroll clip) { … }

/* ✅ Asks whether the browser implements the behaviour */
@supports named-feature(single-axis-scroll-container) { … }
```

Two-value `overflow` has parsed since Chrome 68 / Firefox 61 / Safari 13.1, and `clip` since Chrome 90 / Firefox 81 / Safari 16 — so the value test passes almost everywhere while the behaviour exists almost nowhere. My probe confirmed it: `CSS.supports('overflow', 'auto clip')` returned **`true`** in a build where the behaviour was **off**.

`named-feature()` ([CSSWG #13677](https://github.com/w3c/csswg-drafts/issues/13677)) exists for exactly this class of undetectable behaviour changes; it arrived in Chrome 150. In JS: `CSS.supports("named-feature(single-axis-scroll-container)")`.

## 6. Check it yourself in 20 lines

```html
<style>
  #probe { overflow: auto clip; width: 100px; height: 100px; }
  #probe > div { width: 900px; height: 900px; }
</style>
<div id="probe"><div></div></div>
<script>
  const el = document.getElementById('probe');
  const cs = getComputedStyle(el);
  el.scrollTop = 200; // try to scroll the axis that should be clipped
  console.log({
    named_feature: CSS.supports('named-feature(single-axis-scroll-container)'),
    parses: CSS.supports('overflow', 'auto clip'),
    computed: [cs.overflowX, cs.overflowY],
    scrollTop_after: el.scrollTop,
  });
</script>
```

| | Without the feature (measured, Chrome 153 build) | With the feature |
| - | ------------------------------------------------ | ---------------- |
| `named_feature` | `false` | `true` |
| `parses` | `true` ← why the value test is useless | `true` |
| `computed` | `["auto", "hidden"]` | `["auto", "clip"]` |
| `scrollTop_after` | `200` (it scrolled!) | `0` (refused) |

## 7. Availability and status

| | |
| - | - |
| **Testable in** | Chrome **153+** Beta / Dev / Canary — **no flag needed** (announced 2026-09-04) |
| **Chrome stable** | Not yet. Chrome's [feature entry](https://chromestatus.com/feature/5067363861004288) lists **Chrome 156** (stable ~20 Oct 2026) as the shipping milestone; stable was 155 when this note was written |
| **Spec** | Defined in the [CSS Overflow 3 editor's draft](https://drafts.csswg.org/css-overflow-3/#overflow-properties) |
| **Firefox / Safari** | **No signal** yet — [mozilla#1418](https://github.com/mozilla/standards-positions/issues/1418), [WebKit#680](https://github.com/WebKit/standards-positions/issues/680) |
| **Also cooking** | Per-axis [scroll-snap propagation](https://chromestatus.com/feature/5086608215900160) for these containers, and `scroll-axis-lock` (Chrome 153) |

**The fallback is graceful**, which is what makes this safe to adopt early: in an engine without the behaviour, `overflow: auto clip` computes to `auto hidden` — exactly today's behaviour. You get the fix where it's implemented and the status quo everywhere else. Treat it as progressive enhancement and don't build a layout that *requires* it until it's broadly shipped.

## 8. Best practices & anti-patterns

**Do**
- ✅ Use `overflow: auto clip` (or `clip auto`) for the sticky-header-and-column table, carousels, and any "one direction only" scroller.
- ✅ Detect with `named-feature(single-axis-scroll-container)`, never with a value query.
- ✅ Re-check flex and grid items after adding `clip` — the automatic minimum size comes back and can widen the layout.
- ✅ Audit code that scrolls the clipped axis programmatically; it will silently stop working.
- ✅ Keep `overflow-clip-margin` in mind when a box shadow, focus ring, or outline needs to paint past the clip edge.

**Avoid**
- ❌ **Clipping an axis whose content users still need to reach.** `clip` forbids *all* scrolling, so overflow on that axis becomes unreachable — by mouse, by keyboard, and by `scrollIntoView` from a screen reader's focus move. `hidden` at least stays scriptable. Only clip an axis that genuinely has nothing to get to. See [Web Accessibility](web-accessibility.md).
- ❌ Reaching for `clip` as a "tidier `hidden`". They differ in scrollability *and* formatting context.
- ❌ Expecting `overflow-y: visible` to survive next to a scrollable axis. That propagation rule is by design and isn't going away.
- ❌ Sniffing Chrome versions instead of the feature. Availability here is channel-gated, not just version-gated.

## 9. Go deeper

Related material in this library:

- 📝 **[Web Accessibility](web-accessibility.md)** — why content you can't scroll to is content some users can't reach at all.
- 📝 **[Core Web Vitals & Performance](web-core-vitals-performance.md)** — scroll containers, sticky elements, and layout stability are all CLS-adjacent.
- 📝 **[Feature Flags & Progressive Delivery](feature-flags-progressive-delivery.md)** — Chrome is canarying a platform change across channels; feature detection is your flag, and graceful fallback is your kill switch.

### Primary references

- [Ready for developer testing: Single-axis scroll containers](https://developer.chrome.com/blog/single-axis-scroll-containers-ready-for-testing) — Chrome for Developers, 2026-09-04 (Bramus Van Damme, Free Debreuil).
- [Single-axis scroll containers explainer](https://github.com/explainers-by-googlers/single-axis-scroll-containers/blob/main/README.md).
- [CSS Overflow Module Level 3](https://drafts.csswg.org/css-overflow-3/#overflow-properties) — the `overflow` property, propagation rules, and the single-axis definition.
- [CSS `position: sticky` now sticks per axis](https://www.bram.us/2026/03/30/css-sticky-per-axis/) and [feature detecting "undetectable" CSS features](https://www.bram.us/2026/08/27/feature-detecting-undetectable-css-features-with-supports-named-feature/) — bram.us.
- [MDN: `overflow`](https://developer.mozilla.org/en-US/docs/Web/CSS/overflow) · [`overflow-clip-margin`](https://developer.mozilla.org/en-US/docs/Web/CSS/overflow-clip-margin).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
