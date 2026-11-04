package bg.hristomanov.education.kubernetes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelloControllerTest {
    @Test
    void exposesOnlyAllowlistedRuntimeInformation() {
        var result = new HelloController("Welcome", "pod-123").hello();
        assertThat(result.message()).isEqualTo("Welcome");
        assertThat(result.pod()).isEqualTo("pod-123");
        assertThat(result.javaFeature()).isGreaterThanOrEqualTo(25);
        assertThat(result.toString()).doesNotContain("password", "token", "secret");
    }
}
