package kyuu.db;

import aic2024.user.Location;

public class EnlistDestination {

    public int enlistedId;
    public Location start;
    public Location destination;
    public boolean approxDestination;

    // First item is same as start, but final destination is not included.
    public Location[] summarizedPath;

    public EnlistDestination(int enlistedId, Location start, Location destination, Location[] summarizedPath, boolean approxDestination) {
        this.enlistedId = enlistedId;
        this.start = start;
        this.destination = destination;
        this.summarizedPath = summarizedPath;
        this.approxDestination = approxDestination;
    }
}
