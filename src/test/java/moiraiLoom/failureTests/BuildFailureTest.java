package moiraiLoom.failureTests;

import moirailoom.moirailoom.MoiraiLoom;
import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class BuildFailureTest {

    @Test
    public void testBuildFailure() {

        assertThrows(IllegalStateException.class, () -> {
            MoiraiLoom moiraiLoom = new MoiraiLoom("moiraiLoomMaxConcurrentNotSet")
                    .maxPendingJobs(2)
                    .build();

            moiraiLoom.close();
        });

        assertThrows(IllegalStateException.class, () -> {
           MoiraiLoom moiraiLoom = new MoiraiLoom("moiraiLoomMaxPendingNotSet")
                   .maxConcurrentJobs(2)
                   .build();

           moiraiLoom.close();
        });

        assertThrows(IllegalStateException.class, () -> {
           MoiraiLoom moiraiLoom = new MoiraiLoom("submitBeforeBuild");

           moiraiLoom.submit(() -> {
               System.out.println("THIS SHOULD NOT RUN!!! CHECK BuildTestFailureTest.java");
           });

           moiraiLoom.close();
        });
    }
}
