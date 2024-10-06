package kyuu.db;

import aic2024.user.Location;
import kyuu.C;
import kyuu.fast.FastLocIntMap;
import kyuu.fast.FastLocStack;

public class LocalDatabase extends Database {

    FastLocIntMap sectorStatusMap;

    FastLocStack pendingExploreStack;

    public boolean horizontalSymmetryPossible = true;
    public boolean verticalSymmetryPossible = true;
    public boolean rotationalSymmetryPossible = true;

    public int[] assignedThisRound;
    public int assignedThisRoundSize;

    public int minReserveOxygen;
    public int minReserveEnlistSlot;
    public int availableEnlistSlot;


    public Location[] symmetryCandidates;

    public LocalDatabase(C c) {
        super(c);
        this.sectorStatusMap = new FastLocIntMap();
        this.assignedThisRound = new int[5];
        this.assignedThisRoundSize = 0;
        this.pendingExploreStack = new FastLocStack(20);
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
