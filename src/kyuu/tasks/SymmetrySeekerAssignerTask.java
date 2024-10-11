package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.fast.FastLocSet;
import kyuu.message.SeekSymmetryComplete;

public class SymmetrySeekerAssignerTask extends Task {


    boolean[] symmetryAssigned;
    boolean[] symmetryComplete;

    int[] symmetrySeekAttempts;
    int[] symmetrySeekTimeout;

    final int SYMMETRY_SEEK_COOLDOWN;

    public SymmetrySeekerAssignerTask(C c) {
        super(c);
        SYMMETRY_SEEK_COOLDOWN = Math.max(uc.getMapWidth() + 5, uc.getMapHeight() + 5);
        rdb.seekSymmetryCompleteReceiver = (int msg) -> {
            Location loc = new Location((msg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                    (msg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT);
            int status = msg & dc.SYMMETRIC_SEEKER_COMPLETE_STATUS_MASKER;
            handleSeekSymmetryComplete(loc, status);
        };
        rdb.seekSymmetryCommandReceiver = (int __) -> {
            uc.pollBroadcast(); // target ID
            Location seek = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
            for (int i = 0; i < ldb.symmetryCandidates.length; i++) {
                if (seek.equals(ldb.symmetryCandidates[i])) {
                    symmetryAssigned[i] = true;
                }
            }
        };
    }

    @Override
    public void run() {
        while (enlistSymmetrySeeker()) {}
    }

    private boolean enlistSymmetrySeeker() {
        int nearestSym = -1;
        int distNearest = Integer.MAX_VALUE;
        if (ldb.enlistFullyReserved()) {
            return false;
        }
        for (int i = 0; i < ldb.symmetryCandidates.length; i++) {
            if (symmetryComplete[i]) {
                continue;
            }
            if (symmetryAssigned[i]) {
                symmetrySeekTimeout[i]--;
                if (symmetrySeekTimeout[i] > 0) {
                    continue;
                } else {
                    symmetryAssigned[i] = false;
                    c.logger.log("symmetry seeker timeout: %s", ldb.symmetryCandidates[i]);
                }
            }
            Location sym = ldb.symmetryCandidates[i];
            int dist = c.loc.distanceSquared(sym);
            if (dist < distNearest) {
                distNearest = dist;
                nearestSym = i;
            }
        }

        if (nearestSym == -1) {
            return false;
        }

        uc.drawLineDebug(c.loc, ldb.symmetryCandidates[nearestSym], 255, 0, 0);

        return enlistSymmetrySeeker(nearestSym);
    }

    private boolean enlistSymmetrySeeker(int symIdx) {
        Location sym = ldb.symmetryCandidates[symIdx];
        CarePackage pax = null;
        if (symmetrySeekAttempts[symIdx] > 2) {
            pax = CarePackage.REINFORCED_SUIT;
        }
        int givenOxygen = 50 + (10 * symmetrySeekAttempts[symIdx]);
        for (Direction d: c.getFirstDirs(c.loc.directionTo(sym))) {
            if (uc.canEnlistAstronaut(d, givenOxygen, pax)) {
                c.enlistAstronaut(d, givenOxygen, pax);
                AstronautInfo a = uc.senseAstronaut(c.loc.add(d));
                rdb.sendSymmetricSeekerCommand(a.getID(), sym);
                symmetryAssigned[symIdx] = true;
                symmetrySeekAttempts[symIdx]++;
                symmetrySeekTimeout[symIdx] = symmetrySeekAttempts[symIdx] * SYMMETRY_SEEK_COOLDOWN;
                return true;
            }
        }
        return false;
    }

    public void initSymmetryCandidates() {
        ldb.symmetryCandidates = new Location[rdb.baseCount * 3];
        symmetryAssigned = new boolean[rdb.baseCount * 3];
        symmetryComplete = new boolean[rdb.baseCount * 3];
        symmetrySeekAttempts = new int[rdb.baseCount * 3];
        symmetrySeekTimeout = new int[rdb.baseCount * 3];
        FastLocSet symSet = new FastLocSet();
        // todo: handle allies HQ in symmetry
        for (int i = 0; i < rdb.baseCount; i++) {
            // horizontal
            ldb.symmetryCandidates[i * 3] = c.mirrorHorizontal(rdb.baseLocs[i]);
            uc.drawLineDebug(c.loc, ldb.symmetryCandidates[i * 3], 0, 0, 255);
            if (!symSet.contains(ldb.symmetryCandidates[i * 3])) {
                symSet.add(ldb.symmetryCandidates[i * 3]);
                symmetryAssigned[i * 3] = false;
            } else {
                symmetryAssigned[i * 3] = true;
            }
            // vertical
            ldb.symmetryCandidates[(i * 3) + 1] = c.mirrorVertical(rdb.baseLocs[i]);
            uc.drawLineDebug(c.loc, ldb.symmetryCandidates[(i * 3) + 1], 0, 0, 255);
            if (!symSet.contains(ldb.symmetryCandidates[(i * 3) + 1])) {
                symSet.add(ldb.symmetryCandidates[(i * 3) + 1]);
                symmetryAssigned[(i * 3) + 1] = false;
            } else {
                symmetryAssigned[(i * 3) + 1] = true;
            }
            // rotational
            ldb.symmetryCandidates[(i * 3) + 2] = c.mirrorRotational(rdb.baseLocs[i]);
            uc.drawLineDebug(c.loc, ldb.symmetryCandidates[(i * 3) + 2], 0, 0, 255);
            if (!symSet.contains(ldb.symmetryCandidates[(i * 3) + 2])) {
                symSet.add(ldb.symmetryCandidates[(i * 3) + 2]);
                symmetryAssigned[(i * 3) + 2] = false;
            } else {
                symmetryAssigned[(i * 3) + 2] = true;
            }
        }

        for (int i = 0; i < ldb.symmetryCandidates.length; i++) {
            if (uc.canSenseLocation(ldb.symmetryCandidates[i])) {
                StructureInfo s = uc.senseStructure(ldb.symmetryCandidates[i]);
                if (s != null && s.getType() == StructureType.HQ && s.getTeam() != c.team) {
                    handleSeekSymmetryComplete(i, dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ);
                    rdb.sendSeekSymmetryCompleteMsg(new SeekSymmetryComplete(ldb.symmetryCandidates[i], dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ));
                } else {
                    handleSeekSymmetryComplete(i, dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_NOTHING);
                    rdb.sendSeekSymmetryCompleteMsg(new SeekSymmetryComplete(ldb.symmetryCandidates[i], dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_NOTHING));
                }
            }
        }

        // let the closest hq handle the seek
        for (int i = 0; i < ldb.symmetryCandidates.length; i++) {
            if (symmetryAssigned[i]) {
                continue;
            }
            int nearestHq = Vector2D.getNearest(ldb.symmetryCandidates[i], rdb.baseLocs, rdb.baseCount);
            if (!rdb.baseLocs[nearestHq].equals(c.loc)) {
                symmetryAssigned[i] = true;
            }
        }
    }

    private void handleSeekSymmetryComplete(Location loc, int status) {
        int symIdx;
        for (symIdx = 0; symIdx < ldb.symmetryCandidates.length; symIdx++) {
            if (ldb.symmetryCandidates[symIdx].equals(loc)) {
                break;
            }
        }
        if (symIdx < ldb.symmetryCandidates.length) {
            handleSeekSymmetryComplete(symIdx, status);
        } else {
            c.logger.log("error");
        }
    }
    private void handleSeekSymmetryComplete(int symIdx, int status) {
        Location target = ldb.symmetryCandidates[symIdx];
        if (status == dc.SYMMETRIC_SEEKER_COMPLETE_FAILED) {
            symmetryAssigned[symIdx] = false;
            c.logger.log("symmetric seeker died :(");
        } else if (status == dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ) {
            symmetryAssigned[symIdx] = true;
            c.logger.log("FOUND ENEMY HQ: %s", target);
            symmetryComplete[symIdx] = true;

            rdb.addEnemyHq(ldb.symmetryCandidates[symIdx]);
        } else {
            for (int i = 0; i < ldb.symmetryCandidates.length; i++) {
                if (i % 3 == symIdx % 3) {
                    symmetryAssigned[i] = true;
                    symmetryComplete[symIdx] = true;
                }
            }
            if (symIdx % 3 == dc.SYMMETRY_HORIZONTAL) {
                ldb.horizontalSymmetryPossible = false;
            } else if (symIdx % 3 == dc.SYMMETRY_VERTICAL) {
                ldb.verticalSymmetryPossible = false;
            } else {
                ldb.rotationalSymmetryPossible = false;
            }
            if (ldb.horizontalSymmetryPossible && !ldb.verticalSymmetryPossible && !ldb.rotationalSymmetryPossible) {
                handleSymmetryFound(dc.SYMMETRY_HORIZONTAL);
                return;
            } else if (!ldb.horizontalSymmetryPossible && ldb.verticalSymmetryPossible && !ldb.rotationalSymmetryPossible) {
                handleSymmetryFound(dc.SYMMETRY_VERTICAL);
                return;
            } else if (!ldb.horizontalSymmetryPossible && !ldb.verticalSymmetryPossible && ldb.rotationalSymmetryPossible) {
                handleSymmetryFound(dc.SYMMETRY_ROTATIONAL);
                return;
            }
            c.logger.log("current symmetry: horizontalSymmetryPossible %s | verticalSymmetryPossible %s | rotationalSymmetryPossible %s",
                    ldb.horizontalSymmetryPossible, ldb.verticalSymmetryPossible, ldb.rotationalSymmetryPossible);
        }
    }

    private void handleSymmetryFound(int symmetryId) {
        c.logger.log("Symmetry found! horizontalSymmetryPossible %s | verticalSymmetryPossible %s | rotationalSymmetryPossible %s",
                ldb.horizontalSymmetryPossible, ldb.verticalSymmetryPossible, ldb.rotationalSymmetryPossible);

        for (int i = 0; i < ldb.symmetryCandidates.length; i++) {
            if (i % 3 != symmetryId) {
                continue;
            }
            rdb.addEnemyHq(ldb.symmetryCandidates[i]);
        }
    }


}
