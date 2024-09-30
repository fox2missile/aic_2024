package kyuu.message;

import aic2024.user.Location;

public class SurveyCommand extends LocationMessage {

    public int expansionId;

    public SurveyCommand(Location target, int expansionId) {
        super(target);
        this.expansionId = expansionId;
    }
}
