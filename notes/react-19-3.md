# ⚛️ React 19.3 — Release Summary

> **Level:** 🟡 Intermediate · **Reading time:** ~10 min · **Released:** 2026-09-09 · **Source:** the official [React 19.3 announcement](https://react.dev/blog/2026/09/09/react-19-3).
> Pairs with the [Exposing React to Other Apps](exposing-react-to-other-apps.md), [Security Fundamentals](security-fundamentals.md), and [Technical SEO](web-seo.md) notes.

React 19.3 is a **stabilisation-and-polish release**: two long-experimental features (**View Transitions** and **Fragment Refs**) graduate to stable, a new **`browser()`** primitive gives you explicit control over server-vs-client rendering, **Trusted Types** finally work, and Server Components lose a chunk of Context boilerplate. **No breaking changes.**

> **The theme:** this release is mostly about **giving you precise control over moments React previously hid** — *when* an animation happens (View Transitions + Transition types), *which* DOM nodes a component can reach (Fragment Refs), and *where* a component renders (`browser()`). Each replaces a common workaround with a first-class API.

## At a glance

| Feature | Status | What it's for |
| ------- | ------ | ------------- |
| **`<ViewTransition>`** | 🎉 now **stable** | Animate elements as they enter/exit/move, via the browser View Transition API |
| **`addTransitionType`** | new | Vary the animation by the *cause* of the update (e.g. next vs. previous) |
| **Fragment Refs** | 🎉 now **stable** | A ref to a *group* of DOM nodes — no wrapper element needed |
| **`browser()`** | new (`react-dom`) | Opt a component out of SSR; suspend on the server only |
| **Trusted Types** | new support | Pass `TrustedHTML`/`TrustedScript` through without string coercion |
| **RSC Context** | improved | Server Components can render a client `Context` directly — no Provider wrapper |

---

## 1. View Transitions (stable)

Wrap anything in `<ViewTransition>` and React animates it using the browser's View Transition API:

```jsx
import { ViewTransition } from 'react';

{isShowing && (
  <ViewTransition>
    <Component />
  </ViewTransition>
)}
```

React picks the animation from what changed in the tree — **enter** (added), **exit** (removed), **update** (children changed), and **share** (a *named* transition removed in one place and added in another, so the element appears to fly between positions).

> ⚠️ **The gotcha that will bite you first:** it only animates **inside a Transition** — `startTransition`, a `<Suspense>` reveal, or `useDeferredValue`. A plain `setState` outside a Transition produces **no animation at all**. If nothing moves, that's almost always why.

Customise with View Transition **CSS classes** or the Web Animations API via `onEnter` / `onExit` / `onShare` / `onUpdate`. **DOM only** for now — React Native support is in progress.

### `addTransitionType` — animate by *cause*

The same state change often deserves a different animation depending on *why* it happened. Transition types let you say so:

```jsx
function nextSlide() {
  startTransition(() => {
    addTransitionType('next');
    setCurrentSlide(c => c + 1);
  });
}
```

```jsx
<ViewTransition
  enter={{ next: 'from-right', previous: 'from-left' }}
  exit={{  next: 'to-left',    previous: 'to-right' }}
>
  <Page />
</ViewTransition>
```

These also surface as browser view-transition types, so you can scope CSS with `:active-view-transition-type(...)`.

### Suspense + View Transitions

Wrapping a `<Suspense>` boundary animates the **fallback → content** swap as an *update*:

```jsx
<ViewTransition update="auto" default="none">
  <Suspense fallback={<Fallback />}>
    <Component />
  </Suspense>
</ViewTransition>
```

The UX rule React recommends: **fallbacks appear instantly without animation; the swap to real content animates.** Animating the fallback *in* makes the app feel slower, not smoother. You can even pull images and fonts into the boundary so the transition waits for them to load.

---

## 2. Fragment Refs (stable)

A `ref` on a `<Fragment>` gives you a handle on a **group of sibling DOM nodes** — without inventing a wrapper `<div>` that changes your layout:

```jsx
<Fragment ref={fragmentRef}>
  {posts.map(post => <Heading key={post.id}>{post.title}</Heading>)}
</Fragment>
```

The `FragmentInstance` you get back supports:

- **Events** — `addEventListener` / `removeEventListener` / `dispatchEvent` across first-level children
- **Focus** — `focus()` (first child, depth-first), `focusLast()`, `blur()`
- **Observers** — `observeUsing(observer)` / `unobserveUsing(observer)` for `IntersectionObserver` / `ResizeObserver`
- **Geometry** — `getClientRects()`, `getRootNode()`, `compareDocumentPosition()`, `scrollIntoView()`

**Why it matters:** you can add behaviour (focus management, visibility tracking, measurement) to *someone else's* components **without modifying them and without adding DOM**. That's the long-standing wrapper-div tax, removed.

---

## 3. `browser()` — opt out of server rendering

Some code genuinely can't run on the server (`Intl` time zones, `window`, `localStorage`). `browser()` makes that explicit:

```jsx
import { use } from 'react';
import { browser } from 'react-dom';

function TimeZone() {
  use(browser());                                   // suspends on the server only
  const tz = new Intl.DateTimeFormat().resolvedOptions().timeZone;
  return <p>{tz}</p>;
}
```

- **On the server:** it suspends → the nearest `<Suspense>` fallback goes into the HTML.
- **On the client:** it does *not* suspend → the component renders normally.

It can be **conditional**, which makes it composable — e.g. only opt out when you have no server-provided default:

```jsx
function useBrowserQuery(query, options) {
  if (options.initialData === undefined) use(browser());
  return useQuery(query, options);
}
```

> 🔍 **SEO caveat:** anything behind `browser()` is **not in the server-rendered HTML**. That's fine for a clock or a personalised widget — but don't hide indexable content behind it (see the [Technical SEO](web-seo.md) note: content that must be indexed should be server-rendered).

---

## 4. Trusted Types support 🔒

React used to coerce values to strings (`'' + value`), which **destroyed** `TrustedHTML` / `TrustedScript` / `TrustedScriptURL` objects and defeated browser validation. 19.3 passes them through intact.

If you enforce `Content-Security-Policy: require-trusted-types-for 'script'`, React now works with it rather than against it. This is exactly the **CSP / DOM-XSS defence-in-depth** layer from the [Security Fundamentals](security-fundamentals.md) note — a real hardening win for anyone who'd been working around it.

---

## 5. Server Components: Context without a Provider

Previously, sharing server data via Context meant a boilerplate `'use client'` wrapper component. Now a Server Component can render the client Context **directly**:

```jsx
// user-context.js
'use client';
export const UserContext = createContext(null);
```

```jsx
// layout.js  (Server Component)
import { UserContext } from './user-context';

export async function Layout({ children }) {
  const currentUser = await getCurrentUser();
  return <UserContext value={currentUser}>{children}</UserContext>;
}
```

One less indirection layer in every RSC app that shares session/user state.

---

## 6. Other changes worth knowing

**Behaviour & performance**
- **Transitions now render independently** — a slow Transition no longer holds up unrelated ones.
- **Strict Mode double-invokes Effects during hydration**, matching client-rendered roots (expect to catch a few latent bugs).
- **`resize` events are batched** until the next frame.

**New DOM surface**
- `onFullscreenChange` / `onFullscreenError` events
- `maskType` SVG property · `fetchPriority` for module resources · `credentialless` iframe attribute

**Forms & Server Actions**
- `onReset` now fires when React auto-resets a form after a Server Action
- `submitter` is included in `submit` events

**Server Components** — `Error.cause` and `AggregateError.errors` now transport to the client; `<Activity>` supported in Flight.

Plus a long tail of fixes: `useDeferredValue` sticking on a stale value, context propagation in Suspense fallbacks, several Fast Refresh bugs with `lazy`/`memo`, `<ViewTransition>` crashes on Mobile Safari, and a `react-dom/server` hang on Deno.

---

## Upgrading

**No breaking changes.** `<ViewTransition>` and Fragment Refs moved experimental → stable, so existing usage keeps working. `browser()` is purely additive — adopt it where you currently guard with `typeof window !== 'undefined'` or `useEffect` hacks. Trusted Types needs no migration beyond removing your old workaround.

The one thing to actually watch: **Strict Mode's new double-invoked Effects during hydration** may surface effect-cleanup bugs that were previously masked.

---

## Go deeper

- 📘 **[Official React 19.3 release notes](https://react.dev/blog/2026/09/09/react-19-3)** — the source for this summary, with full changelog and PR links.
- 📝 **[Exposing React to Other Apps](exposing-react-to-other-apps.md)** — SSR vs. CSR, the BFF pattern, and how `browser()` fits the rendering decision.
- 📝 **[Security Fundamentals](security-fundamentals.md)** — CSP, Trusted Types, and DOM-XSS sinks.
- 📝 **[Technical SEO](web-seo.md)** — why content behind `browser()` won't be indexed.
- 📝 **[Core Web Vitals & Performance](web-core-vitals-performance.md)** — View Transitions affect *perceived* smoothness; hydration cost affects INP.
- 📝 **[React Flow](react-flow.md)** — the other React note in this library.

*Summary note of an official release — see the linked announcement for the authoritative changelog.*
