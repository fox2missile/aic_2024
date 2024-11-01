package kyuu.message;

import aic2024.user.Location;

public class SeekSymmetryComplete implements Message {
    public Location target;
    public int status;

    public SeekSymmetryComplete(Location target, int status) {
        this.target = target;
        this.status = status;
    }
}
