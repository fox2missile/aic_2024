package kyuu.pathfinder;


import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;

import java.util.Objects;

public class ParallelSearch {

    final int CENTER_IDX = 36;
    final long DEFAULT_REACHABLE_0 = (0) | (1L << CENTER_IDX);

    final long WEST_CELLS = 0b1111111011111110111111101111111011111110111111101111111011111110L;
    final long EAST_CELLS = 0b0111111101111111011111110111111101111111011111110111111101111111L;
    final long NORTH_CELLS = 0b0000000011111111111111111111111111111111111111111111111111111111L;
    final long SOUTH_CELLS = 0b1111111111111111111111111111111111111111111111111111111100000000L;

    final long WEST_GENERAL_DIRECTION_DESTINATION = 0b0000000000000000000000010000000100000001000000010000000000000000L;
    final long EAST_GENERAL_DIRECTION_DESTINATION = 0b0000000000000000100000001000000010000000100000000000000000000000L;
    final long NORTH_GENERAL_DIRECTION_DESTINATION = 0b0011110000000000000000000000000000000000000000000000000000000000L;
    final long SOUTH_GENERAL_DIRECTION_DESTINATION = 0b0000000000000000000000000000000000000000000000000000000000111100L;
    final long NORTH_WEST_GENERAL_DIRECTION_DESTINATION = 0b0000001100000001000000000000000000000000000000000000000000000000L;
    final long NORTH_EAST_GENERAL_DIRECTION_DESTINATION = 0b1100000010000000000000000000000000000000000000000000000000000000L;
    final long SOUTH_WEST_GENERAL_DIRECTION_DESTINATION = 0b0000000000000000000000000000000000000000000000000000000100000011L;
    final long SOUTH_EAST_GENERAL_DIRECTION_DESTINATION = 0b0000000000000000000000000000000000000000000000001000000011000000L;


    final long NORTH_WEST_CELLS = NORTH_CELLS & WEST_CELLS;
    final long NORTH_EAST_CELLS = NORTH_CELLS & EAST_CELLS;
    final long SOUTH_WEST_CELLS = SOUTH_CELLS & WEST_CELLS;
    final long SOUTH_EAST_CELLS = SOUTH_CELLS & EAST_CELLS;

    // Java pad MSB with ones!!!! wtf right
    // Do this to prevent one padding on signed numbers (negatives)
    final long SHR_9 = 0b000000000111111111111111111111111111111111111111111111111111111L;
    final long SHR_8 = 0b000000001111111111111111111111111111111111111111111111111111111L;
    final long SHR_7 = 0b000000011111111111111111111111111111111111111111111111111111111L;
    final long SHR_1 = 0b011111111111111111111111111111111111111111111111111111111111111L;


    final int IMMEDIATE_NORTH_WEST = 43;
    final int IMMEDIATE_NORTH = 44;
    final int IMMEDIATE_NORTH_EAST = 45;
    final int IMMEDIATE_EAST = 37;
    final int IMMEDIATE_SOUTH_EAST = 29;
    final int IMMEDIATE_SOUTH = 28;
    final int IMMEDIATE_SOUTH_WEST = 27;
    final int IMMEDIATE_WEST = 35;
    final int IMMEDIATE_CENTER = 36;

    final int[] IMMEDIATE_DIRECTION_INDEX = {
            IMMEDIATE_NORTH_WEST,
            IMMEDIATE_NORTH,
            IMMEDIATE_NORTH_EAST,
            IMMEDIATE_EAST,
            IMMEDIATE_SOUTH_EAST,
            IMMEDIATE_SOUTH,
            IMMEDIATE_SOUTH_WEST,
            IMMEDIATE_WEST,
    };

    final int[] IMMEDIATE_DIRECTION_NORTH_WEST = {
            IMMEDIATE_NORTH_WEST,
            IMMEDIATE_WEST,
            IMMEDIATE_NORTH,
            IMMEDIATE_SOUTH_WEST,
            IMMEDIATE_NORTH_EAST,
            IMMEDIATE_SOUTH,
            IMMEDIATE_EAST,
            IMMEDIATE_SOUTH_EAST,
    };
    final int[] IMMEDIATE_DIRECTION_NORTH = {
            IMMEDIATE_NORTH,
            IMMEDIATE_NORTH_WEST,
            IMMEDIATE_NORTH_EAST,
            IMMEDIATE_WEST,
            IMMEDIATE_EAST,
            IMMEDIATE_SOUTH_WEST,
            IMMEDIATE_SOUTH_EAST,
            IMMEDIATE_SOUTH,
    };
    final int[] IMMEDIATE_DIRECTION_NORTH_EAST = {
            IMMEDIATE_NORTH_EAST,
            IMMEDIATE_NORTH,
            IMMEDIATE_EAST,
            IMMEDIATE_NORTH_WEST,
            IMMEDIATE_SOUTH_EAST,
            IMMEDIATE_WEST,
            IMMEDIATE_SOUTH,
            IMMEDIATE_SOUTH_WEST,
    };
    final int[] IMMEDIATE_DIRECTION_EAST = {
            IMMEDIATE_EAST,
            IMMEDIATE_NORTH_EAST,
            IMMEDIATE_SOUTH_EAST,
            IMMEDIATE_NORTH,
            IMMEDIATE_SOUTH,
            IMMEDIATE_NORTH_WEST,
            IMMEDIATE_SOUTH_WEST,
            IMMEDIATE_WEST,
    };
    final int[] IMMEDIATE_DIRECTION_SOUTH_EAST = {
            IMMEDIATE_SOUTH_EAST,
            IMMEDIATE_EAST,
            IMMEDIATE_SOUTH,
            IMMEDIATE_NORTH_EAST,
            IMMEDIATE_SOUTH_WEST,
            IMMEDIATE_NORTH,
            IMMEDIATE_WEST,
            IMMEDIATE_NORTH_WEST,
    };
    final int[] IMMEDIATE_DIRECTION_SOUTH = {
            IMMEDIATE_SOUTH,
            IMMEDIATE_SOUTH_EAST,
            IMMEDIATE_SOUTH_WEST,
            IMMEDIATE_EAST,
            IMMEDIATE_WEST,
            IMMEDIATE_NORTH_EAST,
            IMMEDIATE_NORTH_WEST,
            IMMEDIATE_NORTH,
    };
    final int[] IMMEDIATE_DIRECTION_SOUTH_WEST = {
            IMMEDIATE_SOUTH_WEST,
            IMMEDIATE_SOUTH,
            IMMEDIATE_WEST,
            IMMEDIATE_SOUTH_EAST,
            IMMEDIATE_NORTH_WEST,
            IMMEDIATE_EAST,
            IMMEDIATE_NORTH,
            IMMEDIATE_NORTH_EAST,
    };
    final int[] IMMEDIATE_DIRECTION_WEST = {
            IMMEDIATE_WEST,
            IMMEDIATE_SOUTH_WEST,
            IMMEDIATE_NORTH_WEST,
            IMMEDIATE_SOUTH,
            IMMEDIATE_NORTH,
            IMMEDIATE_SOUTH_EAST,
            IMMEDIATE_NORTH_EAST,
            IMMEDIATE_EAST,
    };

    final Direction[] DIRECTIONS_NORTH_WEST_FIRST = {
            Direction.NORTHWEST,
            Direction.WEST,
            Direction.NORTH,
            Direction.SOUTHWEST,
            Direction.NORTHEAST,
            Direction.SOUTH,
            Direction.EAST,
            Direction.SOUTHEAST,
    };

    final Direction[] DIRECTIONS_NORTH_FIRST = {
            Direction.NORTH,
            Direction.NORTHWEST,
            Direction.NORTHEAST,
            Direction.WEST,
            Direction.EAST,
            Direction.SOUTHWEST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
    };

    final Direction[] DIRECTIONS_NORTH_EAST_FIRST = {
            Direction.NORTHEAST,
            Direction.NORTH,
            Direction.EAST,
            Direction.NORTHWEST,
            Direction.SOUTHEAST,
            Direction.WEST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
    };

    final Direction[] DIRECTIONS_EAST_FIRST = {
            Direction.EAST,
            Direction.NORTHEAST,
            Direction.SOUTHEAST,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.NORTHWEST,
            Direction.SOUTHWEST,
            Direction.WEST,
    };

    final Direction[] DIRECTIONS_SOUTH_EAST_FIRST = {
            Direction.SOUTHEAST,
            Direction.EAST,
            Direction.SOUTH,
            Direction.NORTHEAST,
            Direction.SOUTHWEST,
            Direction.NORTH,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    final Direction[] DIRECTIONS_SOUTH_FIRST = {
            Direction.SOUTH,
            Direction.SOUTHEAST,
            Direction.SOUTHWEST,
            Direction.EAST,
            Direction.WEST,
            Direction.NORTHEAST,
            Direction.NORTHWEST,
            Direction.NORTH,
    };

    final Direction[] DIRECTIONS_SOUTH_WEST_FIRST = {
            Direction.SOUTHWEST,
            Direction.SOUTH,
            Direction.WEST,
            Direction.SOUTHEAST,
            Direction.NORTHWEST,
            Direction.EAST,
            Direction.NORTH,
            Direction.NORTHEAST,
    };

    final Direction[] DIRECTIONS_WEST_FIRST = {
            Direction.WEST,
            Direction.SOUTHWEST,
            Direction.NORTHWEST,
            Direction.SOUTH,
            Direction.NORTH,
            Direction.SOUTHEAST,
            Direction.NORTHEAST,
            Direction.EAST,
    };

    private int originX;
    private int originY;

    private int maxStep;

    private int bestDirMinStep;

    // reachable[i] = reachable matrix in i step of move

    long[] reachable;

    interface PassabilityStrategy {
        long getPassabilityMask(int originX, int originY);

        static long getInitialPassability(C c, int originX, int originY) {
            final long WEST_CELLS = 0b1111111011111110111111101111111011111110111111101111111011111110L;
            final long EAST_CELLS = 0b0111111101111111011111110111111101111111011111110111111101111111L;
            final long NORTH_CELLS = 0b0000000011111111111111111111111111111111111111111111111111111111L;
            final long SOUTH_CELLS = 0b1111111111111111111111111111111111111111111111111111111100000000L;

            final long DEFAULT_PASSABILITY = 0xFFFFFFFFFFFFFFFFL;
            long passable = DEFAULT_PASSABILITY;
            if (originX < 0) {
                long verticalScan = WEST_CELLS;
                int x = originX;
                while (x < 0) {
                    passable &= verticalScan;
                    verticalScan = (verticalScan << 1) | (~WEST_CELLS);
                    ++x;
                }
            }

            // todo: map borders
//            if (originX + 7 >= G.rc.getMapWidth()) {
//                long verticalScan = EAST_CELLS;
//                int x = originX + 7;
//                while (x >= G.rc.getMapWidth()) {
//                    passable &= verticalScan;
//                    verticalScan = (verticalScan >> 1) | (~EAST_CELLS);
//                    --x;
//                }
//            }

            if (originY < 0) {
                long horizontalScan = SOUTH_CELLS;
                int y = originY;
                while (y < 0) {
                    passable &= horizontalScan;
                    horizontalScan = (horizontalScan << 8) | (~SOUTH_CELLS);
                    ++y;
                }
            }

            // todo: map borders
//            if (originY + 7 >= G.rc.getMapHeight()) {
//                long horizontalScan = NORTH_CELLS;
//                int y = originY + 7;
//                while (y >= G.rc.getMapHeight()) {
//                    passable &= horizontalScan;
//                    horizontalScan = (horizontalScan >> 8) | (~NORTH_CELLS);
//                    --y;
//                }
//            }

            return passable;
        }
    }

    PassabilityStrategy passabilityStrategy;
    C c;

    ParallelSearch(C c, PassabilityStrategy passabilityStrategy) {
        this.passabilityStrategy = passabilityStrategy;
        this.reachable = null;
        this.c = c;
    }


    // Allies are impassable, enemies are passable
    public static ParallelSearch getDefaultSearch(C c) {
        return new ParallelSearch(c, (originX, originY) -> {
            long passable = PassabilityStrategy.getInitialPassability(c, originX, originY);
            for (int i = 0; i < c.s.obstaclesLength; i++) {
                Location mapLoc = c.s.obstacles[i].loc;
                Location localLoc = mapLoc.add(-originX, -originY);
                if (localLoc.x < 0 || localLoc.x > 7 || localLoc.y < 0 || localLoc.y > 7) {
                    continue;
                }
                passable = passable & ~(1L << (getMatrixIdx(localLoc)));
            }

            passable = calculateBlockingAllies(c, passable, originX, originY);
            return passable;
        });
    }


    static long calculateBlockingAllies(C c, long passable, int originX, int originY) {
        if (c.s.immediateBlockers > 4) {
            // limit our search
            return calculateBlockingUnits(passable, originX, originY, c.s.alliesTooClose);
        }
        return passable;
    }

    static long calculateBlockingUnits(long passable, int originX, int originY, AstronautInfo[] units) {
        for (AstronautInfo unit: units) {
            Location localLoc = unit.getLocation().add(-originX, -originY);
            if (localLoc.x < 0 || localLoc.x > 7 || localLoc.y < 0 || localLoc.y > 7) {
                continue;
            }
            passable = passable & ~(1L << (getMatrixIdx(localLoc)));
        }
        return passable;
    }


    public void calculateReach(int step) {
        Location center = c.loc;
        originX = center.x - 4;
        originY = center.y - 4;
        maxStep = step;
        reachable = new long[maxStep + 1];
        long passable = passabilityStrategy.getPassabilityMask(originX, originY);
        reachable[0] = DEFAULT_REACHABLE_0 & passable;

        for (int i = 1; i <= maxStep; i++) {
            reachable[i] = doOneIteration(reachable[i - 1], reachable[i], passable);
        }
    }

    public Direction calculateBestDirection(Location target, Direction moveBackDir, int steps) {
        Location center = c.loc;
        originX = center.x - 4;
        originY = center.y - 4;
        long[] destinationMask = new long[steps + 1];
        int targetLocalX = target.x - originX;
        int targetLocalY = target.y - originY;
        Direction generalDir = center.directionTo(target);
        if (targetLocalX >= 0 && targetLocalX < 8 && targetLocalY >= 0 && targetLocalY < 8) {
            destinationMask[0] |= (1L << getMatrixIdx(targetLocalX, targetLocalY));
            return calculateBestDirection(destinationMask, generalDir, moveBackDir, steps);
        }
        if (Objects.requireNonNull(generalDir) == Direction.NORTH) {
            destinationMask[0] = NORTH_GENERAL_DIRECTION_DESTINATION;
        } else if (generalDir == Direction.NORTHEAST) {
            destinationMask[0] = NORTH_EAST_GENERAL_DIRECTION_DESTINATION;
        } else if (generalDir == Direction.EAST) {
            destinationMask[0] = EAST_GENERAL_DIRECTION_DESTINATION;
        } else if (generalDir == Direction.SOUTHEAST) {
            destinationMask[0] = SOUTH_EAST_GENERAL_DIRECTION_DESTINATION;
        } else if (generalDir == Direction.SOUTH) {
            destinationMask[0] = SOUTH_GENERAL_DIRECTION_DESTINATION;
        } else if (generalDir == Direction.SOUTHWEST) {
            destinationMask[0] = SOUTH_WEST_GENERAL_DIRECTION_DESTINATION;
        } else if (generalDir == Direction.WEST) {
            destinationMask[0] = WEST_GENERAL_DIRECTION_DESTINATION;
        } else if (generalDir == Direction.NORTHWEST) {
            destinationMask[0] = NORTH_WEST_GENERAL_DIRECTION_DESTINATION;
        } else if (generalDir == Direction.ZERO) {
            return null;
        }
        return calculateBestDirection(destinationMask, generalDir, moveBackDir, steps);
    }

    private long getGeneralDirectionDestinationMask(Direction dir) {
        if (Objects.requireNonNull(dir) == Direction.NORTH) {
            return NORTH_GENERAL_DIRECTION_DESTINATION;
        } else if (dir == Direction.NORTHEAST) {
            return NORTH_EAST_GENERAL_DIRECTION_DESTINATION;
        } else if (dir == Direction.EAST) {
            return EAST_GENERAL_DIRECTION_DESTINATION;
        } else if (dir == Direction.SOUTHEAST) {
            return SOUTH_EAST_GENERAL_DIRECTION_DESTINATION;
        } else if (dir == Direction.SOUTH) {
            return SOUTH_GENERAL_DIRECTION_DESTINATION;
        } else if (dir == Direction.SOUTHWEST) {
            return SOUTH_WEST_GENERAL_DIRECTION_DESTINATION;
        } else if (dir == Direction.WEST) {
            return WEST_GENERAL_DIRECTION_DESTINATION;
        } else if (dir == Direction.NORTHWEST) {
            return NORTH_WEST_GENERAL_DIRECTION_DESTINATION;
        }
        return 0;
    }

    private Direction  calculateBestDirection(long[] destinationMask, Direction generalDir, Direction moveBackDir, int steps) {
        maxStep = steps;
        reachable = new long[maxStep + 1];
        long passable = passabilityStrategy.getPassabilityMask(originX, originY);
//        printMatrix(passable, -1, -1, "---");
//        printMatrix(destinationMask[0], -1, -1, "---");
        reachable[0] = destinationMask[0] & passable;
        if (moveBackDir != null) {
            Location moveBackLoc = c.loc.add(moveBackDir);
            passable &= ~(1L << (getMatrixIdx(moveBackLoc.add(-originX, -originY))));
        }
        int minDist = -1;
        for (int i = 1; i <= maxStep; i++) {
            reachable[i] |= destinationMask[i]; // simulate "far beyond vision" targets
            reachable[i] = doOneIteration(reachable[i - 1], reachable[i], passable);
            if (isReachable(IMMEDIATE_CENTER, i)) {
                minDist = i;
                break;
            }
        }
        if (minDist == -1) {
            return null;
        }

        int[] immediateDirs = {};
        if (Objects.requireNonNull(generalDir) == Direction.NORTH) {
            immediateDirs = IMMEDIATE_DIRECTION_NORTH;
        } else if (generalDir == Direction.NORTHEAST) {
            immediateDirs = IMMEDIATE_DIRECTION_NORTH_EAST;
        } else if (generalDir == Direction.EAST) {
            immediateDirs = IMMEDIATE_DIRECTION_EAST;
        } else if (generalDir == Direction.SOUTHEAST) {
            immediateDirs = IMMEDIATE_DIRECTION_SOUTH_EAST;
        } else if (generalDir == Direction.SOUTH) {
            immediateDirs = IMMEDIATE_DIRECTION_SOUTH;
        } else if (generalDir == Direction.SOUTHWEST) {
            immediateDirs = IMMEDIATE_DIRECTION_SOUTH_WEST;
        } else if (generalDir == Direction.WEST) {
            immediateDirs = IMMEDIATE_DIRECTION_WEST;
        } else if (generalDir == Direction.NORTHWEST) {
            immediateDirs = IMMEDIATE_DIRECTION_NORTH_WEST;
        } else if (generalDir == Direction.ZERO) {
            return null;
        }

        bestDirMinStep = minDist - 2;

        // todo: shuffle.
        for (int index: immediateDirs) {
            if (isReachable(index, minDist - 1)) {
                if (index == IMMEDIATE_NORTH_WEST) {
                    return Direction.NORTHWEST;
                } else if (index == IMMEDIATE_NORTH) {
                    return Direction.NORTH;
                } else if (index == IMMEDIATE_NORTH_EAST) {
                    return Direction.NORTHEAST;
                } else if (index == IMMEDIATE_EAST) {
                    return Direction.EAST;
                } else if (index == IMMEDIATE_SOUTH_EAST) {
                    return Direction.SOUTHEAST;
                } else if (index == IMMEDIATE_SOUTH) {
                    return Direction.SOUTH;
                } else if (index == IMMEDIATE_SOUTH_WEST) {
                    return Direction.SOUTHWEST;
                } else if (index == IMMEDIATE_WEST) {
                    return Direction.WEST;
                }
            }
        }
        return null;
    }

    private long doOneIteration(long prev, long next, long passable) {
        next |= prev;
        next &= passable;
        next |= ((prev & EAST_CELLS) << 1); // E
        next &= passable;
        next |= (((prev & WEST_CELLS) >> 1) & SHR_1); // W
        next &= passable;
        next |= ((prev & NORTH_CELLS) << 8); // N
        next &= passable;
        next |= (((prev & SOUTH_CELLS) >> 8) & SHR_8); // S
        next &= passable;
        next |= ((prev & NORTH_WEST_CELLS) << 7); // NW
        next &= passable;
        next |= ((prev & NORTH_EAST_CELLS) << 9); // NE
        next &= passable;
        next |= (((prev & SOUTH_EAST_CELLS) >> 7) & SHR_7); // SE
        next &= passable;
        next |= (((prev & SOUTH_WEST_CELLS) >> 9) & SHR_9); // SW
        next &= passable;
        return next;
    }

    private int getImmediateDirectionIndex(Direction dir) {
        if (Objects.requireNonNull(dir) == Direction.NORTHWEST) {
            return IMMEDIATE_NORTH_WEST;
        } else if (dir == Direction.NORTH) {
            return IMMEDIATE_NORTH;
        } else if (dir == Direction.NORTHEAST) {
            return IMMEDIATE_NORTH_EAST;
        } else if (dir == Direction.EAST) {
            return IMMEDIATE_EAST;
        } else if (dir == Direction.SOUTHEAST) {
            return IMMEDIATE_SOUTH_EAST;
        } else if (dir == Direction.SOUTH) {
            return IMMEDIATE_SOUTH;
        } else if (dir == Direction.SOUTHWEST) {
            return IMMEDIATE_SOUTH_WEST;
        } else if (dir == Direction.WEST) {
            return IMMEDIATE_WEST;
        }
        return -1;
    }

    void printMatrix(long matrix, int debugX, int debugY, String end) {
        c.logger.log("  0 1 2 3 4 5 6 7");
        for (int y = 7; y >= 0; y--) {
            StringBuilder str = new StringBuilder();
            str.append(y);
            str.append(" ");
            for (int x = 0; x <= 7; x++) {
                if (x == debugX && y == debugY) {
                    str.append("x ");
                    continue;
                }
                int idx = getMatrixIdx(new Location(x, y));
                if (((matrix >> idx) & 1) == 1) {
                    str.append("1 ");
                } else {
                    str.append("0 ");
                }
            }
            c.logger.log(str.toString());
        }
        c.logger.log(end);
    }

    // Assumed calculateBestDirection is already called previously
    public Direction nextBestDirection(Location src, Direction generalDir) {
        if (bestDirMinStep < 0) {
            // you have arrived, now recalculate the whole thing
            return null;
        }
        for (Direction dir: getFirstDirectionArrays(generalDir)) {
            if (isReachable(src.add(dir), bestDirMinStep)) {
                bestDirMinStep--;
                return dir;
            }
        }
        return null;
    }

    private Direction[] getFirstDirectionArrays(Direction dir) {
        if (Objects.requireNonNull(dir) == Direction.NORTHWEST) {
            return DIRECTIONS_NORTH_WEST_FIRST;
        } else if (dir == Direction.NORTH) {
            return DIRECTIONS_NORTH_FIRST;
        } else if (dir == Direction.NORTHEAST) {
            return DIRECTIONS_NORTH_EAST_FIRST;
        } else if (dir == Direction.EAST) {
            return DIRECTIONS_EAST_FIRST;
        } else if (dir == Direction.SOUTHEAST) {
            return DIRECTIONS_SOUTH_EAST_FIRST;
        } else if (dir == Direction.SOUTH) {
            return DIRECTIONS_SOUTH_FIRST;
        } else if (dir == Direction.SOUTHWEST) {
            return DIRECTIONS_SOUTH_WEST_FIRST;
        } else if (dir == Direction.WEST) {
            return DIRECTIONS_WEST_FIRST;
        }
        return DIRECTIONS_NORTH_FIRST;
    }

    public boolean isReachable(Location target) {
        return ((reachable[maxStep] >> getMatrixIdx(target.add(-originX, -originY))) & 1) == 1;
    }

    public boolean isReachable(Location target, int step) {
        if (step >= reachable.length) return false;
        return ((reachable[step] >> getMatrixIdx(target.add(-originX, -originY))) & 1) == 1;
    }

    private boolean isReachable(int index, int step) {
        if (step >= reachable.length) return false;
        return ((reachable[step] >> index) & 1) == 1;
    }

    public int getStepDistance(Location target) {
        for (int step = 1; step < maxStep; step++) {
            if (isReachable(target, step)) {
                return step;
            }
        }
        return -1;
    }

    private static int getMatrixIdx(Location loc) {
        return (loc.y * 8) + loc.x;
    }

    private static int getMatrixIdx(int x, int y) {
        return (y * 8) + x;
    }
}
