package kyuu.message;

import aic2024.user.Location;

public class ExpansionCommand extends LocationMessage {

    public int state;
    public ExpansionCommand(Location target, int state) {
        super(target);
        this.state = state;
    }
}
