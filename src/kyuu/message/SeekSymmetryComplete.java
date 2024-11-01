package kyuu.message;

import aic2024.user.Location;

public class SeekSymmetryComplete extends LocationMessage {
    public int status;

    public SeekSymmetryComplete(Location target, int status) {
        super(target);
        this.status = status;
    }
}
