package kyuu;


import aic2024.user.*;
import kyuu.fast.FastLocSet;
import kyuu.message.*;
import kyuu.pathfinder.ParallelSearch;
import kyuu.tasks.*;

public class HeadquarterTask extends Task {


    private final int SYMMETRY_HORIZONTAL = 0;
    private final int SYMMETRY_VERTICAL = 1;
    private final int SYMMETRY_ROTATIONAL = 2;


    Location[] symmetryCandidates;
    boolean[] symmetryAssigned;
    boolean[] symmetryComplete;

    int[] symmetrySeekAttempts;
    int[] symmetrySeekTimeout;

    final int SYMMETRY_SEEK_COOLDOWN;


    boolean enemyHqBroadcasted;

    ParallelSearch packageSearch;

    Task packageAssignerTask;
    DefenseAssginerTask defenseAssignerTask;
    Task expansionTask;
    Task enlistAttackersTask;


    HeadquarterTask(C c) {
        super(c);
        rdb.subscribeSeekSymmetryComplete = true;
        rdb.subscribeEnemyHq = true;
        rdb.subscribeSeekSymmetryCommand = true;
        rdb.subscribeSurveyComplete = true;
        rdb.subscribeExpansionEstablished = true;
        rdb.subscribeDomeBuilt = true;
        rdb.subscribeDomeDestroyed = true;
        rdb.initExpansionData();
        packageSearch = ParallelSearch.getDefaultSearch(c);
        packageAssignerTask = new PackageAssignerTask(c);
        defenseAssignerTask = new DefenseAssginerTask(c);
        enlistAttackersTask = new EnlistAttackersTask(c);

        SYMMETRY_SEEK_COOLDOWN = Math.max(uc.getMapWidth() + 5, uc.getMapHeight() + 5);
    }


    @Override
    public void run() {
        c.s.scan();
        enemyHqBroadcasted = false;
        ldb.minReserveOxygen = 0;
        ldb.resetAssignedThisRound();


        if (uc.getRound() == 0) {
            rdb.sendHqInfo();
        } else if (uc.getRound() == 1) {
            rdb.initHqLocs();
            rdb.sendHqInfo();
            defenseAssignerTask.run();
            expansionTask = new ExpansionTask(c);
        } else if (uc.getRound() == 2) {
            initSymmetryCandidates();
            defenseAssignerTask.run();
        } else  {
            defenseAssignerTask.run();
            Message msg = rdb.retrieveNextMessage();
            while (msg != null) {
                if (msg instanceof SeekSymmetryComplete) {
                    SeekSymmetryComplete seekMsg = (SeekSymmetryComplete) msg;
                    handleSeekSymmetryComplete(seekMsg.target, seekMsg.status);
                } else if (msg instanceof EnemyHqMessage) {
                    enemyHqBroadcasted = true;
                } else if (msg instanceof SeekSymmetryCommand) {
                    Location target = ((SeekSymmetryCommand)msg).target;
                    for (int i = 0; i < symmetryCandidates.length; i++) {
                        if (target.equals(symmetryCandidates[i])) {
                            symmetryAssigned[i] = true;
                        }
                    }
                } else if (msg instanceof AlertMessage) {
                    defenseAssignerTask.assignDefenders((AlertMessage) msg);
                }
                msg = rdb.retrieveNextMessage();
            }
            if (rdb.enemyHqSize == 0) {
                while (enlistSymmetrySeeker()) {}
            }

            broadcastEnemyHq();
            enlistAttackersTask.run();
            expansionTask.run();
            packageAssignerTask.run();



        }
    }

    private boolean enlistSymmetrySeeker() {
        int nearestSym = -1;
        int distNearest = Integer.MAX_VALUE;
        for (int i = 0; i < symmetryCandidates.length; i++) {
            if (symmetryComplete[i]) {
                continue;
            }
            if (symmetryAssigned[i]) {
                symmetrySeekTimeout[i]--;
                if (symmetrySeekTimeout[i] > 0) {
                    continue;
                } else {
                    symmetryAssigned[i] = false;
                    c.logger.log("symmetry seeker timeout: %s", symmetryCandidates[i]);
                }
            }
            Location sym = symmetryCandidates[i];
            int dist = c.loc.distanceSquared(sym);
            if (dist < distNearest) {
                distNearest = dist;
                nearestSym = i;
            }
        }

        if (nearestSym == -1) {
            return false;
        }

        uc.drawLineDebug(c.loc, symmetryCandidates[nearestSym], 255, 0, 0);

        return enlistSymmetrySeeker(nearestSym);
    }

    private boolean enlistSymmetrySeeker(int symIdx) {
        Location sym = symmetryCandidates[symIdx];
        CarePackage pax = null;
        if (symmetrySeekAttempts[symIdx] > 2) {
            pax = CarePackage.REINFORCED_SUIT;
        }
        int givenOxygen = 50 + (10 * symmetrySeekAttempts[symIdx]);
        for (Direction d: c.getFirstDirs(c.loc.directionTo(sym))) {
            if (uc.canEnlistAstronaut(d, givenOxygen, pax)) {
                uc.enlistAstronaut(d, givenOxygen, pax);
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

    private void initSymmetryCandidates() {
        symmetryCandidates = new Location[rdb.hqCount * 3];
        symmetryAssigned = new boolean[rdb.hqCount * 3];
        symmetryComplete = new boolean[rdb.hqCount * 3];
        symmetrySeekAttempts = new int[rdb.hqCount * 3];
        symmetrySeekTimeout = new int[rdb.hqCount * 3];
        FastLocSet symSet = new FastLocSet();
        // todo: handle allies HQ in symmetry
        for (int i = 0; i < rdb.hqCount; i++) {
            // horizontal
            symmetryCandidates[i * 3] = new Location(rdb.hqLocs[i].x, c.mapHeight - rdb.hqLocs[i].y - 1);
            uc.drawLineDebug(c.loc, symmetryCandidates[i * 3], 0, 0, 255);
            if (!symSet.contains(symmetryCandidates[i * 3])) {
                symSet.add(symmetryCandidates[i * 3]);
                symmetryAssigned[i * 3] = false;
            } else {
                symmetryAssigned[i * 3] = true;
            }
            // vertical
            symmetryCandidates[(i * 3) + 1] = new Location(c.mapWidth - rdb.hqLocs[i].x - 1, rdb.hqLocs[i].y);
            uc.drawLineDebug(c.loc, symmetryCandidates[(i * 3) + 1], 0, 0, 255);
            if (!symSet.contains(symmetryCandidates[(i * 3) + 1])) {
                symSet.add(symmetryCandidates[(i * 3) + 1]);
                symmetryAssigned[(i * 3) + 1] = false;
            } else {
                symmetryAssigned[(i * 3) + 1] = true;
            }
            // rotational
            symmetryCandidates[(i * 3) + 2] = new Location(c.mapWidth - rdb.hqLocs[i].x - 1, c.mapHeight - rdb.hqLocs[i].y - 1);
            uc.drawLineDebug(c.loc, symmetryCandidates[(i * 3) + 2], 0, 0, 255);
            if (!symSet.contains(symmetryCandidates[(i * 3) + 2])) {
                symSet.add(symmetryCandidates[(i * 3) + 2]);
                symmetryAssigned[(i * 3) + 2] = false;
            } else {
                symmetryAssigned[(i * 3) + 2] = true;
            }
        }

        for (int i = 0; i < symmetryCandidates.length; i++) {
            if (uc.canSenseLocation(symmetryCandidates[i])) {
                StructureInfo s = uc.senseStructure(symmetryCandidates[i]);
                if (s != null && s.getType() == StructureType.HQ && s.getTeam() != c.team) {
                    handleSeekSymmetryComplete(i, dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ);
                    rdb.sendSeekSymmetryCompleteMsg(new SeekSymmetryComplete(symmetryCandidates[i], dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ));
                } else {
                    handleSeekSymmetryComplete(i, dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_NOTHING);
                    rdb.sendSeekSymmetryCompleteMsg(new SeekSymmetryComplete(symmetryCandidates[i], dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_NOTHING));
                }
            }
        }

        // let the closest hq handle the seek
        for (int i = 0; i < symmetryCandidates.length; i++) {
            if (symmetryAssigned[i]) {
                continue;
            }
            int nearestHq = Vector2D.getNearest(symmetryCandidates[i], rdb.hqLocs, rdb.hqCount);
            if (!rdb.hqLocs[nearestHq].equals(c.loc)) {
                symmetryAssigned[i] = true;
            }
        }
    }

    private void handleSeekSymmetryComplete(Location loc, int status) {
        int symIdx;
        for (symIdx = 0; symIdx < symmetryCandidates.length; symIdx++) {
            if (symmetryCandidates[symIdx].equals(loc)) {
                break;
            }
        }
        if (symIdx < symmetryCandidates.length) {
            handleSeekSymmetryComplete(symIdx, status);
        } else {
            c.logger.log("error");
        }
    }


    private void handleSeekSymmetryComplete(int symIdx, int status) {
        Location target = symmetryCandidates[symIdx];
        if (status == dc.SYMMETRIC_SEEKER_COMPLETE_FAILED) {
            symmetryAssigned[symIdx] = false;
            c.logger.log("symmetric seeker died :(");
        } else if (status == dc.SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ) {
            symmetryAssigned[symIdx] = true;
            c.logger.log("FOUND ENEMY HQ: %s", target);
            symmetryComplete[symIdx] = true;

            rdb.addEnemyHq(symmetryCandidates[symIdx]);
        } else {
            for (int i = 0; i < symmetryCandidates.length; i++) {
                if (i % 3 == symIdx % 3) {
                    symmetryAssigned[i] = true;
                    symmetryComplete[symIdx] = true;
                }
            }
            if (symIdx % 3 == SYMMETRY_HORIZONTAL) {
                ldb.horizontalSymmetryPossible = false;
            } else if (symIdx % 3 == SYMMETRY_VERTICAL) {
                ldb.verticalSymmetryPossible = false;
            } else {
                ldb.rotationalSymmetryPossible = false;
            }
            if (ldb.horizontalSymmetryPossible && !ldb.verticalSymmetryPossible && !ldb.rotationalSymmetryPossible) {
                handleSymmetryFound(SYMMETRY_HORIZONTAL);
                return;
            } else if (!ldb.horizontalSymmetryPossible && ldb.verticalSymmetryPossible && !ldb.rotationalSymmetryPossible) {
                handleSymmetryFound(SYMMETRY_VERTICAL);
                return;
            } else if (!ldb.horizontalSymmetryPossible && !ldb.verticalSymmetryPossible && ldb.rotationalSymmetryPossible) {
                handleSymmetryFound(SYMMETRY_ROTATIONAL);
                return;
            }
            c.logger.log("current symmetry: horizontalSymmetryPossible %s | verticalSymmetryPossible %s | rotationalSymmetryPossible %s",
                    ldb.horizontalSymmetryPossible, ldb.verticalSymmetryPossible, ldb.rotationalSymmetryPossible);
        }
    }

    private void handleSymmetryFound(int symmetryId) {
        c.logger.log("Symmetry found! horizontalSymmetryPossible %s | verticalSymmetryPossible %s | rotationalSymmetryPossible %s",
                ldb.horizontalSymmetryPossible, ldb.verticalSymmetryPossible, ldb.rotationalSymmetryPossible);

        for (int i = 0; i < symmetryCandidates.length; i++) {
            if (i % 3 != symmetryId) {
                continue;
            }
            rdb.addEnemyHq(symmetryCandidates[i]);
        }
    }

    private void broadcastEnemyHq() {
        if (enemyHqBroadcasted || rdb.enemyHqSize == 0) {
            return;
        }

        for (int i = 0; i < rdb.enemyHqSize; i++) {
            uc.drawPointDebug(rdb.enemyHq[i], 255, 0, 0);
            rdb.sendEnemyHqLocMessage(rdb.enemyHq[i]);
        }
    }
}
