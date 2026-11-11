package bg.hristomanov.education.ffi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

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
        assertThat(NativeCStringLength.utf8LengthWithoutCacheForComparison(text))
                .isEqualTo(5L);
    }

    @Test
    void cachedAndUncachedPathsPreserveTheSameValueAndEmptyStringContract() {
        assumeTrue(NativeCStringLength.isSupported(),
                "Unsupported native strlen ABI on the current platform");
        for (String value : List.of("", "ASCII", "България", "A😊")) {
            assertThat(NativeCStringLength.utf8Length(value))
                    .isEqualTo(NativeCStringLength.utf8LengthWithoutCacheForComparison(value));
        }
    }

    @Test
    void cachedHandleIsCreatedOnlyOnceEvenUnderConcurrentCalls() throws Exception {
        assumeTrue(NativeCStringLength.isSupported(),
                "Unsupported native strlen ABI on the current platform");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Long>> futures = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                futures.add(executor.submit(() -> NativeCStringLength.utf8Length("concurrent")));
            }
            for (Future<Long> result : futures) {
                assertThat(result.get()).isEqualTo(10L);
            }
        }
        assertThat(NativeCStringLength.successfulCachedLinksForTesting()).isEqualTo(1);
    }

    @Test
    void embeddedNullCannotSilentlyTruncateInput() {
        assertThatThrownBy(() -> NativeCStringLength.utf8Length("good\0bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NUL");
        assertThatThrownBy(() ->
                NativeCStringLength.utf8LengthWithoutCacheForComparison("good\0bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullFailsBeforeTryingToCrossNativeBoundary() {
        assertThatThrownBy(() -> NativeCStringLength.utf8Length(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> NativeCStringLength.utf8LengthWithoutCacheForComparison(null))
                .isInstanceOf(NullPointerException.class);
    }
}
