package kyuu.db;

import aic2024.user.Location;
import aic2024.user.StructureType;
import kyuu.C;
import kyuu.fast.FastLocIntMap;
import kyuu.fast.FastLocStack;

import java.util.Iterator;

public class LocalDatabase extends Database {

    FastLocIntMap sectorStatusMap;

    FastLocStack pendingExploreStack;

    public boolean symmetryFound = false;
    public boolean horizontalSymmetryPossible = true;
    public boolean verticalSymmetryPossible = true;
    public boolean rotationalSymmetryPossible = true;

    public int[] assignedThisRound;
    public int assignedThisRoundSize;

    public int minReserveOxygen;
    public int minReserveEnlistSlot;
    public int availableEnlistSlot;


    public Location[] symmetryCandidates;
    public boolean[] symmetryComplete;

    public int neededHyperJumps;

    public Expansion rootExpansion;
    public Expansion[] expansionsDepth1; // may expand again
    public int expansionsDepth1Size;
    public Expansion[] expansionsDepth2; // cannot expand again
    public int expansionsDepth2Size;
    public float oxygenProductionRate;

    // Reversed, first loc is destination.
    // Every destination is reserved 8 paths.
    // The astronauts will reverse it.
    public Location[][] knownPaths;
    public int[] knownPathsLength;
    public int knownDestinations;
    FastLocIntMap destinationPathIdx;

    public EnlistDestination[] recentEnlists;
    public int recentEnlistsLength;

    public LocalDatabase(C c) {
        super(c);
        this.sectorStatusMap = new FastLocIntMap();
        this.assignedThisRound = new int[5];
        this.assignedThisRoundSize = 0;
        this.pendingExploreStack = new FastLocStack(20);
        this.neededHyperJumps = 0;
        this.oxygenProductionRate = uc.isStructure() && uc.getStructureInfo().getType() == StructureType.HQ ? 5 : 0;
        knownPaths = new Location[200][];
        knownPathsLength = new int[200];
        knownDestinations = 0;
        destinationPathIdx = new FastLocIntMap();
        recentEnlists = new EnlistDestination[c.allDirs.length];
        recentEnlistsLength = 0;
    }

    public int allocatePathBuffer(Location destination, Location start) {
        int destPathIdx = destinationPathIdx.getVal(destination);
        if (destPathIdx == -1) {
            destPathIdx = knownDestinations * c.s.defaultAvailableEnlistSlot;
            knownDestinations++;
            destinationPathIdx.add(destination, destPathIdx);
        }
        for (int i = destPathIdx; i < destPathIdx + c.s.defaultAvailableEnlistSlot; i++) {
            if (knownPaths[i] == null || knownPaths[i][knownPathsLength[i] - 1].equals(start)) {
                knownPaths[i] = new Location[dc.MAX_PATH_LENGTH];
                return i;
            }
        }
        // impossible
        throw new IllegalStateException("Allocate path buffer bugged");
    }

    public int getPath(Location start, Location destination, boolean approxDestination) {
        int nearestDestDist = Integer.MAX_VALUE;
        Location nearestDest = null;
        int maxDestDist = approxDestination ? 30 * 30 : 8;
        for (Iterator<Location> it = destinationPathIdx.getIterator(); it.hasNext(); ) {
            Location knownDest = it.next();
            int dist = knownDest.distanceSquared(destination);
            if (dist <= nearestDestDist && dist <= maxDestDist) {
                nearestDest = knownDest;
                nearestDestDist = dist;
            }
        }
        if (nearestDest == null) {
            return -1;
        }
        int destPathIdx = destinationPathIdx.getVal(nearestDest);
        int nearestStartDist = Integer.MAX_VALUE;
        int bestPathIdx = -1;
        for (int i = destPathIdx; i < destPathIdx + c.s.defaultAvailableEnlistSlot; i++) {
            if (knownPaths[i] == null) {
                break;
            }
            Location checkStart = knownPaths[i][knownPathsLength[i] - 1];
            int dist = checkStart.distanceSquared(start);
            if (dist <= nearestStartDist && dist <= 18) {
                nearestStartDist = dist;
                bestPathIdx = i;
            }
        }
        if (bestPathIdx != -1) {
            return bestPathIdx;
        }
        return -1;
    }

    public int getPath(Location start, Location destination) {
        return getPath(start, destination, false);
    }

    public void allocateExpansionData() {
        rootExpansion = new Expansion(c, c.loc, 0, -1, 0, null);
        expansionsDepth1 = new Expansion[8];
        expansionsDepth1Size = 0;
        expansionsDepth2 = new Expansion[8];
        expansionsDepth2Size = 0;
    }

    public Iterator<Expansion> iterateExpansions() {
        return new Iterator<Expansion>() {

            boolean rootExpansionGiven = false;
            int currentDepth1 = 0;
            int currentDepth2 = 0;
            @Override
            public boolean hasNext() {
                if (!rootExpansionGiven) {
                    return rootExpansion != null;
                }
                return currentDepth1 < expansionsDepth1Size || currentDepth2 < expansionsDepth2Size;
            }

            @Override
            public Expansion next() {
                if (!rootExpansionGiven) {
                    rootExpansionGiven = true;
                    return rootExpansion;
                }
                if (currentDepth1 < expansionsDepth1Size) {
                    return expansionsDepth1[currentDepth1++];
                }
                if (currentDepth2 < expansionsDepth2Size) {
                    return expansionsDepth2[currentDepth2++];
                }
                return null;
            }
        };
    }

    public boolean isHorizontalSymmetry() {
        return horizontalSymmetryPossible && !verticalSymmetryPossible && !rotationalSymmetryPossible;
    }

    public boolean isVerticalSymmetry() {
        return !horizontalSymmetryPossible && verticalSymmetryPossible && !rotationalSymmetryPossible;
    }

    public boolean isRotationalSymmetry() {
        return !horizontalSymmetryPossible && !verticalSymmetryPossible && rotationalSymmetryPossible;
    }

    public boolean enlistFullyReserved() {
        return availableEnlistSlot <= minReserveEnlistSlot || uc.getStructureInfo().getOxygen() <= minReserveOxygen;
    }

    public void resetAssignedThisRound() {
        assignedThisRoundSize = 0;
    }

    public void pushAssignedThisRound(int id) {
        if (assignedThisRoundSize < assignedThisRound.length) {
            assignedThisRound[assignedThisRoundSize++] = id;
        }
    }

    public int popAssignedThisRound() {
        return assignedThisRound[--assignedThisRoundSize];
    }

    public void updateSectorDiscovered(Location sector) {
        sectorStatusMap.add(sector, dc.STATUS_SECTOR_DISCOVERED);
    }

    public void updateSectorExplored(Location sector) {
        sectorStatusMap.add(sector, dc.STATUS_SECTOR_EXPLORED);
    }

    public void updateSectorUnreachable(Location sector) {
        sectorStatusMap.add(sector, dc.STATUS_SECTOR_UNREACHABLE);
    }

    public int getSectorStatus(Location sector) {
        return sectorStatusMap.getVal(sector);
    }

    public boolean pushSectorExplorationStack(Location sector) {
        return pendingExploreStack.push(sector);
    }

    public Location popSectorExplorationStack() {
        while (!pendingExploreStack.isEmpty()) {
            Location sector = pendingExploreStack.pop();
            if (getSectorStatus(sector) < dc.STATUS_SECTOR_EXPLORING) {
                return sector;
            }
        }
        return null;
    }
}
