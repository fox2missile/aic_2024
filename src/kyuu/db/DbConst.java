package kyuu.db;

public class DbConst {

    public int MAX_MAP_OFFSET = 1000;

    // sector properties: constants
    public final int SECTOR_SQUARE_SIZE = 5;
    public final int SECTOR_MAX_LENGTH = (MAX_MAP_OFFSET / SECTOR_SQUARE_SIZE) +
            ((MAX_MAP_OFFSET % SECTOR_SQUARE_SIZE != 0) ? 1 : 0);
    public final int SECTOR_HALF_SQUARE_SIZE = SECTOR_SQUARE_SIZE / 2;
    public final int SECTOR_EXPLORATION_STACK_MAX = 20;

    // sector status enum: values
    public final int STATUS_SECTOR_UNKNOWN = 0;
    public final int STATUS_SECTOR_DISCOVERED = 1;
    public final int STATUS_SECTOR_EXPLORING = 2;
    public final int STATUS_SECTOR_EXPLORED = 3;
    public final int STATUS_SECTOR_UNREACHABLE = 4;
}
