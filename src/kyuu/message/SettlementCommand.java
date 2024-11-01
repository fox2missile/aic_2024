package kyuu.message;

import aic2024.user.Location;

public class SettlementCommand extends LocationMessage {

    public int companionId;
    public Location[] path;
    public SettlementCommand(Location target, int companionId, Location[] path) {
        super(target);
        this.path = path;
        this.companionId = companionId;
    }
}
