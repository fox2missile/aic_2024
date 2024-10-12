package kyuu;

import aic2024.user.*;
import kyuu.db.DbConst;
import kyuu.db.LocalDatabase;
import kyuu.db.RemoteDatabase;
import kyuu.log.Logger;
import kyuu.log.LoggerDummy;
import kyuu.log.LoggerStandard;

// C stands for Context
public class C {
    public final boolean DEBUG = false; // todo: better boom
    public UnitController uc;
    public Team team;
    public Team opponent;
    public Logger logger;
    public Location destination = null;
    public int seed;
    public final int spawnRound;
    public final float visionRange;
    public final float visionRangeReduced1;
    public final float visionRangeReduced2;
    public final int visionRangeStep;
    public final float actionRange;
    public Location loc;
    public final Location startLoc;
    public final int id;
    public Scanner s;
    public DbConst dc;
    public LocalDatabase ldb;
    public RemoteDatabase rdb;

    public final int mapWidth;
    public final int mapHeight;

    public final Direction[] directionsNorthCcw = {
            Direction.NORTH,
            Direction.NORTHWEST,
            Direction.WEST,
            Direction.SOUTHWEST,
            Direction.SOUTH,
            Direction.SOUTHEAST,
            Direction.EAST,
            Direction.NORTHEAST,
    };



    public final Direction[] directionsNorthCwZero = {
            Direction.ZERO,
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    public final Direction[] directionsNorthFirst = new Direction[8];
    public final Direction[] directionsNorthWestFirst = new Direction[8];
    public final Direction[] directionsWestFirst = new Direction[8];
    public final Direction[] directionsSouthWestFirst = new Direction[8];
    public final Direction[] directionsSouthFirst = new Direction[8];
    public final Direction[] directionsSouthEastFirst = new Direction[8];
    public final Direction[] directionsEastFirst = new Direction[8];
    public final Direction[] directionsNorthEastFirst = new Direction[8];

    public final Direction[][] allFirstDirs = new Direction[][]{
            directionsNorthFirst,
            directionsNorthWestFirst,
            directionsWestFirst,
            directionsSouthWestFirst,
            directionsSouthFirst,
            directionsSouthEastFirst,
            directionsEastFirst,
            directionsNorthEastFirst
    };

    public final Direction[] fourDirs;
    public final Direction[] fourDirsZero;

    public final Direction[] diagonalDirs;
    public final Direction[] diagonalDirsZero;

    public final Direction[] allDirs;
    public final Direction[] allDirsZero;

    final MapObject[] obstacleObjectTypes = {
            MapObject.WATER,
    };

    public int bitCount(int num) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(num);
    }


    public boolean canMove(Direction dir) {
        return uc.canPerformAction(ActionType.MOVE, dir, 1);
    }

    public boolean canMove() {
        return uc.getAstronautInfo().getCurrentMovementCooldown() < 1;
    }

    public void move(Direction dir) {
        uc.performAction(ActionType.MOVE, dir, 1);
        loc = uc.getLocation();
    }

    public void enlistAstronaut(Direction dir, int oxygen, CarePackage pax) {
        uc.enlistAstronaut(dir, oxygen, pax);
        ldb.availableEnlistSlot--;
    }

    public Location getSectorOrigin(Location sector) {
        return new Location(sector.x * dc.SECTOR_SQUARE_SIZE, sector.y * dc.SECTOR_SQUARE_SIZE);
    }

    public Location getSectorAntiOrigin(Location sector) {
        return new Location((sector.x * dc.SECTOR_SQUARE_SIZE) + dc.SECTOR_SQUARE_SIZE - 1,
                (sector.y * dc.SECTOR_SQUARE_SIZE) + dc.SECTOR_SQUARE_SIZE - 1);
    }

    public Location getSectorCenter(Location sector) {
        return new Location(sector.x * dc.SECTOR_SQUARE_SIZE + dc.SECTOR_HALF_SQUARE_SIZE, sector.y * dc.SECTOR_SQUARE_SIZE + dc.SECTOR_HALF_SQUARE_SIZE);
    }

    public Location getCurrentSector() {
        return getSector(uc.getLocation());
    }

    public Location getSector(Location loc) {
        return new Location(loc.x / dc.SECTOR_SQUARE_SIZE, loc.y / dc.SECTOR_SQUARE_SIZE);
    }

    public boolean isObstacle(MapObject obj) {
        return obj == MapObject.WATER;
    }

    public int remainingSteps() {
        int multiplier = 1;
        if (uc.getAstronautInfo().getCarePackage() == CarePackage.SURVIVAL_KIT) {
            multiplier *= 2;
        }
        if (uc.isDomed(loc)) {
            multiplier *= 2;
        }

        double defaultMultiplier = 0.75;
        if (uc.getAstronautInfo().getOxygen() <= 3) {
            defaultMultiplier = 1;
        }

        return (int)Math.ceil(uc.getAstronautInfo().getOxygen() * defaultMultiplier * multiplier);
    }

    C(UnitController unitController) {
        for (int i = 0; i < directionsNorthCcw.length; i++) {
            Direction first = directionsNorthCcw[i];
            allFirstDirs[i][0] = first;
            allFirstDirs[i][1] = first.rotateLeft();
            allFirstDirs[i][2] = first.rotateRight();
            allFirstDirs[i][3] = first.rotateLeft().rotateLeft();
            allFirstDirs[i][4] = first.rotateRight().rotateRight();
            allFirstDirs[i][5] = first.opposite().rotateRight();
            allFirstDirs[i][6] = first.opposite().rotateLeft();
            allFirstDirs[i][7] = first.opposite();
        }

        id = unitController.getID();

        if (id % 4 == 0) {
            this.fourDirs = new Direction[]{
                    Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH};
            this.fourDirsZero = new Direction[]{
                    Direction.ZERO, Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH};
            this.diagonalDirs = new Direction[]{
                    Direction.NORTHWEST, Direction.NORTHEAST, Direction.SOUTHEAST, Direction.SOUTHWEST};
            this.diagonalDirsZero = new Direction[]{
                    Direction.ZERO, Direction.NORTHWEST, Direction.NORTHEAST, Direction.SOUTHEAST, Direction.SOUTHWEST};
            this.allDirsZero = new Direction[]{
                    Direction.ZERO, Direction.WEST, Direction.NORTHWEST, Direction.NORTH, Direction.NORTHEAST,
                    Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST};
            this.allDirs = new Direction[]{
                    Direction.WEST, Direction.NORTHWEST, Direction.NORTH, Direction.NORTHEAST,
                    Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST};
        } else if (id % 4 == 1) {
            this.fourDirs = new Direction[]{
                    Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH};
            this.fourDirsZero = new Direction[]{
                    Direction.ZERO, Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH};
            this.diagonalDirs = new Direction[]{
                    Direction.SOUTHEAST, Direction.NORTHEAST, Direction.NORTHWEST, Direction.SOUTHWEST};
            this.diagonalDirsZero = new Direction[]{
                    Direction.ZERO, Direction.SOUTHEAST, Direction.NORTHEAST, Direction.NORTHWEST, Direction.SOUTHWEST};
            this.allDirs = new Direction[]{
                    Direction.EAST, Direction.NORTHEAST, Direction.NORTH, Direction.NORTHWEST,
                    Direction.WEST, Direction.SOUTHWEST, Direction.SOUTH, Direction.SOUTHEAST};
            this.allDirsZero = new Direction[]{
                    Direction.ZERO, Direction.EAST, Direction.NORTHEAST, Direction.NORTH, Direction.NORTHWEST,
                    Direction.WEST, Direction.SOUTHWEST, Direction.SOUTH, Direction.SOUTHEAST};
        } else if (id % 4 == 2) {
            this.fourDirs = new Direction[]{
                    Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            this.diagonalDirs = new Direction[]{
                    Direction.NORTHEAST, Direction.SOUTHEAST, Direction.SOUTHWEST, Direction.NORTHWEST};;
            this.allDirs = new Direction[]{
                    Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
                    Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};
            this.fourDirsZero = new Direction[]{
                    Direction.ZERO, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            this.diagonalDirsZero = new Direction[]{
                    Direction.ZERO, Direction.NORTHEAST, Direction.SOUTHEAST, Direction.SOUTHWEST, Direction.NORTHWEST};;
            this.allDirsZero = new Direction[]{
                    Direction.ZERO, Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
                    Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};
        } else {
            this.fourDirs = new Direction[]{
                    Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
            this.diagonalDirs = new Direction[]{
                    Direction.SOUTHWEST, Direction.SOUTHEAST, Direction.NORTHEAST, Direction.NORTHWEST};;
            this.allDirs = new Direction[]{
                    Direction.SOUTH, Direction.SOUTHEAST, Direction.EAST, Direction.NORTHEAST,
                    Direction.NORTH, Direction.NORTHWEST, Direction.WEST, Direction.SOUTHWEST};
            this.fourDirsZero = new Direction[]{
                    Direction.ZERO, Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
            this.diagonalDirsZero = new Direction[]{
                    Direction.ZERO, Direction.SOUTHWEST, Direction.SOUTHEAST, Direction.NORTHEAST, Direction.NORTHWEST};;
            this.allDirsZero = new Direction[]{
                    Direction.ZERO, Direction.SOUTH, Direction.SOUTHEAST, Direction.EAST, Direction.NORTHEAST,
                    Direction.NORTH, Direction.NORTHWEST, Direction.WEST, Direction.SOUTHWEST};
        }


        uc = unitController;
        team = uc.getTeam();
        opponent = uc.getOpponent();
        dc = new DbConst();
        ldb = new LocalDatabase(this);
        rdb = new RemoteDatabase(this);
        if (DEBUG) {
            logger = new LoggerStandard(uc);
        } else {
            logger = new LoggerDummy();
        }

        mapWidth = uc.getMapWidth();
        mapHeight = uc.getMapHeight();

        seed = (int)(uc.getRandomDouble() * 100);
        loc = uc.getLocation();
        startLoc = uc.getLocation();
        spawnRound = uc.getRound();
        s = new Scanner(this);

        visionRange = uc.isStructure() ? (uc.getType() == StructureType.HQ ? 64 : 49) : 25;
        visionRangeReduced1 = uc.isStructure() ? (uc.getType() == StructureType.HQ ? 49 : 36) : 16;
        visionRangeReduced2 = uc.isStructure() ? (uc.getType() == StructureType.HQ ? 36 : 25) : 9;
        visionRangeStep = (int)Math.sqrt(visionRange);
        actionRange = 2;


    }

    public Direction[] getFirstDirs(Direction first) {
        return new Direction[]{
                first, first.rotateRight(), first.rotateLeft(),
                first.rotateRight().rotateRight(), first.rotateLeft().rotateLeft(),
                first.opposite().rotateLeft(), first.opposite().rotateRight(),
                first.opposite()
        };
    }

    public Direction[] getFirstFourDirs(Direction first) {
        if (Math.abs(first.dx) + Math.abs(first.dy) == 2) {
            first = first.rotateRight();
        }
        return new Direction[]{
                first, first.rotateRight().rotateRight(), first.rotateLeft().rotateLeft(), first.opposite(),
        };
    }

    public Location mirrorHorizontal(Location loc) {
        return new Location(loc.x, mapHeight - loc.y - 1);
    }

    public Location mirrorVertical(Location loc) {
        return new Location(mapWidth - loc.x - 1, loc.y);
    }

    public Location mirrorRotational(Location loc) {
        return new Location(mapWidth - loc.x - 1, mapHeight - loc.y - 1);
    }
}
