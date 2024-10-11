package kyuu.tasks;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.message.SettlementCommand;

public class SettlerOxygenTask extends SettlerTask {

    boolean previouslyVisible;

    public SettlerOxygenTask(C c, SettlementCommand cmd) {
        super(c, cmd);
        previouslyVisible = false;
    }

    @Override
    public void run() {
        if (!deployed) {
            if (!plannedMoveTask.isFinished()) {
                plannedMoveTask.run();
            } else {
                c.destination = cmd.target;
            }

            if (uc.senseStructures(c.actionRange, c.team).length == 0) {
                deployed = true;
            }
        }

        if (!deployed) {
            return;
        }


        AstronautInfo builder = null;
        for (AstronautInfo a: uc.senseAstronauts(c.visionRange, c.team)) {
            if (a.getID() == cmd.companionId) {
                builder = a;
                break;
            }
        }

        if (builder != null) {
            c.destination = builder.getLocation();
            previouslyVisible = true;
        } else {

            if (previouslyVisible) {
                transferToSettlement();
                if (c.destination != null) {
                    return;
                }
            }

            if (!plannedMoveTask.isFinished()) {
                plannedMoveTask.run();
            } else {
                c.destination = cmd.target;
            }
        }
    }

    private void transferToSettlement() {
        StructureInfo settlement = null;
        for (StructureInfo s: uc.senseStructures(c.visionRange, c.team)) {
            if (s.getType() == StructureType.SETTLEMENT && Vector2D.chebysevDistance(cmd.target, s.getLocation()) <= 3) {
                settlement = s;
                break;
            }
        }
        if (settlement == null) {
            for (StructureInfo s: uc.senseStructures(c.visionRange, c.team)) {
                if (s.getType() == StructureType.SETTLEMENT && s.getOxygen() == 0) {
                    settlement = s;
                    break;
                }
            }
        }
        if (settlement != null) {
            if (c.loc.distanceSquared(settlement.getLocation()) <= c.actionRange) {
                uc.performAction(ActionType.TRANSFER_OXYGEN, c.loc.directionTo(settlement.getLocation()), 1);
            } else {
                c.destination = settlement.getLocation();
            }
        }
    }

}
