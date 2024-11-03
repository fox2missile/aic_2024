package kyuu.fast;

public class FastIntIntMap {
    public StringBuilder keyVals;
    public int size;

    public FastIntIntMap() {
        keyVals = new StringBuilder();
    }

    private String keyToStr(int key) {
        return "^" + (char) (key + 0x100);
    }

    private String keyValToStr(int key, int val) {
        return "^" + (char) (key + 0x100) + (char) (val + 0x100);
    }

    public void add(int key, int val) {
        // String keyTemp = keyToStr(key);
        String keyTemp = "^" + (char) (key + 0x100);
        if (keyVals.indexOf(keyTemp) == -1) {
            keyVals.append(keyTemp + (char) (val + 0x100));
            size++;
        }
    }

    public void remove(int key) {
        // String keyTemp = keyToStr(key);
        String keyTemp = "^" + (char) (key + 0x100);
        int index;
        if ((index = keyVals.indexOf(keyTemp)) >= 0) {
            keyVals.delete(index, index + 3);
            size--;
        }
    }

    public void addReplace(int key, int val) {
        remove(key);
        add(key, val);
    }

    public boolean contains(int key) {
        // return keyVals.indexOf(keyToStr(key)) >= 0;
        return keyVals.indexOf("^" + (char) (key + 0x100)) >= 0;
    }

    public void clear() {
        size = 0;
        keyVals = new StringBuilder();
    }

    public int getVal(int key) {
        String keyTemp = "^" + (char) (key + 0x100);
        // String keyTemp = keyToStr(key);
        int idx = keyVals.indexOf(keyTemp);
        if (idx != -1) {
            return (int) keyVals.charAt(idx + 2) - 0x100;
        }

        // Could not find element
        return -1;
    }

    public int[] getKeys() {
        int[] keys = new int[size];
        for (int i = 1; i < keyVals.length(); i += 3) {
            keys[i / 3] = (int) keyVals.charAt(i) - 0x100;
        }
        return keys;
    }

    public static void main(String[] args) {
        FastIntIntMap map = new FastIntIntMap();
        map.addReplace(1, 3);
        map.addReplace(3, 5);
        map.addReplace(3, 6);
        map.addReplace(6, 7);
        for (int i = 0; i <= 6; i++) {
            System.out.printf("i: %d | val: %d\n", i, map.getVal(i));
        }
    }
}