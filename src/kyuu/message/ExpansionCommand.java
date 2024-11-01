package kyuu.message;

import aic2024.user.Location;

public class ExpansionCommand extends LocationMessage {

    public int state;
    public int expansionId;
    public Location[] sources;
    public Location possibleNext;
    public ExpansionCommand(Location target, int state, int expansionId, Location[] sources, Location possibleNext) {
        super(target);
        this.state = state;
        this.expansionId = expansionId;
        this.sources = sources;
        this.possibleNext = possibleNext;
    }
}
