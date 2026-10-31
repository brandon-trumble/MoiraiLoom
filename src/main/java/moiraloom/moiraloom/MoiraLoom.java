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

    private int maxPendingJobs;

    private final Logger logger = Logger.getLogger(MoiraLoom.class.getName());

    private ExecutorService executorService;

    private boolean useVirtualThreads;

    private final AtomicBoolean built = new AtomicBoolean(false);

    private final AtomicInteger jobsCurrentlyRunning = new AtomicInteger(0);

    private final AtomicInteger jobsCompleted = new AtomicInteger(0);

    private final AtomicInteger jobsFailed = new AtomicInteger(0);

    private final AtomicInteger jobsPending = new AtomicInteger(0);

    private final AtomicInteger jobsCancelled = new AtomicInteger(0);

    Semaphore maxConcurrentJobsSemaphore;

    Semaphore maxPendingJobsSemaphore;

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
     * of maxConcurrentJobsSemaphore permits rather than the number of threads created.</p>
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
     * Configures additional admission capacity beyond the concurrent
     * execution limit.
     *
     * <p>Total admission capacity is
     * {@code maxConcurrentJobs + maxPendingJobs}. A value of zero
     * provides no additional capacity beyond the concurrency limit.</p>
     *
     * <p>This setting is not an independent limit on the pending-job
     * counter. Accepted jobs may briefly remain pending while workers
     * are starting, even when execution permits are available.</p>
     *
     * <p>The value must be non-negative. Validation occurs when
     * {@link #build()} is called.</p>
     *
     * @param maxPendingJobs the additional admission capacity
     * @return this instance for method chaining
     * @throws IllegalStateException if this instance has already
     *         been built
     */
    public synchronized MoiraLoom maxPendingJobs(int maxPendingJobs) {
        if (!built.get()) {
            this.maxPendingJobs= maxPendingJobs;
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
     * Returns a best-effort snapshot of this instance's statistics.
     *
     * <p>The snapshot contains the instance name, concurrency limit,
     * execution mode, running-job count, completed-job count, and
     * failed-job count. Pending and canceled counts are not included
     * in the current statistics object.</p>
     *
     * <p>Counters are read independently while other threads may
     * update them. The returned values therefore do not necessarily
     * describe one exact instant.</p>
     *
     * <p>This method does not wait for jobs to finish. Waiting for
     * one job's future does not wait for other submitted jobs.</p>
     *
     * @return a statistics snapshot
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
     * Initializes this instance using the configured settings.
     *
     * <p>Creates the executor, initializes the execution and admission
     * semaphores, and registers a JVM shutdown hook. Configuration
     * cannot be changed after a successful build.</p>
     *
     * <p>Virtual-thread mode uses
     * {@link Executors#newVirtualThreadPerTaskExecutor()}.
     * Platform-thread mode uses a fixed pool of daemon threads
     * sized according to {@link #maxConcurrentJobs(int)}.</p>
     *
     * <p>The shutdown hook calls {@link ExecutorService#close()}.
     * This normally waits for accepted work to finish, but does not
     * provide durable storage or protection against abrupt process
     * termination.</p>
     *
     * <p>This method must complete successfully before jobs can be
     * submitted. An instance cannot be built again after a successful
     * build, including after it has been closed.</p>
     *
     * @return this initialized instance
     * @throws IllegalStateException if this instance has already
     *         been built
     * @throws IllegalArgumentException if {@code maxConcurrentJobs}
     *         is not positive or {@code maxPendingJobs} is negative
     * @throws ArithmeticException if the combined admission capacity
     *         exceeds {@link Integer#MAX_VALUE}
     *
     * @see #submit(MoiraLoomJob)
     * @see #submit(Callable)
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

        // Validate pending task configuration
        if (maxPendingJobs < 0) {
            throw new IllegalArgumentException(
                    "maxPendingJobs cannot be negative"
            );
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

        maxConcurrentJobsSemaphore = new Semaphore(
                maxConcurrentJobs, true
        );

        maxPendingJobsSemaphore = new Semaphore(
                Math.addExact(maxConcurrentJobs, maxPendingJobs), true
        );

        // Register a JVM shutdown hook
        shutdownHook = new Thread(() -> {
            logger.info("Shutting down MoiraLoom");

            executorService.close();

            logger.info("MoiraLoom shutdown complete");
        });

        Runtime.getRuntime().addShutdownHook(shutdownHook);

        built.set(true);

        return this;
    }

    /**
     * Submits a job that does not produce a result.
     *
     * <p>The job executes asynchronously. The returned {@link Future}
     * can be used to wait for completion, observe execution failures,
     * or request cancellation. Its {@link Future#get() get()} method
     * returns {@code null} on successful completion.</p>
     *
     * <p>This method delegates to {@link #submit(Callable)} and uses
     * the same capacity limits, failure handling, and cancellation
     * behavior.</p>
     *
     * <h3>Example</h3>
     *
     * <pre>{@code
     * Future<?> future = moiraLoom.submit(() -> {
     *     System.out.println("Running a background job");
     * });
     * }</pre>
     *
     * @param job the job to execute
     * @return a future representing the job's completion
     * @throws NullPointerException if {@code job} is {@code null}
     * @throws IllegalStateException if {@link #build()} has not
     *         completed successfully
     * @throws RejectedExecutionException if admission capacity is
     *         exhausted or the executor no longer accepts work
     *
     * @see #submit(Callable)
     * @see Future
     */
    public Future<?> submit(MoiraLoomJob job) {
        Objects.requireNonNull(job, "job");

        Callable<Void> callable = () -> {
            job.run();
            return null;
        };

        return submit(callable);
    }

    /**
     * Submits a result-producing job for asynchronous execution.
     *
     * <p>The job's {@link Callable#call() call()} method executes on
     * an executor-managed thread. This method returns a {@link Future}
     * without waiting for the job to finish.</p>
     *
     * <h3>Capacity and concurrency</h3>
     *
     * <p>Total admission capacity is the sum of
     * {@link #maxConcurrentJobs(int) maxConcurrentJobs} and
     * {@link #maxPendingJobs(int) maxPendingJobs}. Each submission
     * must reserve one admission permit before entering the executor.
     * If no permit is available, submission is rejected immediately.</p>
     *
     * <p>Accepted workers wait for an execution permit before invoking
     * the job. No more than {@code maxConcurrentJobs} job bodies may
     * execute simultaneously.</p>
     *
     * <h3>Results and failures</h3>
     *
     * <p>Calling {@link Future#get() get()} waits for completion and
     * returns the value produced by the job, which may be {@code null}.
     * An execution failure is reported through an
     * {@link ExecutionException}, unless the future was cancelled.</p>
     *
     * <p>Execution failures are not thrown directly to the thread
     * that called this submission method.</p>
     *
     * <p>Exceptions and errors caught during execution are recorded
     * and passed to the configured failure handler, except for
     * {@link InterruptedException}. An interruption restores the
     * worker's interrupt status and is propagated without invoking
     * the failure handler or incrementing the failed-job counter.</p>
     *
     * <p>If the configured failure handler throws, its exception
     * can replace the original execution failure reported by
     * the future.</p>
     *
     * <h3>Cancellation</h3>
     *
     * <p>The returned future supports {@link Future#cancel(boolean)}.
     * Requesting interruption does not guarantee that executing
     * job code stops; the job must cooperate with interruption.</p>
     *
     * <p>Cancellation cleanup returns admission capacity when it
     * claims a still-pending job. Once execution has claimed the job,
     * its capacity remains reserved until execution cleanup runs.</p>
     *
     * <p>With virtual threads, cancelling without interruption can
     * leave an already-waiting worker blocked until an execution
     * permit becomes available.</p>
     *
     * <h3>Example</h3>
     *
     * <pre>{@code
     * Future<Integer> future = moiraLoom.submit(() -> {
     *     int subtotal = 120;
     *     int shipping = 15;
     *     return subtotal + shipping;
     * });
     *
     * try {
     *     int total = future.get();
     *     System.out.println(total);
     * } catch (InterruptedException e) {
     *     Thread.currentThread().interrupt();
     * } catch (ExecutionException e) {
     *     System.err.println("Job failed: " + e.getCause());
     * } catch (CancellationException e) {
     *     System.out.println("Job was cancelled");
     * }
     * }</pre>
     *
     * @param <T> the type of result produced by the job
     * @param job the job to execute
     * @return a future providing access to the job's result,
     *         completion status, and cancellation
     * @throws NullPointerException if {@code job} is {@code null}
     * @throws IllegalStateException if {@link #build()} has not
     *         completed successfully
     * @throws RejectedExecutionException if admission capacity is
     *         exhausted or the executor no longer accepts work
     *
     * @see #submit(MoiraLoomJob)
     * @see Callable
     * @see Future
     */
    public <T> Future<T> submit(Callable<T> job) {
        Objects.requireNonNull(job, "job");

        if (!built.get()) {
            throw new IllegalStateException(
                    "Call .build() before .submit()"
            );
        }

        if (!maxPendingJobsSemaphore.tryAcquire()) {
            throw new RejectedExecutionException(
                    "MoiraLoom job capacity is full"
            );
        }

        jobsPending.incrementAndGet();

        AtomicBoolean pending = new AtomicBoolean(true);

        // Return capacity only if execution has not claimed this job.
        Runnable releasePending = () -> {
            if (pending.compareAndSet(true, false)) {
                jobsPending.decrementAndGet();
                maxPendingJobsSemaphore.release();
            }
        };

        FutureTask<T> task = new FutureTask<>(() -> {
            boolean acquired = false;
            boolean started = false;

            try {
                maxConcurrentJobsSemaphore.acquire();
                acquired = true;

                // Skip user code if cancellation already claimed the job.
                if (!pending.compareAndSet(true, false)) {
                    return null;
                }

                started = true;
                jobsPending.decrementAndGet();
                jobsCurrentlyRunning.incrementAndGet();

                T result = job.call();
                jobsCompleted.incrementAndGet();

                return result;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;

            } catch (Exception | Error e) {
                executeJobFailure(e);
                throw e;

            } finally {
                if (started) {
                    jobsCurrentlyRunning.decrementAndGet();
                    maxPendingJobsSemaphore.release();
                } else {
                    releasePending.run();
                }

                if (acquired) {
                    maxConcurrentJobsSemaphore.release();
                }
            }
        }) {
            @Override
            protected void done() {
                if (isCancelled()) {
                    jobsCancelled.incrementAndGet();
                    releasePending.run();

                    if (executorService instanceof ThreadPoolExecutor pool) {
                        pool.remove(this);
                    }
                }
            }
        };

        try {
            executorService.execute(task);
            return task;

        } catch (RuntimeException | Error e) {
            releasePending.run();
            throw e;
        }
    }

    /**
     * Stops accepting new jobs and waits for executor termination.
     *
     * <p>Delegates to {@link ExecutorService#close()}, which normally
     * allows previously accepted work to finish before returning.</p>
     *
     * <p>If the calling thread is interrupted while waiting, the
     * executor attempts to interrupt executing work and does not
     * execute work still awaiting execution. It continues waiting
     * for termination and restores the caller's interrupt status.</p>
     *
     * <p>This method has no timeout. Jobs that do not terminate can
     * prevent it from returning. Do not call this method from a job
     * executing on this instance, because it can wait for its own
     * completion.</p>
     *
     * <p>After executor termination, this method attempts to remove
     * the registered shutdown hook. The instance cannot be rebuilt
     * or reused for new submissions.</p>
     *
     * @throws IllegalStateException if {@link #build()} has not
     *         completed successfully
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