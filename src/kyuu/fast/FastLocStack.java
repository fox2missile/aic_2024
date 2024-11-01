package kyuu.fast;

import aic2024.user.Location;

public class FastLocStack {

    private final FastStack stack;

    public FastLocStack(int maxSize) {
        stack = new FastStack(maxSize * 2);
    }

    public boolean push(Location loc) {
        return stack.push(loc.x) && stack.push(loc.y);
    }

    public Location pop() {
        return new Location(stack.pop(), stack.pop());
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
