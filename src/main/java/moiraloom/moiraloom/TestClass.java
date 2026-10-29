package taskforge.taskforge;

public class TestClass implements TaskForgeJob {

    @Override
    public void run() {
        System.out.println("Test class job.");
    }

}
