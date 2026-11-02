package bg.hristomanov.education.ffi;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeCStringLengthTest {

    @Test
    void javaCallsCStrlenUsingAnExplicitNativeDowncall() {
        assumeTrue(NativeCStringLength.isSupported(),
                "The current platform does not expose C strlen with 64-bit size_t");

        assertThat(NativeCStringLength.utf8Length("Hello FFM")).isEqualTo(9L);
        assertThat(NativeCStringLength.utf8Length("")).isZero();
    }

    @Test
    void nativeStrlenCountsUtf8BytesRatherThanJavaUtf16CodeUnits() {
        assumeTrue(NativeCStringLength.isSupported(),
                "The current platform does not expose C strlen with 64-bit size_t");

        String text = "A😊";
        assertThat(text.length()).isEqualTo(3);
        assertThat(NativeCStringLength.utf8Length(text))
                .isEqualTo(text.getBytes(StandardCharsets.UTF_8).length)
                .isEqualTo(5L);
    }

    @Test
    void embeddedNullCannotSilentlyTruncateInput() {
        assertThatThrownBy(() -> NativeCStringLength.utf8Length("good\0bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NUL");
    }

    @Test
    void nullFailsBeforeTryingToCrossNativeBoundary() {
        assertThatThrownBy(() -> NativeCStringLength.utf8Length(null))
                .isInstanceOf(NullPointerException.class);
    }
}
