package bg.hristomanov.education.algorithms.hashing.openaddressing;

/**
 * Стратегия за probe sequence (последователност от позиции при collision).
 */
public enum ProbeStrategy {

    LINEAR {
        @Override
        int index(int baseIndex, int keyHash, int attempt, int capacity) {
            return Math.floorMod(baseIndex + attempt, capacity);
        }
    },

    QUADRATIC {
        @Override
        int index(int baseIndex, int keyHash, int attempt, int capacity) {
            long offset = (long) attempt * attempt;
            return Math.floorMod((int) ((baseIndex + offset) % capacity), capacity);
        }
    },

    DOUBLE_HASH {
        @Override
        int index(int baseIndex, int keyHash, int attempt, int capacity) {
            int step = 1 + Math.floorMod(keyHash, capacity - 1);
            long offset = (long) attempt * step;
            return Math.floorMod((int) ((baseIndex + offset) % capacity), capacity);
        }
    };

    abstract int index(int baseIndex, int keyHash, int attempt, int capacity);
}
