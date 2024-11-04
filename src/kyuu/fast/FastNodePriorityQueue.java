package kyuu.fast;

import aic2024.user.Location;

public class FastNodePriorityQueue {

    private Node[] heap;
    private int size;

    public FastNodePriorityQueue(int capacity) {
        heap = new Node[capacity];
        size = 0;
    }

    public void offer(Node key) {
        heap[size] = key;
        siftUp(size, key);
        size++;
    }

    public void clear() {
        size = 0;
    }

    public Node poll() {
        Node min = heap[0];
        heap[0] = heap[size - 1];
        size--;
        siftDown(0, heap[0]);
        return min;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private void siftUp(int k, Node x) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Node e = heap[parent];
            if (x.f - e.f >= 0)
                break;
            heap[k] = e;
            k = parent;
        }
        heap[k] = x;
    }

    private void siftDown(int k, Node x) {
        int half = size >>> 1;        // loop while a non-leaf
        while (k < half) {
            int child = (k << 1) + 1; // assume left child is least
            Node c = heap[child];
            int right = child + 1;
            if (right < size &&
                    (c.f - heap[right].f > 0))
                c = heap[child = right];
            if (x.f - c.f <= 0)
                break;
            heap[k] = c;
            k = child;
        }
        heap[k] = x;
    }

    public static void main(String[] args) {
        FastNodePriorityQueue pq = new FastNodePriorityQueue(10);
        pq.offer(new Node(new Location(5, 5), 5, 5));
        pq.offer(new Node(new Location(1, 1), 1, 1));
        pq.offer(new Node(new Location(10, 10), 10, 10));
        for (int i = 0; i < 2; i++) {
            Node current = pq.poll();
            System.out.printf("%s | %d\n", current.loc, current.f); // Output: 1 4 5 10
        }
        pq.offer(new Node(new Location(4, 4), 4, 4));
        pq.offer(new Node(new Location(12, 12), 12, 12));
        pq.offer(new Node(new Location(41, 41), 41, 41));
        pq.offer(new Node(new Location(15, 15), 15, 15));
        pq.offer(new Node(new Location(34, 34), 34, 34));

        while (!pq.isEmpty()) {
            Node current = pq.poll();
            System.out.printf("%s | %d\n", current.loc, current.f); // Output: 1 4 5 10
        }
    }
}
