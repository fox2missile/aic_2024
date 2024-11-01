package kyuu.tasks;

import aic2024.user.ActionType;
import aic2024.user.AstronautInfo;
import aic2024.user.CarePackage;
import aic2024.user.Location;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.message.DefenseCommand;

public class DefenseTask extends Task {

    DefenseCommand cmd;
    boolean finished;
    Task nearbyPaxRetrievalTask;

    public DefenseTask(C c, DefenseCommand cmd) {
        super(c);
        this.cmd = cmd;
        finished = false;
        nearbyPaxRetrievalTask = RetrievePackageTask.createNearbyPackageTask(c);
    }

    @Override
    public void run() {
        if (rdb.enemyHqSize > 0) {
            int nearestEnemyHqIdx = Vector2D.getNearest(c.loc, rdb.enemyHq, rdb.enemyHqSize);
            Location nearestEnemyHq = rdb.enemyHq[nearestEnemyHqIdx];
            while (c.loc.distanceSquared(nearestEnemyHq) <= c.actionRange && uc.canPerformAction(ActionType.SABOTAGE, c.loc.directionTo(nearestEnemyHq), 1)) {
                uc.performAction(ActionType.SABOTAGE, c.loc.directionTo(nearestEnemyHq), 1);
            }
        }

        // attack anyone nearby
        AstronautInfo[] enemies = uc.senseAstronauts(c.visionRange, c.team.getOpponent());
        if (enemies.length > 0) {

            int attackIdx = -1;
            int highestValue = 0;
            for (int i = 0; i < enemies.length; i++) {
                if (Vector2D.chebysevDistance(c.loc, enemies[i].getLocation()) > c.remainingSteps()) {
                    continue;
                }
                int value = c.s.getEnemyAstronautValue(enemies[i]);
                // defenders always attack highest value
                if (value > highestValue) {
                    attackIdx = i;
                    highestValue = value;
                }
            }
            if (attackIdx != -1) {
                c.destination = enemies[attackIdx].getLocation();
                while (c.loc.distanceSquared(c.destination) <= c.actionRange && uc.canPerformAction(ActionType.SABOTAGE, c.loc.directionTo(c.destination), 1)) {
                    uc.performAction(ActionType.SABOTAGE, c.loc.directionTo(c.destination), 1);
                }
            } else {
                c.destination = cmd.target;
            }

        } else {
            c.destination = cmd.target;
        }

        if (Vector2D.chebysevDistance(c.loc, c.destination) > c.remainingSteps()) {
            finished = true;
            c.destination = null;
            nearbyPaxRetrievalTask.run();
            if (c.destination == null) {
                c.destination = cmd.target;
            }
        }

        if (Vector2D.manhattanDistance(c.loc, cmd.target) <= 3 && enemies.length == 0) {
            finished = true;
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }
}
