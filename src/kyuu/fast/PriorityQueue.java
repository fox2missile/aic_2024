package kyuu.fast;

public class PriorityQueue<T extends Comparable<T>> {

    private T[] heap;
    private int size;

    public PriorityQueue(int capacity) {
        heap = (T[]) new Comparable[capacity];
        size = 0;
    }

    public void offer(T key) {
        heap[size] = key;
        siftUp(size, key);
        size++;
    }

    public T poll() {
        T min = heap[0];
        heap[0] = heap[size - 1];
        size--;
        siftDown(0, heap[0]);
        return min;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private void siftUp(int k, T x) {
        Comparable<? super T> key = (Comparable<? super T>) x;
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Object e = heap[parent];
            if (key.compareTo((T) e) >= 0)
                break;
                heap[k] = (T)e;
            k = parent;
        }
        heap[k] = (T)key;
    }

    private void siftDown(int k, T x) {
        Comparable<? super T> key = (Comparable<? super T>)x;
        int half = size >>> 1;        // loop while a non-leaf
        while (k < half) {
            int child = (k << 1) + 1; // assume left child is least
            Object c = heap[child];
            int right = child + 1;
            if (right < size &&
                ((Comparable<? super T>) c).compareTo((T) heap[right]) > 0)
                c = heap[child = right];
            if (key.compareTo((T) c) <= 0)
                break;
                heap[k] = (T)c;
            k = child;
        }
        heap[k] = (T)key;
    }

    public static void main(String[] args) {
        PriorityQueue<Integer> pq = new PriorityQueue<>(10);
        pq.offer(5);
        pq.offer(1);
        pq.offer(10);
        pq.offer(4);
        pq.offer(12);
        pq.offer(41);
        pq.offer(15);
        pq.offer(34);

        while (!pq.isEmpty()) {
            System.out.println(pq.poll()); // Output: 1 4 5 10
        }
    }
}