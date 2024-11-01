package kyuu.message;

import aic2024.user.Location;

public class BuildDomeCommand extends LocationMessage {

    public int expansionId;
    public Location[] path;

    public BuildDomeCommand(Location target, int expansionId, Location[] path) {
        super(target);
        this.expansionId = expansionId;
        this.path = path;
    }
}
