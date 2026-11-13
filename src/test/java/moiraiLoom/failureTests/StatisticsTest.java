package moiraiLoom.failureTests;

import moirailoom.utils.MoiraiLoom;
import moirailoom.utils.domain.MoiraiLoomStats;
import org.junit.Test;

public class StatisticsTest {

    @Test
    public void testStatistics() {
        MoiraiLoom moiraiLoom = new MoiraiLoom("statisticsTest")
                .maxConcurrentJobs(2)
                .maxPendingJobs(2)
                .build();

        try {
            // block thread to get accurate stats
            moiraiLoom.submit(() -> {
                System.out.println("stat test");
            }).get();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }

        MoiraiLoomStats moiraiLoomStats = moiraiLoom.getStats();

        String expectedStats = "MoiraiLoomStats[moiraiLoomName=statisticsTest, currentRunningJobs=0, maxConcurrentJobs=2, isVirtualThread=false, totalCompletedJobs=1, totalFailedJobs=0, totalJobsPending=0, totalJobsCanceled=0]";

        assert(moiraiLoomStats.toString().equals(expectedStats));

        // non blocked one

        MoiraiLoom moiraiLoom1 = new MoiraiLoom("nonBlockedStatisticsTest")
                .maxConcurrentJobs(2)
                .maxPendingJobs(2)
                .build();

        moiraiLoom1.submit(() -> {
           System.out.println("stat test 2");
        });

        MoiraiLoomStats moiraiLoomStats1 = moiraiLoom1.getStats();

        System.out.println(moiraiLoomStats1);

        String expectedNonBlockStats = "MoiraiLoomStats[moiraiLoomName=nonBlockedStatisticsTest, currentRunningJobs=0, maxConcurrentJobs=2, isVirtualThread=false, totalCompletedJobs=0, totalFailedJobs=0, totalJobsPending=1, totalJobsCanceled=0]";

        assert(moiraiLoomStats1.toString().equals(expectedNonBlockStats));

        moiraiLoom.close();
    }
}
