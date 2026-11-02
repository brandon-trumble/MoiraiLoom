package moiraLoom.failureTests;

import moiraloom.moiraloom.MoiraLoom;
import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class BuildFailureTest {

    @Test
    public void testBuildFailure() {

        assertThrows(IllegalStateException.class, () -> {
            MoiraLoom moiraLoom = new MoiraLoom("moiraLoomMaxConcurrentNotSet")
                    .maxPendingJobs(2)
                    .build();

            moiraLoom.close();
        });

        assertThrows(IllegalStateException.class, () -> {
           MoiraLoom moiraLoom = new MoiraLoom("moiraLoomMaxPendingNotSet")
                   .maxConcurrentJobs(2)
                   .build();

           moiraLoom.close();
        });

        assertThrows(IllegalStateException.class, () -> {
           MoiraLoom moiraLoom = new MoiraLoom("submitBeforeBuild");

           moiraLoom.submit(() -> {
               System.out.println("THIS SHOULD NOT RUN!!! CHECK BuildTestFailureTest.java");
           });

           moiraLoom.close();
        });
    }
}
