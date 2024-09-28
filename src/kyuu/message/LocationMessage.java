package kyuu.message;

import aic2024.user.Location;

public class LocationMessage implements Message {
    public Location target;
    public LocationMessage(Location target) {
        this.target = target;
    }
}
