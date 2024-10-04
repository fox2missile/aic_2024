package kyuu;

import aic2024.user.*;

public class Vector2D {
    public static Location rotate(Location loc, double angle) {
        double newX = loc.x * Math.cos(angle) - loc.y * Math.sin(angle);
        double newY = loc.x * Math.sin(angle) + loc.y * Math.cos(angle);
        return new Location((int)Math.floor(newX), (int)Math.floor(newY));
    }

    public static Location direction(Location current, Location destination) {
        return subtract(destination, current);
    }

    public static Location multiply(Location vec, int k) {
        return new Location(vec.x * k, vec.y * k);
    }

    public static Location add(Location vec1, Location vec2) {
        return new Location(vec1.x + vec2.x, vec1.y + vec2.y);
    }

    public static Location subtract(Location vec1, Location vec2) {
        return new Location(vec1.x - vec2.x, vec1.y - vec2.y);
    }

    public static Location add(Location src, Direction dir, int distance) {
        return new Location(src.x + (dir.dx * distance), src.y + (dir.dy * distance));
    }

    public static boolean isAligned(Location origin, Location point1, Location point2, double tolerance) {
        Location direction1 = Vector2D.direction(point1, origin);
        double angle1 = Math.atan2(direction1.y, direction1.x);
        Location direction2 = Vector2D.direction(point2, origin);
        double angle2 = Math.atan2(direction2.y, direction2.x);
        if (Math.abs(angle1 - angle2) < tolerance) {
            return true;
        }
        double angle1Reversed = -angle1;
        return Math.abs(angle1Reversed - angle2) < tolerance;
    }

    public static double distanceToLine(Location point, Location lineP1, Location lineP2) {
        double numerator = Math.abs((lineP1.x - point.x) * (lineP2.y - lineP1.y) - (lineP1.x - lineP2.x) * (point.y - lineP1.y));
        double denumerator = Math.sqrt(Math.pow(lineP2.x - lineP1.x, 2) + Math.pow(lineP2.y - lineP1.y, 2));
        return numerator / denumerator;
    }

    public static int manhattanDistance(Location loc1, Location loc2) {
        return Math.abs(loc1.x - loc2.x) + Math.abs(loc1.y - loc2.y);
    }

    public static int chebysevDistance(Location A, Location B) {
        return Math.max(Math.abs(A.x - B.x), Math.abs(A.y - B.y));
    }

    public static Direction getAlignment(Location src, Location target) {
        if (src.x == target.x) {
            return src.directionTo(target);
        }
        if (src.y == target.y) {
            return src.directionTo(target);
        }
        if (Math.abs(src.x - target.x) == Math.abs(src.y - target.y)) {
            return src.directionTo(target);
        }
        return null;
    }

    public static int getNearest(Location src, Location[] search, int searchSize) {
        int nearestIdx = -1;
        int distNearest = Integer.MAX_VALUE;
        for (int i = 0; i < searchSize; i++) {
            int dist = src.distanceSquared(search[i]);
            if (dist < distNearest) {
                distNearest = dist;
                nearestIdx = i;
            }
        }
        return nearestIdx;
    }

    public static int getNearestChebysev(Location src, Location[] search, int searchSize) {
        int nearestIdx = -1;
        int distNearest = Integer.MAX_VALUE;
        for (int i = 0; i < searchSize; i++) {
            int dist = chebysevDistance(src, search[i]);
            if (dist < distNearest) {
                distNearest = dist;
                nearestIdx = i;
            }
        }
        return nearestIdx;
    }

    public static int getNearest(Location src, AstronautInfo[] search, int searchSize) {
        int nearestIdx = -1;
        int distNearest = Integer.MAX_VALUE;
        for (int i = 0; i < searchSize; i++) {
            int dist = src.distanceSquared(search[i].getLocation());
            if (dist < distNearest) {
                distNearest = dist;
                nearestIdx = i;
            }
        }
        return nearestIdx;
    }
}
