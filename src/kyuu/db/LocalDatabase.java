package kyuu.db;

import aic2024.user.Location;
import kyuu.C;
import kyuu.fast.FastLocIntMap;
import kyuu.fast.FastLocStack;

public class LocalDatabase {

    C c;
    DbConst dc;

    FastLocIntMap sectorStatusMap;

    FastLocStack pendingExploreStack;

    public LocalDatabase(C c) {
        this.c = c;
        this.dc = c.dc;
        this.sectorStatusMap = new FastLocIntMap();
        this.pendingExploreStack = new FastLocStack(20);
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
