package kyuu.db;

import aic2024.user.CarePackage;
import aic2024.user.GameConstants;
import aic2024.user.Location;

public class DbConst {

    public final int MAX_MAP_OFFSET = 1000;

    public final int MAX_BASES = 20;

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

    // MSG_ID_SYMMETRIC_SEEKER_CMD
    public final int MSG_SIZE_SYMMETRIC_SEEKER_CMD = 3;
    public final int SYMMETRY_HORIZONTAL = 0;
    public final int SYMMETRY_VERTICAL = 1;
    public final int SYMMETRY_ROTATIONAL = 2;
    // {TARGET UNIT, LOC X, LOC Y}

    // MSG_ID_GET_PACKAGES_CMD
    public final int MSG_SIZE_GET_PACKAGES_CMD = 3;
    // {TARGET UNIT, LOC X, LOC Y}

    // MSG_ID_DEFENSE_CMD
    public final int MSG_SIZE_DEFENSE_CMD = 3;
    // {DEFENDER ID, THREAT X, THREAT Y}

    // expansion
    public final int MAX_EXPANSION_DEPTH = 3;
    public final int MAX_HQ = 3;
    public final Location INVALID_LOCATION = new Location(GameConstants.MAX_MAP_SIZE, GameConstants.MAX_MAP_SIZE);

    public final int EXPANSION_SIZE = 9;

    // MSG_ID_SURVEY_CMD
    public final int MSG_SIZE_SURVEY_CMD = 11;
    // {SURVEYOR ID, x, y, expansion id, path size (loc), path1.x, path1.y, path2.x, path2.y, path3.x, path3.y}

    // MSG_ID_EXPANSION
    public final int MSG_SIZE_EXPANSION = 14;
    // {worker id, x, y, state, expansion id, next.x, next.y, path size (loc), path1.x, path1.y, path2.x, path2.y, path3.x, path3.y}
    public final int EXPANSION_STATE_INIT = 0;
    public final int EXPANSION_STATE_ESTABLISHED = 1;
    public final int EXPANSION_STATE_BUILDING_DOME = 2;
    public final int EXPANSION_STATE_HAS_DOME = 3;

    public final int MSG_SIZE_ENEMY_HQ = 2;
    // {x, y}

    // MSG_ID_BUILD_DOME_CMD
    public final int MSG_SIZE_BUILD_DOME_CMD = 11;
    // {builder id, x, y, expansion id, path size (loc), path1.x, path1.y, path2.x, path2.y, path3.x, path3.y }

    // MSG_ID_SETTLEMENT_CMD
    public final int MSG_SIZE_SETTLEMENT_CMD = 11;
    // {settler id, companion id, x, y, path size (loc), path1.x, path1.y, path2.x, path2.y, path3.x, path3.y }

    // MSG_ID_BUILD_HYPER_JUMP_CMD
    public final int MSG_SIZE_BUILD_HYPER_JUMP_CMD = 3;
    // {builder id, x, y}

    public final int MSG_SIZE_INQUIRE_DOME = 2;
    // {

    public final int MSG_SIZE_SUPPRESSION_CMD = 3;
    // {suppressor id, x, y}

    public final int MSG_SIZE_PACKAGE_PRIORITY_MAP = CarePackage.values().length;

    public final int MSG_SIZE_BASE_HEARTBEAT = 1;
    // {baseIdx}
    public final int MSG_SIZE_NEW_SETTLEMENT = 1;
    // {baseIdx}


    // MSG_ID_SYMMETRIC_SEEKER_COMPLETE
    // {type, LOC X, LOC Y} in 32bit int
    public final int SYMMETRIC_SEEKER_COMPLETE_FOUND_HQ = 1;
    public final int SYMMETRIC_SEEKER_COMPLETE_FOUND_NOTHING = 2;
    public final int SYMMETRIC_SEEKER_COMPLETE_FAILED = 3;
    public final int SYMMETRIC_SEEKER_COMPLETE_STATUS_MASKER = 0x0000000F;



    // MSG_ID_SURVEY_COMPLETE
    public final int SURVEY_NONE = 0;
    public final int SURVEY_BAD = 1;
    public final int SURVEY_GOOD = 2;
    public final int SURVEY_EXCELLENT = 3;
    public final int SURVEY_COMPLETE_EXPANSION_ID_MASKER = 0x000000FF;
    public final int EXPANSION_ESTABLISHED_EXPANSION_ID_MASKER = 0x000000FF;
    public final int EXPANSION_MISSED_EXPANSION_ID_MASKER = 0x000000FF;
    public final int ALERT_STRENGTH_MASKER = 0x000000FF;
    public final int DOME_DESTROYED_EXPANSION_ID_MASKER = 0x000000FF;


    public final int MSG_ID_MASK_SYMMETRIC_SEEKER_COMPLETE = 0x01000000;
    public final int MSG_ID_MASK_ENEMY_HQ_DESTROYED = 0x02000000;
    public final int MSG_ID_MASK_SURVEY_COMPLETE_GOOD = 0x03000000;
    public final int MSG_ID_MASK_SURVEY_COMPLETE_BAD = 0x04000000;
    public final int MSG_ID_MASK_SURVEY_FAILED = 0x05000000;
    public final int MSG_ID_MASK_SURVEY_COMPLETE_EXCELLENT = 0x06000000;
    public final int MSG_ID_MASK_EXPANSION_ESTABLISHED = 0x07000000;
    public final int MSG_ID_MASK_ALERT = 0x08000000;
    public final int MSG_ID_MASK_DOME_BUILT = 0x09000000;
    public final int MSG_ID_MASK_DOME_DESTROYED = 0x0A000000;
    public final int MSG_ID_MASK_EXPANSION_MISSED = 0x0B000000;

    // common maskers
    public final int MASKER_LOC_X = 0x00FF0000;
    public final int MASKER_LOC_X_SHIFT = 16;
    public final int MASKER_LOC_Y = 0x0000FF00;
    public final int MASKER_LOC_Y_SHIFT = 8;

    // message id
    public final int MSG_ID_BEGIN = 0x7FFFFFFF;
    public final int MSG_ID_MASKER = 0x7F000000;
    public final int MSG_ID_HQ = MSG_ID_BEGIN - 1;
    public final int MSG_ID_SYMMETRIC_SEEKER_CMD = MSG_ID_BEGIN - 2;
    public final int MSG_ID_SYMMETRIC_SEEKER_COMPLETE = MSG_ID_BEGIN - 3;
    public final int MSG_ID_ENEMY_HQ = MSG_ID_BEGIN - 4;
    public final int MSG_ID_ENEMY_HQ_DESTROYED = MSG_ID_BEGIN - 5;
    public final int MSG_ID_GET_PACKAGES_CMD = MSG_ID_BEGIN - 6;
    public final int MSG_ID_DEFENSE_CMD = MSG_ID_BEGIN - 7;
    public final int MSG_ID_SURVEY_CMD = MSG_ID_BEGIN - 8;
    public final int MSG_ID_SURVEY_COMPLETE_GOOD = MSG_ID_BEGIN - 9;
    public final int MSG_ID_SURVEY_COMPLETE_BAD = MSG_ID_BEGIN - 10;
    public final int MSG_ID_SURVEY_COMPLETE_EXCELLENT = MSG_ID_BEGIN - 11;
    public final int MSG_ID_SURVEY_FAILED = MSG_ID_BEGIN - 12;
    public final int MSG_ID_EXPANSION = MSG_ID_BEGIN - 13;
    public final int MSG_ID_EXPANSION_ESTABLISHED = MSG_ID_BEGIN - 14;
    public final int MSG_ID_ALERT = MSG_ID_BEGIN - 15;
    public final int MSG_ID_BUILD_DOME_CMD = MSG_ID_BEGIN - 16;
    public final int MSG_ID_INQUIRE_DOME = MSG_ID_BEGIN - 17;
    public final int MSG_ID_DOME_BUILT = MSG_ID_BEGIN - 18;
    public final int MSG_ID_DOME_DESTROYED = MSG_ID_BEGIN - 19;
    public final int MSG_ID_EXPANSION_MISSED = MSG_ID_BEGIN - 20;
    public final int MSG_ID_BUILD_HYPER_JUMP_CMD = MSG_ID_BEGIN - 21;
    public final int MSG_ID_SUPPRESSION_CMD = MSG_ID_BEGIN - 22;
    public final int MSG_ID_PACKAGE_PRIORITY_MAP = MSG_ID_BEGIN - 23;
    public final int MSG_ID_SETTLEMENT_CMD = MSG_ID_BEGIN - 24;
    public final int MSG_ID_BASE_HEARTBEAT = MSG_ID_BEGIN - 25;
    public final int MSG_ID_NEW_SETTLEMENT = MSG_ID_BEGIN - 26;
}
