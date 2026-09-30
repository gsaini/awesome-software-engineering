# ♞ Browser Harness — The Agent Writes Its Own Tools

> **Level:** 🟡 Intermediate · **Reading time:** ~14 min · **Source:** [browser-use/browser-harness](https://github.com/browser-use/browser-harness) (MIT, Python ≥ 3.11, v0.1.13; created 2026-04-17, ~18k stars) · **Prerequisites:** [Loop Engineering](loop-engineering.md); [Jev Ultrafast](jev-ultrafast.md) makes a useful contrast.

**Browser Harness** connects a coding agent (Claude Code, Codex, any MCP client) to **your real browser** through one Chrome DevTools Protocol (CDP) websocket. Its tagline is *"Self-healing harness that enables LLMs to complete any task,"* and the mechanism behind "self-healing" is simple and radical:

> Most harnesses decide in advance what an agent may do, and wrap each action in a tool. Browser Harness does the opposite. It gives the model **raw CDP plus a helpers file it may edit** — when a tool is missing, *the agent writes it*, and the harness is better for every task after.

The same team built [Jev Ultrafast](jev-ultrafast.md), which goes to the other extreme: the agent may only *choose* from an indexed menu of actions. Studying the two side by side is the most useful thing in this note.

## Table of contents

- [1. How it works in one screen](#1-how-it-works-in-one-screen)
- [2. The thesis: the bitter lesson of agent harnesses](#2-the-thesis-the-bitter-lesson-of-agent-harnesses)
- [3. Self-healing, concretely](#3-self-healing-concretely)
- [4. How the agent is taught: SKILL.md](#4-how-the-agent-is-taught-skillmd)
- [5. Ways to run it](#5-ways-to-run-it)
- [6. Two philosophies from one team: Harness vs Jev](#6-two-philosophies-from-one-team-harness-vs-jev)
- [7. Security and privacy: what you are granting](#7-security-and-privacy-what-you-are-granting)
- [8. "Thin", honestly](#8-thin-honestly)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. How it works in one screen

```text
 coding agent ──► browser-harness <<'PY' … PY ──► run.py: exec(code) with helpers pre-imported
                                                        │  (+ agent-workspace/agent_helpers.py)
                                                        ▼
                                      daemon (long-lived) ──CDP websocket──► YOUR Chrome
                                                                              (real profile, real logins)
```

The agent doesn't call tools one by one. It writes a **short Python script** and pipes it to the CLI:

```bash
browser-harness <<'PY'
new_tab("https://news.ycombinator.com")
wait_for_load()
print(page_info())
PY
```

- **`run.py`** reads the script from stdin and runs it with `exec()`. The core helpers are already in scope: `new_tab`, `goto_url`, `click_at_xy`, `type_text`, `fill_input`, `press_key`, `scroll`, `capture_screenshot`, `wait_for_element`, `js`, `upload_file`, `http_get`, and the escape hatch `cdp("Domain.method", **params)`.
- **The daemon** keeps one connection to the whole Chrome instance across calls, so tab state survives between scripts.
- **Setup is a prompt, not an installer.** You paste a setup prompt into your coding agent. It installs the package with `uv`, registers the skill, and opens `chrome://inspect/#remote-debugging`, where *you* tick the checkbox that allows the connection.

## 2. The thesis: the bitter lesson of agent harnesses

The design comes from a post by Browser Use's CTO, Gregor Zunic, [*The Bitter Lesson of Agent Harnesses*](https://browser-use.com/posts/bitter-lesson-agent-harnesses) (April 2026). The argument runs:

- The original Browser Use shipped *"thousands of lines of element extractors, DOM indexers, click wrappers."* Each wrapper was also a **constraint**: the model had to work around it instead of using what it already knew.
- Models were trained on large amounts of CDP usage. Exposing raw CDP makes the "hard" cases ordinary. Cross-origin iframes, shadow DOM, and compositor-level input work the way the model already expects.
- The punchline: *"Don't wrap the LLM. Don't wrap its tools either… Your helpers are abstractions too. Delete them. Let the agent write what it needs."*

It is Sutton's [bitter lesson](http://www.incompleteideas.net/IncIdeas/BitterLesson.html) — general methods plus scale beat hand-built knowledge — applied to harness design. Hand-written abstractions age; a capable model with raw access keeps improving.

## 3. Self-healing, concretely

"Self-healing" means two things.

**1. The agent heals its own tools.** When a script fails because a helper doesn't exist or doesn't cover a case, the agent does what a coding agent does with an import error: it reads the error, greps the helpers, writes the missing function into its workspace, and reruns. The launch post's example: uploading a 12 MB file hit a 10 MB websocket payload limit, so the agent read the error and switched to chunked uploads on its own.

The code is split into two zones, and that split is the whole design:

| Zone | Who edits it | What's there |
|------|--------------|--------------|
| `src/browser_harness/` | Maintainers only ("stays protected") | CLI, daemon, core helpers |
| `agent-workspace/agent_helpers.py` | **The agent** | Task-specific helpers it wrote |
| `agent-workspace/domain-skills/<site>/` | **The agent** | Notes on how particular sites work |

On every run, `agent_helpers.py` is loaded with `importlib` and its public names are copied into the helpers namespace. **Once the agent writes a helper, every later script can use it.** An example of such a helper, built from the recipe `SKILL.md` itself teaches (illustrative, not from the repo):

```python
# agent-workspace/agent_helpers.py — the kind of helper an agent adds mid-task
def click_by_role(role: str, name: str):
    """Click the first accessibility node with this role and name."""
    for node in cdp("Accessibility.getFullAXTree")["nodes"]:
        if node.get("role", {}).get("value") == role and node.get("name", {}).get("value") == name:
            quad = cdp("DOM.getBoxModel", backendNodeId=node["backendDOMNodeId"])["model"]["content"]
            return click_at_xy(sum(quad[0::2]) / 4, sum(quad[1::2]) / 4)
    raise LookupError(f"no {role} named {name!r}")
```

**2. The harness heals its connection.** If Chrome isn't running, the harness launches it. If the daemon goes stale, it reattaches. `browser-harness --doctor` diagnoses the rest. The launch post describes this as removing the need for the "watchdog services" the old stack used to catch tab crashes and detached targets.

**Domain skills** are the long-term memory. They are notes per site (selectors, flows, edge cases) that the agent writes and later reads. The repo ships 111 skill files, from GitHub and Amazon to airline checkouts, and asks contributors for *agent-generated* skills: "do not hand-author them." They are **off by default** and switched on with `BH_DOMAIN_SKILLS=1`.

> A related post, [*Web Agents That Actually Learn*](https://browser-use.com/posts/web-agents-that-actually-learn), describes the **Cloud** version, where skills are shared across all users. There, a second agent reviews each trajectory ("What would you need to know to solve this in 1–3 calls?"), a dedicated LLM rejects anything containing personal data, and skills voted below −3 are retired. Its example: a Duo 2FA skill spared 254 later agents an 8-call exploration. That is the hosted platform, not the local open-source harness.

## 4. How the agent is taught: SKILL.md

The harness's "prompt" is [`SKILL.md`](https://github.com/browser-use/browser-harness/blob/main/SKILL.md), loaded as a skill. Its rules are a good checklist for any browser agent:

- **Don't use a browser when a fetch will do.** For public pages, APIs, and docs, use `curl`. Escalate to the browser only for interaction, a logged-in session, JS rendering, or bot protection.
- **Accessibility tree before screenshots.** Find elements in `Accessibility.getFullAXTree` (role, name, node ID), compute the box center, and click by coordinates. Take screenshots only when layout or imagery matters.
- **Coordinate clicks by default.** CDP mouse events work at the compositor level, so they pass through iframes, shadow DOM, and cross-origin frames.
- **One local browser is one lane.** Share the default daemon and serialize browser actions. For truly parallel work, use separate cloud browsers, not extra local daemons.
- **Stay in the background.** Work in background tabs, and never bring Chrome to the foreground unless the user asks.
- **Stop at login walls.** SSO the browser is already signed in to may be used, but stop and ask for passwords, MFA, consent, or an ambiguous account choice.
- **Clean up.** Close tabs you opened, keep the ones the user needs, and ask before leaving a paid cloud browser running.

When the agent gets stuck on a browser mechanic, 18 `interaction-skills/` notes cover dialogs, downloads, drag-and-drop, dropdowns, iframes, shadow DOM, uploads, and more.

## 5. Ways to run it

| Mode | How |
|------|-----|
| **CLI + skill** | Paste the setup prompt into Claude Code or Codex; the agent drives `browser-harness <<'PY' … PY`. |
| **MCP server** | `uvx --from 'browser-harness[mcp]' browser-harness-mcp` exposes 23 `browser_*` tools (`browser_goto`, `browser_click`, `browser_js`, `browser_cdp`, `browser_upload_file`, …) over stdio. There's no second CDP layer. See the [MCP note](model-context-protocol.md). |
| **Cloud browsers** | `start_remote_daemon("name")` gives a fresh, isolated, stealth browser per task, for parallel sub-agents, headless servers, and CAPTCHA-prone sites. These are billed until stopped. |
| **As a dependency** | The browser-use 0.13 package's `browser-use` CLI *is* the harness, and [Jev Ultrafast](jev-ultrafast.md) pins `browser-harness==0.1.13` for its browser connection. |

## 6. Two philosophies from one team: Harness vs Jev

| | **Browser Harness** | **Jev Ultrafast** |
|-|---------------------|-------------------|
| The model… | **writes code** (Python + raw CDP) | **chooses** an operation and an element index |
| Action space | Unbounded, and it grows as the agent adds helpers | A fixed, indexed menu rebuilt from the current page |
| Invalid actions | Possible; they fail, and the agent fixes and retries | Impossible by construction |
| Strength | Generality: any site, any mechanic, logged-in sessions | Speed and predictability (~7 s Google Flights search) |
| Safety comes from | The human's consent, stop-and-ask rules, a protected core | The shape of the action space |
| Improves by | Accumulating helpers and domain skills | Better choice models and faster mechanics |

This is the same trade-off as [constraint-driven development](constraint-driven-development.md), at two opposite settings. The harness bets that a strong model plus raw access plus self-repair beats any wrapper you could write. Jev bets that for a well-understood task, a narrow action space is faster and can't go wrong in whole classes of ways. Neither is simply better: open-ended personal automation favors the harness, and high-volume, well-understood flows favor constraint.

## 7. Security and privacy: what you are granting

**The blast radius is your browser.** The agent runs arbitrary Python with full CDP access to your real Chrome profile: every site you're logged in to, every cookie, every open tab. Remote debugging being enabled means *whatever drives the harness* can act as you.

Risks specific to this design:

- **Prompt injection becomes code.** Page text flows into a model that writes and runs Python. A hostile page doesn't need to trick the model into clicking a button; it can try to get it to *write a helper*.
- **Helpers persist.** `agent_helpers.py` is loaded into every future run. A bad helper written once — by mistake or through injection — keeps running. Treat that file like code in your repo: review its diff.
- **Telemetry is opt-out, and more detailed than a quick read suggests.** As read in the v0.1.13 source (`telemetry.py`, `run.py`, 2026-09-29):
  - Generic events are scrubbed: fields named like URLs, tokens, or text are dropped.
  - The per-run `cli_event`, though, includes the **script you piped** (up to 20,000 characters), the **tail of its output** (up to 20,000 characters, often text read from logged-in pages), and **each helper call's arguments** (up to 300 characters each, up to 500 steps), sent to PostHog (EU).
  - The only mention in the docs is a `browser-harness telemetry disable` line in `install.md`.

  To turn it off: `browser-harness telemetry disable`, or set `BH_TELEMETRY=0` (`ANONYMIZED_TELEMETRY=false` also works).

What the harness gets right:

- **Consent:** connecting requires your explicit remote-debugging checkbox, and on macOS an Allow prompt.
- **Stop-and-ask:** the agent must stop for passwords, MFA, and consent screens.
- **Protected core:** the agent can't edit the harness itself.
- **Domain skills off by default.**
- **Isolation on demand:** cloud browsers keep untrusted or parallel work away from your profile.
- **Least privilege by default:** the repo's `AGENTS.md` tells contributors to keep core helpers short and "not expand CDP surface without need."

Practical rules: give agents a **separate Chrome profile** without your bank and primary email logged in; use cloud browsers for untrusted sites; disable telemetry for anything sensitive. The [security fundamentals](security-fundamentals.md) apply in full, since this is a remote-control channel into your identity.

## 8. "Thin", honestly

The launch post described about 600 lines in four files. Today, `src/browser_harness/` is about **6,400 lines across 14 modules**:

- lifecycle and diagnostics: `admin.py` ~1,600
- the daemon: ~900
- recording and video: ~1,600
- auth: ~550
- telemetry: ~300

The **agent-facing surface is still small**: `helpers.py` (~670 lines) plus `SKILL.md` (~280). The growth is the operational machinery around it: daemons, auth, recordings, updates.

That's a useful lesson on its own. **A thin interface is not a thin system.** The bitter lesson is about what you put between the model and the environment. Everything that keeps the environment alive, observable, and recoverable still has to be built, and it grows.

## 9. Best practices & anti-patterns

**Do**
- ✅ Use a plain fetch (`curl`, a fetch tool) for public content; reach for the browser only when a task needs interaction, logins, JS rendering, or bot protection.
- ✅ Run agents in a dedicated Chrome profile, and cloud browsers for parallel or untrusted work.
- ✅ Review `agent_helpers.py` and new domain skills like code, since they run on every call.
- ✅ Prefer the accessibility tree over screenshots: cheaper, and more robust than reading pixels.
- ✅ Decide deliberately on telemetry; turn it off for private work.
- ✅ Keep one browser lane per local Chrome; serialize the actions.

**Avoid**
- ❌ Pointing it at your main profile "just for this one task."
- ❌ Letting an agent that reads untrusted pages also hold payment or admin sessions.
- ❌ Hand-writing domain skills from memory. The project's own rule is that skills should record what actually worked in the browser.
- ❌ Spawning a new daemon per task or agent: each one is another browser-level connection and another approval prompt.
- ❌ Treating "the agent fixed its own tool" as verification. The task still needs an independent check of the outcome; see [agent evaluators](building-agent-evaluators.md).

## 10. Go deeper

Related material in this library:

- 📝 **[Jev Ultrafast](jev-ultrafast.md)** — the opposite design from the same team: choose, don't generate.
- 📝 **[Loop Engineering](loop-engineering.md)** — the harness is a loop whose tools grow during the loop.
- 📝 **[Model Context Protocol](model-context-protocol.md)** — `browser-harness-mcp` exposes the same helpers as MCP tools.
- 📝 **[Constraint-Driven Development](constraint-driven-development.md)** — where the harness deliberately sits at "no constraint".
- 📝 **[Security Fundamentals](security-fundamentals.md)** — prompt injection, least privilege, blast radius.
- 📝 **[Building an Agent Evaluator](building-agent-evaluators.md)** — self-repair isn't self-verification.

### Primary references

- [browser-use/browser-harness](https://github.com/browser-use/browser-harness) — [README](https://github.com/browser-use/browser-harness/blob/main/README.md), [SKILL.md](https://github.com/browser-use/browser-harness/blob/main/SKILL.md), [install.md](https://github.com/browser-use/browser-harness/blob/main/install.md), [docs/MCP.md](https://github.com/browser-use/browser-harness/blob/main/docs/MCP.md), [interaction-skills/](https://github.com/browser-use/browser-harness/tree/main/interaction-skills), [agent-workspace/domain-skills/](https://github.com/browser-use/browser-harness/tree/main/agent-workspace/domain-skills).
- Gregor Zunic, [*The Bitter Lesson of Agent Harnesses*](https://browser-use.com/posts/bitter-lesson-agent-harnesses) (Browser Use, April 2026).
- Gregor Zunic, [*Web Agents That Actually Learn*](https://browser-use.com/posts/web-agents-that-actually-learn) (Browser Use, April 2026) — the Cloud skills platform.
- Rich Sutton, [*The Bitter Lesson*](http://www.incompleteideas.net/IncIdeas/BitterLesson.html) (2019).

*Original study note — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
