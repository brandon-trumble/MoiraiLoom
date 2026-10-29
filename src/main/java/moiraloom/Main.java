package taskforge;

import taskforge.taskforge.TaskForge;
import taskforge.taskforge.TestClass;

import java.util.concurrent.Future;

public class Main {
    static void main() {

       TaskForge taskForge = new TaskForge("mainClassThreads")
                .maxConcurrentJobs(2)
                .onTaskFailure(error -> {
                    System.out.println(error.getMessage());
                })
                .build();

       TestClass testClass = new TestClass();

        Future<?> task = taskForge.submit(testClass);

       try {
          System.out.println(taskForge.getStats());
       } catch (Exception ignore) {

       }


       taskForge.close();
    }
}
