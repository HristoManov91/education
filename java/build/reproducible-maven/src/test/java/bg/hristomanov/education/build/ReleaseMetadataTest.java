package bg.hristomanov.education.build;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseMetadataTest {

    @Test
    void theSameMetadataIsIdenticalRegardlessOfIterationOrder() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("version", "1.5.0");
        first.put("revision", "abc1234");

        Map<String, String> second = new LinkedHashMap<>();
        second.put("revision", "abc1234");
        second.put("version", "1.5.0");

        assertArrayEquals(ReleaseMetadata.encode(first), ReleaseMetadata.encode(second));
        assertEquals("revision=abc1234\nversion=1.5.0\n",
                new String(ReleaseMetadata.encode(first), StandardCharsets.UTF_8));
    }

    @Test
    void nullAttributeMapIsRejected() {
        assertThrows(NullPointerException.class, () -> ReleaseMetadata.encode(null));
    }

    @Test
    void aLineBreakCannotInjectAnotherProperty() {
        assertThrows(IllegalArgumentException.class,
                () -> ReleaseMetadata.encode(Map.of("version", "1.5.0\nsecret=bad")));
    }

    @Test
    void anInvalidKeyIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ReleaseMetadata.encode(Map.of("my key", "value")));
    }

    @Test
    void propertiesStoreHasAClockDerivedCommentUnlikeCanonicalMetadata() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", "1.5.0");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        properties.store(out, null);

        // Properties.store() adds a date comment even when the caller supplies no comment.
        String naive = out.toString(StandardCharsets.ISO_8859_1);
        assertTrue(naive.startsWith("#"), "The naive format adds a build-time comment");
        assertEquals("version=1.5.0\n",
                new String(ReleaseMetadata.encode(Map.of("version", "1.5.0")),
                        StandardCharsets.UTF_8));
    }
}
