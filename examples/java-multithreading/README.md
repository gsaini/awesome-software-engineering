# ☕ Java Multithreading — Smallest Example

The minimal runnable example of "how do I run two things at once in Java": [`HelloThreads.java`](HelloThreads.java).

## Run it

```bash
# Single-file mode (JDK 11+):
java HelloThreads.java

# or compile + run:
javac HelloThreads.java && java HelloThreads
```

## What it shows

- **Create work** as a `Runnable` (a lambda).
- **Create threads** with `new Thread(task, name)`.
- **`start()`** runs each on a *new* thread, concurrently. *(`run()` would run inline — no concurrency; that's the #1 beginner mistake.)*
- **`join()`** makes `main` wait for the threads to finish.

Run it a few times: the `Thread-A` / `Thread-B` lines **interleave differently each run** — that's the two threads racing (concurrency). `"Both threads done."` always prints last, thanks to `join()`.

## Next steps (from here)

- Prefer an **`ExecutorService`** (a managed thread pool) over raw `Thread` in real code:
  ```java
  try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      pool.submit(task);
      pool.submit(task);
  } // auto-shuts down and waits
  ```
- For I/O-bound work on JDK 21+, use **virtual threads**: `Executors.newVirtualThreadPerTaskExecutor()`.
- The moment two threads touch the same mutable variable, you need synchronization (`synchronized`, `AtomicInteger`, locks) — see the full **[Concurrency & Parallelism note](../../notes/concurrency-parallelism.md)**.
