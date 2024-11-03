package kyuu.fast;

import aic2024.user.Location;

public class Node implements Comparable<Node> {
    public final Location loc;
    public final int f;
    public final int g;

    public Node(Location loc, int f, int g) {
        this.loc = loc;
        this.f = f;
        this.g = g;
    }

    @Override
    public int compareTo(Node o) {
        return f - o.f;
    }

    @Override
    public String toString() {
        return String.format("<Node %s | f: %d | g: %d >", loc, f, g);
    }
}
