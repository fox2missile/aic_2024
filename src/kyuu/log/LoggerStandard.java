package kyuu.log;

import aic2024.user.UnitController;

public class LoggerStandard implements Logger {

    private final UnitController mCtrl;

    private int mEnergyCheckpoint;

    public LoggerStandard(UnitController ctrl) {
        this.mCtrl = ctrl;
    }

    @Override
    public void log(String format, Object... args) {
        String prefix = String.format("[%s#%s%d@%d]: ",
                mCtrl.isStructure() ? mCtrl.getType().toString() : "ASTRONAUT", mCtrl.getTeam(), mCtrl.getID(), mCtrl.getRound());
        String str = String.format(format, args);
        mCtrl.println(prefix + str);
    }

    @Override
    public void logEnergyStart() {
        mEnergyCheckpoint = mCtrl.getEnergyLeft();
    }

    @Override
    public void logEnergyFlush(String format, Object... args) {
        log(format, args);
        log("Used energy: %d", mEnergyCheckpoint - mCtrl.getEnergyLeft());
    }
}
