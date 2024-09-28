package kyuu;


import kyuu.tasks.EnlistTask;
import kyuu.tasks.Task;

public class HeadquarterTask extends Task {


    HeadquarterTask(C c) {
        super(c);
    }

    @Override
    public void run() {

        EnlistTask.run(c);
    }

    private void debugCarePackages() {

    }
}
