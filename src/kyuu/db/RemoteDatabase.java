package kyuu.db;

import aic2024.user.*;
import kyuu.C;
import kyuu.Vector2D;
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

    public int[] surveyorState;
    public Location[] surveySites;
    public int[] expansionStates;

    public RemoteDatabase(C c) {
        super(c);
        enemyHq = new Location[3];
        surveyorState = new int[c.allDirs.length];
        expansionStates = new int[c.allDirs.length];
    }

    public void sendHqInfo() {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_HQ);
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
        hqLocs[0] = c.loc;
        System.arraycopy(tempLocs, 0, hqLocs, 1, otherHqCount);
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


    public void sendSurveyCommand(int surveyor, Location targetLoc) {
        c.logger.log("send out surveyor %d to %s", surveyor, targetLoc);
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_SURVEY_CMD);
        uc.performAction(ActionType.BROADCAST, null, surveyor);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.x);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.y);
    }

    public void sendExpansionCommand(int workerId, Location targetLoc, int state) {
        uc.performAction(ActionType.BROADCAST, null, dc.MSG_ID_EXPANSION);
        uc.performAction(ActionType.BROADCAST, null, workerId);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.x);
        uc.performAction(ActionType.BROADCAST, null, targetLoc.y);
        uc.performAction(ActionType.BROADCAST, null, state);
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
                        return new SurveyCommand(new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage()));
                    } else {
                        uc.eraseBroadcastBuffer(dc.MSG_SIZE_SURVEY_CMD - 1); // -1 ID
                    }
                } else {
                    uc.eraseBroadcastBuffer(dc.MSG_SIZE_SURVEY_CMD); // ID wasn't read
                }
            } else if (msgId == dc.MSG_ID_EXPANSION) {
                if (subscribeExpansionCommand) {
                    if (c.id == uc.pollBroadcast().getMessage()) {
                        return new ExpansionCommand(new Location(uc.pollBroadcast().getMessage(), uc.pollBroadcast().getMessage()),
                                uc.pollBroadcast().getMessage());
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
            } else if (msgId == dc.MSG_ID_SURVEY_COMPLETE) {
                if (subscribeSurveyComplete) {
                    SurveyComplete s = new SurveyComplete(
                            new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                                    (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT),
                            fullMsg & dc.SURVEY_COMPLETE_STATUS_MASKER);
                    for (int i = 0; i < c.allDirs.length; i++) {
                        if (Vector2D.chebysevDistance(surveySites[i], s.target) < 3) {
                            c.logger.log("Received survey status: %s - %s", s.target, s.status == dc.SURVEY_GOOD ? "good" : "bad");
                            surveyorState[i] = s.status;
                        }
                    }
                    return s;
                } // no payload
            } else if (msgId == dc.MSG_ID_EXPANSION_ESTABLISHED) {
                if (subscribeExpansionEstablished) {
                    ExpansionEstablishedMessage ex = new ExpansionEstablishedMessage(
                            new Location((fullMsg & dc.MASKER_LOC_X) >> dc.MASKER_LOC_X_SHIFT,
                                    (fullMsg & dc.MASKER_LOC_Y) >> dc.MASKER_LOC_Y_SHIFT));
                    for (int i = 0; i < c.allDirs.length; i++) {
                        if (Vector2D.chebysevDistance(surveySites[i], ex.target) < 3) {
                            c.logger.log("Expansion established: %s", ex.target);
                            expansionStates[i] = dc.EXPANSION_STATE_ESTABLISHED;
                        }
                    }
                    return ex;
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
        c.logger.log("survey: %s", msg.status == dc.SURVEY_GOOD ? "good" : "bad");
        int encoded = dc.MSG_ID_MASK_SURVEY_COMPLETE;
        encoded |= (msg.target.x << dc.MASKER_LOC_X_SHIFT);
        encoded |= (msg.target.y << dc.MASKER_LOC_Y_SHIFT);
        encoded |= msg.status;
        uc.performAction(ActionType.BROADCAST, null, encoded);
    }

    public void sendExpansionEstablishedMsg(ExpansionEstablishedMessage msg) {
        c.logger.log("expansion established: %s", msg.target);
        int encoded = dc.MSG_ID_MASK_EXPANSION_ESTABLISHED;
        encoded |= (msg.target.x << dc.MASKER_LOC_X_SHIFT);
        encoded |= (msg.target.y << dc.MASKER_LOC_Y_SHIFT);
        uc.performAction(ActionType.BROADCAST, null, encoded);
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
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_SURVEY_COMPLETE) {
            return dc.MSG_ID_SURVEY_COMPLETE;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_SYMMETRIC_SEEKER_COMPLETE) {
            return dc.MSG_ID_SYMMETRIC_SEEKER_COMPLETE;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_ENEMY_HQ_DESTROYED) {
            return dc.MSG_ID_ENEMY_HQ_DESTROYED;
        } else if ((broadcasted & dc.MSG_ID_MASKER) == dc.MSG_ID_MASK_EXPANSION_ESTABLISHED) {
            return dc.MSG_ID_EXPANSION_ESTABLISHED;
        }
        return 0;
    }

}
