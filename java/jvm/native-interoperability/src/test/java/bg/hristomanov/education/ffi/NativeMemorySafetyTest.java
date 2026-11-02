package bg.hristomanov.education.ffi;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativeMemorySafetyTest {

    @Test
    void aConfinedArenaSupportsExplicitLayoutAndBoundedWrites() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(ValueLayout.JAVA_INT);
            value.set(ValueLayout.JAVA_INT, 0, 42);
            assertThat(value.get(ValueLayout.JAVA_INT, 0)).isEqualTo(42);
            assertThat(value.isNative()).isTrue();

            assertThatThrownBy(() -> value.get(ValueLayout.JAVA_INT, 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Test
    void usingAClosedArenaSegmentFailsRatherThanAccessingFreedMemory() {
        MemorySegment stale;
        try (Arena arena = Arena.ofConfined()) {
            stale = arena.allocate(ValueLayout.JAVA_LONG);
            stale.set(ValueLayout.JAVA_LONG, 0, 99L);
        }
        assertThatThrownBy(() -> stale.get(ValueLayout.JAVA_LONG, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confinedSegmentsRejectReadsFromAnotherThread() throws Exception {
        try (Arena arena = Arena.ofConfined();
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            MemorySegment value = arena.allocate(ValueLayout.JAVA_INT);
            value.set(ValueLayout.JAVA_INT, 0, 8);

            Future<Throwable> result = executor.submit(() -> {
                try {
                    value.get(ValueLayout.JAVA_INT, 0);
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            });
            assertThat(result.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(WrongThreadException.class);
        }
    }

    @Test
    void sharedArenaAllowsCrossThreadAccessWithinItsLifetime() throws Exception {
        try (Arena arena = Arena.ofShared();
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            MemorySegment value = arena.allocate(ValueLayout.JAVA_LONG);
            value.set(ValueLayout.JAVA_LONG, 0, 123L);

            Future<Long> result = executor.submit(
                    () -> value.get(ValueLayout.JAVA_LONG, 0));
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(123L);
        }
    }

    @Test
    void businessArithmeticHasTheSameCorrectnessWithOffHeapMemory() {
        assertThat(NativeIntBuffer.addExactly(10, 32)).isEqualTo(42);
        assertThatThrownBy(() -> NativeIntBuffer.addExactly(Integer.MAX_VALUE, 1))
                .isInstanceOf(ArithmeticException.class);
    }
}
