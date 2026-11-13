package bg.hristomanov.education.dop.amber;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bg.hristomanov.education.dop.amber.DeserializationBoundaryLab.GuardedAdult;
import bg.hristomanov.education.dop.amber.DeserializationBoundaryLab.LegacyAdult;
import bg.hristomanov.education.dop.amber.DeserializationBoundaryLab.RecordAdult;

/**
 * Използваме синтетично подменен stream с ЕДНО int instance поле.
 * Това е deterministic fixture за native Java serialization format,
 * а не serializer/general stream rewriting библиотека.
 *
 * Никога не десериализирай непроверен Java object stream в production:
 * readObject и ObjectInputFilter не са универсална sandbox защита.
 */
class DeserializationBoundaryLabTest {

    @Test
    void legacySerializableRestoresInvalidStateWithoutCallingConstructor() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new LegacyAdult(17));

        LegacyAdult valid = new LegacyAdult(19);
        int callsBefore = LegacyAdult.constructorCalls();
        byte[] maliciousBytes = replaceTrailingIntField(serialize(valid), 19, 17);

        LegacyAdult recovered = deserialize(maliciousBytes, null, LegacyAdult.class);
        assertEquals(17, recovered.age());
        assertEquals(callsBefore, LegacyAdult.constructorCalls(),
                "Native deserialization bypasses the serializable class constructor");
        assertNotSame(valid, recovered);
    }

    @Test
    void recordCanonicalConstructorIsCalledForValidNativeDeserialization() throws Exception {
        RecordAdult initial = new RecordAdult(19);
        int before = RecordAdult.constructorCalls();
        RecordAdult restored = deserialize(serialize(initial), null, RecordAdult.class);
        assertEquals(19, restored.age());
        assertEquals(before + 1, RecordAdult.constructorCalls());
        assertNotSame(initial, restored);
    }

    @Test
    void recordRejectsInvalidStreamByRevalidatingInCanonicalConstructor() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new RecordAdult(17));
        byte[] modified = replaceTrailingIntField(serialize(new RecordAdult(19)), 19, 17);

        InvalidObjectException thrown = assertThrows(InvalidObjectException.class,
                () -> deserialize(modified, null, RecordAdult.class));
        assertTrue(thrown.getCause() instanceof IllegalArgumentException,
                "The canonical constructor exception is wrapped by serialization");
    }

    @Test
    void ordinaryClassCanExplicitlyValidateRestoredStateInReadObject() throws Exception {
        byte[] modified = replaceTrailingIntField(serialize(new GuardedAdult(19)), 19, 17);

        InvalidObjectException thrown = assertThrows(InvalidObjectException.class,
                () -> deserialize(modified, null, GuardedAdult.class));
        assertTrue(thrown.getCause() instanceof IllegalArgumentException);
        GuardedAdult valid = deserialize(serialize(new GuardedAdult(20)), null, GuardedAdult.class);
        assertEquals(20, valid.age());
    }

    @Test
    void recordComponentCannotBeMutatedWithDeepReflection() throws Exception {
        RecordAdult adult = new RecordAdult(19);
        Field age = RecordAdult.class.getDeclaredField("age");
        age.setAccessible(true);
        assertThrows(IllegalAccessException.class, () -> age.setInt(adult, 17));
        assertEquals(19, adult.age());
    }

    @Test
    void objectInputFilterCanRejectAClassOutsideAnExplicitAllowlist() throws Exception {
        ObjectInputFilter onlyRecord = info -> {
            if (info.depth() > 5 || info.references() > 12 || info.streamBytes() > 4096) {
                return ObjectInputFilter.Status.REJECTED;
            }
            Class<?> candidate = info.serialClass();
            if (candidate == null) {
                return ObjectInputFilter.Status.UNDECIDED;
            }
            return candidate == RecordAdult.class
                    ? ObjectInputFilter.Status.ALLOWED
                    : ObjectInputFilter.Status.REJECTED;
        };

        RecordAdult adult = deserialize(serialize(new RecordAdult(25)), onlyRecord, RecordAdult.class);
        assertEquals(25, adult.age());

        assertThrows(InvalidClassException.class, () ->
                deserialize(serialize(new ArrayList<>(Arrays.asList("user"))),
                        onlyRecord, Object.class));
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static <T> T deserialize(byte[] bytes, ObjectInputFilter filter, Class<T> type)
            throws Exception {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            if (filter != null) {
                in.setObjectInputFilter(filter);
            }
            return type.cast(in.readObject());
        }
    }

    private static byte[] replaceTrailingIntField(byte[] original, int valid, int forged) {
        byte[] modified = original.clone();
        ByteBuffer tail = ByteBuffer.wrap(modified, modified.length - Integer.BYTES, Integer.BYTES);
        assertEquals(valid, tail.getInt(), "One-field lab object must end in its int state");
        tail.position(modified.length - Integer.BYTES);
        tail.putInt(forged);
        return modified;
    }
}
