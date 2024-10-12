package kyuu.db;

import aic2024.user.Direction;
import aic2024.user.Location;
import kyuu.C;
import kyuu.Vector2D;

public class Expansion {
    public int id;

    public int depth;
    public Location expansionLoc;

    public int[] surveyorStart;


    public int[] expansionWorkers;
    public int[] expansionStart;

    public int expansionDirectionIdx;

    public int[] expansionCounter;


    public int currentExpansionDirIdx;

    public Location[] expansionTree;
    public boolean[] deepened;

    public Expansion parent;

    public Expansion(C c, Location loc, int localExpansionId, int generalDirIdx, int depth, Expansion parent) {
        id = localExpansionId + c.rdb.getThisBaseExpansionId();
        this.parent = parent;

        expansionLoc = loc;
        expansionDirectionIdx = generalDirIdx;
        Direction generalDir = generalDirIdx != -1 ? c.allDirs[generalDirIdx] : null;
        this.depth = depth;
        expansionWorkers = new int[c.allDirs.length];
        expansionStart = new int[c.allDirs.length];
        expansionCounter = new int[c.allDirs.length];
        surveyorStart = new int[c.allDirs.length];
        deepened = new boolean[c.allDirs.length];
        currentExpansionDirIdx = 0;

        for (int i = 0; i < c.allDirs.length; i++) {
            // todo: allow more expansion dir
            if (generalDir != null && c.allDirs[i] != generalDir && c.allDirs[i] != generalDir.rotateRight() && c.allDirs[i] != generalDir.rotateLeft() && c.allDirs[i] != generalDir.rotateLeft().rotateLeft() && c.allDirs[i] != generalDir.rotateRight().rotateRight()) {
//                if (generalDir != null && c.allDirs[i] != generalDir && c.allDirs[i] != generalDir.rotateRight() && c.allDirs[i] != generalDir.rotateLeft()) {
//                if (generalDir != null && c.allDirs[i] != generalDir) {
                c.rdb.surveyorStates[id][i] = c.dc.SURVEY_BAD;
                c.rdb.lastBadSurvey[id][i] = -1;
                continue;
            }
            Direction dir = c.allDirs[i];;
            c.rdb.expansionSites[id][i] = expansionLoc.add(dir.dx * 10, dir.dy * 10);
            if (c.uc.isOutOfMap(c.rdb.expansionSites[id][i])) {
                c.rdb.surveyorStates[id][i] = c.dc.SURVEY_BAD;
                c.rdb.lastBadSurvey[id][i] = -1;
            }

            int mapMagnitude = Math.max(c.uc.getMapWidth(), c.uc.getMapHeight());
            boolean parentCheck = parent == null;
            if (mapMagnitude > 35) {
                parentCheck = true;
            }

            if (c.uc.isOutOfMap(c.rdb.expansionSites[id][i]) && parentCheck) {
                boolean saved = false;
                for (int j = 0; j < 3; j++) {
                    c.rdb.expansionSites[id][i] = expansionLoc.add(dir.dx * (10 - j), dir.dy * (10 - j));
                    if (!c.uc.isOutOfMap(c.rdb.expansionSites[id][i]) && !(c.uc.canSenseLocation(c.rdb.expansionSites[id][i]) && c.isObstacle(c.uc.senseTileType(c.rdb.expansionSites[id][i])))) {
                        saved = true;
                        break;
                    }
                }
                if (saved) {
                    c.rdb.surveyorStates[id][i] = c.dc.SURVEY_NONE;
                    c.rdb.lastBadSurvey[id][i] = 0;
                }
            }

            for (int h = 0; h < c.rdb.baseCount; h++) {
                if (c.rdb.baseLocs[h].equals(c.loc)) {
                    continue;
                }
                if (Vector2D.chebysevDistance(c.rdb.baseLocs[h],c.rdb.expansionSites[id][i]) < 10) {
                    c.rdb.surveyorStates[id][i] = c.dc.SURVEY_BAD;
                    c.rdb.lastBadSurvey[id][i] = -1;
                }
            }

        }

        if (depth == 0) {
            expansionTree = new Location[]{expansionLoc};
        } else if (depth == 1) {
            expansionTree = new Location[]{parent.expansionLoc, expansionLoc};
        } else if (depth == 2) {
            expansionTree = new Location[]{parent.expansionTree[0], parent.expansionTree[1], expansionLoc};
        }
    }


}

