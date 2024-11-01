package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;

public class ScanSectorTask extends Task {

    Location targetSector = null;
    Location targetSectorCenter = null;
    Location targetSectorOrigin = null;
    Location targetSectorAntiOrigin = null;
    boolean seenOrigin = false;
    boolean seenCenter = false;
    boolean seenAntiOrigin = false;

    int targetCountdown;

    final int TIME_OUT = 30;

    Location loc;

    Location prevLoc;

    Direction[] directions;

    public ScanSectorTask(C c) {
        super(c);
        directions = new Direction[Direction.values().length];
        directions[0] = Direction.ZERO;
        directions[1] = c.directionsNorthCwZero[c.seed % directions.length];
        for (int i = 2; i < directions.length; i++) {
            directions[i] = c.seed % 2 == 0 ? directions[i - 1].rotateLeft() : directions[i - 1].rotateRight();
        }
    }

    @Override
    public void run() {
        c.s.trySendAlert();
        loc = c.loc;
        if (loc.equals(prevLoc) && targetSector != null) {
            targetCountdown -= 6;
        } else {
            targetCountdown--;
        }
        checkSector();
        if (targetCountdown <= 0 && targetSector != null) {
            c.logger.log("Timeout trying to get to sector %s", targetSector.toString());
            ldb.updateSectorUnreachable(targetSector);
            targetSector = null;
            targetSectorCenter = null;
            c.destination = null;
        }
        scanSector();

        if (targetSector == null) {
            Location trySector = ldb.popSectorExplorationStack();
            if (trySector != null) {
                setTargetSector(trySector);
            }
        }

        if (targetSector != null) {
            int distCenter = !seenCenter ? loc.distanceSquared(targetSectorCenter) : Integer.MAX_VALUE;
            int distOrigin = !seenOrigin ? loc.distanceSquared(targetSectorOrigin) : Integer.MAX_VALUE;
            int distAntiOrigin = !seenAntiOrigin ? loc.distanceSquared(targetSectorAntiOrigin) : Integer.MAX_VALUE;

            if (distCenter <= distOrigin && distCenter <= distAntiOrigin) {
                c.destination = targetSectorCenter;
                c.logger.log("checking center");
            } else if (distOrigin <= distCenter && distOrigin <= distAntiOrigin) {
                c.destination = targetSectorOrigin;
                c.logger.log("checking origin");
            } else {
                c.destination = targetSectorAntiOrigin;
                c.logger.log("checking anti-origin");
            }

        }
        prevLoc = loc;
    }

    private void checkSector() {
        if (targetSector == null) {
            return;
        }
        if (Vector2D.chebysevDistance(loc, targetSectorCenter) <= 2 || uc.isOutOfMap(targetSectorCenter)) {
            seenCenter = true;
        }
        if (Vector2D.chebysevDistance(loc, targetSectorOrigin) <= 2 || uc.isOutOfMap(targetSectorOrigin)) {
            seenOrigin = true;
        }
        if (Vector2D.chebysevDistance(loc, targetSectorAntiOrigin) <= 2 || uc.isOutOfMap(targetSectorAntiOrigin)) {
            seenAntiOrigin = true;
        }
        if (seenCenter && seenOrigin && seenAntiOrigin) {
            ldb.updateSectorExplored(targetSector);
            targetSector = null;
            targetSectorCenter = null;
            c.destination = null;
        }
    }

    private void setTargetSector(Location sector) {
        targetSector = sector;
        targetSectorCenter = c.getSectorCenter(sector);
        targetSectorOrigin = c.getSectorOrigin(sector);
        targetSectorAntiOrigin = c.getSectorAntiOrigin(sector);
        seenCenter = false;
        seenOrigin = false;
        seenAntiOrigin = false;
        // todo: update remote db status exploring
        targetCountdown = TIME_OUT;
        // todo: update remote db exploration direction
    }

    private void scanSector() {
        for (Direction dir: c.allDirs) {
            Location sector = c.getSector(new Location(loc.x + (dir.dx * dc.SECTOR_SQUARE_SIZE), loc.y + (dir.dy * dc.SECTOR_SQUARE_SIZE)));
            // todo: check sector status before exploring
            int status = ldb.getSectorStatus(sector);
            if (targetSector == null && status < dc.STATUS_SECTOR_EXPLORING) {
                if (uc.isOutOfMap(c.getSectorCenter(sector))) {
                    c.logger.log("sector is out of map: %s, mark as explored", sector.toString());
                    ldb.updateSectorExplored(sector);
                } else {
                    setTargetSector(sector);
                }
            } else if (status == dc.STATUS_SECTOR_UNKNOWN) {
                ldb.updateSectorDiscovered(sector);
                uc.drawLineDebug(loc, c.getSectorCenter(sector), 0, 0, 200);
                ldb.pushSectorExplorationStack(sector);
            }
            // todo: exploration stack
        }
    }

}
