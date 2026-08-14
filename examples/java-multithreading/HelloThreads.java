// The smallest, simplest Java multithreading example.
// Two threads run the SAME task at the same time; main waits for both to finish.
//
// Run it:
//   java HelloThreads          (JDK 11+ runs a single .java file directly)
// or compile + run:
//   javac HelloThreads.java && java HelloThreads

public class HelloThreads {

    public static void main(String[] args) throws InterruptedException {

        // 1. Define the work as a Runnable (a lambda — no return value).
        Runnable task = () -> {
            for (int i = 1; i <= 3; i++) {
                System.out.println(Thread.currentThread().getName() + " → " + i);
            }
        };

        // 2. Create two threads that will run that task.
        Thread a = new Thread(task, "Thread-A");
        Thread b = new Thread(task, "Thread-B");

        // 3. start() runs each on a NEW thread, concurrently.
        //    (Calling task.run() or a.run() would run inline on the main thread — no concurrency.)
        a.start();
        b.start();

        // 4. join() makes main WAIT until each thread finishes.
        a.join();
        b.join();

        System.out.println("Both threads done.");
    }
}

// Expected output: A and B lines INTERLEAVE, and the order changes between runs
// (that's concurrency — the two threads race). "Both threads done." always prints last,
// because of the join() calls.
//
// Example run:
//   Thread-A → 1
//   Thread-B → 1
//   Thread-A → 2
//   Thread-B → 2
//   Thread-A → 3
//   Thread-B → 3
//   Both threads done.
