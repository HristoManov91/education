package bg.hristomanov.education.patterns.advanced.nullobject;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RegistrationServiceTest {
    @Test void optionalAuditCanBeDisabledWithoutNullChecks() {
        RegistrationService service = new RegistrationService(AuditSink.NoOp.INSTANCE);
        assertThat(service.register("C-1")).isEqualTo("registered:C-1");
    }

    @Test void realAuditReceivesBusinessEvent() {
        AuditSink.InMemory audit = new AuditSink.InMemory();
        RegistrationService service = new RegistrationService(audit);
        service.register("C-2");
        assertThat(audit.events()).containsExactly(new AuditSink.AuditEntry("REGISTER", "C-2"));
    }

    @Test void invalidRequestsDoNotGenerateAuditAndNullDependencyIsNotAllowed() {
        AuditSink.InMemory audit = new AuditSink.InMemory();
        RegistrationService service = new RegistrationService(audit);
        assertThatThrownBy(() -> service.register(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(audit.events()).isEmpty();
        assertThatThrownBy(() -> new RegistrationService(null)).isInstanceOf(NullPointerException.class);
    }
}
