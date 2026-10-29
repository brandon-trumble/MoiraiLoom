package taskforge.taskforge;

/**
 * Represents a job that can be submitted to TaskForge for execution.
 *
 * <p>Implement this interface and override the {@link #run()} method
 * to define a reusable job instead of submitting a lambda.</p>
 *
 * <h2>Example 1: Implementing TaskForgeJob in a class</h2>
 *
 * <pre>{@code
 * public class TestClass implements TaskForgeJob {
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
 * <p>Then instantiate and submit the job to TaskForge:</p>
 *
 * <pre>{@code
 * public class Main {
 *
 *     public static void main(String[] args) {
 *
 *         // Configure and build TaskForge
 *         TaskForge taskForge = new TaskForge()
 *                 .useVirtualThreads(true)
 *                 .maxConcurrentJobs(2)
 *                 .build();
 *
 *         // Create a reusable job instance
 *         TestClass testClass = new TestClass();
 *
 *         // Submit the job
 *         taskForge.submit(testClass);
 *
 *         // Shut down the executor when finished
 *         taskForge.close();
 *     }
 * }
 * }</pre>
 *
 * <h2>Example 2: Using an anonymous class</h2>
 *
 * <p>For simpler jobs, create a TaskForgeJob directly without
 * implementing it in a separate class.</p>
 *
 * <pre>{@code
 * public class Main {
 *
 *     public static void main(String[] args) {
 *
 *         TaskForgeJob taskForgeJob = new TaskForgeJob() {
 *
 *             @Override
 *             public void run() {
 *                 // Perform the job
 *             }
 *         };
 *
 *         // Configure and build TaskForge
 *         TaskForge taskForge = new TaskForge()
 *                 .useVirtualThreads(true)
 *                 .maxConcurrentJobs(2)
 *                 .build();
 *
 *         // Submit the job
 *         taskForge.submit(taskForgeJob);
 *
 *         // Shut down the executor
 *         taskForge.close();
 *     }
 * }
 * }</pre>
 *
 * @see Runnable
 * @see TaskForge
 */
@FunctionalInterface
public interface TaskForgeJob extends Runnable {

    /**
     * Executes the task defined by this job.
     */
    @Override
    void run();
}