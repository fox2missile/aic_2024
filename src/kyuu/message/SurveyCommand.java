package kyuu.message;

import aic2024.user.Location;

public class SurveyCommand extends LocationMessage {
    public SurveyCommand(Location target) {
        super(target);
    }
}
