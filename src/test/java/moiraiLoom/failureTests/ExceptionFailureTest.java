package moiraiLoom.failureTests;

import moirailoom.moirailoom.MoiraiLoom;
import moirailoom.moirailoom.MoiraiLoomJob;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ExceptionFailureTest {

    /**
     * The MoiraiLoom .submit throws a ExecutionException if the job throws an exception
     * during it's execution
     */
    private static class TestException implements MoiraiLoomJob {
        @Override
        public void run() {
            throw new RuntimeException("test");
        }
    }

    @Test
    public void testFailures() {
        AtomicReference<Throwable> capturedFailure = new AtomicReference<>();

        try (MoiraiLoom moiraiLoom = new MoiraiLoom("exceptionTest")
                .maxPendingJobs(3)
                .maxConcurrentJobs(2)
                .onTaskFailure(capturedFailure::set)
                .build()) {

            TestException testException = new TestException();

            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> moiraiLoom.submit(testException).get(5, TimeUnit.SECONDS)
            );

            assertEquals("test", exception.getCause().getMessage());
            assertSame(exception.getCause(), capturedFailure.get());
            assertEquals(1, moiraiLoom.getTotalFailedJobs());
        }
    }
}
