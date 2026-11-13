package moirailoom.utils;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AdmissionCapacityTest {

    @Test(timeout = 20000)
    public void returnsCapacityBeforePublishingAResult() throws Exception {
        assertReleasePrecedesCompletion(false, null);
        assertReleasePrecedesCompletion(true, null);
    }

    @Test(timeout = 20000)
    public void returnsCapacityBeforePublishingAFailure() throws Exception {
        var failure = new IllegalStateException("test job failed");
        assertReleasePrecedesCompletion(false, failure);
        assertReleasePrecedesCompletion(true, failure);
    }

    private void assertReleasePrecedesCompletion(boolean useVirtualThreads, RuntimeException failure)
            throws Exception {
        var releaseStarted = new CountDownLatch(1);
        var allowRelease = new CountDownLatch(1);

        try (var loom = new MoiraiLoom("admissionCapacityTest")
                .maxConcurrentJobs(1)
                .useVirtualThreads(useVirtualThreads)
                .build()) {
            loom.maxPendingJobsSemaphore = new Semaphore(1, true) {
                @Override
                public void release() {
                    releaseStarted.countDown();
                    try {
                        allowRelease.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    super.release();
                }
            };

            Future<Integer> future;
            try {
                future = loom.submit(() -> {
                    if (failure != null) {
                        throw failure;
                    }
                    return 42;
                });

                assertTrue("Worker did not reach permit release",
                        releaseStarted.await(5, TimeUnit.SECONDS));
                assertFalse("Future completed before its admission permit was returned", future.isDone());
            } finally {
                allowRelease.countDown();
            }

            if (failure == null) {
                assertEquals(Integer.valueOf(42), future.get(5, TimeUnit.SECONDS));
            } else {
                var error = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
                assertSame(failure, error.getCause());
            }

            assertEquals(Integer.valueOf(7), loom.submit(() -> 7).get(5, TimeUnit.SECONDS));
            assertEquals(1, loom.maxPendingJobsSemaphore.availablePermits());
        }
    }
}
