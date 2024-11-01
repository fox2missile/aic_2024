package kyuu.message;

import aic2024.user.Location;

public class BuildDomeCommand extends LocationMessage {
    public BuildDomeCommand(Location target) {
        super(target);
    }
}
