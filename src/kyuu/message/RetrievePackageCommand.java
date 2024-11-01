package kyuu.message;

import aic2024.user.Location;

public class RetrievePackageCommand extends LocationMessage {
    public RetrievePackageCommand(Location target) {
        super(target);
    }
}
