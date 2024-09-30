package kyuu.message;

import aic2024.user.Location;

public class ExpansionEstablishedMessage extends LocationMessage {

    public int expansionId;

    public ExpansionEstablishedMessage(Location target, int expansionId) {
        super(target);
        this.expansionId = expansionId;
    }
}
