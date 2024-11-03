package kyuu.experiments;

import javax.management.RuntimeErrorException;

import aic2024.user.UnitController;
import kyuu.C;
import kyuu.tasks.Task;

public class BytecodeExperiment extends Task {

    
    public BytecodeExperiment(C c) {
        super(c);
    }

    private int nextRandom(UnitController uc) {
        return (int)(uc.getRandomDouble() * 1000);
    }

    @Override
    public void run() {
        int[] arr1 = new int[1000];
        int[] arr2 = new int[1000];
        int var1 = 3;
        int var2 = 4;
        for (int i = 0; i < 100; i++) {
            arr1[i] = nextRandom(uc);
            arr2[i] = nextRandom(uc);
        }
        int energy_before_array = uc.getEnergyLeft();
        for (int i = 0; i < 1000; i++) {
            if (arr1[i] == uc.getEnergyLeft()) {
                arr1[i]++;
            }
            if (arr2[i] == uc.getEnergyLeft()) {
                arr2[i]++;
            }
        }
        c.loggerAlways.log("array spent: %d", energy_before_array - uc.getEnergyLeft());


        int energy_before_vars = uc.getEnergyLeft();
        for (int i = 0; i < 1000; i++) {
            if (var1 == uc.getEnergyLeft()) {
                var1++;
            }
            if (var2 == uc.getEnergyLeft()) {
                var2++;
            }
        }
        c.loggerAlways.log("vars spent: %d", energy_before_vars - uc.getEnergyLeft());

        throw new RuntimeException();
    }
    
}
