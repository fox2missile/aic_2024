package kyuu.message;

public class PackagePriorityNotice implements Message {
    public int[] priorityBoostMap;


    public PackagePriorityNotice(int[] priorityBoostMap) {
        this.priorityBoostMap = priorityBoostMap;
    }
}
