# ✅ commitlint — A Study Note

> **Level:** 🟢 Beginner–Intermediate · **Reading time:** ~10 min · **Prerequisites:** basic git. Pairs with the [pnpm](pnpm-tips.md) and [Knip](knip.md) tooling notes, and the versioning ideas in [API Design](api-design.md) & [Serialization & Schema Evolution](serialization-schema-evolution.md).

**commitlint** checks that your **git commit messages follow a convention** — almost always **[Conventional Commits](https://www.conventionalcommits.org/)** (`type(scope): subject`). It's a linter for commit messages: run it in a git hook and a malformed commit is rejected *before* it lands, keeping your history clean and, crucially, **machine-readable**.

> **The core idea:** a commit message is an **API for your git history** — read by humans *and* tools. Ad-hoc messages ("fix stuff", "wip", "asdf") are unparseable and unsearchable. A consistent, structured format turns your history into data that automation can act on: version bumps, changelogs, release notes. commitlint is the enforcement that makes the convention actually hold.

## Table of contents

- [1. Conventional Commits in 60 seconds](#1-conventional-commits-in-60-seconds)
- [2. How commitlint works](#2-how-commitlint-works)
- [3. The benefits](#3-the-benefits)
- [4. The ecosystem it unlocks](#4-the-ecosystem-it-unlocks)
- [5. Setup & config](#5-setup--config)
- [6. Best practices & anti-patterns](#6-best-practices--anti-patterns)
- [7. Go deeper](#7-go-deeper)

---

## 1. Conventional Commits in 60 seconds

The convention commitlint most often enforces:

```
<type>(<optional scope>): <subject>

<optional body>

<optional footer>
```

```
feat(auth): add passkey login
fix: guard against null user in session lookup
docs(readme): document the setup steps
refactor(api)!: drop the deprecated v1 endpoints   ← "!" marks a BREAKING CHANGE
```

Common **types**: `feat` (new feature), `fix` (bug fix), `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.

The magic is that **type maps to a semantic-version bump**: `fix` → **patch**, `feat` → **minor**, `!`/`BREAKING CHANGE:` → **major**. That single mapping is what unlocks the automation in §4.

---

## 2. How commitlint works

Three moving parts:

1. **A config** (`commitlint.config.js` / `.commitlintrc`) — usually extends **`@commitlint/config-conventional`**, a ruleset for the Conventional Commits spec.
2. **A git hook** — the **`commit-msg`** hook (via [Husky](https://typicode.github.io/husky/), lefthook, or simple-git-hooks) runs commitlint on your message the moment you commit. Fail the rules → the commit is **blocked** locally, with an explanation.
3. **CI enforcement** — the same check runs in the pipeline on PRs, so the rule holds even if someone bypasses the local hook with `--no-verify`.

```
git commit -m "updated stuff"
  → commit-msg hook runs commitlint
  → ✖  subject may not be empty / type must be one of [feat, fix, ...]
  → commit rejected. Fix the message, try again.
```

Local hook = **fast feedback**; CI = **the actual guarantee**. You want both (hooks are bypassable and don't run for everyone).

---

## 3. The benefits

The reason to adopt it:

- **A readable, scannable history.** `git log --oneline` becomes a coherent changelog you can actually read; `feat`/`fix`/`perf` prefixes let you filter ("show me every fix this release").
- **Automated semantic versioning.** Because the type encodes the semver impact, tools compute the next version *from the commits* — no manual "should this be 1.3 or 2.0?" debates. Ties directly to the [semver discipline](serialization-schema-evolution.md) your API/schema notes depend on.
- **Automated changelogs & release notes.** Generated from commits, grouped by type — no hand-maintained `CHANGELOG.md` that drifts out of date.
- **Machine-readable commits.** Tooling can parse scope, type, and breaking-change flags to route notifications, build release notes, or trigger workflows.
- **Consistency across the team.** A shared, *enforced* standard removes bikeshedding and makes onboarding trivial — the format is the same in every repo.
- **Better commits, as a side effect.** Being forced to name a `type` and `scope` nudges people toward **atomic, well-scoped** commits ("is this a feat *and* a fix? → split it"), which improves reviewability and `git bisect`.
- **Shift-left / fast feedback.** Catching a bad message at commit time (or PR time) is the same cheap-early-check instinct as [linting, type-checking, and tests](testing-strategy.md) — fix it before it's permanent history.

---

## 4. The ecosystem it unlocks

commitlint is rarely used alone — it's the *gate* that makes these possible:

- **[semantic-release](https://semantic-release.gitbook.io/) / [release-please](https://github.com/googleapis/release-please) / [changesets](https://github.com/changesets/changesets)** — read the conventional commits since the last release, compute the next version, tag it, generate the changelog, and publish. Fully automated releases.
- **[conventional-changelog](https://github.com/conventional-changelog/conventional-changelog)** — generate/update `CHANGELOG.md` from history.
- **[Commitizen](https://commitizen-tools.github.io/commitizen/) (`cz`)** — an *interactive prompt* that walks you through writing a conforming message (type → scope → subject). The friendly front-door that pairs with commitlint's back-door enforcement: Commitizen helps you *write* it right, commitlint *verifies* it.

> The chain is the point: **commitlint (enforce) → conventional commits (structure) → semantic-release (automate versioning + changelog + publish).** One convention, and your release process runs itself.

---

## 5. Setup & config

Typical install (works with any package manager — see [pnpm](pnpm-tips.md)):

```bash
pnpm add -D @commitlint/cli @commitlint/config-conventional husky
```

```js
// commitlint.config.js
export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // rule = [level, applicable, value]  · level: 0 off, 1 warn, 2 error
    'type-enum': [2, 'always', ['feat', 'fix', 'docs', 'refactor', 'perf', 'test', 'build', 'ci', 'chore', 'revert']],
    'scope-enum': [2, 'always', ['api', 'ui', 'auth', 'deps']],  // optional: constrain scopes
    'subject-case': [2, 'never', ['upper-case', 'pascal-case']],
    'header-max-length': [2, 'always', 100],
  },
};
```

```bash
# Husky v9: create the commit-msg hook
echo 'npx --no-install commitlint --edit "$1"' > .husky/commit-msg
```

And the CI guarantee (GitHub Actions): run `commitlint` over the PR's commit range so the local hook can't be the only thing standing between you and a bad message.

---

## 6. Best practices & anti-patterns

**Do**
- **Enforce in CI, not just the local hook** — hooks are bypassable (`--no-verify`) and don't run for everyone.
- **Start from `@commitlint/config-conventional`** and customize only what you need.
- **Pair with Commitizen** to make the *right* message the *easy* message for the team.
- **Decide your squash-merge policy** — the PR title becomes the commit, so lint *that* (many teams lint PR titles too).
- **Keep the type list small and agreed** — fewer, clearer types beat a sprawling taxonomy.

**Avoid**
- **Local-hook-only enforcement** — it's advisory, not a guarantee.
- **Over-strict rules that annoy without value** (e.g. mandatory scopes on a tiny repo) — friction breeds `--no-verify` habits.
- **Adopting the linter but not the payoff** — if you enforce Conventional Commits, *use* it for automated versioning/changelogs; otherwise it's ceremony.
- **Ignoring merge/squash commits** — an unlinted squash message defeats the whole history.

---

## 7. Go deeper

Related material in this library:

- 📝 **[pnpm Tips](pnpm-tips.md)** & **[Knip](knip.md)** — the same Tooling & DevEx family; fast, enforced, shift-left checks.
- 📝 **[Serialization & Schema Evolution](serialization-schema-evolution.md)** & **[API Design](api-design.md)** — semantic versioning (which conventional commits automate) is the backbone of both.
- 📝 **[Testing Strategy](testing-strategy.md)** — commit linting is the same cheap-early-feedback instinct as CI tests and type checks.

### Primary references

- [Conventional Commits specification](https://www.conventionalcommits.org/).
- [commitlint](https://commitlint.js.org/) — docs, rules, and config reference.
- [Husky](https://typicode.github.io/husky/) — the usual git-hook runner.

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
