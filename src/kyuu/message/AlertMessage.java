package kyuu.message;

import aic2024.user.Location;

public class AlertMessage extends LocationMessage {

    public int enemyStrength;
    public AlertMessage(Location target, int enemyStrength) {
        super(target);
        this.enemyStrength = enemyStrength;
    }
}
