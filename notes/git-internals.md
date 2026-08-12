# 🌿 Git Internals & Workflows — A Detailed Study Note

> **Level:** 🟡 Intermediate · **Reading time:** ~22 min · **Prerequisites:** you use git daily (add/commit/push/branch). Pairs with the [Data Structures & Algorithms](data-structures-algorithms.md) note (content-addressable storage = hashing) and [commitlint](commitlint.md).

Most of us use git as a set of memorized incantations. But git is **remarkably simple underneath** — a tiny content-addressable key-value store with a few object types and some pointers on top. Once you see the object model, the confusing commands (rebase, reset, detached HEAD, "lost" commits) become obvious. This note is the model, then the workflows that follow from it.

> **The core reframe:** git is not a "diff tool" that stores changes between versions. It's a **content-addressable filesystem** that stores **complete snapshots**, each addressed by the SHA hash of its content. Branches, tags, and HEAD are just **movable pointers** into a graph of those snapshots. Learn the four object types and three pointers and git stops being magic.

## Table of contents

- [1. The object model](#1-the-object-model)
- [2. Content-addressable storage](#2-content-addressable-storage)
- [3. Refs: branches, tags, HEAD](#3-refs-branches-tags-head)
- [4. The three areas & how a commit is made](#4-the-three-areas--how-a-commit-is-made)
- [5. Merge vs. rebase](#5-merge-vs-rebase)
- [6. Reset, revert, restore](#6-reset-revert-restore)
- [7. The reflog: your safety net](#7-the-reflog-your-safety-net)
- [8. Branching workflows](#8-branching-workflows)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. The object model

Everything in `.git/objects` is one of **four object types**, each identified by the SHA-1/SHA-256 hash of its contents:

| Object | Is | Holds |
| ------ | -- | ----- |
| **blob** | file *contents* (no name, no metadata) | the raw bytes of one file version |
| **tree** | a *directory* | a list of (mode, name, hash) → blobs and subtrees |
| **commit** | a *snapshot* | pointer to one **tree** + parent commit(s) + author/committer + message |
| **tag** | an *annotated tag* | pointer to an object + tagger + message |

```
commit  ─── tree ──┬── blob   (README.md)
  │                ├── blob   (index.js)
  │                └── tree ── blob  (src/app.js)
  └── parent → previous commit → …
```

A **commit points to a tree** (the full snapshot of your project at that moment), and to its **parent commit(s)**. Follow parents backward and you have your whole history — a **directed acyclic graph (DAG)** of commits. That's it. That's git.

---

## 2. Content-addressable storage

The key idea: an object's **address is the hash of its content**. `git hash-object` shows it:

```bash
echo "hello" | git hash-object --stdin      # → ce013625... (the blob's id)
```

Consequences that explain a lot:

- **Identical content is stored once.** Two files with the same bytes → one blob, shared. A commit that doesn't change a file **reuses** its blob and tree. (This is *why* git is space-efficient despite storing full snapshots — deduplication by hash, later compressed into packfiles.)
- **Integrity is built in.** If a byte changes, the hash changes, so corruption is detectable and history is **tamper-evident** — a commit's hash depends on its tree *and* its parent, so rewriting any old commit changes every hash after it.
- **This is the exact idea behind [pnpm's store](pnpm-tips.md) and content-addressed caches** — hash the content, use it as the key. Git is a content-addressable filesystem you already use every day. (See the [DSA note](data-structures-algorithms.md): *content-addressing = hashing*.)

> Mental model: `.git` is a **key-value database** where the key is `hash(value)`. Commands like `commit`, `checkout`, and `merge` are just ways of writing objects and moving pointers.

---

## 3. Refs: branches, tags, HEAD

If commits are the graph, **refs are named pointers into it** — and they're astonishingly simple:

- A **branch** (`main`) is a **file** in `.git/refs/heads/` containing **one commit hash**. That's all a branch *is* — a movable label on a commit. "Creating a branch" writes a 41-byte file; it's why branching is instant.
- **HEAD** is a pointer to *"where you are"* — normally it contains `ref: refs/heads/main` (it points at a branch). Committing moves the branch HEAD points to.
- **Detached HEAD** = HEAD points **directly at a commit** instead of a branch. Not broken — just "you're looking at a commit with no branch label here; new commits won't be kept by any branch unless you make one."
- A **tag** is a fixed pointer (a release marker) that doesn't move as you commit.

```
        HEAD
          │
          ▼
main ──► C3 ──► C2 ──► C1        (branch = pointer to C3)
feature ──► C4 ──► C2            (shares history back to C2)
```

> Once you internalize "a branch is just a pointer to a commit," `reset`, `merge`, `rebase`, and fast-forward all become "which commit does this pointer now point at?"

---

## 4. The three areas & how a commit is made

Git has **three trees** you move content between:

| Area | Is | Command to move into it |
| ---- | -- | ----------------------- |
| **Working directory** | your actual files | edit them |
| **Staging area (index)** | the *proposed* next snapshot | `git add` |
| **Repository (`.git`)** | committed history | `git commit` |

A commit is a two-step: `add` copies the working-dir content into the **index** (creating blobs), and `commit` writes a **tree** from the index plus a **commit** object pointing at it. The staging area is what lets you commit *part* of your changes (`git add -p`) — a feature that confuses newcomers but is pure leverage once understood.

---

## 5. Merge vs. rebase

Two ways to combine branches — the perennial debate, now with the model to reason about it:

**Merge** — creates a **new merge commit** with **two parents**, tying the branches together. History is *truthful* (it shows what actually happened) but can get tangled ("a spaghetti of merge commits").

```
main:    A─B─C─────M       (M = merge commit, parents C and E)
feature:      D─E─┘
```

**Rebase** — **replays** your commits on top of another branch, creating **new commits** (new hashes) with a **linear** history. Cleaner to read, but it **rewrites history**.

```
before:  main A─B─C   feature (from B): D─E
after:   git rebase main →  A─B─C─D'─E'   (D', E' are new commits)
```

- **Merge** when: integrating a completed feature into a shared branch; you want the true topology; the branch is public.
- **Rebase** when: tidying *your own local* commits before sharing; keeping a feature branch current with `main` linearly.
- **The golden rule: never rebase commits that others have already pulled.** Rewriting shared history changes hashes and forces everyone into painful reconciliation. Rebase *local/private*, merge *public*.

> Interactive rebase (`git rebase -i`) also lets you **squash, reorder, edit, and drop** commits — the tool for curating a messy local branch into clean, [conventional commits](commitlint.md) before a PR.

---

## 6. Reset, revert, restore

Three commands people confuse — they operate on different areas:

- **`git reset --soft <commit>`** — move the branch pointer; **keep** index + working dir. (Uncommit, keep changes staged.)
- **`git reset --mixed <commit>`** (default) — move pointer + reset index; keep working dir. (Uncommit + unstage.)
- **`git reset --hard <commit>`** — move pointer + reset index **and** working dir. ⚠️ **Discards uncommitted work.**
- **`git revert <commit>`** — create a **new commit** that undoes another. **Safe on shared history** (doesn't rewrite) — the right "undo" for `main`.
- **`git restore` / `git switch`** — the newer, clearer verbs: `restore` for file contents, `switch` for changing branches (splitting the overloaded old `checkout`).

> Rule: **`revert` to undo public history; `reset` to fix local history.** And `reset --hard` is the one command that can actually lose uncommitted work — pause before it.

---

## 7. The reflog: your safety net

The single most reassuring thing to know: **git almost never truly deletes commits.** Every time HEAD moves (commit, checkout, reset, rebase, merge), git records it in the **reflog**:

```bash
git reflog                 # every position HEAD has been, with hashes
git reset --hard HEAD@{1}  # jump back to where you were one move ago
```

"I rebased and lost my commits" / "I hard-reset by mistake" → they're almost always still in the reflog (and reachable by hash) for ~90 days, until garbage collection. **Orphaned commits aren't gone, just unreferenced.** This is why the object model matters: a commit exists as long as *something* can reach it, and the reflog remembers the pointers.

---

## 8. Branching workflows

The model supports several team conventions — pick by release cadence:

- **Trunk-Based Development** — everyone commits to `main` (behind feature flags) with very short-lived branches. Favors continuous delivery; minimizes merge pain. The modern default for many teams.
- **GitHub Flow** — `main` is always deployable; work on short feature branches → PR → merge. Simple, CI/CD-friendly.
- **Git Flow** — long-lived `develop` + `release` + `hotfix` branches. Powerful but heavy; suited to versioned/released software, increasingly seen as overkill for web apps.

Cross-cutting practices: **small, reviewable PRs**; **[conventional commit messages](commitlint.md)** (which enable automated versioning/changelogs); protected `main` with required CI; squash-merge for a clean history (lint the squash message!).

---

## 9. Best practices & anti-patterns

**Do**
- **Commit small and often**, with clear [conventional messages](commitlint.md); curate with interactive rebase before sharing.
- **Rebase local, merge public** — never rewrite shared history.
- **Use `revert` to undo on `main`**, `reset` only on local branches.
- **Know the reflog exists** — it's the undo button for "disasters."
- **Prefer `switch`/`restore`** over the overloaded `checkout`.
- **Protect `main`** with CI + review; keep branches short-lived.

**Avoid**
- **`git push --force`** to a shared branch — use `--force-with-lease` at minimum, and ideally never on `main`.
- **`reset --hard`** without checking what you'll lose.
- **Giant, long-lived branches** — merge pain compounds; integrate often.
- **Committing secrets/large binaries** — history is forever (and content-addressed); use `.gitignore`, git-lfs, and secret scanning ([security note](security-fundamentals.md)).
- **Treating merge conflicts as scary** — they're just git asking you to resolve two edits to the same lines; the model makes them legible.

---

## 10. Go deeper

Related material in this library:

- 📝 **[Data Structures & Algorithms](data-structures-algorithms.md)** — git *is* content-addressable storage (hashing) + a DAG; the object model is a live example.
- 📝 **[pnpm Tips](pnpm-tips.md)** — the same content-addressed store idea (hash the content, key by hash, dedupe).
- 📝 **[commitlint](commitlint.md)** — enforce clean, conventional commit messages that make history (and automated releases) work.
- 📝 **[Security Fundamentals](security-fundamentals.md)** — committed secrets, tamper-evidence, and why history is forever.
- 📗 **[Pro Git (Chacon & Straub)](../books/)** — free online; **Chapter 10 "Git Internals"** is the canonical deep dive this note summarizes.

### Primary references

- Scott Chacon & Ben Straub, *[Pro Git](https://git-scm.com/book)* — especially Ch. 10, *Git Internals*.
- `git help <command>` and the [Git reference](https://git-scm.com/docs).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
