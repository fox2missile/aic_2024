package kyuu.message;

import aic2024.user.Location;

public class WorldMapperCommand extends LocationMessage {
    public WorldMapperCommand(Location target) {
        super(target);
    }
}
