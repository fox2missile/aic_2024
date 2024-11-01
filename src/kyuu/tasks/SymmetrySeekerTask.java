package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.fast.FastLocIntMap;
import kyuu.message.SeekSymmetryCommand;
import kyuu.message.SeekSymmetryComplete;

public class SymmetrySeekerTask extends Task {

    private final int WATER = 1;
    private final int PLAIN_LAND = 2;
    private final int HOT_ZONE = 3;

    FastLocIntMap knownLocations;

    SeekSymmetryCommand cmd;
    private boolean isFinished;

    boolean horizontalSymmetryPossible;
    boolean verticalSymmetryPossible;
    boolean rotationalSymmetryPossible;
    int prevRound;

    Location[] currentWaters;
    Location[] currentLands;
    Location[] currentHotZones;

    int dH;
    int dV;
    int dR;

    public SymmetrySeekerTask(C c, SeekSymmetryCommand cmd) {
        super(c);
        this.cmd = cmd;
        this.isFinished = false;
        this.knownLocations = new FastLocIntMap();
        this.horizontalSymmetryPossible = true;
        this.verticalSymmetryPossible = true;
        this.rotationalSymmetryPossible = true;
        this.prevRound = -1;
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    @Override
    public void run() {
        dH = 0;
        dV = 0;
        dR = 0;
        if (rdb.enemyHqSize > 0) {
            for (int i = 0; i < rdb.enemyHqSize; i++) {
                if (rdb.enemyHq[i].equals(cmd.target)) {
                    isFinished = true;
                    return;
                }
            }
        }
        if (!uc.canSenseLocation(cmd.target)) {
            if (uc.getAstronautInfo().getOxygen() < 2) {
                SeekSymmetryComplete msg = new SeekSymmetryComplete(cmd.target, dc.SYMMETRIC_SEEKER_COMPLETE_FAILED, horizontalSymmetryPossible, verticalSymmetryPossible, rotationalSymmetryPossible);
                rdb.sendSeekSymmetryCompleteMsg(msg);
                c.destination = null;
                cmd = null;
            } else {
                c.destination = cmd.target;
            }
        } else {
            StructureInfo s = uc.senseStructure(cmd.target);
            int status = s != null && s.getType() == StructureType.HQ && s.getTeam() != c.team ? dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ : dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_NOTHING;
            SeekSymmetryComplete msg = new SeekSymmetryComplete(cmd.target, status, horizontalSymmetryPossible, verticalSymmetryPossible, rotationalSymmetryPossible);
            rdb.sendSeekSymmetryCompleteMsg(msg);
            c.destination = null;
            cmd = null;
        }

        if (uc.getRound() != prevRound) {
            recordSymmetry();
            boolean symmetryFound = false;
            if (horizontalSymmetryPossible && !verticalSymmetryPossible && !rotationalSymmetryPossible) {
                c.logger.log("symmetry found earlier! -- horizontal");
                symmetryFound = true;
            } else if (!horizontalSymmetryPossible && verticalSymmetryPossible && !rotationalSymmetryPossible) {
                c.logger.log("symmetry found earlier! -- vertical");
                symmetryFound = true;
            } else if (!horizontalSymmetryPossible && !verticalSymmetryPossible && rotationalSymmetryPossible) {
                c.logger.log("symmetry found earlier! -- rotational");
                symmetryFound = true;
            }
            if (symmetryFound) {
                SeekSymmetryComplete msg = new SeekSymmetryComplete(cmd.target, dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_SYMMETRY, horizontalSymmetryPossible, verticalSymmetryPossible, rotationalSymmetryPossible);
                rdb.sendSeekSymmetryCompleteMsg(msg); // 6499; 21,26; 137
            }
        }
        prevRound = uc.getRound();
    }

    private void recordSymmetry() {

        int mod5 = uc.getRound() % 5;
        if (mod5 % 5 == (c.spawnRound % 5) || currentWaters == null || currentLands == null || currentHotZones == null) {
            currentWaters = uc.senseObjects(MapObject.WATER, c.visionRange);
            currentLands = uc.senseObjects(MapObject.LAND, c.visionRange);
            currentHotZones = uc.senseObjects(MapObject.HOT_ZONE, c.visionRange);
        }

        // water
        int scanWaterBegin = (currentWaters.length / 5) * mod5;
        int scanWaterEnd = (currentWaters.length / 5) * (mod5 + 1);
        if (mod5 == 4) {
            scanWaterEnd = currentWaters.length;
        }
        for (int i = scanWaterBegin; i < scanWaterEnd; i++) {
            knownLocations.add(currentWaters[i], WATER);
        }

        // land
        int scanLandBegin = (currentLands.length / 5) * mod5;
        int scanLandEnd = (currentLands.length / 5) * (mod5 + 1);
        if (mod5 == 4) {
            scanLandEnd = currentLands.length;
        }
        for (int i = scanLandBegin; i < scanLandEnd; i++) {
            knownLocations.add(currentLands[i], PLAIN_LAND);
        }

        // hotZones
        int scanHotZoneBegin = (currentHotZones.length / 5) * mod5;
        int scanHotZoneEnd = (currentHotZones.length / 5) * (mod5 + 1);
        if (mod5 == 4) {
            scanHotZoneEnd = currentHotZones.length;
        }
        for (int i = scanHotZoneBegin; i < scanHotZoneEnd; i++) {
            knownLocations.add(currentHotZones[i], HOT_ZONE);
        }

        // symmetry check
        for (int i = scanWaterBegin; i < scanWaterEnd; i++) {
            symmetryCheck(currentWaters[i], WATER);
        }
        for (int i = scanLandBegin; i < scanLandEnd; i++) {
            symmetryCheck(currentLands[i], PLAIN_LAND);
        }
        for (int i = scanHotZoneBegin; i < scanHotZoneEnd; i++) {
            symmetryCheck(currentHotZones[i], HOT_ZONE);
        }

    }

    private void symmetryCheck(Location loc, int code) {
        if (horizontalSymmetryPossible) {
            Location reflected = c.mirrorHorizontal(loc);
            if (dH > 0) {
                dH--;
                uc.drawLineDebug(loc, reflected, 255, 0, 0);
            }
            if (knownLocations.contains(reflected)) {
                if (code != knownLocations.getVal(reflected)) {
                    horizontalSymmetryPossible = false;
                }
            }
        }
        if (verticalSymmetryPossible) {
            Location reflected = c.mirrorVertical(loc);
            if (dV > 0) {
                dV--;
                uc.drawLineDebug(loc, reflected, 0, 255, 0);
            }
            if (knownLocations.contains(reflected)) {
                if (code != knownLocations.getVal(reflected)) {
                    verticalSymmetryPossible = false;
                }
            }
        }
        if (rotationalSymmetryPossible) {
            Location reflected = c.mirrorRotational(loc);
            if (dR > 0) {
                dR--;
                uc.drawLineDebug(loc, reflected, 0, 0, 255);
            }
            if (knownLocations.contains(reflected)) {
                if (code != knownLocations.getVal(reflected)) {
                    rotationalSymmetryPossible = false;
                }
            }
        }
    }
}
