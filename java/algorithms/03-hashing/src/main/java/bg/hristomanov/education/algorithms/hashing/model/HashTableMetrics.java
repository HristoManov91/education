package bg.hristomanov.education.algorithms.hashing.model;

public record HashTableMetrics(
        long collisions,
        long probes,
        long rehashes
) {
}
