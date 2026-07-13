# 📦 pnpm Tips & Capabilities (v11) — A Study Note

> **Level:** 🟢 Beginner–Intermediate · **Reading time:** ~15 min · **Scope:** pnpm **v11** (requires Node.js 22+, distributed as pure ESM). Notes flagged **[v11]** are new or changed in this major.

**pnpm** ("performant npm") is a fast, disk-efficient package manager. Its superpower is a **content-addressable global store**: every version of every package is stored **once** on your machine and hard-linked into projects — so a fresh install is mostly link-creation, not copying. It also builds a **strict, non-flat `node_modules`** that stops you from importing packages you never declared.

## Table of contents

- [1. Why pnpm is different](#1-why-pnpm-is-different)
- [2. Everyday cheat sheet](#2-everyday-cheat-sheet)
- [3. Monorepos: workspaces & filtering](#3-monorepos-workspaces--filtering)
- [4. Catalogs — version numbers in one place](#4-catalogs--version-numbers-in-one-place)
- [5. Overrides & patching](#5-overrides--patching)
- [6. What's new in v11](#6-whats-new-in-v11)
- [7. Config & version pinning](#7-config--version-pinning)
- [8. CI, Docker & speed](#8-ci-docker--speed)
- [9. Gotchas](#9-gotchas)
- [10. Go deeper](#10-go-deeper)

---

## 1. Why pnpm is different

Two design choices explain almost everything:

- **Global content-addressable store** (`~/.pnpm-store`, **[v11]** now a **SQLite-backed** "Store v11" instead of millions of JSON files). Packages are downloaded once and **hard-linked** into each project's `node_modules`. Result: huge disk savings and near-instant installs of already-seen packages.
- **Symlinked, non-flat `node_modules`.** Unlike npm/yarn's flattened tree, pnpm only exposes a package's **declared** dependencies. This kills **phantom dependencies** — code that accidentally works because a transitive dep happened to be hoisted, then breaks later.

> Mental model: the store is the *library*; each project's `node_modules` is a set of *links* into it, arranged so you can only reach what you asked for.

---

## 2. Everyday cheat sheet

```bash
pnpm install                 # install from lockfile (alias: pnpm i)
pnpm add react               # add a prod dependency
pnpm add -D vitest           # add a dev dependency
pnpm add -g typescript       # global install  ([v11] isolated per-tool env)
pnpm remove lodash           # uninstall

pnpm up                      # update within semver ranges
pnpm up --latest             # update to latest, bumping ranges
pnpm up -i --latest          # interactive picker — the one to remember

pnpm why esbuild             # explain WHY a package is installed (dep chain)
pnpm dedupe                  # collapse duplicate versions
pnpm outdated                # list packages behind their latest

pnpm dlx create-vite my-app  # run a package without installing it (npx equivalent)
pnpm create vite my-app      # scaffolding shortcut
pnpm exec eslint .           # run a binary from node_modules
pnpm run build               # run a package.json script (bare `pnpm build` also works)
```

Two habits worth building: **`pnpm why <pkg>`** whenever you wonder how something got pulled in, and **`pnpm up -i --latest`** for controlled, reviewable upgrades.

---

## 3. Monorepos: workspaces & filtering

pnpm has first-class monorepo support. Declare packages in `pnpm-workspace.yaml`:

```yaml
packages:
  - "apps/*"
  - "packages/*"
```

Reference sibling packages with the **`workspace:` protocol** (`"@me/ui": "workspace:*"`) — it links locally in dev and is rewritten to a real version on publish.

**Filtering** is where the power is — target a subset of the monorepo:

```bash
pnpm --filter web build             # just the `web` package
pnpm --filter web... build          # web AND everything it depends on
pnpm --filter ...web build          # web AND everything that depends on it
pnpm --filter "./packages/**" test  # by path glob
pnpm --filter "...[origin/main]" build   # only packages changed since main (+ dependents)
pnpm -r test                        # run `test` in every package (recursive)
pnpm -r --parallel dev              # run all dev servers at once
```

That `"...[origin/main]"` selector is gold for CI — build/test **only what changed**.

---

## 4. Catalogs — version numbers in one place

**Catalogs** stop you repeating a version across dozens of `package.json` files. Declare it once in `pnpm-workspace.yaml`:

```yaml
catalog:
  react: ^19.0.0
  zod: ^3.24.0
```

Then reference it anywhere with the `catalog:` protocol:

```jsonc
// any package.json in the workspace
"dependencies": { "react": "catalog:", "zod": "catalog:" }
```

Bump the version in one spot and the whole monorepo moves together — no drift, no mismatched React copies. You can also define **named catalogs** (e.g. `catalog:react18` vs `catalog:react19`) for staged migrations.

---

## 5. Overrides & patching

**Force a transitive dependency version** (npm's `resolutions` equivalent) — great for security fixes:

```yaml
# pnpm-workspace.yaml  ([v11] pnpm settings live here, not .npmrc)
overrides:
  "lodash@<4.17.21": ">=4.17.21"
```

**Patch a dependency in place** — no fork, no `patch-package`:

```bash
pnpm patch react            # opens the package in a temp dir to edit
# ...make your edits...
pnpm patch-commit <temp-dir>  # writes patches/ + wires it into the lockfile
```

The patch is committed to your repo and reapplied on every install — a clean way to hotfix a broken upstream package until a real release lands.

---

## 6. What's new in v11

pnpm 11 is a security- and consistency-focused major:

- **[v11] Build scripts blocked by default → `allowBuilds`.** Dependency lifecycle scripts (`postinstall`, etc.) **do not run** unless explicitly allowed. The old `onlyBuiltDependencies` / `neverBuiltDependencies` / `ignoredBuiltDependencies` settings are **replaced by a single `allowBuilds`** map (package-pattern → boolean). Approve interactively:
  ```bash
  pnpm approve-builds          # review & allow packages that need to run scripts
  ```
  This is a supply-chain defense: a malicious `postinstall` can't run just because you installed the package.
- **[v11] Supply-chain defaults on:** `minimumReleaseAge` defaults to **1 day** (a package version won't be installed until it's been public ~1440 min — dodges freshly-published malware), and `blockExoticSubdeps` defaults to **true**.
- **[v11] Native publishing:** `pnpm publish` and friends **no longer shell out to the npm CLI**.
- **[v11] Config split** (see §7): `.npmrc` is auth/registry only; pnpm settings move to `pnpm-workspace.yaml`.
- **[v11] New commands:** `pnpm ci` (clean, lockfile-only install for CI), `pnpm clean`, `pnpm sbom` (generate a Software Bill of Materials for security audits), and `pnpm with`. Check the release notes for exact semantics.
- **[v11] Isolated global installs:** each `pnpm add -g <tool>` gets its own environment, so global tools can't clash on shared deps.

---

## 7. Config & version pinning

**[v11] Configuration was reorganized — this trips up upgraders:**

| Concern | Where it lives now |
| ------- | ------------------ |
| Auth tokens, registry URLs | `.npmrc` (auth/registry **only**) |
| All pnpm behavior settings | `pnpm-workspace.yaml`, or global `~/.config/pnpm/config.yaml` |
| Environment variables | `pnpm_config_*` prefix (was `npm_config_*`) |
| Scoped registries | the new `registries` setting |

**Pin the pnpm version for the whole team** via the `packageManager` field + Corepack, so everyone uses the same pnpm:

```jsonc
// package.json
"packageManager": "pnpm@11.0.0"
```

```bash
corepack enable        # Node ships Corepack; this makes `pnpm` honor the field above
```

No more "works on my machine" from mismatched package-manager versions.

---

## 8. CI, Docker & speed

- **`pnpm ci`** **[v11]** (or `pnpm install --frozen-lockfile`) — fail if `pnpm-lock.yaml` is out of date; never mutate the lockfile in CI.
- **`pnpm fetch`** — download everything the lockfile needs into the store **without** a `package.json`, so Docker can cache the dependency layer independently of your source. **[v11]** it pre-populates the store. Classic Dockerfile pattern:
  ```dockerfile
  COPY pnpm-lock.yaml .
  RUN pnpm fetch                 # cached until the lockfile changes
  COPY . .
  RUN pnpm install --offline     # resolves from the pre-populated store
  ```
- **`pnpm deploy <target>`** — produce a self-contained, hard-linked copy of a single workspace package (with only its deps) for deployment — no dev cruft, no other workspace packages.
- **`--workspace-concurrency`** / `--parallel` — tune how many package scripts run at once.

---

## 9. Gotchas

- **Strictness surfaces hidden bugs.** Migrating from npm/yarn can reveal phantom-dependency imports — the *right* fix is to add the missing dep to `package.json`, not to reach for `node-linker=hoisted` or `shamefully-hoist`.
- **Post-install scripts won't run by default** **[v11]** — if a native package (e.g. `esbuild`, `sharp`) isn't working, it probably needs `pnpm approve-builds`.
- **Fresh packages won't install for a day** **[v11]** — `minimumReleaseAge` can surprise you right after publishing your own package; override it per-package if you genuinely need the newest release immediately.
- **`.npmrc` pnpm settings silently ignored** **[v11]** — move them to `pnpm-workspace.yaml` when upgrading.
- **Commit `pnpm-lock.yaml`** — always. It's the source of reproducibility.

---

## 10. Go deeper

- 📘 Official docs: [pnpm.io](https://pnpm.io/) — the CLI reference and [Settings](https://pnpm.io/settings) pages are excellent.
- 📄 [pnpm 11.0 release notes](https://pnpm.io/blog/releases/11.0) — the authoritative list of this major's changes.
- 📄 [pnpm approve-builds](https://pnpm.io/cli/approve-builds) — the build-script allowlist workflow.
- 🧩 Related in this library: [notes/](README.md) — see the tooling & code-quality notes.

### Why it belongs in a software-engineering library

pnpm is a compact case study in good systems design: **content-addressable storage** (dedup by hash), **strictness that prevents whole bug classes** (no phantom deps), and **security-by-default** (blocked build scripts, release-age gating). The same ideas show up in Git, Nix, and container image layers.

*Original study note (pnpm v11) — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
