package moiraloom;

import moiraloom.moiraloom.MoiraLoom;
import moiraloom.moiraloom.TestClass;

import java.util.concurrent.Future;

public class Main {
    public static void main(String[] args) {

       MoiraLoom moiraLoom = new MoiraLoom("mainClassThreads")
                .maxConcurrentJobs(2)
                .maxPendingJobs(10)
                .useDetailedErrorLogging(true)
                .build();

       TestClass testClass = new TestClass();


       try {
           moiraLoom.submit(() -> {
               System.out.println("stat test");
           }).get();

           System.out.println(moiraLoom.getStats());
       } catch (Exception ignore) {

       }


       moiraLoom.close();
    }
}
