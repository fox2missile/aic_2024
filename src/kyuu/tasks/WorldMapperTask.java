package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.message.WorldMapperCommand;

public class WorldMapperTask extends Task {

    WorldMapperCommand cmd;
    Location[] currentObstacles;
    Location startLoc;

    public WorldMapperTask(C c, WorldMapperCommand cmd) {
        super(c);
        this.cmd = cmd;
        this.startLoc = c.loc;
    }

    @Override
    public void run() {
        Location alertLoc = c.s.trySendAlert();

        if (alertLoc == null) {
            c.destination = cmd.target;
        } else {
            if (!c.canMove()) {
                c.destination = startLoc;
            } else {
                // find dir farthest from both start loc and alertLoc
                int bestScore = 0;
                Direction returnDir = c.loc.directionTo(startLoc);
                Direction alertDir = c.loc.directionTo(alertLoc);
                Direction bestDir = returnDir;
                for (Direction dir: c.allDirs) {
                    int score = getEvasionScore(dir, returnDir, alertDir);
                    if (score > bestScore) {
                        bestScore = score;
                        bestDir = dir;
                    }
                }
                // todo: maybe there is hyper jump luckily
                if (c.canMove(bestDir)) {
                    c.move(bestDir);
                }
            }

        }

        int mod5 = uc.getRound() % 5;
        if (mod5 % 5 == (c.spawnRound % 5) || currentObstacles == null) {
            currentObstacles = uc.senseObjects(MapObject.WATER, c.visionRange);
        }

        // water
        int scanObsBegin = (currentObstacles.length / 5) * mod5;
        int scanObsEnd = (currentObstacles.length / 5) * (mod5 + 1);
        if (mod5 == 4) {
            scanObsEnd = currentObstacles.length;
        }
        int scanObsLength = scanObsEnd - scanObsBegin;
        if (scanObsLength >= 1) {
            rdb.sendWorldObstacles(currentObstacles, scanObsBegin, scanObsLength);
        }

    }

    private int getEvasionScore(Direction dir, Direction returnDir, Direction alertDir) {
        if (c.canMove(dir)) {
            return 0;
        }
        int score = 0;
        if (dir == returnDir || dir == alertDir) {
            score -= 3;
        }
        if (dir == returnDir.rotateLeft() || dir == returnDir.rotateRight() || dir == alertDir.rotateLeft() || dir == alertDir.rotateRight()) {
            score -= 1;
        }
        if (dir == returnDir.rotateLeft().rotateLeft() || dir == returnDir.rotateRight().rotateRight() || dir == alertDir.rotateLeft().rotateLeft() || dir == alertDir.rotateRight().rotateRight()) {
            score += 1;
        }
        if (dir == returnDir.opposite().rotateLeft() || dir == returnDir.opposite().rotateRight() || dir == alertDir.opposite().rotateLeft() || dir == alertDir.opposite().rotateRight()) {
            score += 2;
        }
        if (dir == returnDir.opposite() || dir == alertDir.opposite()) {
            score += 3;
        }
        return score;
    }
}
