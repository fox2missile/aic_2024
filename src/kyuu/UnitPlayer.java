package kyuu;

import aic2024.user.UnitController;
import kyuu.tasks.Task;

public class UnitPlayer {

	public void run(UnitController uc) {
		/*Insert here the code that should be executed only at the beginning of the unit's lifespan*/
		C c = new C(uc);
		/*enemy team*/
		Task strat = uc.isStructure() ? new HeadquarterTask(c) : new AstronautTask(c);

		while (true) {
			c.loc = uc.getLocation();
			strat.run();
			uc.yield(); //End of turn
		}
	}
}
