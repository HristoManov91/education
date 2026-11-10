package bg.hristomanov.education.performance.gc;

/**
 * Повтаряем GC write-barrier workload: многократни промени на обектни референции.
 *
 * <p>Не е production benchmark: не твърдим, че времето на този цикъл изолира
 * единствено JEP 522, нито че еднакъв checksum означава еднакво GC поведение.
 */
public final class ReferenceUpdateWorkload {

    private ReferenceUpdateWorkload() {
    }

    public static long run(int nodeCount, int updates, int seed) {
        if (nodeCount < 2 || updates < 0) {
            throw new IllegalArgumentException("nodeCount >= 2 and updates >= 0 are required");
        }

        Node[] nodes = new Node[nodeCount];
        for (int index = 0; index < nodes.length; index++) {
            nodes[index] = new Node(index);
        }

        int state = seed;
        for (int iteration = 0; iteration < updates; iteration++) {
            state = Integer.rotateLeft(state * 1664525 + 1013904223, 5);
            int source = (state & Integer.MAX_VALUE) % nodeCount;
            int destination = ((state >>> 1) & Integer.MAX_VALUE) % nodeCount;
            // Реален heap reference store: G1 може да изпълнява write barrier.
            nodes[source].next = nodes[destination];
        }

        long checksum = 0;
        for (Node node : nodes) {
            checksum += node.next == null ? -1L : node.next.id;
        }
        return checksum;
    }

    public static void main(String[] arguments) {
        if (arguments.length != 3) {
            throw new IllegalArgumentException("Usage: <nodes> <referenceUpdates> <seed>");
        }
        long checksum = run(Integer.parseInt(arguments[0]),
                Integer.parseInt(arguments[1]), Integer.parseInt(arguments[2]));
        System.out.println("CHECKSUM=" + checksum);
    }

    private static final class Node {
        private final int id;
        private Node next;

        private Node(int id) {
            this.id = id;
        }
    }
}
