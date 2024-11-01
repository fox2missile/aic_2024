package kyuu.tasks;

import aic2024.user.AstronautInfo;
import aic2024.user.CarePackage;
import aic2024.user.Direction;
import kyuu.C;
import kyuu.fast.FastIntIntMap;

public class DefenseAssginerTask extends Task {

    FastIntIntMap attackerDefenderMap;

    public DefenseAssginerTask(C c) {
        super(c);
        attackerDefenderMap = new FastIntIntMap();
    }

    @Override
    public void run() {
        assignDefenders();
        cleanup();
    }

    private void assignDefenders() {
        for (AstronautInfo a: uc.senseAstronauts(c.visionRange, c.opponent)) {
            if (attackerDefenderMap.contains(a.getID())) {
                if (c.s.isAllyVisible(attackerDefenderMap.getVal(a.getID()))) {
                    continue;
                }
            }
            if (!c.s.isReachableDirectly(a.getLocation())) {
                continue;
            }
            if (uc.getStructureInfo().getOxygen() < 11) {
                c.logger.log("Danger: HQ is threatened but not enough oxygen!");
                return;
            }
            int enemyStrength = 1;
            int enemyOxygen = (int)Math.ceil(a.getOxygen());
            if (a.getCarePackage() == CarePackage.REINFORCED_SUIT) {
                enemyStrength = c.bitCount(enemyOxygen);
            }
            for (Direction dir: c.getFirstDirs(c.loc.directionTo(a.getLocation()))) {
                if (enemyStrength > 1 && uc.getStructureInfo().getCarePackagesOfType(CarePackage.REINFORCED_SUIT) > 0) {
                    // todo: optimize 2^N
                    int givenOxygen = Math.min(enemyOxygen + 10, (int)Math.floor(uc.getStructureInfo().getOxygen()));
                    if (uc.canEnlistAstronaut(dir, givenOxygen, CarePackage.REINFORCED_SUIT)) {
                        uc.enlistAstronaut(dir, givenOxygen, null);
                        int enlistId = uc.senseAstronaut(c.loc.add(dir)).getID();
                        rdb.sendDefenseCommand(enlistId, a.getLocation());
                        if (!attackerDefenderMap.contains(a.getID())) {
                            attackerDefenderMap.add(a.getID(),enlistId);
                        }
                        uc.drawLineDebug(c.loc.add(dir), a.getLocation(), 0, 0, 255);
                        if (givenOxygen < enemyOxygen + 10) {
                            // no more oxygen!
                            return;
                        }
                    }
                } else {
                    if (uc.canEnlistAstronaut(dir, 11, null)) {
                        uc.enlistAstronaut(dir, 11, null);
                        int enlistId = uc.senseAstronaut(c.loc.add(dir)).getID();
                        rdb.sendDefenseCommand(enlistId, a.getLocation());
                        if (!attackerDefenderMap.contains(a.getID())) {
                            attackerDefenderMap.add(a.getID(),enlistId);
                        }
                        uc.drawLineDebug(c.loc.add(dir), a.getLocation(), 0, 0, 255);
                        enemyStrength--;
                    }
                }
                if (enemyStrength <= 0) {
                    break;
                }

            }
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
