package moiraloom;

import moiraloom.moiraloom.MoiraLoom;
import moiraloom.moiraloom.TestClass;

import java.util.concurrent.Future;

public class Main {
    static void main() {

       MoiraLoom moiraLoom = new MoiraLoom("mainClassThreads")
                .maxConcurrentJobs(2)
                .onTaskFailure(error -> {
                    System.out.println(error.getMessage());
                })
                .build();

       TestClass testClass = new TestClass();

        Future<?> task = moiraLoom.submit(testClass);

       try {
          System.out.println(moiraLoom.getStats());
       } catch (Exception ignore) {

       }


       moiraLoom.close();
    }
}
