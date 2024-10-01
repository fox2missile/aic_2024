package nullplayer;

import aic2024.user.*;

public class UnitPlayer {

	public void run(UnitController uc) {
		// Code to be executed only at the beginning of the unit's lifespan

		if (uc.getLocation().equals(new Location(10, 4))) {
			uc.enlistAstronaut(Direction.NORTHEAST, 11, null);
			uc.enlistAstronaut(Direction.NORTH, 11, null);
			uc.enlistAstronaut(Direction.NORTHWEST, 11, null);
			uc.enlistAstronaut(Direction.WEST, 11, null);
			uc.enlistAstronaut(Direction.SOUTHWEST, 11, null);
			uc.enlistAstronaut(Direction.SOUTH, 11, null);
		}

		if (uc.getLocation().equals(new Location(28, 10))) {
			for (Direction dir: Direction.values()) {
				if (dir == Direction.ZERO) continue;
				uc.enlistAstronaut(dir, 11, null);
			}
		}

		while (true) {
			// Code to be executed every round
			uc.yield(); // End of turn
		}
	}
}
