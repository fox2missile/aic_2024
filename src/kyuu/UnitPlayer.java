package kyuu;

import aic2024.user.UnitController;
import kyuu.log.LoggerStandard;
import kyuu.tasks.Task;

public class UnitPlayer {

	public void run(UnitController uc) {
		/*Insert here the code that should be executed only at the beginning of the unit's lifespan*/
		C c = new C(uc);
		/*enemy team*/
		Task strat = uc.isStructure() ? new HeadquarterTask(c) : new AstronautTask(c);
		LoggerStandard exceptionLogger = new LoggerStandard(uc);

		while (true) {
			c.loc = uc.getLocation();
			try {
				strat.run();
			} catch (Exception e) {
				exceptionLogger.log("Exception!!!");
				if (c.DEBUG) {
					// lol I don't care, be gone
					throw e;
				}
			}
			uc.yield(); //End of turn
		}
	}
}
