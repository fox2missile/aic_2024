package kyuu.pathfinder;

import aic2024.user.Direction;
import aic2024.user.Location;
import kyuu.C;
import kyuu.fast.*;
import kyuu.tasks.Task;

public class GlobalPathFinderTask extends Task {


    final int MANHATTAN_COST = 100;
    final int DIAGONAL_COST = 140;
    final int HEURISTIC_WEIGHT = 100;
    final int TERRAFORM_DISCOUNT = 20;
    final int DOME_DISCOUNT = 40;
    final int MAX_ITERATION = 2000;

    final Direction[] dirs;

    public final Location start;
    public final Location[] alternateStarts;
    public final Location destination;
    final FastLocIntMap map;

    FastNodePriorityQueue open;
    FastLocIntMap minG;
    FastLocSet closed;
    FastLocLocMap parents;

    Location[] sortLocBuffer;
    int[] sortLocBufferKeys;
    

    public int extraPriority;

    boolean pathReported;
    int fullMappedCount;
    int iterationLeft;


    public GlobalPathFinderTask(C c, Location start, Location[] alternateStarts, Location destination, FastLocIntMap map, int extraPriority) {
        super(c);
        this.dirs = c.allDirs;
        this.start = start;
        this.alternateStarts = alternateStarts;
        this.destination = destination;
        this.map = map.copy();
        this.extraPriority = extraPriority;

        map.addReplace(start, dc.TILE_LAND);
        pathReported = false;
        open = new FastNodePriorityQueue(500);
        closed = new FastLocSet();
        parents = new FastLocLocMap();
        minG = new FastLocIntMap();
        int startG = 0;
        int startH = (int) (Math.sqrt(destination.distanceSquared(start)) * HEURISTIC_WEIGHT);
        priority = ((uc.getRound() * HEURISTIC_WEIGHT * 4) / 3) + startH - (extraPriority * HEURISTIC_WEIGHT * 10);
        open.offer(new Node(destination, startG + startH, startG));
        minG.add(destination, 0);
        fullMappedCount = 0;
        iterationLeft = MAX_ITERATION;

        // buffers
        sortLocBuffer = new Location[16];
        sortLocBufferKeys = new int[16];
    }

    @Override
    public void run() {
        while (iterationLeft > 0 && uc.getEnergyLeft() > 4000 && c.currentRound == uc.getRound()) {
            iterateOnce();
            progress++;
            iterationLeft--;
        }
    }

    @Override
    public boolean isFinished() {
        return iterationLeft <= 0;
    }

    private void iterateOnce() {
        if (open.isEmpty()) {
            iterationLeft = 0;
            return;
        }
        Node current = open.poll();
        while (current != null && closed.contains(current.loc) && !open.isEmpty()) {
            current = open.poll();
        }
        if (current == null) {
            iterationLeft = 0;
            return;
        }
//        c.logger.log("closing to %s", current);

        Location currentLoc = current.loc;

        if (fullMappedCount == 0) {
            uc.drawPointDebug(currentLoc, 255, 255, 255);
        } else {
            uc.drawPointDebug(currentLoc, 192, 192, 192);
        }

        if (currentLoc.distanceSquared(start) <= 2) {
            fullMappedCount++;
            iterationLeft = 20;
            // Find the remaining alternate start point in limited time.
            // Each one found, granted extension time.
            // Even if all alternate start found, keep iterating to find alternate routes in case of congestion.
            closed.add(currentLoc);
            return;
        }

        // todo: work with hyper jump in reversed path finding
        if (map.getVal(currentLoc) % dc.TILE_HYPER_JUMP_LANDING == 0) {
            Location next = currentLoc;
            for (Direction dir: c.fourDirs) {
                for (int j = 0; j < 3; j++) {
                    next = next.add(dir);
                    if (map.getVal(next) % dc.TILE_HYPER_JUMP != 0) {
                        continue;
                    }
                    int nextG = current.g;

                    /* todo: duplicated code with next section */
                    if (minG.contains(next)) {
                        int minGVal = minG.getVal(next);
                        if (minGVal <= nextG) {
                            continue;
                        }
                    }

                    parents.addReplace(next, currentLoc);
                    int nextH = (int) (Math.sqrt(next.distanceSquared(start)) * HEURISTIC_WEIGHT);
                    Node nextNode = new Node(next, nextG + nextH, nextG);
//                c.logger.log("expand to %s", nextNode);
                    open.offer(nextNode);
                    minG.addReplace(next, nextG);
                }
            }
        }

        for (Direction dir: dirs) {
            Location next = currentLoc.add(dir);
            int nextTile = map.getVal(next);
            if (uc.isOutOfMap(next) || nextTile == dc.TILE_OBSTACLE) {
                continue;
            }

            int nextG = current.g + (c.isDiagonalDir(dir) ? DIAGONAL_COST : MANHATTAN_COST);
            if (nextTile % dc.TILE_TERRAFORMED == 0) {
                nextG -= TERRAFORM_DISCOUNT;
            } else if (nextTile % dc.TILE_DOMED == 0) {
                nextG -= DOME_DISCOUNT;
            }

            /* todo: duplicated code with previous section */

            if (minG.contains(next)) {
                int minGVal = minG.getVal(next);
                if (minGVal <= nextG) {
                    continue;
                }
            }

            parents.addReplace(next, currentLoc);
            int nextH = (int) (Math.sqrt(next.distanceSquared(start)) * HEURISTIC_WEIGHT);
            Node nextNode = new Node(next, nextG + nextH, nextG);
//                c.logger.log("expand to %s", nextNode);
            open.offer(nextNode);
            minG.addReplace(next, nextG);
        }

        closed.add(currentLoc);
    }

    // private void reconstructPath() {
    //     int pathIdx = ldb.allocatePathBuffer(destination, start);
    //     Location[] saveBuffer = ldb.knownPaths[pathIdx];
    //     saveBuffer[0] = destination;
    //     int length = 1;
    //     Location current = finalLoc;
    //     while (current != null) {
    //         saveBuffer[length++] = current;
    //         // todo: delete debug
    //         uc.drawPointDebug(current, 0, 127, 255);
    //         current = parents.getVal(current);
    //     }
    //     ldb.knownPathsLength[pathIdx] = length;
    //     pathReported = true;
    // }

    public boolean validStart(Location startPoint) {
        return closed.contains(startPoint);
    }

    public int constructFullPath(Location startPoint, Location[] out) {
        Location current = startPoint;
        int length = 0;
        while (!current.equals(destination)) {
            current = parents.getVal(current);
            out[length++] = current;
        }
        return length;
    }

    public int getNextCandidates(Location startPoint, Location[] out) {
        int length = 0;
        int currentG = minG.getVal(startPoint);

        for (Direction dir: c.allDirs) {
            Location check = startPoint.add(dir);
            if (!minG.contains(check)) {
                continue;
            }
            int checkG = minG.getVal(check);
            if (checkG < currentG) {
                sortLocBuffer[length] = check;
                sortLocBufferKeys[length] = checkG;
                length++;
            }
        }

        if (map.getVal(startPoint) % dc.TILE_HYPER_JUMP == 0) {
            
        }

        return length;
    }
}
