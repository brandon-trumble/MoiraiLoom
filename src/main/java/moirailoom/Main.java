package moirailoom;

import moirailoom.moirailoom.MoiraiLoom;
import moirailoom.moirailoom.MoiraiLoomJob;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Main {

    public static void main(String[] args) {

        // Configure and build MoiraiLoom
        MoiraiLoom moiraiLoom = new MoiraiLoom("mainClassThreads")
                .maxConcurrentJobs(2)
                .maxPendingJobs(10)
                .useVirtualThreads(true)
                .useDetailedErrorLogging(true)
                .onTaskFailure(error -> {
                    System.err.println("Job failed: " + error.getMessage());
                })
                .build();

        // submit a test job
        moiraiLoom.submit(() -> {
            System.out.println("Test job");
        });


        // test class job
        TestJob testJob = new TestJob();


        // submit a preset class(MoiraiLoomJob implementation)
        moiraiLoom.submit(testJob);

        moiraiLoom.close();

    }

    private static class TestJob implements MoiraiLoomJob {

        @Override
        public void run() {
            // do something
        }
    }
}
