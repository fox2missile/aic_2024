package kyuu.fast;

import aic2024.user.Location;

public class FastPriorityQueue<E> {

    private E[] heap;
    private int[] keys;
    private int size;

    public FastPriorityQueue(int capacity) {
        heap = (E[]) new Object[capacity];
        keys = new int[capacity];
        size = 0;
    }

    public void offer(E obj, int key) {
        heap[size] = obj;
        keys[size] = key;
        siftUp(size, obj, key);
        size++;
    }

    public E poll() {
        E min = heap[0];
        heap[0] = heap[size - 1];
        keys[0] = keys[size - 1];
        size--;
        siftDown(0, heap[0], keys[0]);
        return min;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private void siftUp(int k, E x, int key) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            E e = heap[parent];
            if (key - keys[parent] >= 0)
                break;
            heap[k] = e;
            keys[k] = keys[parent];
            k = parent;
        }
        heap[k] = x;
        keys[k] = key;
    }

    private void siftDown(int k, E x, int key) {
        int half = size >>> 1;        // loop while a non-leaf
        while (k < half) {
            int child = (k << 1) + 1; // assume left child is least
            E c = heap[child];
            int kk = keys[child];
            int right = child + 1;
            if (right < size && (kk - keys[right] > 0)) {
                c = heap[child = right];
                kk = keys[right];
            }
            if (key - kk <= 0)
                break;
            heap[k] = c;
            keys[k] = kk;
            k = child;
        }
        heap[k] = x;
        keys[k] = key;
    }

    public static void main(String[] args) {
        FastPriorityQueue<Location> pq = new FastPriorityQueue<>(10);
        pq.offer(new Location(5, 5), 5);
        pq.offer(new Location(1, 1), 1);
        pq.offer(new Location(10, 10), 10);
        for (int i = 0; i < 2; i++) {
            Location current = pq.poll();
            System.out.printf("%s\n", current); // Output: 1 4 5 10
        }
        pq.offer(new Location(4, 4), 4);
        pq.offer(new Location(12, 12), 12);
        pq.offer(new Location(41, 41), 41);
        pq.offer(new Location(15, 15), 15);
        pq.offer(new Location(34, 34), 34);

        while (!pq.isEmpty()) {
            Location current = pq.poll();
            System.out.printf("%s\n", current); // Output: 1 4 5 10
        }
    }
}
