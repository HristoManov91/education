package bg.hristomanov.education.patterns.structural.bridge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BridgePatternTest {

    @Test
    void alertTypeAndTransportCanVaryIndependently() {
        Alert operationalEmail = new OperationalAlert(new EmailAlertTransport());
        Alert operationalSlack = new OperationalAlert(new SlackAlertTransport());
        Alert securityEmail = new SecurityAlert(new EmailAlertTransport());

        assertThat(operationalEmail.notify("ops@example.com", "DB slow"))
                .startsWith("EMAIL|")
                .contains("Operational alert");

        assertThat(operationalSlack.notify("#ops", "DB slow"))
                .startsWith("SLACK|")
                .contains("Operational alert");

        assertThat(securityEmail.notify("sec@example.com", "Suspicious login"))
                .startsWith("EMAIL|")
                .contains("SECURITY")
                .contains("[HIGH]");
    }
}
