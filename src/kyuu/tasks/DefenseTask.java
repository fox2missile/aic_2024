package kyuu.tasks;

import aic2024.user.ActionType;
import aic2024.user.AstronautInfo;
import aic2024.user.Location;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.message.DefenseCommand;

public class DefenseTask extends Task {

    DefenseCommand cmd;
    boolean finished;


    public DefenseTask(C c, DefenseCommand cmd) {
        super(c);
        this.cmd = cmd;
        finished = false;
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
            int nearestIdx = Vector2D.getNearest(c.loc, enemies, enemies.length);
            c.destination = enemies[nearestIdx].getLocation();
            while (c.loc.distanceSquared(c.destination) <= c.actionRange && uc.canPerformAction(ActionType.SABOTAGE, c.loc.directionTo(c.destination), 1)) {
                uc.performAction(ActionType.SABOTAGE, c.loc.directionTo(c.destination), 1);
            }
        } else {
            c.destination = cmd.target;
        }

        if (Vector2D.chebysevDistance(c.loc, c.destination) > c.remainingSteps()) {
            finished = true;
            c.destination = null;
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
