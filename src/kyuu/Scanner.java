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
        this.obstacles = new MapObjectLocation[100];
        for (int i = 0; i < 100; i++) this.obstacles[i] = new MapObjectLocation();
        unreachableLocs = new FastLocSet();
        visibleAllies = new FastIntIntMap();
        visibleAlliesValid = false;
        visibleEnemies = new FastIntIntMap();
        visibleEnemiesValid = false;
        prevLoc = c.loc;
    }

    public void initAvailableEnlistSlot() {
        c.ldb.availableEnlistSlot = getRealtimeAvailableEnlistSlot();
        defaultAvailableEnlistSlot = c.ldb.availableEnlistSlot;
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
        if (!prevLoc.equals(c.loc)) {
            unreachableLocs.clear();
        }
        obstaclesLength = 0;
        for (MapObject type: c.obstacleObjectTypes) {
            Location[] locs = uc.senseObjects(type, c.visionRange);
            for (Location loc: locs) {
                // out of range check ignored
                obstacles[obstaclesLength].loc = loc;
                obstacles[obstaclesLength].type = type;
                obstaclesLength++;
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
        MapObject obj = c.uc.senseObjectAtLocation(loc);
        return c.isObstacle(obj);
    }

    public boolean isReachableDirectly(Location target) {
        if (unreachableLocs.contains(target)) {
            return false;
        }
        Location currentLoc = uc.getLocation();
        for (int i = c.visionRangeStep; i != 0 && !currentLoc.equals(target); i--) {
            Direction dir = currentLoc.directionTo(target);
            Location next = currentLoc.add(dir);
            if (uc.canSenseLocation(next) && !c.isObstacle(uc.senseObjectAtLocation(next))) {
                currentLoc = next;
                continue;
            }
            next = currentLoc.add(dir.rotateLeft());
            if (uc.canSenseLocation(next) && !c.isObstacle(uc.senseObjectAtLocation(next))) {
                currentLoc = next;
                continue;
            }
            next = currentLoc.add(dir.rotateRight());
            if (uc.canSenseLocation(next) && !c.isObstacle(uc.senseObjectAtLocation(next))) {
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
}
