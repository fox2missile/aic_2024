package kyuu.message;

import aic2024.user.Location;

public class SurveyComplete extends LocationMessage {

    public int status;

    public SurveyComplete(Location target, int status) {
        super(target);
        this.status = status;
    }
}
