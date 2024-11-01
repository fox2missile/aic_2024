package kyuu.fast;

import aic2024.user.Location;

public class FastLocLocMap {
    private FastLocIntMap map;

    public FastLocLocMap() {
        map = new FastLocIntMap();
    }

    public void add(Location loc, Location val) {
        map.add(loc, toInt(val));
    }

    public void add(int x, int y, int xVal, int yVal) {
        map.add(x, y, toInt(xVal, yVal));
    }

    public void addReplace(Location loc, Location val) {
        remove(loc);
        add(loc, val);
    }

    public void remove(Location loc) {
        map.remove(loc);
    }

    public void remove(int x, int y) {
        map.remove(x, y);
    }

    public boolean contains(Location loc) {
        return map.contains(loc);
    }

    public boolean contains(int x, int y) {
        return map.contains(x, y);
    }

    public void clear() {
        map.clear();
    }

    public Location getVal(Location loc) {
        int res = map.getVal(loc);
        if (res == -1) {
            return null;
        }
        return toLoc(res);
    }

    public Location[] getKeys() {
        return map.getKeys();
    }

    private Location toLoc(int repr) {
        return new Location(repr % 60, repr / 60);
    }

    private int toInt(Location loc) {
        return (loc.y * 60) + loc.x;
    }

    private int toInt(int x, int y) {
        return (y * 60) + x;
    }
}
