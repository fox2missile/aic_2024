package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;

public class EnlistTask {
    public static void run(C c) {
        for (Direction dir: Direction.values()) {
            if (c.uc.canEnlistAstronaut(dir, 25, null)) {
                c.uc.enlistAstronaut(dir, 25, null);
            }
        }
    }

}
