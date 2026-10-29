package moiraloom.moiraloom;

import moiraloom.moiraloom.domain.MoiraLoomStats;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MoiraLoom is a lightweight asynchronous job execution framework
 * built on top of Java's {@link ExecutorService}.
 *
 * <p>It provides a simple configuration API for managing concurrent
 * job execution using platform threads or virtual threads.</p>
 *
 * <h2>Configuration Options</h2>
 *
 * <ul>
 *     <li>
 *         {@link #maxConcurrentJobs(int)} - Sets the maximum number
 *         of jobs allowed to execute concurrently.
 *     </li>
 *     <li>
 *         {@link #useVirtualThreads(boolean)} - Enables or disables
 *         virtual threads for job execution.
 *     </li>
 * </ul>
 *
 * <p>A {@link Semaphore} limits how many submitted jobs can execute
 * their bodies simultaneously, regardless of the executor type.</p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * public class Main {
 *
 *     public static void main(String[] args) {
 *
 *         // Configure MoiraLoom
 *         MoiraLoom moiraLoom = new MoiraLoom("mainClassTest")
 *                 .useVirtualThreads(true)
 *                 .maxConcurrentJobs(5)
 *                 .onTaskFailure(error -> {
 *                   System.out.println("Error: " + error.getMessage());
 *                 })
 *                 .build();
 *
 *         // Submit an asynchronous job
 *         Future<?> future = moiraLoom.submit(() -> {
 *             System.out.println("Executing job...");
 *         });
 *
 *        // or just call it directly
 *        // Job failures invoke the configured failure callback.
 *        // Detailed exception logging is optional.
 *        // Call future.get() to observe a job failure as an ExecutionException.
 *         moiraLoom.submit(() -> {
 *             System.out.println("Test");
 *         });
 *
 *         // Shut down the executor
 *         moiraLoom.close();
 *     }
 * }
 * }</pre>
 *
 * <h2>Example usage using try with resources</h2>
 * <p>Benefit of this is to auto close the resource instead of needing to call the .close() method.
 *    More verbose however it's cleaner, you have multiple options.</p>
 * <pre>{@code
 * public class Main {
 *
 *     public static void main(String[] args) {
 *
 *         // Configure MoiraLoom
 *         try(MoiraLoom moiraLoom = new MoiraLoom("mainClassTest")
 *                 .useVirtualThreads(true)
 *                 .maxConcurrentJobs(5)
 *                 .onTaskFailure(error -> {
 *                   System.out.println("Error: " + error.getMessage());
 *                 })
 *                 .build()) {
 *
 *         // Submit an asynchronous job
 *         Future<?> future = moiraLoom.submit(() -> {
 *             System.out.println("Executing job...");
 *         });
 *
 *         // or just call it directly
 *         // NOTE: .submit() logs(optionally) errors and throws the exception
 *         // it's a good idea to surround these with try / catch blocks or
 *         // just call the future.get() method with a try / catch block.
 *         moiraLoom.submit(() -> {
 *             System.out.println("Test");
 *          });
 *        }
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread Management</h2>
 *
 * <p>When virtual threads are enabled, MoiraLoom uses
 * {@link Executors#newVirtualThreadPerTaskExecutor()}.
 * Otherwise, it uses a fixed thread pool.</p>
 *
 * <p>With virtual threads, the semaphore limits concurrent job
 * execution but does not limit the number of virtual threads
 * created or waiting for permits.</p>
 *
 * <p>A JVM shutdown hook is registered during
 * {@link #build()} to request executor shutdown when the
 * application terminates.</p>
 *
 * @see ExecutorService
 * @see Semaphore
 * @see MoiraLoomJob
 */
public class MoiraLoom implements AutoCloseable {

    private int maxConcurrentJobs;

    private final Logger logger = Logger.getLogger(MoiraLoom.class.getName());

    private ExecutorService executorService;

    private boolean useVirtualThreads;

    private final AtomicBoolean built = new AtomicBoolean(false);

    private final AtomicInteger jobsCurrentlyRunning = new AtomicInteger(0);

    private final AtomicInteger jobsCompleted = new AtomicInteger(0);

    private final AtomicInteger jobsFailed = new AtomicInteger(0);

    Semaphore semaphore;

    private final String moiraLoomName;

    private boolean useDetailedErrorLogging;

    private Thread shutdownHook;

    private Consumer<Throwable> failureHandler;

    public MoiraLoom(String moiraLoomName) {
        this.moiraLoomName = moiraLoomName;
    }

    /**
     * Configures the maximum number of concurrently executing jobs.
     *
     * <p>When using platform threads, this value also determines
     * the size of the fixed thread pool.</p>
     *
     * <p>When using virtual threads, this value controls the number
     * of semaphore permits rather than the number of threads created.</p>
     *
     * @param maxConcurrentJobs the maximum number of concurrent jobs
     * @return this MoiraLoom instance for method chaining
     */
    public synchronized MoiraLoom maxConcurrentJobs(int maxConcurrentJobs) {
        if (!built.get()) {
            this.maxConcurrentJobs = maxConcurrentJobs;
            return this;
        } else {
            throw new IllegalStateException("You cannot change the maxConcurrentJobs value after the object is built");
        }
    }

    /**
     * Enables or disables virtual thread execution.
     *
     * <p>When enabled, MoiraLoom creates an executor using
     * {@link Executors#newVirtualThreadPerTaskExecutor()}.</p>
     *
     * <p>When disabled, MoiraLoom uses a fixed thread pool
     * configured by {@link #maxConcurrentJobs(int)}.</p>
     *
     * @param useVirtualThreads true to enable virtual threads,
     *                          false to use platform threads
     * @return this MoiraLoom instance for method chaining
     */
    public synchronized MoiraLoom useVirtualThreads(boolean useVirtualThreads) {
        if (!built.get()) {
            this.useVirtualThreads = useVirtualThreads;
            return this;
        } else {
            throw new IllegalStateException("You cannot change useVirtualThreads after the object is built");
        }
    }

    /**
     * Basic getter for analytics of active threads / jobs.
     * @return the value from the AtomicInteger jobsCurrentlyRunning
     */
    public int getCurrentRunningJobs() {
        return this.jobsCurrentlyRunning.get();
    }

    /**
     * Basic getter for analytics of total completed jobs.
     * @return the value of the AtomicInteger jobsCompleted
     */
    public int getTotalCompletedJobs() {
        return this.jobsCompleted.get();
    }

    /**
     * Basic getter for analytics of total failed jobs.
     * @return the value of the AtomicInteger jobsFailed
     */
    public int getTotalFailedJobs() {
        return this.jobsFailed.get();
    }

    /**
     * Useful for viewing current MoiraLoom objects statistics. Such as:
     * <br>
     * <ul>
     *     <li>Name of the MoiraLoom thread pool</li>
     *     <li>Current jobs running</li>
     *     <li>Max allowed thread allocation</li>
     *     <li>If the threads are Virtual threads</li>
     *     <li>Total jobs completed</li>
     *     <li>Total jobs failed</li>
     * </ul>
     * <br>
     * @apiNote Since the MoiraLoom api is async by default, getStats may return delayed data
     * (since this will return immediate while the other threads will take longer). Please use a Future and use get()
     * in order to get accurate stats(It blocks the calling thread it's finished).
     * @return MoiraLoomStats object.
     * @see MoiraLoomStats
     */
    public MoiraLoomStats getStats() {
        return new MoiraLoomStats(
                    moiraLoomName,
                    jobsCurrentlyRunning.get(),
                    maxConcurrentJobs,
                    useVirtualThreads,
                    jobsCompleted.get(),
                    jobsFailed.get()
            );
    }

    /**
     * Configuration if you want the most detailed stack traces and errors to be printed. The default is
     * the timestamp of when the failure occurred.
     * Set this to true if you want these fields printed: <br>
     * <ul>
     *     <li>Failure</li>
     *     <li>Failure message</li>
     *     <li>Failure cause</li>
     * </ul>
     * @param useDetailedErrorLogging Boolean
     * @return MoiraLoom object with the useDetailedErrorLogging field edited.
     */
    public synchronized MoiraLoom useDetailedErrorLogging(boolean useDetailedErrorLogging) {
        if (!built.get()) {
            this.useDetailedErrorLogging = useDetailedErrorLogging;
            return this;
        } else {
            throw new IllegalStateException("You cannot change useDetailedErrorLogging after the object is built");
        }
    }

    /**
     * A way for users to implement custom logic if the task fails, ie: stop hosting on a port,
     * stop running a database query, etc. The handler is then accepted in a private method in this class
     * which runs when the task / job fails which then runs handler.accept(failure)
     * @return MoiraLoom object with the taskFailureJob edited
     */
    public synchronized MoiraLoom onTaskFailure(Consumer<Throwable> handler) {
        if (built.get()) {
            throw new IllegalStateException("Please set .onTaskFailure() before calling .build()");
        }
        this.failureHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /**
     * Helper method for the .submit() method, this captures the exceptions thrown during a job failure and
     * runs the set task and prints(optionally) the exceptions details ie: Stack trace, cause, message.
     */
    private void executeJobFailure(Throwable failure) {
        logger.log(Level.SEVERE, "Job failed. Time of failure: " + Instant.now());

        jobsFailed.incrementAndGet();

        if (useDetailedErrorLogging) {
            logger.log(Level.SEVERE, "Failure: ", failure);
            logger.log(Level.SEVERE, "Failure message: " + failure.getMessage());
            logger.log(Level.SEVERE, "Failure cause: " + failure.getCause());
        }

        if (failureHandler == null) {
            return;
        }

        try {
            failureHandler.accept(failure);
        } catch (RuntimeException | Error e) {
            logger.log(Level.SEVERE, "Error running failure handler. Error: " + e.getMessage());
            throw e;
        }
        }

    /**
     * Initializes MoiraLoom using the configured settings.
     *
     * <p>This method creates the appropriate {@link ExecutorService},
     * initializes the {@link Semaphore}, and registers a JVM
     * shutdown hook. We use daemon threads so the
     * work that is being processed may not save or
     * finish running if the application is forcefully
     * shutdown or interrupted.</p>
     *
     * <p>The shutdown hook calls {@code shutdownNow()} to request
     * interruption of running jobs and returns tasks that never
     * commenced execution.</p>
     *
     * <p>This method must be called before submitting jobs and
     * can only be called once per MoiraLoom instance.</p>
     *
     * @return this initialized MoiraLoom instance
     *
     * @throws IllegalStateException if MoiraLoom has already been built
     * @throws IllegalArgumentException if maxConcurrentJobs is
     *                                  less than or equal to zero
     *
     * @see #submit(MoiraLoomJob)
     * @see #close()
     */
    public synchronized MoiraLoom build() {

        // Prevent multiple build calls
        if (built.get()) {
            throw new IllegalStateException(
                    "You cannot call build more than once per MoiraLoom initialization"
            );
        }

        // Validate concurrency configuration
        if (maxConcurrentJobs <= 0) {
            throw new IllegalArgumentException(
                    "maxConcurrentJobs must be greater than zero"
            );
        }

        if (failureHandler == null) {
            throw new IllegalStateException("Please set .onTaskFailure()'s runnable");

        }

        // Initialize the appropriate executor
        if (useVirtualThreads) {
            this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        } else {
            this.executorService = Executors.newFixedThreadPool(
                    maxConcurrentJobs,
                    Thread.ofPlatform()
                            .daemon(true)
                            .name("moiraLoom-", 0)
                            .factory()
            );
        }

        // Limit concurrent job execution
        semaphore = new Semaphore(maxConcurrentJobs);

        // Register a JVM shutdown hook
        shutdownHook = (new Thread(() -> {
            int pendingJobs = executorService.shutdownNow().size();

            logger.log(
                    Level.INFO,
                    "Returned " + pendingJobs + " pending jobs."
            );
        }));

        Runtime.getRuntime().addShutdownHook(shutdownHook);

        built.set(true);

        return this;
    }

    /**
     * Submits a job for asynchronous execution.
     *
     * <p>A semaphore permit is reserved before submission to the configured
     * {@link ExecutorService}. If no permit is available, this method
     * immediately throws {@link RejectedExecutionException} without
     * waiting for capacity.</p>
     *
     * <p>The limit configured through {@link #maxConcurrentJobs(int)}
     * applies to accepted jobs that have not yet finished, including
     * jobs that have been submitted but have not started executing.</p>
     *
     * <p>If executor submission fails, the reserved permit is returned.
     * Once the job starts, its permit is released in a {@code finally}
     * block when execution finishes, including when the job throws.</p>
     *
     * <p>If the job throws a {@link RuntimeException}, the exception is
     * logged and captured by the returned {@link Future}. Calling
     * {@link Future#get()} reports the failure through an
     * {@link ExecutionException}.</p>
     *
     * <h3>Example</h3>
     *
     * <pre>{@code
     * Future<?> future = MoiraLoom.submit(() -> {
     *     System.out.println("Running asynchronous job");
     * });
     *
     * // Wait for completion and observe any execution failure
     * future.get();
     *
     * // Or just let the .submit() handle failures instead
     * // .submit() propegates errors via logs and throws the Exception
     * moiraLoom.submit(() -> {
     *    System.out.println("Running async job.");
     * });
     * }</pre>
     *
     * @param job the job to execute asynchronously
     * @return a Future representing the submitted job
     * @see MoiraLoomJob
     * @see Future
     * @see Semaphore
     */
    public Future<?> submit(MoiraLoomJob job) {

        Objects.requireNonNull(job, "job");

        // Ensure MoiraLoom has been initialized
        if (!built.get()) {
            throw new IllegalStateException(
                    "You must call .build() before calling .submit()"
            );
        }

        // check that the max jobs are not full / taken
        if (!semaphore.tryAcquire()) {
            throw new RejectedExecutionException(
                    "MoiraLoom job queue is currently full"
            );
        }

        try {
            return executorService.submit(() -> {

                try {

                    jobsCurrentlyRunning.incrementAndGet();

                    // Execute the submitted job
                    job.run();

                    jobsCompleted.incrementAndGet();

                } catch (RuntimeException e) {

                    executeJobFailure(e);
                    throw e;

                } finally {

                    jobsCurrentlyRunning.decrementAndGet();

                    semaphore.release();

                }
            });
        } catch (Exception e) {
            executeJobFailure(e);
            semaphore.release();
            throw e;
        }
    }

    /**
     * Closes the MoiraLoom executor service.
     *
     * <p>This method calls {@link ExecutorService#close()},
     * which initiates an orderly shutdown and waits for submitted
     * tasks to complete.</p>
     *
     * <p>Users may call this method manually when MoiraLoom is
     * no longer needed. A JVM shutdown hook is also registered
     * as a fallback for application termination.</p>
     *
     * <p>Calling this method prevents any new jobs from being
     * submitted to the executor.</p>
     *
     * <p>This method also removes the shutdown hooks that are registered when it's
     *    called.</p>
     *
     * @throws IllegalStateException if MoiraLoom has not been built
     *
     * @see ExecutorService#close()
     */
    @Override
    public void close() {

        if (!built.get()) {
            throw new IllegalStateException(
                    "You must call .build() before calling .close()"
            );
        }

        executorService.close();

        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, e.getMessage());
        }
    }
}