package kyuu.message;

import aic2024.user.Location;

public class DomeDestroyedNotification extends LocationMessage {
    public DomeDestroyedNotification(Location target) {
        super(target);
    }
}
