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

/**
 * Calls the platform C library's strlen(const char*) through a downcall handle.
 *
 * <p>Educational example for 64-bit native ABIs. An incorrect FunctionDescriptor
 * can crash the process, so ABI constraints are checked before linking.
 */
public final class NativeCStringLength {

    private NativeCStringLength() {
    }

    public static boolean isSupported() {
        Linker linker = Linker.nativeLinker();
        MemoryLayout sizeT = linker.canonicalLayouts().get("size_t");
        return sizeT != null
                && sizeT.byteSize() == Long.BYTES
                && linker.defaultLookup().find("strlen").isPresent();
    }

    public static long utf8Length(String text) {
        Objects.requireNonNull(text, "text");
        // C strlen stops at the first NUL, unlike Java String.length().
        // Reject embedded NUL to avoid a silent semantic truncation.
        if (text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Embedded NUL cannot be encoded as a C string");
        }

        Linker linker = Linker.nativeLinker();
        MemoryLayout sizeT = linker.canonicalLayouts().get("size_t");
        if (sizeT == null || sizeT.byteSize() != Long.BYTES) {
            throw new UnsupportedOperationException("Only 64-bit C size_t is demonstrated");
        }
        SymbolLookup lookup = linker.defaultLookup();
        MemorySegment symbol = lookup.find("strlen")
                .orElseThrow(() -> new UnsupportedOperationException("C strlen symbol unavailable"));

        // On a supported 64-bit ABI: size_t strlen(const char *value).
        MethodHandle strlen = linker.downcallHandle(symbol,
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

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
}
