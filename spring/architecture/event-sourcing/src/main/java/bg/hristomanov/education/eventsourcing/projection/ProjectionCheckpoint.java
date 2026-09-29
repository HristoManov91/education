package bg.hristomanov.education.eventsourcing.projection;

import jakarta.persistence.*;

@Entity
@Table(name = "es_projection_checkpoints")
public class ProjectionCheckpoint {

    @Id
    @Column(name = "projection_name", length = 100)
    private String projectionName;

    @Column(name = "last_global_position", nullable = false)
    private long lastGlobalPosition;

    protected ProjectionCheckpoint() {
    }

    public ProjectionCheckpoint(
            String projectionName,
            long lastGlobalPosition
    ) {
        this.projectionName = projectionName;
        this.lastGlobalPosition = lastGlobalPosition;
    }

    public void advanceTo(long globalPosition) {
        this.lastGlobalPosition = globalPosition;
    }

    public String getProjectionName() {
        return projectionName;
    }

    public long getLastGlobalPosition() {
        return lastGlobalPosition;
    }
}
