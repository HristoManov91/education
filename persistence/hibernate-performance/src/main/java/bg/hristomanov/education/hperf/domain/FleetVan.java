package bg.hristomanov.education.hperf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@DiscriminatorValue("VAN")
@Table(name = "vm28_vans")
public class FleetVan extends TransportAsset {
    @Column(name = "payload_kg", nullable = false)
    private int payloadKg;

    protected FleetVan() {}

    public FleetVan(String code, int payloadKg) {
        super(code);
        if (payloadKg <= 0) throw new IllegalArgumentException("Payload must be positive");
        this.payloadKg = payloadKg;
    }

    public int getPayloadKg() { return payloadKg; }
}
