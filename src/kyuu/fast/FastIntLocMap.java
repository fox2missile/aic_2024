package kyuu.fast;

import aic2024.user.Location;

public class FastIntLocMap {

    private FastIntIntMap map;

    public FastIntLocMap() {
        map = new FastIntIntMap();
    }

    public void add(int key, Location val) {
        map.add(key, toInt(val));
    }

    public void add(int key, int xVal, int yVal) {
        map.add(key, toInt(xVal, yVal));
    }

    public void addReplace(int key, Location val) {
        remove(key);
        add(key, val);
    }

    public void remove(int key) {
        map.remove(key);
    }

    public boolean contains(int key) {
        return map.contains(key);
    }

    public void clear() {
        map.clear();
    }

    public Location getVal(int key) {
        int res = map.getVal(key);
        if (res == -1) {
            return null;
        }
        return toLoc(res);
    }

    public int[] getKeys() {
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
