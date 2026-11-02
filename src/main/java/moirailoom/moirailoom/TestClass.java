package moirailoom.moirailoom;

import java.util.Random;

public class TestClass implements MoiraiLoomJob {

    @Override
    public void run() {
        Random random = new Random();
        try {
            Thread.sleep(2000);
            System.out.println("Test");

        } catch (Exception e) {
            try {
                throw e;
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

}
