package moiraLoom.failureTests;

import moiraloom.moiraloom.MoiraLoom;
import moiraloom.moiraloom.MoiraLoomJob;
import org.junit.Test;

import java.util.concurrent.RejectedExecutionException;

import static org.junit.Assert.assertThrows;

public class PendingJobFailureTest {

    @Test
    public void testMaxPendingJobs() {
        MoiraLoom moiraLoom = new MoiraLoom("testMaxPendingJobs")
                .maxConcurrentJobs(1)
                .maxPendingJobs(2)
                .onTaskFailure(error -> {
                    System.out.println(error.getMessage());
                })
                .build();

        MoiraLoomJob job = new MoiraLoomJob() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        moiraLoom.submit(job);

        moiraLoom.submit(job);

        moiraLoom.submit(job);

        assertThrows(RejectedExecutionException.class, () -> moiraLoom.submit(job));

    }
}
