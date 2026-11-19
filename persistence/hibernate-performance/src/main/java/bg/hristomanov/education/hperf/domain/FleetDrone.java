package bg.hristomanov.education.hperf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@DiscriminatorValue("DRONE")
@Table(name = "vm28_drones")
public class FleetDrone extends TransportAsset {
    @Column(name = "flight_minutes", nullable = false)
    private int flightMinutes;

    protected FleetDrone() {}

    public FleetDrone(String code, int flightMinutes) {
        super(code);
        if (flightMinutes <= 0) throw new IllegalArgumentException("Flight time must be positive");
        this.flightMinutes = flightMinutes;
    }

    public int getFlightMinutes() { return flightMinutes; }
}
