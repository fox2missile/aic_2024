package kyuu.message;

import aic2024.user.Location;

public class SurveyComplete extends LocationMessage {

    public int status;
    public int expansionId;

    public SurveyComplete(Location target, int status, int expansionId) {
        super(target);
        this.status = status;
        this.expansionId = expansionId;
    }
}
