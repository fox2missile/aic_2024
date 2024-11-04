package kyuu.fast;

import aic2024.user.Location;

public class FastLocInsertionSort {
    
    public static void sort(Location[] locs, int keys[], int n) {
        int key;
        Location loc;
        for (int i = 1; i < n; ++i) {
            key = keys[i];
            loc = locs[i];
            int j = i - 1;

            /* Move elements of arr[0..i-1], that are
               greater than key, to one position ahead
               of their current position */
            while (j >= 0 && keys[j] > key) {
                keys[j + 1] = keys[j];
                locs[j + 1] = locs[i];
                j = j - 1;
            }
            keys[j + 1] = key;
            locs[j + 1] = loc;
        }
    }

    public static void main(String[] args) {
        Location[] locs = new Location[] {
            new Location(3, 4),
            new Location(8, 9),
            new Location(10, 11),
            new Location(11, 12),
            new Location(2, 3),
            new Location(1, 2),
            new Location(15, 16),
            new Location(16, 17),
            new Location(14, 15),
            new Location(7, 8),
            new Location(12, 13),
            new Location(4, 5),
            new Location(5, 6),
            new Location(6, 7),
            new Location(9, 10),
            new Location(13, 14),
        };
        int[] keys = new int[] {
            locs[0].x,
            locs[1].x,
            locs[2].x,
            locs[3].x,
            locs[4].x,
            locs[5].x,
            locs[6].x,
            locs[7].x,
            locs[8].x,
            locs[9].x,
            locs[10].x,
            locs[11].x,
            locs[12].x,
            locs[13].x,
            locs[14].x,
            locs[15].x,
        };
        sort(locs, keys, 16);
        for (int i = 0; i < 16; i++) {
            System.out.printf("%s - %d\n", locs[i], keys[i]);
        }
    }

}
