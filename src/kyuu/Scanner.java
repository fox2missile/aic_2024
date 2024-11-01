package kyuu;

import aic2024.user.*;
import kyuu.fast.FastIntIntMap;
import kyuu.fast.FastLocSet;
import kyuu.message.AlertMessage;


public class Scanner {

    C c;
    UnitController uc;
    FastLocSet unreachableLocs;

    // Parallel array
    public class MapObjectLocation {
        public Location loc;
        public MapObject type;
    }
    public MapObjectLocation[] obstacles;
    public int obstaclesLength;

    public AstronautInfo[] alliesTooClose;
    Location prevLoc;
    FastIntIntMap visibleAllies;
    private boolean visibleAlliesValid;
    FastIntIntMap visibleEnemies;
    private boolean visibleEnemiesValid;
    public int immediateBlockers;

    int totalSavedOxygen;
    public int defaultAvailableEnlistSlot;
    public boolean allyHqNearby;
    public Location nearestEnemyStructure;

    public Scanner(C c) {
        this.c = c;
        this.uc = c.uc;
        this.obstaclesLength = 0;
        this.obstacles = new MapObjectLocation[200];
        for (int i = 0; i < 100; i++) this.obstacles[i] = new MapObjectLocation();
        unreachableLocs = new FastLocSet();
        visibleAllies = new FastIntIntMap();
        visibleAlliesValid = false;
        visibleEnemies = new FastIntIntMap();
        visibleEnemiesValid = false;
        prevLoc = c.loc;
        defaultAvailableEnlistSlot = getRealtimeAvailableEnlistSlot();
    }

    public void initAvailableEnlistSlot() {
        c.ldb.availableEnlistSlot = getRealtimeAvailableEnlistSlot();
    }

    public int getRealtimeAvailableEnlistSlot() {
        int slot = 0;
        for (Direction dir: c.allDirs) {
            if (uc.canEnlistAstronaut(dir, 10, null)) {
                slot++;
            }
        }
        return slot;
    }

    public void trySendAlert() {
        if (!uc.isStructure()) {
            if (uc.senseStructures(c.visionRange, c.team).length > 0) {
                return;
            }
        }
        AstronautInfo[] enemies = uc.senseAstronauts(c.visionRange, c.opponent);
        if (enemies.length == 0) {
            return;
        }
        int totalStrength = 0;
        int sumX = 0;
        int sumY = 0;
        for (AstronautInfo enemy: enemies) {
            int strength = 1;
            if (enemy.getCarePackage() == CarePackage.REINFORCED_SUIT) {
                strength = c.bitCount((int)(Math.ceil(enemy.getOxygen())));
            } else if (enemy.getOxygen() < 30) {
                continue;
            }
            totalStrength += strength;
            sumX += enemy.getLocation().x * strength;
            sumY += enemy.getLocation().y * strength;
        }
        if (totalStrength <= 3) {
            return;
        }
        AlertMessage msg = new AlertMessage(new Location(sumX / totalStrength, sumY / totalStrength), totalStrength);
        c.rdb.trySendAlert(msg);
    }



    public void scan() {
        visibleAllies.clear();
        visibleAlliesValid = false;
        visibleEnemies.clear();
        visibleEnemiesValid = false;
        if (!prevLoc.equals(c.loc) || uc.getRound() == c.spawnRound) {
            unreachableLocs.clear();
            obstaclesLength = 0;
            for (MapObject type: c.obstacleObjectTypes) {
                Location[] locs = uc.senseObjects(type, c.visionRange);
                if (locs.length > 100) {
                    locs = uc.senseObjects(type, c.visionRangeReduced1);
                    if (locs.length > 100) {
                        locs = uc.senseObjects(type, c.visionRangeReduced2);
                    }
                }
                for (Location loc: locs) {
                    if (obstaclesLength >= obstacles.length) {
                        break;
                    }
                    obstacles[obstaclesLength].loc = loc;
                    obstacles[obstaclesLength].type = type;
                    obstaclesLength++;
                }
            }
        }

        alliesTooClose = uc.senseAstronauts(8, c.team);

        immediateBlockers = 0;
        if (!c.canMove(Direction.NORTH)) {
            immediateBlockers++;
        }
        if (!c.canMove(Direction.NORTHEAST)) {
            immediateBlockers++;
        }
        if (!c.canMove(Direction.EAST)) {
            immediateBlockers++;
        }
        if (!c.canMove(Direction.SOUTHEAST)) {
            immediateBlockers++;
        }
        if (!c.canMove(Direction.SOUTH)) {
            immediateBlockers++;
        }
        if (!c.canMove(Direction.SOUTHWEST)) {
            immediateBlockers++;
        }
        if (!c.canMove(Direction.WEST)) {
            immediateBlockers++;
        }
        if (!c.canMove(Direction.NORTHWEST)) {
            immediateBlockers++;
        }
        prevLoc = c.loc;

        allyHqNearby = false;
        for (StructureInfo s: uc.senseStructures(c.visionRange, c.team)) {
            if (s.getType() == StructureType.HQ) {
                allyHqNearby = true;
            }
        }

        nearestEnemyStructure = null;
        int nearestDist = Integer.MAX_VALUE;
        for (StructureInfo s: uc.senseStructures(c.visionRange, c.opponent)) {
            int dist = c.loc.distanceSquared(s.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestEnemyStructure = s.getLocation();
            }
        }
    }

    public boolean isAllyVisible(int id) {
        if (!visibleAlliesValid) {
            for (AstronautInfo a: uc.senseAstronauts(c.visionRange, c.team)) {
                visibleAllies.add(a.getID(), 1);
            }
        }
        return visibleAllies.contains(id);
    }

    public boolean isEnemyVisible(int id) {
        if (!visibleEnemiesValid) {
            for (AstronautInfo a: uc.senseAstronauts(c.visionRange, c.opponent)) {
                visibleEnemies.add(a.getID(), 1);
            }
        }
        return visibleEnemies.contains(id);
    }

    public boolean hasObstacle(Location loc) {
        return c.isObstacle(uc.senseTileType(loc));
    }

    public boolean isReachableDirectly(Location target) {
        if (unreachableLocs.contains(target)) {
            return false;
        }
        Location currentLoc = uc.getLocation();
        for (int i = c.visionRangeStep; i != 0 && !currentLoc.equals(target); i--) {
            Direction dir = currentLoc.directionTo(target);
            Location next = currentLoc.add(dir);
            if (uc.canSenseLocation(next) && !c.isObstacle(uc.senseTileType(next))) {
                currentLoc = next;
                continue;
            }
            next = currentLoc.add(dir.rotateLeft());
            if (uc.canSenseLocation(next) && !c.isObstacle(uc.senseTileType(next))) {
                currentLoc = next;
                continue;
            }
            next = currentLoc.add(dir.rotateRight());
            if (uc.canSenseLocation(next) && !c.isObstacle(uc.senseTileType(next))) {
                currentLoc = next;
                continue;
            }
            break;
        }
        if (currentLoc.equals(target)) {
            return true;
        }
        unreachableLocs.add(target);
        return false;
    }

    public int getEnemyAstronautValue(AstronautInfo enemy) {
        int value = (int) Math.ceil(enemy.getOxygen());
        CarePackage pax = enemy.getCarePackage();
        if (pax == CarePackage.REINFORCED_SUIT) {
            value = (value * 2 * value);
        } else if (pax == CarePackage.SURVIVAL_KIT) {
            value *= 2;
        } else if (pax == CarePackage.SETTLEMENT) {
            value += 9999999;
        }

        // attack anyone nearest
        if (allyHqNearby) {
            // todo: fix hyperjump bug
            value += 1000000 / c.loc.distanceSquared(enemy.getLocation());
        }

        // if can move but the enemy is still under construction, skip if we can slip through
        if (uc.getAstronautInfo().getCurrentMovementCooldown() >= 1.0 && enemy.isBeingConstructed() && nearestEnemyStructure != null) {
            value = 1;
            // todo: refactor move this code
            for (Direction dir: c.allDirs) {
                if (!c.canMove(dir)) {
                    continue;
                }
                Location check = c.loc.add(dir);
                if (check.distanceSquared(nearestEnemyStructure) <= c.actionRange) {
                    c.move(dir);
                    Direction structureDir = check.directionTo(nearestEnemyStructure);
                    while (uc.canPerformAction(ActionType.SABOTAGE, structureDir, 1)) {
                        uc.performAction(ActionType.SABOTAGE, structureDir, 1);
                    }
                }
            }
        }

        return value;
    }

    public void attackEnemyBaseDirectly(Location target) {
        while (uc.canPerformAction(ActionType.SABOTAGE, c.loc.directionTo(target), 1)) {
            uc.performAction(ActionType.SABOTAGE, c.loc.directionTo(target), 1);
        }
    }

    public void attackEnemyBaseStepNeeded(Location target) {
        if (uc.getAstronautInfo().getCurrentMovementCooldown() < 1.0) {
            // attempt kill this round

            int[] directionScore = new int[c.allDirs.length];

            for (int i = 0; i < c.allDirs.length; i++) {
                Direction dir = c.allDirs[i];
                Location check = c.loc.add(dir);
                if (check.distanceSquared(target) > c.actionRange) {
                    continue;
                }
                if (c.canMove(dir)) {
                    directionScore[i] = 1000;
                    break;
                }

                AstronautInfo a = uc.senseAstronaut(check);
                if (a == null || a.getTeam() == c.team) {
                    // water tile or ally
                    continue;
                }

                StructureInfo s = uc.senseStructure(check);
                if (s != null && s.getTeam() == c.opponent) {
                    while (uc.canPerformAction(ActionType.SABOTAGE, dir, 1)) {
                        uc.performAction(ActionType.SABOTAGE, dir, 1);
                    }
                    s = uc.senseStructure(check);
                    if (s == null) {
                        directionScore[i] = 1000;
                        break;
                    } else {
                        directionScore[i] = 0;
                        continue;
                    }
                }

                // there is an enemy astronaut here
                directionScore[i] = 500;

                if (a.getCarePackage() == CarePackage.REINFORCED_SUIT) {
                    if (uc.getAstronautInfo().getCarePackage() != CarePackage.REINFORCED_SUIT) {
                        directionScore[i] = 0;
                        continue;
                    }
                    if (c.bitCount((int)a.getOxygen()) >= c.bitCount((int)uc.getAstronautInfo().getOxygen())) {
                        // both reinforced but they are stronger
                        directionScore[i] = 0;
                        continue;
                    }
                    directionScore[i] -= (int)a.getOxygen();
                }
            }

            int bestDirIdx = 0;
            for (int i = 1; i < c.allDirs.length; i++) {
                if (directionScore[i] > directionScore[bestDirIdx]) {
                    bestDirIdx = i;
                }
            }
            if (directionScore[bestDirIdx] > 0) {
                Direction bestDir = c.allDirs[bestDirIdx];
                // best dir is either empty or there is an astronaut
                while (uc.canPerformAction(ActionType.SABOTAGE, bestDir, 1)) {
                    uc.performAction(ActionType.SABOTAGE, bestDir, 1);
                }
                if (c.canMove(bestDir)) {
                    c.move(bestDir);
                    Direction targetDir = c.loc.directionTo(target);
                    while (uc.canPerformAction(ActionType.SABOTAGE, targetDir, 1)) {
                        uc.performAction(ActionType.SABOTAGE, targetDir, 1);
                    }
                }
            }
        }
    }
}
