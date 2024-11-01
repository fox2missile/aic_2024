package kyuu.message;

import aic2024.user.Location;

public class DomeDestroyedNotification extends LocationMessage {

    public int expansionId;
    public DomeDestroyedNotification(Location target, int expansionId) {
        super(target);
        this.expansionId = expansionId;
    }
}
