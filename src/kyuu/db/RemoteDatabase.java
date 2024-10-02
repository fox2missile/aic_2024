package kyuu.db;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
import kyuu.fast.FastLocIntMap;
import kyuu.message.*;

public class RemoteDatabase extends Database {

    public Location[] hqLocs;
    public int hqCount;
    public Location[] enemyHq;
    public int enemyHqSize;

    public boolean subscribeSeekSymmetryCommand = false;
    public boolean subscribeSeekSymmetryComplete = false;
    public boolean subscribeEnemyHq = false;
    public boolean subscribeDefenseCommand = false;
    public boolean subscribeSurveyCommand = false;
    public boolean subscribeExpansionCommand = false;
    public boolean subscribePackageRetrievalCommand = false;
    public boolean subscribeSurveyComplete = false;
    public boolean subscribeExpansionEstablished = false;
    public boolean subscribeAlert = true;

    public int[][] surveyorStates;
    public Location[][] expansionSites;
    public int[][] expansionStates;


    public int hqIdx;

    private final FastLocIntMap knownAlertLocations;

    public RemoteDatabase(C c) {
        super(c);
        enemyHq = new Location[3];
        hqIdx = -1;
        knownAlertLocations = new FastLocIntMap();
    }

    public void initExpansionData() {
        surveyorStates = new int[dc.EXPANSION_SIZE * dc.MAX_HQ][c.allDirs.length];
        expansionSites = new Location[dc.EXPANSION_SIZE * dc.MAX_HQ][c.allDirs.length];
        expansionStates = new int[dc.EXPANSION_SIZE * dc.MAX_HQ][c.allDirs.length];
    }

    public void sendHqInfo() {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_HQ);
        if (hqIdx == -1) {
            hqIdx = 0;
            while (uc.pollBroadcast() != null) {
                // NOTE: no one else should broadcast at round 0
                hqIdx++;
            }
        }
    }

    public void initHqLocs() {
        BroadcastInfo b = uc.pollBroadcast();
        int otherHqCount = 0;
        Location[] tempLocs = new Location[3];
        while (b != null && b.getMessage() == dc.MSG_ID_HQ) {
            tempLocs[otherHqCount++] = b.getLocation();
            b = uc.pollBroadcast();
        }
        hqCount = 1 + otherHqCount;
        hqLocs = new Location[hqCount];
        hqLocs[hqIdx] = c.loc;
        if (otherHqCount >= 1) {
            hqLocs[(hqIdx + 1) % hqCount] = tempLocs[0];
        }
        if (otherHqCount == 2) {
            hqLocs[(hqIdx + 2) % hqCount] = tempLocs[1];
        }
    }

    public void sendSymmetricSeekerCommand(int targetId, Location targetLoc) {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_SYMMETRIC_SEEKER_CMD);
        uc.performAction(ActionType.BROADCAST, null, targetId);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.x);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.y);
    }

    public void sendGetPackagesCommand(int targetId, Location targetLoc) {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_GET_PACKAGES_CMD);
        uc.performAction(ActionType.BROADCAST, null, targetId);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.x);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.y);
    }

    public void sendEnemyHqLocMessage(Location loc) {
        c.logger.log("broadcasting enemy hq: %s", loc);
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_ENEMY_HQ);
        uc.performAction(ActionType.BROADCAST, null, loc.x);
        uc.performAction(ActionType.BROADCAST, null, loc.y);
    }


    public void sendDefenseCommand(int defenderId, Location targetLoc) {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_DEFENSE_CMD);
        uc.performAction(ActionType.BROADCAST, null, defenderId);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.x);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.y);
    }

    public void sendSurveyCommand(int surveyor, Location targetLoc, int expansionId, Location[] sources) {
        c.logger.log("send out surveyor %d to %s [expansion id: %d]", surveyor, targetLoc, expansionId);
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_SURVEY_CMD);
        uc.performAction(ActionType.BROADCAST, null, surveyor);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.x);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.y);
        uc.performAction(ActionType.BROADCAST, null, expansionId);
        uc.performAction(ActionType.BROADCAST, null, sources.length);
        for (Location source : sources) {
            uc.performAction(ActionType.BROADCAST, null, source.x);
            uc.performAction(ActionType.BROADCAST, null, source.y);
        }
        // padding
        if (sources.length < dc.MAX_EXPANSION_DEPTH) {
            for (int i = sources.length; i < dc.MAX_EXPANSION_DEPTH; i++) {
                // won't be read
                uc.performAction(ActionType.BROADCAST, null, 0);
                uc.performAction(ActionType.BROADCAST, null, 0);
            }
        }
    }

    public void sendExpansionCommand(int workerId, Location targetLoc, int state, int expansionId, Location[] sources, Location possibleNext) {
        c.logger.log("send out expansion worker %d to %s [expansion id: %d]", workerId, targetLoc, expansionId);
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_EXPANSION);
        uc.performAction(ActionType.BROADCAST, null, workerId);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.x);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.y);
        uc.performAction(ActionType.BROADCAST, null, state);
        uc.performAction(ActionType.BROADCAST, null, expansionId);
        if (possibleNext != null) {
            uc.performAction(ActionType.BROADCAST, null, possibleNext.x);
            uc.performAction(ActionType.BROADCAST, null, possibleNext.y);
        } else {
            uc.performAction(ActionType.BROADCAST, null, dc.INVALID_LOCATION.x);
            uc.performAction(ActionType.BROADCAST, null, dc.INVALID_LOCATION.y);
        }
        uc.performAction(ActionType.BROADCAST, null, sources.length);
        for (Location source : sources) {
            uc.performAction(ActionType.BROADCAST, null, source.x);
            uc.performAction(ActionType.BROADCAST, null, source.y);
        }
        // padding
        if (sources.length < dc.MAX_EXPANSION_DEPTH) {
            for (int i = sources.length; i < dc.MAX_EXPANSION_DEPTH; i++) {
                // won't be read
                uc.performAction(ActionType.BROADCAST, null, 0);
                uc.performAction(ActionType.BROADCAST, null, 0);
            }
        }
    }

    private boolean isSubscribingExpansionId(int id) {
        int rangeBegin = hqIdx * dc.EXPANSION_SIZE;
        int rangeEnd = (hqIdx + 1) * dc.EXPANSION_SIZE;
        return rangeBegin <= id && id < rangeEnd;
    }


    public Message retrieveNextMessage() {
        BroadcastInfo b = uc.pollBroadcast();
        while (b != null) {
            int fullMsg = b.getMessage();
            int msgId = getMsgId(fullMsg);
            if (msgId == dc.MSG_ID_SYMMETRIC_SEEKER_CMD) {
                if (subscribeSeekSymmetryCommand) {
                    if (c.id == uc.pollBroadcast().getMessage() || uc.isStructure()) {
                        return new SeekSymmetryCommand(new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage()));
                    } else {
                        uc.eraseBroadcastBuffer(dc.MSG_SIZE_SYMMETRIC_SEEKER_CMD - 1); // -1 ID
                    }
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_SYMMETRIC_SEEKER_CMD); // ID wasn't read
                }
            } else if (msgId == dc.MSG_ID_GET_PACKAGES_CMD) {
                if (subscribePackageRetrievalCommand) {
                    if (c.id == uc.pollBroadcast().getMessage()) {
                        return new RetrievePackageCommand(new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage()));
                    } else {
                        uc.eraseBroadcastBuffer(dc.MSG_SIZE_GET_PACKAGES_CMD - 1); // -1 ID
                    }
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_GET_PACKAGES_CMD); // ID wasn't read
                }
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
                if (subscribeSeekSymmetryComplete) {
                    return new SeekSymmetryComplete(
                            new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                                    (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT),
                            fullMsg & dc.SYMMETRIC_SEEKER_COMPLETE_STATUS_MASKER);
                } // no payload
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
            } else if (msgId == dc.MSG_ID_ENEMY_HQ) {
                if (subscribeEnemyHq) {
                    Location loc = new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage());
                    addEnemyHq(loc);
                    return new EnemyHqMessage(loc);
                } else {
                    // skip payload
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_ENEMY_HQ);
                }
            } else if (msgId == dc.MSG_ID_ALERT) {
                if (subscribeAlert) {
                    Location loc = new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                            (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT);
                    int strength = fullMsg & dc.ALERT_STRENGTH_MASKER;
                    AlertMessage msg = new AlertMessage(loc, strength);
                    knownAlertLocations.add(msg.target, uc.getRound());
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
            b = uc.pollBroadcast();
        }
        return null;
    }

    public void resetEnemyHq() {
        enemyHqSize = 0;
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

    public void sendSeekSymmetryCompleteMsg(SeekSymmetryComplete msg) {
        int encoded = dc.MSG_ID_MASK_SYMMETRIC_SEEKER_COMPLETE;
        encoded |= (msg.target.x << dc.MASKER_LOC_X_SHIFT);
        encoded |= (msg.target.y << dc.MASKER_LOC_Y_SHIFT);
        encoded |= msg.status;
        uc.performAction(ActionType.BROADCAST, null, encoded);
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
        knownAlertLocations.add(msg.target, uc.getRound());
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
        }
        return 0;
    }

}
