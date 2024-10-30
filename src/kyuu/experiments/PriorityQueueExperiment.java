package kyuu.experiments;

import aic2024.user.UnitController;
import kyuu.C;
import kyuu.tasks.Task;

public class PriorityQueueExperiment extends Task {

    public PriorityQueueExperiment(C c) {
        super(c);
    }

    private int nextRandom(UnitController uc) {
		return (int)(uc.getRandomDouble() * 100);
	}


    @Override
    public void run() {
        if (uc.getEnergyLeft() > 10000) {
			int energy_before_pqu = uc.getEnergyLeft();
			java.util.PriorityQueue<Integer> pq_u = new java.util.PriorityQueue<>(200);
			for (int i = 0; i < 100; i++) {
				pq_u.offer(nextRandom(uc));
			}
			c.loggerAlways.log("java util spent: %d", energy_before_pqu - uc.getEnergyLeft());

			while (!pq_u.isEmpty()) {
				pq_u.poll();
			}

			c.loggerAlways.log("java util spent: %d", energy_before_pqu - uc.getEnergyLeft());



			int energy_before_pqf = uc.getEnergyLeft();
			kyuu.fast.PriorityQueue<Integer> pq_f = new kyuu.fast.PriorityQueue<>(200);
			for (int i = 0; i < 100; i++) {
				pq_f.offer(nextRandom(uc));
			}
			c.loggerAlways.log("kyuu fast spent: %d", energy_before_pqf - uc.getEnergyLeft());

			while (!pq_f.isEmpty()) {
				pq_f.poll();
			}

			c.loggerAlways.log("kyuu fast spent: %d", energy_before_pqf - uc.getEnergyLeft());

			throw new RuntimeException();
		}
		
    }
    
}
