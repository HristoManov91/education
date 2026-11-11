package bg.hristomanov.education.handles;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VersionedPointMarshallerTest {

    @Test
    void v1RoundTripHasDocumentedBigEndianWireContract() {
        VersionedPointMarshaller.PointV1 point =
                new VersionedPointMarshaller.PointV1(1, -1);
        byte[] encoded = VersionedPointMarshaller.encode(point);
        assertArrayEquals(new byte[]{1, 0, 0, 0, 1, -1, -1, -1, -1}, encoded);
        assertEquals(point, VersionedPointMarshaller.decode(encoded));
    }

    @Test
    void v2RoundTripHasIndependentThreeFieldSchema() {
        VersionedPointMarshaller.PointV2 point =
                new VersionedPointMarshaller.PointV2(-5, 10, Integer.MAX_VALUE);
        byte[] encoded = VersionedPointMarshaller.encode(point);
        assertEquals(13, encoded.length);
        assertEquals(2, Byte.toUnsignedInt(encoded[0]));
        assertEquals(point, VersionedPointMarshaller.decode(encoded));
    }

    @Test
    void oldVersionRequiresExplicitMigrationInsteadOfImplicitShapeGuessing() {
        VersionedPointMarshaller.PointV1 old =
                new VersionedPointMarshaller.PointV1(7, 8);
        Object decoded = VersionedPointMarshaller.decode(
                VersionedPointMarshaller.encode(old));
        assertEquals(old, decoded);
        assertNotEquals(new VersionedPointMarshaller.PointV2(7, 8, 0), decoded);
        assertEquals(new VersionedPointMarshaller.PointV2(7, 8, 0),
                VersionedPointMarshaller.upgradeV1(old));
    }

    @Test
    void unknownTypesAndSchemaVersionsAreRejectedBeforeAnyDynamicClassLoad() {
        assertThrows(IllegalArgumentException.class,
                () -> VersionedPointMarshaller.encode(new Object()));
        assertThrows(IllegalArgumentException.class,
                () -> VersionedPointMarshaller.decode(new byte[]{99, 0, 0, 0, 0}));
    }

    @Test
    void truncatedExtraAndEmptyFramesAreRejected() {
        byte[] valid = VersionedPointMarshaller.encode(
                new VersionedPointMarshaller.PointV1(1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> VersionedPointMarshaller.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> VersionedPointMarshaller.decode(Arrays.copyOf(valid, valid.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> VersionedPointMarshaller.decode(Arrays.copyOf(valid, valid.length + 1)));
    }

    @Test
    void nullInputsAreNotAcceptedAsSchemaOrPayload() {
        assertThrows(NullPointerException.class,
                () -> VersionedPointMarshaller.encode(null));
        assertThrows(NullPointerException.class,
                () -> VersionedPointMarshaller.decode(null));
        assertThrows(NullPointerException.class,
                () -> VersionedPointMarshaller.upgradeV1(null));
    }

    @Test
    void cachedSchemaDoesNotMakeResultsMutableAcrossCalls() {
        VersionedPointMarshaller.PointV1 point =
                new VersionedPointMarshaller.PointV1(12, 34);
        byte[] first = VersionedPointMarshaller.encode(point);
        byte[] second = VersionedPointMarshaller.encode(point);
        assertArrayEquals(first, second);
        first[1] = (byte) 111;
        assertEquals(point, VersionedPointMarshaller.decode(second));
    }
}
