package kyuu.db;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.fast.FastIntIntMap;
import kyuu.fast.FastLocIntMap;
import kyuu.fast.FastLocSet;
import kyuu.message.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class RemoteDatabase extends Database {

    public Location[] baseLocs;
    public int[] baseHeartbeats;
    public int baseCount;
    public Location[] enemyHq;
    public int enemyHqSize;

//    public boolean subscribeSeekSymmetryCommand = false;
//    public boolean subscribeSeekSymmetryComplete = false;
    public MessageReceiver seekSymmetryCompleteReceiver;
    public MessageReceiver seekSymmetryCompleteReceiver2;
    public MessageReceiver seekSymmetryCommandReceiver;
    public MessageReceiver suppressionCommandReceiver;
    public MessageReceiver buildDomeCommandReceiver;
    public MessageReceiver domeInquiryReceiver;
    public MessageReceiver domeBuiltReceiver;
    public MessageReceiver domeDestroyedReceiver;
    public MessageReceiver packagePriorityNoticeReceiver;
    public MessageReceiver expansionMissedReceiver;
    public MessageReceiver settlementCommandReceiver;
    public MessageReceiver baseHeartbeatReceiver;
    public MessageReceiver newSettlementReceiver;
    public MessageReceiver newSettlementReceiver2;
    public MessageReceiver retrievePackageFailedReceiver;
    public MessageReceiver retrievePackageCommandReceiver;
    public MessageReceiver suppressorHeartbeatReceiver;
    public MessageReceiver worldMapperCommandReceiver;
    public MessageReceiver worldObstacleReceiver;
    public MessageReceiver pathReceiver;
    public boolean subscribeEnemyHq = false;
    public boolean subscribeDefenseCommand = false;
    public boolean subscribeSurveyCommand = false;
    public boolean subscribeExpansionCommand = false;
    public boolean subscribeSurveyComplete = false;
    public boolean subscribeExpansionEstablished = false;
    public boolean subscribeBuildHyperJumpCmd = false;
    public boolean subscribeAlert = true;

    public int[][] surveyorStates;
    public int[][] lastBadSurvey;
    public Location[][] expansionSites;
    public int[][] expansionStates;
    public int[][] expansionMissed;



    public int baseIdx;

    private final FastLocIntMap knownAlertLocations;
    private final FastLocSet dangerSectors;
    public final FastIntIntMap unitSpawnRounds;
    public final FastLocIntMap sectorAvgDist;
    public final FastLocIntMap sectorSumDist;
    public final FastLocIntMap sectorDistReportCount;
    private final FastLocIntMap sectorLastDanger;
    public Deque<LocationRound> recentDangerSectors;
    public final int RECENT_DANGER_SECTOR_MAX = 7;
    public final int RECENT_DANGER_TIMEOUT = 30;
    public int alertCount;

    private final int[] broadcastBuffer;
    private int broadcastBufferLength;

    public RemoteDatabase(C c) {
        super(c);
        enemyHq = new Location[3];
        baseIdx = -1;
        knownAlertLocations = new FastLocIntMap();
        dangerSectors = new FastLocSet();
        sectorLastDanger = new FastLocIntMap();
        recentDangerSectors = new ArrayDeque<>(RECENT_DANGER_SECTOR_MAX);
        unitSpawnRounds = new FastIntIntMap();
        sectorAvgDist = new FastLocIntMap();
        sectorSumDist = new FastLocIntMap();
        sectorDistReportCount = new FastLocIntMap();
        alertCount = 0;
        baseHeartbeats = new int[dc.MAX_BASES];
        if (uc.isStructure()) {
            broadcastBuffer = new int[1000];
        } else {
            broadcastBuffer = new int[0];
        }
        broadcastBufferLength = 0;
    }

    public void flushBroadcastBuffer() {
        if (broadcastBufferLength == 0) {
            return;
        }
        int minEnergy = broadcastBufferLength * 100;
        if (uc.getEnergyLeft() < minEnergy) {
            return;
        }
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_HQ_SETUP_BROADCAST_MARKER);
        uc.performAction(ActionType.BROADCAST, null, broadcastBufferLength);
        for (int i = 0; i < broadcastBufferLength; i++) {
            uc.performAction(ActionType.BROADCAST, null, broadcastBuffer[i]);
        }
        broadcastBufferLength = 0;
    }

    public boolean isKnownDangerousLocation(Location loc) {
        return dangerSectors.contains(c.getSector(loc));
    }

    public int countNearbyRecentDangerSectors(Location loc) {
        int count = 0;
        for (LocationRound lr: recentDangerSectors) {
            Location sectorCenter = c.getSectorCenter(lr.loc);
            if (loc.distanceSquared(sectorCenter) <= 8 * 8) {
                count++;
            }
        }
        return count;
    }

    public int countNearbyRecentDangerSectors(Location loc, int lookback, int stepDist) {
        int count = 0;
        for (Location sector: dangerSectors.getKeys()) {
            Location sectorCenter = c.getSectorCenter(sector);
            if (loc.distanceSquared(sectorCenter) <= stepDist * stepDist && uc.getRound() - sectorLastDanger.getVal(sector) < lookback) {
                count++;
            }
        }
        return count;
    }

    public void initExpansionData() {
        surveyorStates = new int[dc.EXPANSION_SIZE * dc.MAX_BASES][c.allDirs.length];
        expansionSites = new Location[dc.EXPANSION_SIZE * dc.MAX_BASES][c.allDirs.length];
        expansionStates = new int[dc.EXPANSION_SIZE * dc.MAX_BASES][c.allDirs.length];
        lastBadSurvey = new int[dc.EXPANSION_SIZE * dc.MAX_BASES][c.allDirs.length];
        expansionMissed = new int[dc.EXPANSION_SIZE * dc.MAX_BASES][c.allDirs.length];
    }

    public void clearBadSurveyHistory(int expansionId, int steps) {
        for (int i = 0; i < c.allDirs.length; i++) {
            if (surveyorStates[expansionId][i] == dc.SURVEY_BAD && lastBadSurvey[expansionId][i] != -1 && uc.getRound() - lastBadSurvey[expansionId][i] >= steps) {
                lastBadSurvey[expansionId][i] = 0;
                surveyorStates[expansionId][i] = dc.SURVEY_NONE;
            }
        }
    }

    public void introduceHq() {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_HQ);
        if (baseIdx == -1) {
            baseIdx = 0;
            while (uc.pollBroadcast() != null) {
                // NOTE: no one else should broadcast at round 0
                baseIdx++;
            }
        }
    }

    public void listenToBaseHeartbeat() {
        baseHeartbeatReceiver = (int __) -> {
            BroadcastInfo idxBroadcast = uc.pollBroadcast();
            int idx = idxBroadcast.getMessage();
            baseHeartbeats[idx] = idxBroadcast.getRound();
        };
    }

    public boolean isBaseAlive(int idx) {
        return uc.getRound() - baseHeartbeats[idx] < 5;
    }

    public void introduceSettlement() {
        baseLocs = new Location[dc.MAX_BASES];
        baseIdx = 0;
        baseCount = 1; // self
        baseHeartbeatReceiver = (int __) -> {
            BroadcastInfo idxBroadcast = uc.pollBroadcast();
            int idx = idxBroadcast.getMessage();
            baseLocs[idx] = idxBroadcast.getLocation();
            baseCount++;
        };
        while (retrieveNextMessage() != null) {}
        for (int i = 0; i < baseCount; i++) {
            if (baseLocs[i] == null) {
                baseIdx = i;
                break;
            }
        }
        baseLocs[baseIdx] = c.loc;
        baseHeartbeatReceiver = null;
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_NEW_SETTLEMENT);
        uc.performAction(ActionType.BROADCAST, null, baseIdx);
        listenToBaseHeartbeat();
    }

    public void sendBaseHeartbeat() {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_BASE_HEARTBEAT);
        uc.performAction(ActionType.BROADCAST, null, baseIdx);
        baseHeartbeats[baseIdx] = uc.getRound();
    }

    private int getPrimaryBaseIdx() {
        for (int i = 0; i < baseCount; i++) {
            if (uc.getRound() - baseHeartbeats[i] < 3) {
                return i;
            }
        }
        // impossible
        throw new IllegalStateException("All bases gone already");
    }

    public boolean isPrimaryBase() {
        return getPrimaryBaseIdx() == baseIdx;
    }

    public void initHqLocs() {
        BroadcastInfo b = uc.pollBroadcast();
        int otherHqCount = 0;
        Location[] tempLocs = new Location[3];
        while (b != null && b.getMessage() == dc.MSG_ID_HQ) {
            tempLocs[otherHqCount++] = b.getLocation();
            b = uc.pollBroadcast();
        }
        baseCount = 1 + otherHqCount;
        baseLocs = new Location[dc.MAX_BASES];
        baseLocs[baseIdx] = c.loc;
        if (otherHqCount >= 1) {
            baseLocs[(baseIdx + 1) % baseCount] = tempLocs[0];
        }
        if (otherHqCount == 2) {
            baseLocs[(baseIdx + 2) % baseCount] = tempLocs[1];
        }
        listenToBaseHeartbeat();
    }

    public void sendSymmetricSeekerCommand(int targetId, Location targetLoc) {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_SYMMETRIC_SEEKER_CMD);
        uc.performAction(ActionType.BROADCAST, null, targetId);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.x);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.y);
    }

    public void sendGetPackagesCommand(int targetId, Location targetLoc) {
        broadcastBuffer[broadcastBufferLength++] = dc.MSG_ID_GET_PACKAGES_CMD;
        broadcastBuffer[broadcastBufferLength++] = targetId;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.x;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.y;
    }

    public void sendEnemyHqLocMessage(Location loc) {
        c.logger.log("broadcasting enemy hq: %s", loc);
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_ENEMY_HQ);
        uc.performAction(ActionType.BROADCAST, null, loc.x);
        uc.performAction(ActionType.BROADCAST, null, loc.y);
    }


    public void sendDefenseCommand(int defenderId, Location targetLoc) {
        c.logger.log("sending defender %d to defend %s", defenderId, targetLoc);
        broadcastBuffer[broadcastBufferLength++] = dc.MSG_ID_DEFENSE_CMD;
        broadcastBuffer[broadcastBufferLength++] = defenderId;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.x;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.y;
    }

    public void sendBuildDomeCommand(int builderId, Location targetLoc, int expansionId, Location[] path) {
        c.logger.log("Sending builder %d to build dome at %s", builderId, targetLoc);
        broadcastBuffer[broadcastBufferLength++] = dc.MSG_ID_BUILD_DOME_CMD;
        broadcastBuffer[broadcastBufferLength++] = builderId;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.x;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.y;
        broadcastBuffer[broadcastBufferLength++] = expansionId;
        broadcastBuffer[broadcastBufferLength++] = path.length;
        for (int i = 0; i < path.length; i++) {
            Location source = path[i];
            if (i >= 1) {
                uc.drawLineDebug(path[i], path[i-1], 0, 127, 127);
            }
            broadcastBuffer[broadcastBufferLength++] = source.x;
            broadcastBuffer[broadcastBufferLength++] = source.y;
        }
        uc.drawLineDebug(targetLoc, path[path.length - 1], 0, 255, 255);
        // padding
        if (path.length < dc.MAX_EXPANSION_DEPTH) {
            for (int i = path.length; i < dc.MAX_EXPANSION_DEPTH; i++) {
                // won't be read
                broadcastBuffer[broadcastBufferLength++] = 0;
                broadcastBuffer[broadcastBufferLength++] = 0;
            }
        }
    }

    public void sendSettlementCommand(int settlerId, int companionId, Location targetLoc, Location[] path) {
        c.logger.log("Sending builder %d to help establish settlement at %s", settlerId, targetLoc);
        broadcastBuffer[broadcastBufferLength++] = dc.MSG_ID_SETTLEMENT_CMD;
        broadcastBuffer[broadcastBufferLength++] = settlerId;
        broadcastBuffer[broadcastBufferLength++] = companionId;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.x;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.y;
        broadcastBuffer[broadcastBufferLength++] = path.length;
        for (int i = 0; i < path.length; i++) {
            Location source = path[i];
            if (i >= 1) {
                uc.drawLineDebug(path[i], path[i-1], 0, 127, 127);
            }
            broadcastBuffer[broadcastBufferLength++] = source.x;
            broadcastBuffer[broadcastBufferLength++] = source.y;
        }
        uc.drawLineDebug(targetLoc, path[path.length - 1], 0, 255, 255);
        // padding
        if (path.length < dc.MAX_EXPANSION_DEPTH) {
            for (int i = path.length; i < dc.MAX_EXPANSION_DEPTH; i++) {
                // won't be read
                broadcastBuffer[broadcastBufferLength++] = 0;
                broadcastBuffer[broadcastBufferLength++] = 0;
            }
        }
    }

    public void sendBuildHyperJumpCommand(int builderId, Location targetLoc) {
        c.logger.log("Sending builder %d to build hyper jump at %s", builderId, targetLoc);
        broadcastBuffer[broadcastBufferLength++] = dc.MSG_ID_BUILD_HYPER_JUMP_CMD;
        broadcastBuffer[broadcastBufferLength++] = builderId;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.x;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.y;
    }

    public void sendSuppressionCommand(int attackerId, Location targetLoc) {
        c.logger.log("Sending attacker %d to suppress %s", attackerId, targetLoc);
        broadcastBuffer[broadcastBufferLength++] = dc.MSG_ID_SUPPRESSION_CMD;
        broadcastBuffer[broadcastBufferLength++] = attackerId;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.x;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.y;
    }

    public void sendDomeInquiry(Location targetLoc) {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_INQUIRE_DOME);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.x);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.y);
    }

    public void sendWorldMapperCommand(int mapperId, Location targetLoc) {
        broadcastBuffer[broadcastBufferLength++] = dc.MSG_ID_WORLD_MAPPER_CMD;
        broadcastBuffer[broadcastBufferLength++] = mapperId;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.x;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.y;
    }

    public void sendSurveyCommand(int surveyor, Location targetLoc, int expansionId, Location[] path) {
        c.logger.log("send out surveyor %d to %s [expansion id: %d]", surveyor, targetLoc, expansionId);
        broadcastBuffer[broadcastBufferLength++] = dc.MSG_ID_SURVEY_CMD;
        broadcastBuffer[broadcastBufferLength++] = surveyor;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.x;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.y;
        broadcastBuffer[broadcastBufferLength++] = expansionId;
        broadcastBuffer[broadcastBufferLength++] = path.length;
        for (int i = 0; i < path.length; i++) {
            Location source = path[i];
            if (i >= 1) {
                uc.drawLineDebug(path[i], path[i-1], 0, 255, 255);
            }
            broadcastBuffer[broadcastBufferLength++] = source.x;
            broadcastBuffer[broadcastBufferLength++] = source.y;
        }
            uc.drawLineDebug(targetLoc, path[path.length - 1], 0, 255, 255);
        // padding
        if (path.length < dc.MAX_EXPANSION_DEPTH) {
            for (int i = path.length; i < dc.MAX_EXPANSION_DEPTH; i++) {
                // won't be read
                broadcastBuffer[broadcastBufferLength++] = 0;
                broadcastBuffer[broadcastBufferLength++] = 0;
            }
        }
    }

    public void sendExpansionCommand(int workerId, Location targetLoc, int state, int expansionId, Location[] path, Location possibleNext) {
        c.logger.log("send out expansion worker %d to %s [expansion id: %d]", workerId, targetLoc, expansionId);
        broadcastBuffer[broadcastBufferLength++] = dc.MSG_ID_EXPANSION;
        broadcastBuffer[broadcastBufferLength++] = workerId;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.x;
        broadcastBuffer[broadcastBufferLength++] = targetLoc.y;
        broadcastBuffer[broadcastBufferLength++] = state;
        broadcastBuffer[broadcastBufferLength++] = expansionId;
        if (possibleNext != null) {
            broadcastBuffer[broadcastBufferLength++] = possibleNext.x;
            broadcastBuffer[broadcastBufferLength++] = possibleNext.y;
        } else {
            broadcastBuffer[broadcastBufferLength++] = dc.INVALID_LOCATION.x;
            broadcastBuffer[broadcastBufferLength++] = dc.INVALID_LOCATION.y;
        }
        broadcastBuffer[broadcastBufferLength++] = path.length;
        for (Location source : path) {
            broadcastBuffer[broadcastBufferLength++] = source.x;
            broadcastBuffer[broadcastBufferLength++] = source.y;
        }
        // padding
        if (path.length < dc.MAX_EXPANSION_DEPTH) {
            for (int i = path.length; i < dc.MAX_EXPANSION_DEPTH; i++) {
                // won't be read
                broadcastBuffer[broadcastBufferLength++] = 0;
                broadcastBuffer[broadcastBufferLength++] = 0;
            }
        }
    }

    public int getThisBaseExpansionId() {
        return baseIdx * dc.EXPANSION_SIZE;
    }

    public boolean isSubscribingExpansionId(int id) {
        int rangeBegin = getThisBaseExpansionId();
        int rangeEnd = (baseIdx + 1) * dc.EXPANSION_SIZE;
        return rangeBegin <= id && id < rangeEnd;
    }

    public void sendPackagePriorityNotice(int[] priorityMap) {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_PACKAGE_PRIORITY_MAP);
        for (int score : priorityMap) {
            uc.performAction(ActionType.BROADCAST, null, score);
        }
    }

    public void sendWorldObstacles(Location[] obstacles, int begin, int length) {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_WORLD_OBSTACLES);
        uc.performAction(ActionType.BROADCAST, null, length);
        for (int i = begin; i < begin + length; i++) {
            uc.performAction(ActionType.BROADCAST, null, obstacles[i].x);
            uc.performAction(ActionType.BROADCAST, null, obstacles[i].y);
        }
    }

    public void sendWorldObstaclesFromBase(Location[] obstacles) {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_WORLD_OBSTACLES);
        uc.performAction(ActionType.BROADCAST, null, obstacles.length);
        for (Location obstacle : obstacles) {
            uc.performAction(ActionType.BROADCAST, null, obstacle.x);
            uc.performAction(ActionType.BROADCAST, null, obstacle.y);
        }
    }

    public void sendWorldObstaclesFromBase(FastLocIntMap map) {
        int obstacleCount = 0;
        for (Iterator<Location> it = map.getIterator(); it.hasNext(); ) {
            Location loc = it.next();
            if (map.getVal(loc) != dc.TILE_OBSTACLE) {
                continue;
            }
            obstacleCount++;
        }
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_WORLD_OBSTACLES);
        uc.performAction(ActionType.BROADCAST, null, obstacleCount);
        for (Iterator<Location> it = map.getIterator(); it.hasNext(); ) {
            Location loc = it.next();
            if (map.getVal(loc) != dc.TILE_OBSTACLE) {
                continue;
            }
            uc.performAction(ActionType.BROADCAST, null, loc.x);
            uc.performAction(ActionType.BROADCAST, null, loc.y);
        }
    }

    public void sendPath(int targetId, int pathIdx) {
        broadcastBuffer[broadcastBufferLength++] = dc.MSG_ID_PATH;
        int length = c.ldb.knownPathsLength[pathIdx];
        broadcastBuffer[broadcastBufferLength++] = length;
        broadcastBuffer[broadcastBufferLength++] = targetId;
        Location[] path = c.ldb.knownPaths[pathIdx];
        // reverse the path for astronaut convenience
        for (int i = length - 1; i >= 0; i--) {
            broadcastBuffer[broadcastBufferLength++] = path[i].x;
            broadcastBuffer[broadcastBufferLength++] = path[i].y;
        }
    }

    private int getStructureExpansionId() {
        return baseIdx * dc.EXPANSION_SIZE;
    }


    public Message retrieveNextMessage() {
        BroadcastInfo b = uc.pollBroadcast();
        while (b != null) {
            int fullMsg = b.getMessage();
            int msgId = getMsgId(fullMsg);
            if (msgId == dc.MSG_ID_HQ_SETUP_BROADCAST_MARKER) {
                int length = uc.pollBroadcast().getMessage();
                if (uc.isStructure() || !uc.getAstronautInfo().isBeingConstructed()) {
                    uc.eraseBroadcastBuffer(length);
                }
            } else if (msgId == dc.MSG_ID_SYMMETRIC_SEEKER_CMD) {
                if (seekSymmetryCommandReceiver != null) {
                    seekSymmetryCommandReceiver.receive(fullMsg);
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_SYMMETRIC_SEEKER_CMD); // ID wasn't read
                }
            } else if (msgId == dc.MSG_ID_GET_PACKAGES_CMD) {
                if (retrievePackageCommandReceiver != null) {
                    retrievePackageCommandReceiver.receive(fullMsg);
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_GET_PACKAGES_CMD); // ID wasn't read
                }
            } else if (msgId == dc.MSG_ID_GET_PACKAGES_FAILED) {
                if (retrievePackageFailedReceiver != null) {
                    retrievePackageFailedReceiver.receive(fullMsg);
                } // no payload
            } else if (msgId == dc.MSG_ID_SUPPRESSOR_HEARTBEAT) {
                if (suppressorHeartbeatReceiver != null) {
                    suppressorHeartbeatReceiver.receive(b.getID());
                } // no payload
            } else if (msgId == dc.MSG_ID_DEFENSE_CMD) {
                if (subscribeDefenseCommand) {
                    if (c.id == uc.pollBroadcast().getMessage()) {
                        return new DefenseCommand(new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage()));
                    } else {
                        uc.eraseBroadcastBuffer(dc.MSG_SIZE_DEFENSE_CMD - 1); // -1 ID
                    }
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_DEFENSE_CMD); // ID wasn't read
                }
            } else if (msgId == dc.MSG_ID_BUILD_DOME_CMD) {
                if (buildDomeCommandReceiver != null) {
                    buildDomeCommandReceiver.receive(fullMsg);
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_BUILD_DOME_CMD); // ID wasn't read
                }
            } else if (msgId == dc.MSG_ID_BUILD_HYPER_JUMP_CMD) {
                if (subscribeBuildHyperJumpCmd) {
                    if (c.id == uc.pollBroadcast().getMessage()) {
                        return new BuildHyperJumpCommand(new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage()));
                    } else {
                        uc.eraseBroadcastBuffer(dc.MSG_SIZE_BUILD_HYPER_JUMP_CMD - 1); // -1 ID
                    }
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_BUILD_HYPER_JUMP_CMD); // ID wasn't read
                }
            } else if (msgId == dc.MSG_ID_INQUIRE_DOME) {
                if (domeInquiryReceiver != null) {
                    domeInquiryReceiver.receive(fullMsg);
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_INQUIRE_DOME);
                }
            } else if (msgId == dc.MSG_ID_SURVEY_CMD) {
                if (subscribeSurveyCommand) {
                    if (c.id == uc.pollBroadcast().getMessage()) {
                        Location target = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
                        int expansionId = uc.pollBroadcast().getMessage();
                        int expansionAncestorSize = uc.pollBroadcast().getMessage();
                        Location[] expansionAncestors = new Location[expansionAncestorSize];
                        for (int i = 0; i < expansionAncestorSize; i++) {
                            expansionAncestors[i] = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
                        }
                        uc.eraseBroadcastBuffer(2 * (dc.MAX_EXPANSION_DEPTH - expansionAncestorSize));
                        return new SurveyCommand(target, expansionId, expansionAncestors);
                    } else {
                        uc.eraseBroadcastBuffer(dc.MSG_SIZE_SURVEY_CMD - 1); // -1 ID
                    }
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_SURVEY_CMD); // ID wasn't read
                }
            } else if (msgId == dc.MSG_ID_EXPANSION) {
                if (subscribeExpansionCommand) {
                    if (c.id == uc.pollBroadcast().getMessage()) {
                        Location target = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
                        int state = uc.pollBroadcast().getMessage();
                        int expansionId = uc.pollBroadcast().getMessage();
                        Location possibleNext = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
                        if (possibleNext.equals(dc.INVALID_LOCATION)) {
                            possibleNext = null;
                        }
                        int expansionAncestorSize = uc.pollBroadcast().getMessage();
                        Location[] expansionAncestors = new Location[expansionAncestorSize];
                        for (int i = 0; i < expansionAncestorSize; i++) {
                            expansionAncestors[i] = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
                        }
                        uc.eraseBroadcastBuffer(2 * (dc.MAX_EXPANSION_DEPTH - expansionAncestorSize));
                        return new ExpansionCommand(target, state, expansionId, expansionAncestors, possibleNext);
                    } else {
                        uc.eraseBroadcastBuffer(dc.MSG_SIZE_EXPANSION - 1); // -1 ID
                    }
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_EXPANSION); // ID wasn't read
                }
            } else if (msgId == dc.MSG_ID_SYMMETRIC_SEEKER_COMPLETE) {
                if (seekSymmetryCompleteReceiver != null) {
                    seekSymmetryCompleteReceiver.receive(fullMsg);
                    // secondary receivers should not read further broadcasts
                    if (seekSymmetryCompleteReceiver2 != null) {
                        seekSymmetryCompleteReceiver2.receive(-1);
                    }
                } // size = 1
            } else if (msgId == dc.MSG_ID_SUPPRESSION_CMD) {
                if (suppressionCommandReceiver != null) {
                    suppressionCommandReceiver.receive(fullMsg);
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_SUPPRESSION_CMD); // ID wasn't read
                }
            } else if (msgId == dc.MSG_ID_WORLD_MAPPER_CMD) {
                if (worldMapperCommandReceiver != null) {
                    worldMapperCommandReceiver.receive(fullMsg);
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_WORLD_MAPPER_CMD); // ID wasn't read
                }
            } else if (msgId == dc.MSG_ID_SETTLEMENT_CMD) {
                if (settlementCommandReceiver != null) {
                    settlementCommandReceiver.receive(fullMsg);
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_SETTLEMENT_CMD); // ID wasn't read
                }
            } else if (msgId == dc.MSG_ID_PACKAGE_PRIORITY_MAP) {
                if (b.getID() == uc.getParent().getID() && packagePriorityNoticeReceiver != null) {
                    packagePriorityNoticeReceiver.receive(fullMsg);
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_PACKAGE_PRIORITY_MAP);
                }
            } else if (msgId == dc.MSG_ID_SURVEY_COMPLETE_GOOD || msgId == dc.MSG_ID_SURVEY_COMPLETE_BAD
                    || msgId == dc.MSG_ID_SURVEY_FAILED || msgId == dc.MSG_ID_SURVEY_COMPLETE_EXCELLENT) {
                if (subscribeSurveyComplete) {
                    int status = dc.SURVEY_GOOD;
                    if (msgId == dc.MSG_ID_SURVEY_COMPLETE_BAD) {
                        status = dc.SURVEY_BAD;
                    } else if (msgId == dc.MSG_ID_SURVEY_FAILED) {
                        status = dc.SURVEY_NONE;
                    } else if (msgId == dc.MSG_ID_SURVEY_COMPLETE_EXCELLENT) {
                        status = dc.SURVEY_EXCELLENT;
                    }
                    int expansionId = fullMsg & dc.SURVEY_COMPLETE_EXPANSION_ID_MASKER;
                    if (isSubscribingExpansionId(expansionId)) {
                        SurveyComplete s = new SurveyComplete(
                                new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                                        (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT),
                                status, expansionId
                                );
                        for (int i = 0; i < c.allDirs.length; i++) {
                            if (expansionSites[expansionId][i] == null) {
                                continue;
                            }
                            if (Vector2D.chebysevDistance(expansionSites[expansionId][i], s.target) < 3) {
                                c.logger.log("Received survey status: %s - %s", s.target, s.status);
                                surveyorStates[expansionId][i] = s.status;
                                if (s.status == dc.SURVEY_BAD) {
                                    lastBadSurvey[expansionId][i] = uc.getRound();
                                }
                                return s;
                            }
                        }
                        // error
                    }

                } // no payload
            } else if (msgId == dc.MSG_ID_EXPANSION_ESTABLISHED) {
                if (subscribeExpansionEstablished) {
                    int expansionId = fullMsg & dc.EXPANSION_ESTABLISHED_EXPANSION_ID_MASKER;
                    if (isSubscribingExpansionId(expansionId)) {
                        ExpansionEstablishedMessage ex = new ExpansionEstablishedMessage(
                                new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                                        (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT),
                                expansionId
                        );
                        for (int i = 0; i < c.allDirs.length; i++) {
                            if (expansionSites[expansionId][i] == null) {
                                continue;
                            }
                            if (Vector2D.chebysevDistance(expansionSites[expansionId][i], ex.target) < 3) {
                                c.logger.log("Expansion established: %s", ex.target);
                                expansionStates[expansionId][i] = dc.EXPANSION_STATE_ESTABLISHED;
                                return ex;
                            }
                        }
                    }

                } // no payload
            } else if (msgId == dc.MSG_ID_EXPANSION_MISSED) {
                if (expansionMissedReceiver != null) {
                    expansionMissedReceiver.receive(fullMsg);
                } // no payload
            } else if (msgId == dc.MSG_ID_ENEMY_HQ) {
                if (subscribeEnemyHq) {
                    Location loc = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
                    addEnemyHq(loc);
                    return new EnemyHqMessage(loc);
                } else {
                    // skip payload
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_ENEMY_HQ);
                }
            } else if (msgId == dc.MSG_ID_DOME_BUILT) {
                if (domeBuiltReceiver != null) {
                    domeBuiltReceiver.receive(fullMsg);
                } // no payload
            } else if (msgId == dc.MSG_ID_DOME_DESTROYED) {
                if (domeDestroyedReceiver != null) {
                    domeDestroyedReceiver.receive(fullMsg);
                } // no payload
            } else if (msgId == dc.MSG_ID_BASE_HEARTBEAT) {
                if (baseHeartbeatReceiver != null) {
                    baseHeartbeatReceiver.receive(fullMsg);
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_BASE_HEARTBEAT);
                }
            } else if (msgId == dc.MSG_ID_NEW_SETTLEMENT) {
                if (newSettlementReceiver != null) {
                    newSettlementReceiver.receive(fullMsg);
                    if (newSettlementReceiver2 != null) {
                        newSettlementReceiver2.receive(-1);
                    }
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_NEW_SETTLEMENT);
                }
            } else if (msgId == dc.MSG_ID_WORLD_OBSTACLES) {
                if (worldObstacleReceiver != null) {
                    worldObstacleReceiver.receive(fullMsg);
                } else {
                    uc.eraseBroadcastBuffer(uc.pollBroadcast().getMessage() * 2); // first message is the length
                }
            } else if (msgId == dc.MSG_ID_PATH) {
                if (pathReceiver != null) {
                    pathReceiver.receive(fullMsg);
                } else {
                    // first message is the length, second message is the target ID
                    uc.eraseBroadcastBuffer((uc.pollBroadcast().getMessage() * 2) + 1);
                }
            } else if (msgId == dc.MSG_ID_ALERT) {
                if (subscribeAlert) {
                    Location loc = new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                            (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT);
                    int strength = fullMsg & dc.ALERT_STRENGTH_MASKER;
                    AlertMessage msg = new AlertMessage(loc, strength);
                    Location sector = c.getSector(msg.target);
                    dangerSectors.add(sector);
                    sectorLastDanger.addReplace(sector, uc.getRound());
                    knownAlertLocations.addReplace(msg.target, uc.getRound());
                    if (recentDangerSectors.size() >= RECENT_DANGER_SECTOR_MAX) {
                        recentDangerSectors.removeFirst();
                    }
                    recentDangerSectors.addLast(new LocationRound(sector, uc.getRound()));
                    while (!recentDangerSectors.isEmpty() && uc.getRound() - recentDangerSectors.peekFirst().round > RECENT_DANGER_TIMEOUT) {
                        recentDangerSectors.removeFirst();
                    }
                    alertCount++;
                    return msg;
                } // no payload
            } else if (msgId == dc.MSG_ID_ENEMY_HQ_DESTROYED) {
                if (subscribeEnemyHq) {
                    Location loc = new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                            (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT);
                    deleteEnemyHq(loc);
                    return new EnemyHqDestroyedMessage(loc);
                } // no payload
            }
            recordDistances(b);
            b = uc.pollBroadcast();
        }
        return null;
    }

    private void recordDistances(BroadcastInfo b) {
        if (unitSpawnRounds.contains(b.getID())) {
            Location reportSector = c.getSector(b.getLocation());
            int roundDist = uc.getRound() - unitSpawnRounds.getVal(b.getID());
            int count = 1;
            int sumDist = roundDist;
            if (sectorDistReportCount.contains(reportSector)) {
                count += sectorDistReportCount.getVal(reportSector);
                sumDist += sectorSumDist.getVal(reportSector);
            }
            sectorDistReportCount.addReplace(reportSector, count);
            sectorSumDist.addReplace(reportSector, sumDist);
            int avg = sumDist / count;
            sectorAvgDist.addReplace(reportSector, avg);
//            c.loggerAlways.log("Estimated dist to %s is %d rounds", b.getLocation(), avg);
        }
    }

    public void resetEnemyHq() {
        enemyHqSize = 0;
    }

    public boolean isKnownEnemyHq(Location loc) {
        for (int i = 0; i < enemyHqSize; i++) {
            if (loc.equals(enemyHq[i])) {
                return true;
            }
        }
        return false;
    }

    public boolean addEnemyHq(Location loc) {
        boolean found = false;
        for (int i = 0; i < enemyHqSize; i++) {
            if (enemyHq[i] == null) {
                break;
            }
            if (enemyHq[i].equals(loc)) {
                found = true;
                break;
            }
        }
        if (!found) {
            enemyHq[enemyHqSize++] = loc;
            return true;
        }
        return false;
    }

    public void deleteEnemyHq(Location del) {
        Location[] prev = enemyHq;
        enemyHq = new Location[3];
        enemyHqSize = 0;
        for (int i = 0; i < 3; i++) {
            if (prev[i] == null) {
                return;
            }
            if (!prev[i].equals(del)) {
                enemyHq[enemyHqSize++] = prev[i];
            }
        }
    }

    public void sendEnemyHqDestroyedMessage(Location loc) {
        int encoded = dc.MSG_ID_MASK_ENEMY_HQ_DESTROYED;
        encoded |= (loc.x << dc.MASKER_LOC_X_SHIFT);
        encoded |= (loc.y << dc.MASKER_LOC_Y_SHIFT);
        uc.performAction(ActionType.BROADCAST, null, encoded);
        deleteEnemyHq(loc);
    }

    public RetrievePackageFailed parseGetPackageFailedMessage(int fullMsg) {
        return new RetrievePackageFailed(new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT));
    }

    public void sendGetPackageFailedMessage(Location paxLocation) {
        int encoded = dc.MSG_ID_GET_PACKAGES_FAILED;
        encoded |= (paxLocation.x << dc.MASKER_LOC_X_SHIFT);
        encoded |= (paxLocation.y << dc.MASKER_LOC_Y_SHIFT);
        uc.performAction(ActionType.BROADCAST, null, encoded);
    }

    public void sendSeekSymmetryCompleteMsg(SeekSymmetryComplete msg) {
        int encoded = dc.MSG_ID_MASK_SYMMETRIC_SEEKER_COMPLETE;
        encoded |= (msg.target.x << dc.MASKER_LOC_X_SHIFT);
        encoded |= (msg.target.y << dc.MASKER_LOC_Y_SHIFT);

        int symmetry = 0;
        if (msg.horizontalSymmetryPossible) {
            symmetry |= (1 << dc.SYMMETRY_HORIZONTAL);
        }
        if (msg.verticalSymmetryPossible) {
            symmetry |= (1 << dc.SYMMETRY_VERTICAL);
        }
        if (msg.rotationalSymmetryPossible) {
            symmetry |= (1 << dc.SYMMETRY_ROTATIONAL);
        }

        encoded |= (symmetry << dc.SYMMETRIC_SEEKER_SYMMETRY_MASK_SHIFT);

        encoded |= msg.status;
        uc.performAction(ActionType.BROADCAST, null, encoded);
    }

    public void sendSuppressorHeartbeat() {
        uc.performAction(ActionType.BROADCAST, null, 1);
    }

    public void sendSurveyCompleteMsg(SurveyComplete msg) {
        int encoded = dc.MSG_ID_MASK_SURVEY_FAILED;
        if (msg.status == dc.SURVEY_BAD) {
            c.logger.log("survey: bad");
            encoded = dc.MSG_ID_MASK_SURVEY_COMPLETE_BAD;
        } else if (msg.status == dc.SURVEY_GOOD) {
            c.logger.log("survey: good");
            encoded = dc.MSG_ID_MASK_SURVEY_COMPLETE_GOOD;
        } else if (msg.status == dc.SURVEY_EXCELLENT) {
            c.logger.log("survey: excellent");
            encoded = dc.MSG_ID_MASK_SURVEY_COMPLETE_EXCELLENT;
        }
        encoded |= (msg.target.x << dc.MASKER_LOC_X_SHIFT);
        encoded |= (msg.target.y << dc.MASKER_LOC_Y_SHIFT);
        encoded |= msg.expansionId;
        uc.performAction(ActionType.BROADCAST, null, encoded);
    }

    public void sendExpansionEstablishedMsg(ExpansionEstablishedMessage msg) {
        c.logger.log("expansion established: %s", msg.target);
        int encoded = dc.MSG_ID_MASK_EXPANSION_ESTABLISHED;
        encoded |= (msg.target.x << dc.MASKER_LOC_X_SHIFT);
        encoded |= (msg.target.y << dc.MASKER_LOC_Y_SHIFT);
        encoded |= msg.expansionId;
        uc.performAction(ActionType.BROADCAST, null, encoded);
    }

    public void sendExpansionMissedMsg(ExpansionMissedMessage msg) {
        c.logger.log("expansion missed: %s", msg.target);
        int encoded = dc.MSG_ID_MASK_EXPANSION_MISSED;
        encoded |= (msg.target.x << dc.MASKER_LOC_X_SHIFT);
        encoded |= (msg.target.y << dc.MASKER_LOC_Y_SHIFT);
        encoded |= msg.expansionId;
        uc.performAction(ActionType.BROADCAST, null, encoded);
    }

    public void sendDomeBuiltMsg(DomeBuiltNotification msg) {
        c.logger.log("witnessed dome built: %s", msg.target);
        int encoded = dc.MSG_ID_MASK_DOME_BUILT;
        encoded |= (msg.target.x << dc.MASKER_LOC_X_SHIFT);
        encoded |= (msg.target.y << dc.MASKER_LOC_Y_SHIFT);
        uc.performAction(ActionType.BROADCAST, null, encoded);
    }

    public DomeBuiltNotification parseDomeBuiltNotification(int fullMsg) {
        return new DomeBuiltNotification(new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT));
    }

    public void sendDomeDestroyedMsg(DomeDestroyedNotification msg) {
        c.logger.log("witnessed dome destroyed: %s", msg.target);
        int encoded = dc.MSG_ID_MASK_DOME_DESTROYED;
        encoded |= (msg.target.x << dc.MASKER_LOC_X_SHIFT);
        encoded |= (msg.target.y << dc.MASKER_LOC_Y_SHIFT);
        encoded |= msg.expansionId;
        uc.performAction(ActionType.BROADCAST, null, encoded);
    }

    public DomeDestroyedNotification parseDomeDestroyedNotification(int fullMsg) {
        return new DomeDestroyedNotification(new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT), fullMsg & dc.DOME_DESTROYED_EXPANSION_ID_MASKER);
    }

    public void trySendAlert(AlertMessage msg) {
        cleanAlert();
        for (Location loc: knownAlertLocations.getKeys()) {
            if (Vector2D.chebysevDistance(loc, msg.target) < 4) {
                c.logger.log("tried to alert at %s but previous alert is still valid", msg.target);
                return;
            }
        }
        c.logger.log("alert: %s ; enemy strength: %s", msg.target, msg.enemyStrength);
        int encoded = dc.MSG_ID_MASK_ALERT;
        encoded |= (msg.target.x << dc.MASKER_LOC_X_SHIFT);
        encoded |= (msg.target.y << dc.MASKER_LOC_Y_SHIFT);
        encoded |= msg.enemyStrength;
        knownAlertLocations.addReplace(msg.target, uc.getRound());
        uc.drawLineDebug(c.loc, msg.target, 255, 0, 0);
        uc.performAction(ActionType.BROADCAST, null, encoded);
    }

    public void cleanAlert() {
        for (Location loc: knownAlertLocations.getKeys()) {
            if (uc.getRound() - knownAlertLocations.getVal(loc) > 10) {
                knownAlertLocations.remove(loc);
            }
        }
    }

    public int getMsgId(int broadcasted) {
        if (broadcasted == dc.MSG_ID_SYMMETRIC_SEEKER_CMD) {
            return dc.MSG_ID_SYMMETRIC_SEEKER_CMD;
        } else if (broadcasted == dc.MSG_ID_ENEMY_HQ) {
            return dc.MSG_ID_ENEMY_HQ;
        } else if (broadcasted == dc.MSG_ID_GET_PACKAGES_CMD) {
            return dc.MSG_ID_GET_PACKAGES_CMD;
        } else if (broadcasted == dc.MSG_ID_DEFENSE_CMD) {
            return dc.MSG_ID_DEFENSE_CMD;
        } else if (broadcasted == dc.MSG_ID_SURVEY_CMD) {
            return dc.MSG_ID_SURVEY_CMD;
        } else if (broadcasted == dc.MSG_ID_EXPANSION) {
            return dc.MSG_ID_EXPANSION;
        } else if (broadcasted == dc.MSG_ID_BUILD_DOME_CMD) {
            return dc.MSG_ID_BUILD_DOME_CMD;
        } else if (broadcasted == dc.MSG_ID_INQUIRE_DOME) {
            return dc.MSG_ID_INQUIRE_DOME;
        } else if (broadcasted == dc.MSG_ID_BUILD_HYPER_JUMP_CMD) {
            return dc.MSG_ID_BUILD_HYPER_JUMP_CMD;
        } else if (broadcasted == dc.MSG_ID_SUPPRESSION_CMD) {
            return dc.MSG_ID_SUPPRESSION_CMD;
        } else if (broadcasted == dc.MSG_ID_PACKAGE_PRIORITY_MAP) {
            return dc.MSG_ID_PACKAGE_PRIORITY_MAP;
        } else if (broadcasted == dc.MSG_ID_SETTLEMENT_CMD) {
            return dc.MSG_ID_SETTLEMENT_CMD;
        } else if (broadcasted == dc.MSG_ID_BASE_HEARTBEAT) {
            return dc.MSG_ID_BASE_HEARTBEAT;
        } else if (broadcasted == dc.MSG_ID_NEW_SETTLEMENT) {
            return dc.MSG_ID_NEW_SETTLEMENT;
        } else if (broadcasted == dc.MSG_ID_SUPPRESSOR_HEARTBEAT) {
            return dc.MSG_ID_SUPPRESSOR_HEARTBEAT;
        } else if (broadcasted == dc.MSG_ID_WORLD_MAPPER_CMD) {
            return dc.MSG_ID_WORLD_MAPPER_CMD;
        } else if (broadcasted == dc.MSG_ID_WORLD_OBSTACLES) {
            return dc.MSG_ID_WORLD_OBSTACLES;
        } else if (broadcasted == dc.MSG_ID_PATH) {
            return dc.MSG_ID_PATH;
        } else if (broadcasted == dc.MSG_ID_HQ_SETUP_BROADCAST_MARKER) {
            return dc.MSG_ID_HQ_SETUP_BROADCAST_MARKER;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_SURVEY_COMPLETE_GOOD) {
            return dc.MSG_ID_SURVEY_COMPLETE_GOOD;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_SURVEY_COMPLETE_BAD) {
            return dc.MSG_ID_SURVEY_COMPLETE_BAD;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_SURVEY_FAILED) {
            return dc.MSG_ID_SURVEY_FAILED;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_SURVEY_COMPLETE_EXCELLENT) {
            return dc.MSG_ID_SURVEY_COMPLETE_EXCELLENT;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_SYMMETRIC_SEEKER_COMPLETE) {
            return dc.MSG_ID_SYMMETRIC_SEEKER_COMPLETE;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_ENEMY_HQ_DESTROYED) {
            return dc.MSG_ID_ENEMY_HQ_DESTROYED;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_EXPANSION_ESTABLISHED) {
            return dc.MSG_ID_EXPANSION_ESTABLISHED;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_ALERT) {
            return dc.MSG_ID_ALERT;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_DOME_BUILT) {
            return dc.MSG_ID_DOME_BUILT;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_DOME_DESTROYED) {
            return dc.MSG_ID_DOME_DESTROYED;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_EXPANSION_MISSED) {
            return dc.MSG_ID_EXPANSION_MISSED;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_GET_PACKAGES_FAILED) {
            return dc.MSG_ID_GET_PACKAGES_FAILED;
        }
        return 0;
    }

}
