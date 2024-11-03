package kyuu.fast;

import aic2024.user.Location;

import java.util.Iterator;

public class FastLocIntMap {
    public StringBuilder keys;
    public int size;
    private int earliestRemoved;

    public FastLocIntMap() {
        keys = new StringBuilder();
    }

    private FastLocIntMap(FastLocIntMap other) {
        keys = new StringBuilder(other.keys.toString());
        size = other.size;
        earliestRemoved = other.earliestRemoved;
    }

    public FastLocIntMap copy() {
        return new FastLocIntMap(this);
    }

    private String locToStr(Location loc) {
        return "^" + (char) (loc.x) + (char) (loc.y);
    }

    private String locToStr(int x, int y) {
        return "^" + (char) (x) + (char) (y);
    }

    public void add(Location loc, int val) {
        String key = locToStr(loc);
        if (keys.indexOf(key) == -1) {
            keys.append(key + (char) (val + 0x100));
            size++;
        }
    }

    public void addReplace(Location loc, int val) {
        remove(loc);
        add(loc, val);
    }

    public void add(int x, int y, int val) {
        String key = "^" + (char) x + (char) y;
        if (keys.indexOf(key) == -1) {
            keys.append(key + (char) (val + 0x100));
            size++;
        }
    }

    public void remove(Location loc) {
        String key = locToStr(loc);
        int index;
        if ((index = keys.indexOf(key)) >= 0) {
            keys.delete(index, index + 4);
            size--;

            if (earliestRemoved > index)
                earliestRemoved = index;
        }
    }

    public void remove(int x, int y) {
        String key = "^" + (char) x + (char) y;
        int index;
        if ((index = keys.indexOf(key)) >= 0) {
            keys.delete(index, index + 4);
            size--;

            if (earliestRemoved > index)
                earliestRemoved = index;
        }
    }

    public boolean contains(Location loc) {
        return keys.indexOf(locToStr(loc)) >= 0;
    }

    public boolean contains(int x, int y) {
        return keys.indexOf("^" + (char) x + (char) y) >= 0;
    }

    public void clear() {
        size = 0;
        keys = new StringBuilder();
        earliestRemoved = 0;
    }

    public int getVal(Location loc) {
        String key = locToStr(loc);
        int idx = keys.indexOf(key);
        if (idx != -1) {
            return (int) keys.charAt(idx + 3) - 0x100;
        }

        return -1;
    }

    public int getVal(int x, int y) {
        String key = locToStr(x, y);
        int idx = keys.indexOf(key);
        if (idx != -1) {
            return (int) keys.charAt(idx + 3) - 0x100;
        }

        return -1;
    }

    public Location[] getKeys() {
        Location[] locs = new Location[size];
        for (int i = 1; i < keys.length(); i += 4) {
            locs[i / 4] = new Location((int) keys.charAt(i), (int) keys.charAt(i + 1));
        }
        return locs;
    }

    public Iterator<Location> getIterator() {
        return new Iterator<Location>() {
            int i = 1;
            @Override
            public boolean hasNext() {
                return i < keys.length();
            }

            @Override
            public Location next() {
                Location ret = new Location((int) keys.charAt(i), (int) keys.charAt(i + 1));
                i += 4;
                return ret;
            }
        };
    }

    public int[] getInts() {
        int[] ints = new int[size];
        for (int i = 3; i < keys.length(); i += 4) {
            ints[i / 4] = (int) keys.charAt(i) - 0x100;
        }
        return ints;
    }
}