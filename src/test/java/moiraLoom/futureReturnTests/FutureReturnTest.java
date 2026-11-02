package moiraLoom.futureReturnTests;

import moiraloom.moiraloom.MoiraLoom;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class FutureReturnTest {

    @Test
    public void testReturn() {
        MoiraLoom moiraLoom = new MoiraLoom("testReturn")
                .maxConcurrentJobs(2)
                .maxPendingJobs(10)
                .useVirtualThreads(false)
                .useDetailedErrorLogging(true)
                .onTaskFailure(error -> {
                    System.out.println(error.getMessage());
                }).build();

        Future<Integer> result = moiraLoom.submit(() -> 10 + 10);

        try {
            assert result.get() == 20;
        } catch (InterruptedException | ExecutionException e) {
            System.out.println(e.getMessage());
        }
        moiraLoom.close();
    }
}
