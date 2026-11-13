package moiraiLoom.futureReturnTests;

import moirailoom.utils.MoiraiLoom;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class FutureReturnTest {

    @Test
    public void testReturn() {
        MoiraiLoom moiraiLoom = new MoiraiLoom("testReturn")
                .maxConcurrentJobs(2)
                .maxPendingJobs(10)
                .useVirtualThreads(false)
                .useDetailedErrorLogging(true)
                .onTaskFailure(error -> {
                    System.out.println(error.getMessage());
                }).build();

        Future<Integer> result = moiraiLoom.submit(() -> 10 + 10);

        try {
            assert result.get() == 20;
        } catch (InterruptedException | ExecutionException e) {
            System.out.println(e.getMessage());
        }
        moiraiLoom.close();
    }
}
