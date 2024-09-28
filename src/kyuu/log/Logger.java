package kyuu.log;

public interface Logger {
    void log(String format, Object... args);

    void logEnergyStart();
    void logEnergyFlush(String format, Object... args);
}
