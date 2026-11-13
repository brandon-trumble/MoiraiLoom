package moiraiLoom.failureTests;

import moirailoom.utils.MoiraiLoom;
import moirailoom.utils.MoiraiLoomJob;
import org.junit.Test;

import java.util.concurrent.RejectedExecutionException;

import static org.junit.Assert.assertThrows;

public class PendingJobFailureTest {

    @Test
    public void testMaxPendingJobs() {
        MoiraiLoom moiraiLoom = new MoiraiLoom("testMaxPendingJobs")
                .maxConcurrentJobs(1)
                .maxPendingJobs(2)
                .onTaskFailure(error -> {
                    System.out.println(error.getMessage());
                })
                .build();

        MoiraiLoomJob job = new MoiraiLoomJob() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        moiraiLoom.submit(job);

        moiraiLoom.submit(job);

        moiraiLoom.submit(job);

        assertThrows(RejectedExecutionException.class, () -> moiraiLoom.submit(job));

    }
}
