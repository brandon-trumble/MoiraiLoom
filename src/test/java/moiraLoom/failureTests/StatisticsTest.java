package moiraLoom.failureTests;

import moiraloom.moiraloom.MoiraLoom;
import moiraloom.moiraloom.domain.MoiraLoomStats;
import org.junit.Test;

public class StatisticsTest {

    @Test
    public void testStatistics() {
        MoiraLoom moiraLoom = new MoiraLoom("statisticsTest")
                .maxConcurrentJobs(2)
                .maxPendingJobs(2)
                .build();

        try {
            // block thread to get accurate stats
            moiraLoom.submit(() -> {
                System.out.println("stat test");
            }).get();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }

        MoiraLoomStats moiraLoomStats = moiraLoom.getStats();

        String expectedStats = "MoiraLoomStats[moiraLoomName=statisticsTest, currentRunningJobs=0, maxConcurrentJobs=2, isVirtualThread=false, totalCompletedJobs=1, totalFailedJobs=0]";

        assert(moiraLoomStats.toString().equals(expectedStats));

        // non blocked one

        MoiraLoom moiraLoom1 = new MoiraLoom("nonBlockedStatisticsTest")
                .maxConcurrentJobs(2)
                .maxPendingJobs(2)
                .build();

        moiraLoom1.submit(() -> {
           System.out.println("stat test 2");
        });

        MoiraLoomStats moiraLoomStats1 = moiraLoom1.getStats();

        String expectedNonBlockStats = "MoiraLoomStats[moiraLoomName=nonBlockedStatisticsTest, currentRunningJobs=0, maxConcurrentJobs=2, isVirtualThread=false, totalCompletedJobs=0, totalFailedJobs=0]";

        assert(moiraLoomStats1.toString().equals(expectedNonBlockStats));

        moiraLoom.close();
    }
}
