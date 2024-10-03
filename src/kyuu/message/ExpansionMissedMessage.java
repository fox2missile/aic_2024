package kyuu.message;

import aic2024.user.Location;

public class ExpansionMissedMessage extends LocationMessage {
    public int expansionId;

    public ExpansionMissedMessage(Location target, int expansionId) {
        super(target);
    }
}
