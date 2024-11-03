package kyuu.fast;

import aic2024.user.Location;

import java.util.Iterator;

public class FastLocLocSet {
    public StringBuilder keys;
    public int size;

    public FastLocLocSet() {
        keys = new StringBuilder();
        size = 0;
    }

    public int size() {
        return size;
    }

    private String locLocToStr(Location loc1, Location loc2) {
        return "^" + (char) (loc1.x) + (char) (loc1.y) + (char) (loc2.x) + (char) (loc2.y);
    }

    public void add(Location loc1, Location loc2) {
        String key = locLocToStr(loc1, loc2);
        if (keys.indexOf(key) == -1) {
            keys.append(key);
            size++;
        }
    }

    public void remove(Location loc1, Location loc2) {
        String key = locLocToStr(loc1, loc2);
        // String key = locToStr(loc);
        int index;
        if ((index = keys.indexOf(key)) >= 0) {
            keys.delete(index, index + 5);
            size--;
        }
    }

    public boolean contains(Location loc1, Location loc2) {
        // return keys.indexOf(locToStr(loc)) >= 0;
        return keys.indexOf(locLocToStr(loc1, loc2)) >= 0;
    }

    public void clear() {
        size = 0;
        keys = new StringBuilder();
    }

    public Iterator<Location[]> getIterator() {
        return new Iterator<Location[]>() {
            int i = 1;
            @Override
            public boolean hasNext() {
                return i < keys.length();
            }

            @Override
            public Location[] next() {
                Location ret1 = new Location((int) keys.charAt(i), (int) keys.charAt(i + 1));
                Location ret2 = new Location((int) keys.charAt(i + 2), (int) keys.charAt(i + 3));
                i += 5;
                return new Location[]{ret1, ret2};
            }
        };
    }

    public static void main(String[] args) {
        FastLocLocSet set = new FastLocLocSet();
        set.add(new Location(1, 3), new Location(4, 5));
        set.add(new Location(14, 31), new Location(78, 11));
        set.add(new Location(67, 28), new Location(16, 51));
        System.out.println(set.contains(new Location(1, 3), new Location(4, 5)));
        System.out.println(set.contains(new Location(14, 31), new Location(78, 11)));
        System.out.println(set.contains(new Location(67, 28), new Location(16, 51)));
        System.out.println(set.contains(new Location(1, 4), new Location(4, 5)));
        System.out.println(set.contains(new Location(14, 32), new Location(78, 11)));
        System.out.println(set.contains(new Location(67, 28), new Location(16, 52)));
        for (Iterator<Location[]> it = set.getIterator(); it.hasNext(); ) {
            Location[] locPair = it.next();
            System.out.printf("%s - %s\n", locPair[0], locPair[1]);
        }
    }
}
