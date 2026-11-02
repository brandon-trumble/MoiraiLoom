# MoiraLoom

MoiraLoom is a Java 21 library for running background jobs with
configurable concurrency and queue capacity.

It provides a small API for submitting work, getting results, handling
failures, and checking job statistics. Jobs can run on a fixed pool of
platform threads or on virtual threads.

## Building

You need Java 21 or newer and Maven.

From the repository root, run:

```bash
mvn clean install
```

This builds the library, runs its tests, and installs it into your local
Maven repository.

You can then add it to another Maven project's `pom.xml`, inside its
`dependencies` section:

```xml
<dependency>
    <groupId>io.github.brandon-trumble</groupId>
    <artifactId>moiraloom</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Usage

Save this example as `Main.java` in a project using MoiraLoom:

```java
import moiraloom.moiraloom.MoiraLoom;
import moiraloom.moiraloom.MoiraLoomJob;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Main {

    // This example does not use try-with-resource as it's an optional
    // way to use the library.
    // However it is a good way to organize and build your code base
    public static void main(String[] args) {

        // Configure and build MoiraLoom
        MoiraLoom moiraLoom = new MoiraLoom("mainClassThreads")
                .maxConcurrentJobs(2)
                .maxPendingJobs(10)
                .useVirtualThreads(true)
                .useDetailedErrorLogging(true)
                .onTaskFailure(error -> {
                    System.err.println("Job failed: " + error.getMessage());
                })
                .build();

        // submit a test job
        moiraLoom.submit(() -> {
            System.out.println("Test job");
        });


        // test class job
        TestJob testJob = new TestJob();


        // submit a preset class(MoiraLoomJob implementation)
        moiraLoom.submit(testJob);

        moiraLoom.close();
        
        // if you want the stats of the current running jobs
        // create a Future and call .get() and then call 
        // .getStats()

    }

    private static class TestJob implements MoiraLoomJob {

        @Override
        public void run() {
            // do something
        }
    }
}
```

This example submits two jobs: a reusable `MoiraLoomJob` and a lambda that
returns a value.

`submit()` returns a `Future` without waiting for the job to finish.
Calling `get()` waits for that job and returns its result. Jobs without
a return value produce `null`.

The try-with-resources block closes MoiraLoom when it ends, waiting for
accepted work to finish.

## Configuration

Set your options before calling `build()`.

| Method                               | Purpose                              |
|--------------------------------------|--------------------------------------|
| `maxConcurrentJobs(int)`             | Limits jobs running at once.         |
| `maxPendingJobs(int)`                | Adds capacity for accepted work.     |
| `useVirtualThreads(boolean)`         | Selects virtual or platform threads. |
| `useDetailedErrorLogging(boolean)`   | Enables detailed failure logs.       |
| `onTaskFailure(Consumer<Throwable>)` | Registers a failure callback.        |

Both capacity settings must be greater than zero in the current
implementation.

Configuration cannot be changed after `build()`. Each instance can only
be built once, including after it has been closed.

### Platform threads

Platform threads are used by default. MoiraLoom creates a fixed pool of
daemon threads sized by `maxConcurrentJobs`.

### Virtual threads

Call `useVirtualThreads(true)` before building to enable virtual threads.

The concurrency limit still applies. Enabling virtual threads does not
allow more than `maxConcurrentJobs` job bodies to run at once.

## Queue capacity

Total admission capacity is:

```text
maxConcurrentJobs + maxPendingJobs
```

For example, a concurrency limit of 2 and pending capacity of 10 allow
up to 12 accepted workers, with at most two job bodies running at once.

When capacity is full, `submit()` immediately throws
`RejectedExecutionException`. Your application decides whether to retry
later, skip the work, or report that it is busy.

## Reusable jobs

Implement `MoiraLoomJob` when a job belongs in its own class. Its `run()`
method contains the work, as shown by `ReportJob` in the example.

You can also submit lambdas directly. Lambdas that return a value use
the `Callable<T>` overload and produce a `Future<T>`.

If you submit the same job object more than once, make sure its shared
state is safe to access from multiple threads.

## Handling failures

Use `onTaskFailure()` to register a callback during configuration.

The callback runs on the worker thread before the future completes.
Keep it short, since the worker waits for it to return.

Calling `Future.get()` on a failed job throws `ExecutionException`.
Use `getCause()` to access the underlying failure.

If the failure callback throws, its exception can replace the original
failure reported by the future.

An `InterruptedException` during execution is handled separately.
MoiraLoom restores the worker's interrupt status without invoking the
failure callback or incrementing the failed-job counter.

Enable `useDetailedErrorLogging(true)` to include exception details and
stack traces in the logs.

## Statistics

Call `getStats()` for a snapshot, or read individual counters using:

- `getCurrentRunningJobs()`
- `getTotalCompletedJobs()`
- `getTotalFailedJobs()`

Statistics can change while jobs are running. Snapshot fields are read
independently and may reflect slightly different moments.

Waiting on one future only waits for that particular job.

## Cancellation

Call `cancel(true)` on the returned future to request cancellation and
interrupt its worker if it has started.

Job code must cooperate with interruption for it to stop.

Calling `cancel(false)` does not interrupt the worker. If the worker has
already started, its admission capacity remains reserved until it exits.
This includes virtual workers waiting for an execution permit.

Calling `get()` on a canceled future throws `CancellationException`.

## Shutdown

Call `close()` when you have finished submitting work, or use
try-with-resources as shown in the example.

Closing stops new submissions and normally waits for accepted work to
finish.

If the thread waiting in `close()` is interrupted, MoiraLoom requests
worker interruption and cancels tasks drained from the executor queue.
It continues waiting for termination, then restores the caller's
interrupt status.

There is no shutdown timeout. A job that never finishes can keep
`close()` waiting.

Call `close()` from the code managing the instance's lifecycle.
Calling it from one of its own jobs can cause it to wait for itself.

## Development

Run the tests with:

```bash
mvn test
```

MoiraLoom is under active development. Bug reports, suggestions, and
examples that reproduce unexpected behavior are welcome through
GitHub issues.