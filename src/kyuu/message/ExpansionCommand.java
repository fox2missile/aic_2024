package kyuu.message;

import aic2024.user.Location;

public class ExpansionCommand extends LocationMessage {

    public int state;
    public int expansionId;
    public ExpansionCommand(Location target, int state, int expansionId) {
        super(target);
        this.state = state;
        this.expansionId = expansionId;
    }
}
