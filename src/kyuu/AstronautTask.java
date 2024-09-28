package kyuu;

import kyuu.pathfinder.NaivePathFinder;
import kyuu.tasks.RetrievePackageTask;
import kyuu.tasks.ScanSectorTask;
import kyuu.tasks.Task;

public class AstronautTask extends Task {

//    Task moveTask;

    NaivePathFinder pathFinder;

    Task scanSectorTask;
    Task retrievePaxTask;
    public AstronautTask(C c) {
        super(c);
        pathFinder = new NaivePathFinder(c);
        scanSectorTask = new ScanSectorTask(c);
        retrievePaxTask = new RetrievePackageTask(c);
    }

    @Override
    public void run() {
        c.s.scan();
        retrievePaxTask.run();
        if (c.destination == null) {
            scanSectorTask.run();
        }
        pathFinder.initTurn();
        if (c.destination != null) {
            pathFinder.move(c.destination);
            c.uc.drawLineDebug(c.uc.getLocation(), c.destination, 0, 255, 0);
            c.destination = null;
        }
    }
}
