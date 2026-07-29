# 🔀 React Flow — A Study Note

> **Level:** 🟢 Beginner–Intermediate · **Reading time:** ~15 min · **Scope:** React Flow **v12** (`@xyflow/react`), maintained by [xyflow](https://xyflow.com/). Pairs with the [Data Structures & Algorithms](data-structures-algorithms.md) note — a node-based editor is a **graph** you can see and edit.

**React Flow** is a customizable React component for building **node-based editors and interactive diagrams** — workflow builders, pipeline editors, mind maps, no-code tools, dependency graphs. It gives you dragging, zoom/pan, selection, and connecting **out of the box**, while letting every node and edge be an ordinary React component you fully control. It's MIT-licensed open source (used by Stripe, Zapier, and Retool), with a **React Flow Pro** tier for advanced examples/support.

> **The mental model:** React Flow is a **controlled component**. *You* own two arrays — `nodes` and `edges` — in your app state; React Flow **renders** them and reports **interactions** (a node moved, an edge was drawn) back as change events you apply to that state. It's the same "you hold the state, the library renders it" contract as a controlled `<input>`, scaled up to a graph canvas.

## Table of contents

- [1. When to reach for it (and when not)](#1-when-to-reach-for-it-and-when-not)
- [2. The core concepts](#2-the-core-concepts)
- [3. A minimal example](#3-a-minimal-example)
- [4. State: controlled nodes & edges](#4-state-controlled-nodes--edges)
- [5. Custom nodes & edges](#5-custom-nodes--edges)
- [6. Built-in components](#6-built-in-components)
- [7. Layout, hooks & the instance](#7-layout-hooks--the-instance)
- [8. Performance](#8-performance)
- [9. Best practices & anti-patterns](#9-best-practices--anti-patterns)
- [10. Go deeper](#10-go-deeper)

---

## 1. When to reach for it (and when not)

**Great fit:** anything where users **create, connect, and arrange nodes** — workflow/automation builders (Zapier-style), ETL/data pipelines, ML/agent graphs, flowcharts, org charts, whiteboards, no-code/low-code canvases, dependency visualizers.

**Not the tool for:** static charts (use a [charting lib](../resources/)), simple tree views, or pure read-only diagrams where [Mermaid](../resources/) or an SVG would be lighter. React Flow earns its weight when the graph is **interactive and editable**.

> It handles the hard, fiddly parts — pan/zoom math, drag, connection handles, selection, viewport coordinates — so you build the *domain*, not a canvas engine.

---

## 2. The core concepts

Four primitives cover almost everything:

| Concept | What it is |
| ------- | ---------- |
| **Node** | A draggable element: `{ id, position: {x, y}, data: {…}, type }`. `data` is *yours* — whatever your node component needs. |
| **Edge** | A connection between two nodes: `{ id, source, target, type, animated, label }`. |
| **Handle** | The connection point *on* a node where edges attach — `<Handle type="source|target" position={Position.Right} />`. A node can have many. |
| **Viewport** | The pannable/zoomable canvas transform (x, y, zoom). React Flow manages the coordinate math. |

Nodes and edges are keyed by **`id`** (a string you assign) — React Flow is diffing these arrays, so stable ids matter, exactly like React `key`s.

---

## 3. A minimal example

```jsx
import { ReactFlow, Background, Controls, useNodesState, useEdgesState, addEdge } from '@xyflow/react';
import { useCallback } from 'react';
import '@xyflow/react/dist/style.css';   // required — the canvas has no styling without it

const initialNodes = [
  { id: '1', position: { x: 0,   y: 0 }, data: { label: 'Start' } },
  { id: '2', position: { x: 200, y: 100 }, data: { label: 'End' } },
];
const initialEdges = [{ id: 'e1-2', source: '1', target: '2' }];

export default function Flow() {
  const [nodes, , onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
  const onConnect = useCallback((params) => setEdges((eds) => addEdge(params, eds)), [setEdges]);

  return (
    <div style={{ width: '100%', height: '100vh' }}>   {/* parent MUST have a height */}
      <ReactFlow
        nodes={nodes} edges={edges}
        onNodesChange={onNodesChange} onEdgesChange={onEdgesChange} onConnect={onConnect}
        fitView
      >
        <Background />
        <Controls />
      </ReactFlow>
    </div>
  );
}
```

Two gotchas already visible: **import the CSS**, and **the container needs an explicit height** (a common "nothing renders" cause).

---

## 4. State: controlled nodes & edges

The heart of React Flow is three event handlers you wire to your state:

- **`onNodesChange`** — a node was moved, selected, or removed. The `useNodesState` hook gives you a ready reducer: `const [nodes, setNodes, onNodesChange] = useNodesState(initial)`.
- **`onEdgesChange`** — an edge was selected/removed. Mirror hook: `useEdgesState`.
- **`onConnect`** — the user dragged from one handle to another; you add the edge (`addEdge(params, edges)`).

`useNodesState`/`useEdgesState` are convenience helpers for local state. For anything real, **lift this state into your own store** — React Flow itself uses **Zustand** internally, and large apps commonly manage nodes/edges in Zustand/Redux so business logic, undo/redo, and persistence live in one place.

> This is the [controlled-vs-uncontrolled](api-design.md) distinction again: you *can* let React Flow hold state (uncontrolled, quick demos), but production apps go **controlled** — you own the source of truth, React Flow is the view.

---

## 5. Custom nodes & edges

The superpower: **nodes and edges are just React components.** Register them by type:

```jsx
function TaskNode({ data }) {
  return (
    <div className="task-node">
      <Handle type="target" position={Position.Left} />
      <strong>{data.title}</strong>
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

const nodeTypes = { task: TaskNode };   // ⚠️ define OUTSIDE the component (see §8)
// then: <ReactFlow nodeTypes={nodeTypes} ... />  and a node with type: 'task'
```

Anything you can render in React — forms, charts, images, buttons — can live inside a node. The **`<Handle>`** components declare where edges connect. **Custom edges** work similarly (an SVG path component via `edgeTypes`), letting you draw labeled, animated, or conditionally-styled connections.

---

## 6. Built-in components

Drop-in children of `<ReactFlow>` that cover the usual canvas furniture:

| Component | Purpose |
| --------- | ------- |
| **`<Background />`** | The dotted/grid/cross canvas backdrop |
| **`<Controls />`** | Zoom in/out, fit-view, and lock buttons |
| **`<MiniMap />`** | A bird's-eye overview of the whole graph |
| **`<Panel />`** | A positioned overlay (top-left, etc.) for your toolbars/legends |
| **`<NodeToolbar />`** | A toolbar that floats next to a node (e.g. on select) |
| **`<NodeResizer />`** | Drag-to-resize handles on a node |

These are opt-in — add only what you need.

---

## 7. Layout, hooks & the instance

- **No automatic layout.** React Flow positions nodes exactly where you tell it (`position`). For *auto*-layout (arrange a graph without hand-placing), integrate a layout library: **Dagre** or **ELK (elkjs)** for hierarchical/layered graphs, **d3-hierarchy** for trees. You run the layout, then feed the computed positions back into your nodes. (This is where the [graph algorithms](data-structures-algorithms.md) — topological order, layered DAG layout — show up for real.)
- **`useReactFlow()`** — the imperative escape hatch: `fitView()`, `getNodes()`, `setNodes()`, and crucially **`screenToFlowPosition()`** (convert a mouse click to canvas coordinates — essential for drag-and-drop-to-add). Requires wrapping your app in **`<ReactFlowProvider>`**.
- Other hooks: `useNodes`, `useEdges`, `useViewport`, `useConnection` for reading live state.

---

## 8. Performance

Node-based canvases get slow fast; the big levers:

- **Define `nodeTypes`/`edgeTypes` outside the component** (or `useMemo` them). Recreating that object every render forces React Flow to remount every node — the #1 React Flow performance bug, and it prints a console warning.
- **Memoize custom node components** (`React.memo`) so unrelated state changes don't re-render every node.
- **Keep `data` minimal and stable** — a new object identity per render defeats memoization.
- **For very large graphs** (thousands of nodes): virtualize with `onlyRenderVisibleElements`, simplify node DOM, and avoid heavy per-node work.

> Same instinct as the [DSA note](data-structures-algorithms.md): the algorithm (diffing/rendering N nodes) is fine; it's the **constants** (re-mounting, re-rendering) that kill you. Stable identities are the fix — the React echo of "stable ids / no needless churn."

---

## 9. Best practices & anti-patterns

**Do**
- **Give the container an explicit height** and **import the stylesheet** — the two setup gotchas.
- **Go controlled** — own `nodes`/`edges` in your store (Zustand fits, it's what React Flow uses); keep business logic out of node components.
- **Define `nodeTypes`/`edgeTypes` module-level or memoized.**
- **Use stable node/edge ids** (like React keys).
- **Integrate a layout lib** (Dagre/ELK) instead of hand-placing large graphs.
- **`React.memo` custom nodes**; keep `data` small.

**Avoid**
- **Recreating `nodeTypes` inline** every render (remounts everything).
- **Putting huge/derived state in each node's `data`** — store ids/refs, look up elsewhere.
- **Fighting the coordinate system** — use `screenToFlowPosition()` rather than hand-rolling transforms.
- **Reaching for React Flow for static diagrams** — Mermaid/SVG is lighter when nothing is interactive.
- **Forgetting `<ReactFlowProvider>`** when you use the hooks outside `<ReactFlow>`.

---

## 10. Go deeper

- 📘 Official: [reactflow.dev](https://reactflow.dev/) — excellent docs, a **Learn** guide, and a large examples gallery. The [xyflow](https://xyflow.com/) family also ships **Svelte Flow**.
- 📝 **[Data Structures & Algorithms](data-structures-algorithms.md)** — a node editor *is* a graph (adjacency of nodes via edges); auto-layout uses topological sort and layered-DAG algorithms.
- 📝 **[Knip](knip.md)** — builds and walks a module **graph**; the same node/edge model, just not drawn.
- 🎨 Related in [resources/](../resources/) — Mermaid (static diagrams) vs. React Flow (interactive editors): pick by whether the user *edits* the graph.

### A fun exercise for this repo

This whole notes library is a graph — each note links to others (`[[…]]`-style cross-references). A small React Flow app could render **the note graph**: one node per study note, edges for every cross-link, colored by category. It'd make the "everything connects" theme literally visible — and it's a perfect first React Flow project.

*Original study note (React Flow v12 / `@xyflow/react`) — corrections and additions welcome via a PR (see [CONTRIBUTING](../CONTRIBUTING.md)).*
