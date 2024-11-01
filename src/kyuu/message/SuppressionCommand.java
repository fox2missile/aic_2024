package kyuu.message;

import aic2024.user.Location;

public class SuppressionCommand extends LocationMessage {
    public SuppressionCommand(Location target) {
        super(target);
    }
}
