package kyuu.fast;


import aic2024.user.Location;
import kyuu.C;
import kyuu.tasks.Task;

public class FastTaskQueue {

    private Task[] heap;
    private int size;

    public FastTaskQueue(int capacity) {
        heap = new Task[capacity];
        size = 0;
    }

    public void offer(Task key) {
        heap[size] = key;
        siftUp(size, key);
        size++;
    }

    public Task poll() {
        Task min = heap[0];
        heap[0] = heap[size - 1];
        size--;
        siftDown(0, heap[0]);
        return min;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private void siftUp(int k, Task x) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Task e = heap[parent];
            if (x.priority - e.priority >= 0)
                break;
            heap[k] = e;
            k = parent;
        }
        heap[k] = x;
    }

    private void siftDown(int k, Task x) {
        int half = size >>> 1;        // loop while a non-leaf
        while (k < half) {
            int child = (k << 1) + 1; // assume left child is least
            Task c = heap[child];
            int right = child + 1;
            if (right < size &&
                    (c.priority - heap[right].priority > 0))
                c = heap[child = right];
            if (x.priority - c.priority <= 0)
                break;
            heap[k] = c;
            k = child;
        }
        heap[k] = x;
    }

    public static void main(String[] args) {
        FastTaskQueue pq = new FastTaskQueue(10);
        class DummyTask extends Task {

            public DummyTask(C c, int prio) {
                super(c);
                priority = prio;
            }

            @Override
            public void run() {

            }
        };

        pq.offer(new DummyTask(null, 5));
        pq.offer(new DummyTask(null, 1));
        pq.offer(new DummyTask(null, 10));
        for (int i = 0; i < 2; i++) {
            Task current = pq.poll();
            System.out.printf("%d\n", current.priority);
        }
        pq.offer(new DummyTask(null, 4));
        pq.offer(new DummyTask(null, 12));
        pq.offer(new DummyTask(null, 41));
        pq.offer(new DummyTask(null, 15));
        pq.offer(new DummyTask(null, 34));

        while (!pq.isEmpty()) {
            Task current = pq.poll();
            System.out.printf("%d\n", current.priority);
        }
    }
}