package kyuu;

import aic2024.user.*;


public class Scanner {

    C c;
    UnitController uc;

    // Parallel array
    public class MapObjectLocation {
        public Location loc;
        public MapObject type;
    }
    public MapObjectLocation[] obstacles;
    public int obstaclesLength;

    public AstronautInfo[] alliesTooClose;

    public Scanner(C c) {
        this.c = c;
        this.uc = c.uc;
        this.obstaclesLength = 0;
        this.obstacles = new MapObjectLocation[100];
        for (int i = 0; i < 100; i++) this.obstacles[i] = new MapObjectLocation();
    }
    
    public int immediateBlockers;

    public void scan() {
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
    }

    public boolean hasObstacle(Location loc) {
        MapObject obj = c.uc.senseObjectAtLocation(loc);
        return c.isObstacle(obj);
    }

    boolean isReachableDirectly(Location target) {
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
        return currentLoc.equals(target);
    }
}
