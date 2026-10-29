package moiraloom.moiraloom;

/**
 * Represents a job that can be submitted to MoiraLoom for execution.
 *
 * <p>Implement this interface and override the {@link #run()} method
 * to define a reusable job instead of submitting a lambda.</p>
 *
 * <h2>Example 1: Implementing MoiraLoomJob in a class</h2>
 *
 * <pre>{@code
 * public class TestClass implements MoiraLoomJob {
 *
 *     @Override
 *     public void run() {
 *         doSomething();
 *     }
 *
 *     public void doSomething() {
 *         // Run tasks, query a database, etc.
 *     }
 * }
 * }</pre>
 *
 * <p>Then instantiate and submit the job to MoiraLoom:</p>
 *
 * <pre>{@code
 * public class Main {
 *
 *     public static void main(String[] args) {
 *
 *         // Configure and build MoiraLoom
 *         MoiraLoom moiraLoom = new MoiraLoom()
 *                 .useVirtualThreads(true)
 *                 .maxConcurrentJobs(2)
 *                 .build();
 *
 *         // Create a reusable job instance
 *         TestClass testClass = new TestClass();
 *
 *         // Submit the job
 *         moiraLoom.submit(testClass);
 *
 *         // Shut down the executor when finished
 *         moiraLoom.close();
 *     }
 * }
 * }</pre>
 *
 * <h2>Example 2: Using an anonymous class</h2>
 *
 * <p>For simpler jobs, create a MoiraLoomJob directly without
 * implementing it in a separate class.</p>
 *
 * <pre>{@code
 * public class Main {
 *
 *     public static void main(String[] args) {
 *
 *         MoiraLoomJob moiraLoomJob = new MoiraLoomJob() {
 *
 *             @Override
 *             public void run() {
 *                 // Perform the job
 *             }
 *         };
 *
 *         // Configure and build MoiraLoom
 *         MoiraLoom moiraLoom = new MoiraLoom()
 *                 .useVirtualThreads(true)
 *                 .maxConcurrentJobs(2)
 *                 .build();
 *
 *         // Submit the job
 *         moiraLoom.submit(moiraLoomJob);
 *
 *         // Shut down the executor
 *         moiraLoom.close();
 *     }
 * }
 * }</pre>
 *
 * @see Runnable
 * @see MoiraLoom
 */
@FunctionalInterface
public interface MoiraLoomJob extends Runnable {

    /**
     * Executes the task defined by this job.
     */
    @Override
    void run();
}