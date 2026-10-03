package bg.hristomanov.education.algorithms.linear.model;

import java.util.List;

public record SnapshotResult<T>(List<T> values, long logicalAccessSteps) {
}
