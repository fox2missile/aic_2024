package kyuu.fast;

import aic2024.user.Location;

import java.util.Iterator;

public class CircularLocBuffer {
    final Location[] buffer;
    final int capacity;

    int front;
    int rear;
    boolean full;

    public CircularLocBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new Location[capacity];
        front = -1;
        rear = 0;
        full = false;
    }

    public boolean isEmpty() {
        return front == -1;
    }

    public void pushBack(Location loc) {
        buffer[rear] = loc;
        if (isEmpty()) {
            front = 0;
        }
        if (full) {
            front++;
            if (front >= capacity) {
                front = 0;
            }
        }
        if (rear + 1 >= capacity) {
            rear = 0;
            full = true;
        } else {
            rear++;
        }
    }

    public boolean contains(Location loc) {
        for (Iterator<Location> it = getBackIterator(); it.hasNext(); ) {
            Location check = it.next();
            if (check.equals(loc)) {
                return true;
            }
        }
        return false;
    }

    public Iterator<Location> getBackIterator() {
        return new Iterator<Location>() {

            int next = rear > 0 ? rear - 1 : capacity - 1;
            boolean completed = false;

            @Override
            public boolean hasNext() {
                return !isEmpty() && !completed;
            }

            @Override
            public Location next() {
                if (next == front) {
                    completed = true;
                }

                Location ret = buffer[next];
                next--;

                if (next < 0 && full) {
                    next = capacity - 1;
                }

                return ret;
            }
        };
    }

    public static void main(String[] args) {
        CircularLocBuffer buff = new CircularLocBuffer(5);
        buff.pushBack(new Location(0, 0));
        buff.pushBack(new Location(1, 2));
        buff.pushBack(new Location(2, 3));
        buff.pushBack(new Location(3, 4));
        buff.pushBack(new Location(4, 5));
        buff.pushBack(new Location(5, 6));;
        buff.pushBack(new Location(7, 8));
        buff.pushBack(new Location(9, 10));

        for (Iterator<Location> it = buff.getBackIterator(); it.hasNext(); ) {
            System.out.println(it.next());
        }
    }


}
