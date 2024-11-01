package kyuu.fast;

public class FastStack {
    private final int[] buffer;
    private int size;
    private final int maxSize;

    public FastStack(int maxSize) {
        this.maxSize = maxSize;
        buffer = new int[maxSize];
        size = 0;
    }

    public boolean push(int val) {
        if (size == maxSize) {
            return false;
        }
        buffer[size++] = val;
        return true;
    }

    public int pop() {
        return buffer[--size];
    }

    public boolean isEmpty() {
        return size == 0;
    }
}
