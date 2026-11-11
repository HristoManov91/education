package bg.hristomanov.education.handles;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal versioned binary format, NOT a general-purpose serializer or a JDK
 * marshalling API. Explicitly allowed records are linked once at class init.
 *
 * <p>On wire: one unsigned schema version byte followed by exactly two or
 * three big-endian signed ints. There are NO class names from untrusted input.
 */
public final class VersionedPointMarshaller {

    public record PointV1(int x, int y) {
    }

    public record PointV2(int x, int y, int z) {
    }

    private static final Schema VERSION_ONE =
            link(PointV1.class, 1, List.of("x", "y"));
    private static final Schema VERSION_TWO =
            link(PointV2.class, 2, List.of("x", "y", "z"));

    private static final Map<Class<?>, Schema> BY_TYPE =
            Map.of(PointV1.class, VERSION_ONE, PointV2.class, VERSION_TWO);
    private static final Map<Integer, Schema> BY_VERSION =
            Map.of(1, VERSION_ONE, 2, VERSION_TWO);

    private VersionedPointMarshaller() {
    }

    public static byte[] encode(Object record) {
        Objects.requireNonNull(record, "record");
        Schema schema = BY_TYPE.get(record.getClass());
        if (schema == null) {
            throw new IllegalArgumentException("Type not on explicit marshalling allowlist");
        }
        ByteBuffer result = ByteBuffer.allocate(1 + Integer.BYTES * schema.getters().size())
                .order(ByteOrder.BIG_ENDIAN);
        result.put((byte) schema.version());
        try {
            for (MethodHandle getter : schema.getters()) {
                // Getter already adapted to (Object)int at linkage.
                int value = (int) getter.invokeExact(record);
                result.putInt(value);
            }
            return result.array();
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Linked record getter failed", throwable);
        }
    }

    public static Object decode(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        if (frame.length < 1) {
            throw new IllegalArgumentException("Missing schema version");
        }
        int version = Byte.toUnsignedInt(frame[0]);
        Schema schema = BY_VERSION.get(version);
        if (schema == null) {
            throw new IllegalArgumentException("Unknown schema version: " + version);
        }
        int count = schema.getters().size();
        if (frame.length != 1 + Integer.BYTES * count) {
            throw new IllegalArgumentException("Incorrect frame length for version " + version);
        }
        ByteBuffer input = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN);
        input.get(); // already validated schema version
        int[] values = new int[count];
        for (int index = 0; index < count; index++) {
            values[index] = input.getInt();
        }
        try {
            // A fixed (int[])Object constructor, linked via asSpreader once.
            return (Object) schema.constructor().invokeExact(values);
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Linked record constructor failed", throwable);
        }
    }

    public static PointV2 upgradeV1(PointV1 oldVersion) {
        Objects.requireNonNull(oldVersion, "oldVersion");
        // Schema migration policy is EXPLICIT; raw V1 frames do not become V2.
        return new PointV2(oldVersion.x(), oldVersion.y(), 0);
    }

    private static Schema link(Class<?> recordClass, int version, List<String> names) {
        RecordComponent[] components = recordClass.getRecordComponents();
        if (components == null || components.length != names.size()) {
            throw new IllegalArgumentException("Expected the explicitly allowed record shape");
        }

        Class<?>[] constructorParameters = new Class<?>[components.length];
        Arrays.fill(constructorParameters, int.class);
        List<MethodHandle> getters = new ArrayList<>();
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            for (int index = 0; index < components.length; index++) {
                RecordComponent component = components[index];
                if (component.getType() != int.class
                        || !component.getName().equals(names.get(index))) {
                    throw new IllegalArgumentException("Unexpected record schema shape");
                }
                // (PointV1|PointV2)int becomes (Object)int once.
                MethodHandle getter = lookup.findVirtual(
                        recordClass, component.getName(), MethodType.methodType(int.class));
                getters.add(getter.asType(MethodType.methodType(int.class, Object.class)));
            }
            // Constructor: (int, int[, int])Record becomes (int[])Object.
            MethodHandle constructor = lookup.findConstructor(
                    recordClass, MethodType.methodType(void.class, constructorParameters));
            MethodHandle spread = constructor.asSpreader(int[].class, components.length)
                    .asType(MethodType.methodType(Object.class, int[].class));
            return new Schema(version, List.copyOf(getters), spread);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Schema(int version, List<MethodHandle> getters, MethodHandle constructor) {
    }
}
