package bg.hristomanov.education.patterns.structural.flyweight;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlyweightPatternTest {

    @Test
    void thousandsOfEventsCanShareTheSameImmutableTypeMetadata() {
        AuditEventTypeFactory factory = new AuditEventTypeFactory();
        AuditEventType loginType = factory.get("LOGIN", "INFO", "SECURITY");
        List<AuditEvent> events = new ArrayList<>();

        for (int index = 0; index < 10_000; index++) {
            events.add(
                    new AuditEvent(
                            "REQ-" + index,
                            "USER-" + index,
                            Instant.EPOCH.plusSeconds(index),
                            "User logged in",
                            factory.get("LOGIN", "INFO", "SECURITY")
                    )
            );
        }

        assertThat(events).hasSize(10_000);
        assertThat(events.get(0).type()).isSameAs(loginType);
        assertThat(events.get(9_999).type()).isSameAs(loginType);
        assertThat(factory.cachedTypeCount()).isEqualTo(1);
    }
}
