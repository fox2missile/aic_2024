package kyuu.tasks;

import aic2024.user.AstronautInfo;
import aic2024.user.CarePackage;
import aic2024.user.Direction;
import aic2024.user.GameConstants;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.fast.FastIntIntMap;
import kyuu.message.AlertMessage;

public class DefenseAssginerTask extends Task {

    FastIntIntMap attackerDefenderMap;
    FastIntIntMap enemyStrengthMap;

    AlertMessage pendingAlert;

    public DefenseAssginerTask(C c) {
        super(c);
        attackerDefenderMap = new FastIntIntMap();
        enemyStrengthMap = new FastIntIntMap();
        pendingAlert = null;
    }

    @Override
    public void run() {
        assignDefenders();
        if (pendingAlert != null) {
            assignDefenders(pendingAlert);
        }

        cleanup();
    }

    private void assignDefenders() {
        int availableSlots = 0;
        for (Direction dir: c.allDirs) {
            if (uc.canEnlistAstronaut(dir, 11, null)) {
                availableSlots++;
            }
        }
        for (AstronautInfo a: uc.senseAstronauts(c.visionRange, c.opponent)) {
            if (attackerDefenderMap.contains(a.getID())) {
                if (c.s.isAllyVisible(attackerDefenderMap.getVal(a.getID()))) {
                    continue;
                }
            }
            if (!c.s.isReachableDirectly(a.getLocation()) && a.getCarePackage() != CarePackage.REINFORCED_SUIT) {
                continue;
            }

            if (a.getCarePackage() != CarePackage.REINFORCED_SUIT && a.getOxygen() < Vector2D.chebysevDistance(c.loc, a.getLocation())) {
                continue;
            }

            if (uc.getStructureInfo().getOxygen() < 11) {
                c.logger.log("Danger: Base is threatened but not enough oxygen!");
                return;
            }
            int enemyStrength = 1;
            if (enemyStrengthMap.contains(a.getID())) {
                enemyStrength = enemyStrengthMap.getVal(a.getID());
            } else {
                int enemyOxygen = (int)Math.ceil(a.getOxygen());
                if (a.getCarePackage() == CarePackage.REINFORCED_SUIT) {
                    enemyStrength = c.bitCount(enemyOxygen);
                }
            }


            boolean hasReinforcedSuit = uc.getStructureInfo().getCarePackagesOfType(CarePackage.REINFORCED_SUIT) > 0;

            for (Direction dir: c.getFirstDirs(c.loc.directionTo(a.getLocation()))) {
                if (availableSlots <= 1 && enemyStrength > 1 && hasReinforcedSuit) {
                    int givenOxygen = (1 << (enemyStrength - 1)) + 10;
                    givenOxygen = Math.max(givenOxygen, 10);
                    ldb.minReserveOxygen += givenOxygen;
                    if (uc.canEnlistAstronaut(dir, givenOxygen, CarePackage.REINFORCED_SUIT)) {
                        c.enlistAstronaut(dir, givenOxygen, CarePackage.REINFORCED_SUIT);
                        int enlistId = uc.senseAstronaut(c.loc.add(dir)).getID();
                        rdb.sendDefenseCommand(enlistId, a.getLocation());
                        if (!attackerDefenderMap.contains(a.getID())) {
                            attackerDefenderMap.add(a.getID(),enlistId);
                        }
                        uc.drawLineDebug(c.loc.add(dir), a.getLocation(), 0, 0, 255);

                        enemyStrength = 0;

                        break;
                    }
                }
                if (uc.canEnlistAstronaut(dir, 10, null)) {
                    c.enlistAstronaut(dir, 10, null);
                    int enlistId = uc.senseAstronaut(c.loc.add(dir)).getID();
                    rdb.sendDefenseCommand(enlistId, a.getLocation());
                    if (!attackerDefenderMap.contains(a.getID())) {
                        attackerDefenderMap.add(a.getID(), enlistId);
                    }
                    ldb.pushAssignedThisRound(enlistId);
                    uc.drawLineDebug(c.loc.add(dir), a.getLocation(), 0, 0, 255);
                    enemyStrength--;
                }
                if (enemyStrength <= 0) {
                    break;
                }
            }
            if (enemyStrength > 0) {
                enemyStrengthMap.add(a.getID(), enemyStrength);
                ldb.minReserveEnlistSlot += enemyStrength;
                ldb.minReserveOxygen += (enemyStrength * 10);
                if (uc.getStructureInfo().getCarePackagesOfType(CarePackage.REINFORCED_SUIT) > 0) {
                    ldb.minReserveOxygen += 10;
                }
            }
        }
    }

    public void assignDefenders(AlertMessage alert) {
        c.logger.log("trying to assign defenders from alert message..");
        int dist = Vector2D.chebysevDistance(c.loc, alert.target);
        if (dist > 35) {
            return;
        }
        // todo: currently settlements are not involved in early defense, incorporate them somehow
        //       could be some kind of interception logic
        int nearestBaseIdx = Vector2D.getNearest(alert.target, rdb.baseLocs, Math.min(rdb.baseCount, dc.MAX_HQ));
        if (nearestBaseIdx != rdb.baseIdx) {
            if (dist < 5) {
                alert.enemyStrength /= 2;
            } else {
                return;
            }
        }

        int oxygenNeeded = Math.max(10, ((Vector2D.chebysevDistance(c.loc, alert.target) * 4) / 5));
        for (Direction dir: c.getFirstDirs(c.loc.directionTo(alert.target))) {
            if (uc.canEnlistAstronaut(dir, oxygenNeeded, null)) {
                c.enlistAstronaut(dir, oxygenNeeded, null);
                int enlistId = uc.senseAstronaut(c.loc.add(dir)).getID();
                rdb.sendDefenseCommand(enlistId, alert.target);
                ldb.pushAssignedThisRound(enlistId);
                uc.drawLineDebug(c.loc.add(dir), alert.target, 0, 0, 255);
                alert.enemyStrength--;
            }
            if (alert.enemyStrength <= 0) {
                break;
            }
        }
        if (alert.enemyStrength > 0) {
            pendingAlert = alert;
            ldb.minReserveEnlistSlot += alert.enemyStrength;
            ldb.minReserveOxygen += (oxygenNeeded * alert.enemyStrength);
        } else {
            pendingAlert = null;
        }
    }

    private void cleanup() {
        for (int enemyId: attackerDefenderMap.getKeys()) {
            if (!c.s.isEnemyVisible(enemyId)) {
                attackerDefenderMap.remove(enemyId);
            }
        }
    }
}
