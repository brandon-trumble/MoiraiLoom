package moiraloom;

import moiraloom.moiraloom.MoiraLoom;
import moiraloom.moiraloom.TestClass;

import java.util.concurrent.Future;

public class Main {
    static void main() {

       MoiraLoom moiraLoom = new MoiraLoom("mainClassThreads")
                .maxConcurrentJobs(2)
                .maxPendingJobs(10)
               .useDetailedErrorLogging(true)
                .build();

       TestClass testClass = new TestClass();


       try {
           Future<Integer> job = moiraLoom.submit(() -> 1 + 1);

           int total = job.get();

           System.out.println(total);
       } catch (Exception ignore) {

       }


       moiraLoom.close();
    }
}
