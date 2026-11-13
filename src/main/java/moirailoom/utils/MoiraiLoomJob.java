package moirailoom.utils;

/**
 * Represents a job that can be submitted to MoiraiLoom for execution.
 *
 * <p>Implement this interface and override the {@link #run()} method
 * to define a reusable job instead of submitting a lambda.</p>
 *
 * <h2>Example 1: Implementing MoiraiLoomJob in a class</h2>
 *
 * <pre>{@code
 * public class TestClass implements MoiraiLoomJob {
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
 * <p>Then instantiate and submit the job to MoiraiLoom:</p>
 *
 * <pre>{@code
 * public class Main {
 *
 *     public static void main(String[] args) {
 *
 *         // Configure and build MoiraiLoom
 *         MoiraiLoom moiraiLoom = new MoiraiLoom()
 *                 .useVirtualThreads(true)
 *                 .maxConcurrentJobs(2)
 *                 .build();
 *
 *         // Create a reusable job instance
 *         TestClass testClass = new TestClass();
 *
 *         // Submit the job
 *         moiraiLoom.submit(testClass);
 *
 *         // Shut down the executor when finished
 *         moiraiLoom.close();
 *     }
 * }
 * }</pre>
 *
 * <h2>Example 2: Using an anonymous class</h2>
 *
 * <p>For simpler jobs, create a MoiraiLoomJob directly without
 * implementing it in a separate class.</p>
 *
 * <pre>{@code
 * public class Main {
 *
 *     public static void main(String[] args) {
 *
 *         MoiraiLoomJob moiraiLoomJob = new MoiraiLoomJob() {
 *
 *             @Override
 *             public void run() {
 *                 // Perform the job
 *             }
 *         };
 *
 *         // Configure and build MoiraiLoom
 *         MoiraiLoom moiraiLoom = new MoiraiLoom()
 *                 .useVirtualThreads(true)
 *                 .maxConcurrentJobs(2)
 *                 .build();
 *
 *         // Submit the job
 *         moiraiLoom.submit(moiraiLoomJob);
 *
 *         // Shut down the executor
 *         moiraiLoom.close();
 *     }
 * }
 * }</pre>
 *
 * @see Runnable
 * @see MoiraiLoom
 */
@FunctionalInterface
public interface MoiraiLoomJob extends Runnable {

    /**
     * Executes the task defined by this job.
     */
    @Override
    void run();
}