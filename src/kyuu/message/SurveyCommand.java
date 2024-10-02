package kyuu.message;

import aic2024.user.Location;

public class SurveyCommand extends LocationMessage {

    public Location[] sources;
    public int expansionId;

    public SurveyCommand(Location target, int expansionId, Location[] sources) {
        super(target);
        this.expansionId = expansionId;
        this.sources = sources;
    }
}
