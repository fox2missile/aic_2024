package kyuu.message;

import aic2024.user.Location;

public class BuildHyperJumpCommand extends LocationMessage {
    public BuildHyperJumpCommand(Location target) {
        super(target);
    }
}
