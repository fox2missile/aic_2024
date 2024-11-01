package kyuu.experiments;

import aic2024.user.Location;
import aic2024.user.UnitController;
import kyuu.C;
import kyuu.fast.FastNodePriorityQueue;
import kyuu.fast.Node;
import kyuu.tasks.Task;

import java.util.Comparator;

public class PriorityQueueExperiment extends Task {

    public PriorityQueueExperiment(C c) {
        super(c);
    }

    private int nextRandom(UnitController uc) {
        return (int)(uc.getRandomDouble() * 1000);
    }


    @Override
    public void run() {
        if (uc.getEnergyLeft() > 10000) {
            int energy_before_pqu = uc.getEnergyLeft();
            java.util.PriorityQueue<Node> pq_u = new java.util.PriorityQueue<>(100);
            for (int i = 0; i < 50; i++) {
                pq_u.offer(new Node(new Location(nextRandom(uc), nextRandom(uc)), nextRandom(uc), nextRandom(uc)));
            }
            c.loggerAlways.log("java util spent: %d", energy_before_pqu - uc.getEnergyLeft());

            while (!pq_u.isEmpty()) {
                pq_u.poll();
            }

            c.loggerAlways.log("java util spent: %d", energy_before_pqu - uc.getEnergyLeft());



            int energy_before_pqf = uc.getEnergyLeft();
            kyuu.fast.PriorityQueue<Node> pq_f = new kyuu.fast.PriorityQueue<>(100);
            for (int i = 0; i < 50; i++) {
                pq_f.offer(new Node(new Location(nextRandom(uc), nextRandom(uc)), nextRandom(uc), nextRandom(uc)));
            }
            c.loggerAlways.log("kyuu fast spent: %d", energy_before_pqf - uc.getEnergyLeft());

            while (!pq_f.isEmpty()) {
                pq_f.poll();
            }

            c.loggerAlways.log("kyuu fast spent: %d", energy_before_pqf - uc.getEnergyLeft());

            int energy_before_pqff = uc.getEnergyLeft();
            kyuu.fast.FastNodePriorityQueue pq_ff = new FastNodePriorityQueue(100);
            for (int i = 0; i < 50; i++) {
                pq_ff.offer(new Node(new Location(nextRandom(uc), nextRandom(uc)), nextRandom(uc), nextRandom(uc)));
            }
            c.loggerAlways.log("kyuu fast spent: %d", energy_before_pqff - uc.getEnergyLeft());

            while (!pq_f.isEmpty()) {
                pq_ff.poll();
            }

            c.loggerAlways.log("kyuu fast spent: %d", energy_before_pqff - uc.getEnergyLeft());

            throw new RuntimeException();
        }

    }

}
