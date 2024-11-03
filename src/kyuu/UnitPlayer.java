package kyuu;

import aic2024.user.StructureType;
import aic2024.user.UnitController;
import kyuu.experiments.BytecodeExperiment;
import kyuu.log.LoggerStandard;
import kyuu.tasks.Task;

public class UnitPlayer {

	public void run(UnitController uc) {
		/*Insert here the code that should be executed only at the beginning of the unit's lifespan*/
		C c = new C(uc);
		/*enemy team*/
		Task strategy = uc.isStructure() ? (uc.getStructureInfo().getType() == StructureType.HQ ? new HeadquarterTask(c) : new SettlementTask(c)) : new AstronautTask(c);
		LoggerStandard exceptionLogger = new LoggerStandard(uc);

		while (true) {
			c.loc = uc.getLocation();
			try {
				strategy.run();
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
