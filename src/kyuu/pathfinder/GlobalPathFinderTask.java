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
    final int TERRAFORM_DISCOUNT = 2;
    final int DOME_DISCOUNT = 4;

    final Direction[] dirs;

    public final Location start;
    public final Location destination;
    final FastLocIntMap map;

    FastNodePriorityQueue open;
    FastLocIntMap seenG;
    FastLocSet closed;
    FastLocLocMap parents;

    Location[] path;


    public GlobalPathFinderTask(C c, Location start, Location destination, FastLocIntMap map) {
        super(c);
        this.dirs = c.allDirs;
        this.start = start;
        this.destination = destination;
        this.map = map.copy();


        open = new FastNodePriorityQueue(1000);
        closed = new FastLocSet();
        parents = new FastLocLocMap();
        seenG = new FastLocIntMap();
        int startG = 0;
        int startH = (int) (Math.sqrt(start.distanceSquared(destination)) * HEURISTIC_WEIGHT);
        open.offer(new Node(start, startG + startH, startG));
        seenG.add(start, 0);
    }

    @Override
    public void run() {
        while (!isFinished() && uc.getEnergyLeft() > 4000) {
            iterateOnce();
        }
    }

    @Override
    public boolean isFinished() {
        return path != null;
    }

    private void iterateOnce() {
        Node current = open.poll();
        while (current != null && closed.contains(current.loc)) {
            current = open.poll();
        }
        if (current == null) {
            return;
        }
//        c.logger.log("closing to %s", current);

        Location currentLoc = current.loc;
        uc.drawPointDebug(currentLoc, 255, 255, 255);

        if (currentLoc.distanceSquared(destination) <= 2) {
            reconstructPath(currentLoc);
            return;
        }

        int jump = 1;
        if (map.getVal(currentLoc) == dc.TILE_HYPER_JUMP) {
            jump = 3;
        }

        for (Direction dir: dirs) {
            for (int j = 0; j < jump; j++) {
                if (j > 0 && c.isDiagonalDir(dir)) {
                    break;
                }
                Location next = currentLoc.add(dir);
                int nextTile = map.getVal(next);
                if (uc.isOutOfMap(next) || nextTile == dc.TILE_OBSTACLE) {
                    continue;
                }

                int nextG;
                if (jump == 1) {
                    nextG = current.g + (c.isDiagonalDir(dir) ? DIAGONAL_COST : MANHATTAN_COST);
                    if (nextTile == dc.TILE_TERRAFORMED) {
                        nextG -= TERRAFORM_DISCOUNT;
                    } else if (nextTile == dc.TILE_DOMED) {
                        nextG -= DOME_DISCOUNT;
                    }
                } else {
                    nextG = current.g;
                }

                if (seenG.contains(next)) {
                    int seenGVal = seenG.getVal(next);
                    if (seenGVal <= nextG) {
                        continue;
                    }
                }

                parents.addReplace(next, currentLoc);
                int nextH = (int) (Math.sqrt(next.distanceSquared(destination)) * HEURISTIC_WEIGHT);
                Node nextNode = new Node(next, nextG + nextH, nextG);
//                c.logger.log("expand to %s", nextNode);
                open.offer(nextNode);
                seenG.addReplace(next, nextG);
            }
        }
    }

    private void reconstructPath(Location finish) {
        path = new Location[0];
        Location current = finish;
        while (current != null) {
            uc.drawPointDebug(current, 0, 127, 255);
            current = parents.getVal(current);
        }
    }

}
