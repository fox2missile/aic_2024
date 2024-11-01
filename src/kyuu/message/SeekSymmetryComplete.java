package kyuu.message;

import aic2024.user.Location;

public class SeekSymmetryComplete extends LocationMessage {
    public int status;
    public boolean horizontalSymmetryPossible;
    public boolean verticalSymmetryPossible;
    public boolean rotationalSymmetryPossible;

    public SeekSymmetryComplete(Location target, int status,
                                boolean horizontalSymmetryPossible,
                                boolean verticalSymmetryPossible,
                                boolean rotationalSymmetryPossible) {
        super(target);
        this.status = status;
        this.horizontalSymmetryPossible = horizontalSymmetryPossible;
        this.verticalSymmetryPossible = verticalSymmetryPossible;
        this.rotationalSymmetryPossible = rotationalSymmetryPossible;
    }
}
