package bg.hristomanov.education.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Calls platform C strlen(const char*) through a cached FFM downcall handle.
 *
 * <p>Links lazily on the first valid operation: a missing symbol/unsupported
 * ABI does not make class initialization fail. Never invent a C ABI signature;
 * invalid descriptors can cause memory corruption or crash the JVM.
 */
public final class NativeCStringLength {

    private static final Object LINK_MONITOR = new Object();
    private static volatile MethodHandle cachedStrlen;
    private static final AtomicInteger SUCCESSFUL_LINKS = new AtomicInteger();

    private NativeCStringLength() {
    }

    public static boolean isSupported() {
        Linker linker = Linker.nativeLinker();
        MemoryLayout sizeT = linker.canonicalLayouts().get("size_t");
        return sizeT instanceof ValueLayout.OfLong
                && linker.defaultLookup().find("strlen").isPresent();
    }

    /**
     * Preferred production-style path: validate on every call but link the
     * native function at most once per classloader.
     */
    public static long utf8Length(String text) {
        validateText(text);
        return invokeStrlen(linkedStrlen(), text);
    }

    /**
     * Educational control: same business operation but the historical
     * per-invocation linker path. Avoid in normal application code.
     */
    public static long utf8LengthWithoutCacheForComparison(String text) {
        validateText(text);
        return invokeStrlen(createStrlenHandle(), text);
    }

    private static void validateText(String text) {
        Objects.requireNonNull(text, "text");
        if (text.indexOf('\0') >= 0) {
            // Native strlen stops at NUL; rejecting it preserves the contract.
            throw new IllegalArgumentException("Embedded NUL cannot be encoded as a C string");
        }
    }

    private static MethodHandle linkedStrlen() {
        MethodHandle handle = cachedStrlen;
        if (handle != null) {
            return handle;
        }
        synchronized (LINK_MONITOR) {
            handle = cachedStrlen;
            if (handle == null) {
                // Assignment only AFTER successful ABI checking / linking.
                handle = createStrlenHandle();
                cachedStrlen = handle;
                SUCCESSFUL_LINKS.incrementAndGet();
            }
        }
        return handle;
    }

    private static MethodHandle createStrlenHandle() {
        Linker linker = Linker.nativeLinker();
        MemoryLayout sizeT = linker.canonicalLayouts().get("size_t");
        if (!(sizeT instanceof ValueLayout.OfLong canonicalSizeT)) {
            throw new UnsupportedOperationException("Only 64-bit C size_t is demonstrated");
        }

        SymbolLookup lookup = linker.defaultLookup();
        MemorySegment symbol = lookup.find("strlen")
                .orElseThrow(() -> new UnsupportedOperationException("C strlen symbol unavailable"));

        // Validate the ABI against an explicitly supported platform shape.
        return linker.downcallHandle(symbol,
                FunctionDescriptor.of(canonicalSizeT, ValueLayout.ADDRESS));
    }

    private static long invokeStrlen(MethodHandle strlen, String text) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cString = arena.allocateFrom(text);
            try {
                return (long) strlen.invokeExact(cString);
            } catch (RuntimeException | Error exception) {
                throw exception;
            } catch (Throwable throwable) {
                throw new IllegalStateException("Native strlen downcall failed", throwable);
            }
        }
    }

    /**
     * Package-local observable contract for the JUnit concurrency test;
     * intentionally does not expose a privileged native MethodHandle.
     */
    static int successfulCachedLinksForTesting() {
        return SUCCESSFUL_LINKS.get();
    }
}
