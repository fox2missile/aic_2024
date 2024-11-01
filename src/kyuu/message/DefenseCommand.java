package kyuu.message;

import aic2024.user.Location;

public class DefenseCommand extends LocationMessage {
    public DefenseCommand(Location target) {
        super(target);
    }
}
