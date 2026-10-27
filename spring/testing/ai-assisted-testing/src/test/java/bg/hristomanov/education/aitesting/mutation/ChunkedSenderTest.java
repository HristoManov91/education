package bg.hristomanov.education.aitesting.mutation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ChunkedSenderTest {

    @Test
    void smallMessageIsDeliveredInOneWrite() {
        byte[] message = "ABC".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        AtomicInteger calls = new AtomicInteger();

        new ChunkedSender(4).send(message, (source, offset, requestedLength) -> {
            calls.incrementAndGet();
            received.write(source, offset, requestedLength);
            return requestedLength;
        });

        assertArrayEquals(message, received.toByteArray());
        assertEquals(1, calls.get());
    }

    @Test
    void exactlyAtTheChunkLimitIsDelivered() {
        byte[] message = {1, 2, 3, 4};
        ByteArrayOutputStream received = new ByteArrayOutputStream();

        new ChunkedSender(4).send(message, (source, offset, requestedLength) -> {
            assertTrue(requestedLength <= 4);
            received.write(source, offset, requestedLength);
            return requestedLength;
        });

        assertArrayEquals(message, received.toByteArray());
    }

    @Test
    void largerMessageIsFullyDeliveredDespitePartialWrites() {
        byte[] message = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        List<Integer> requestedLengths = new ArrayList<>();

        new ChunkedSender(4).send(message, (source, offset, requestedLength) -> {
            requestedLengths.add(requestedLength);
            int written = Math.min(2, requestedLength);
            received.write(source, offset, written);
            return written;
        });

        // Assert externally visible bytes, not merely the number of writer calls.
        assertArrayEquals(message, received.toByteArray());
        assertTrue(requestedLengths.stream().allMatch(n -> n > 0 && n <= 4));
    }

    @Test
    void emptyMessageDoesNotInvokeWriter() {
        assertDoesNotThrow(() -> new ChunkedSender(4).send(new byte[0],
                (source, offset, requestedLength) -> {
                    fail("Empty input must not be written");
                    return 0;
                }));
    }

    @Test
    void zeroProgressFailsInsteadOfLooping() {
        assertThrows(IllegalStateException.class,
                () -> new ChunkedSender(4).send(new byte[]{1},
                        (source, offset, requestedLength) -> 0));
    }

    @Test
    void writerCannotClaimMoreThanRequested() {
        assertThrows(IllegalStateException.class,
                () -> new ChunkedSender(4).send(new byte[]{1, 2},
                        (source, offset, requestedLength) -> requestedLength + 1));
    }

    @Test
    void negativeProgressIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> new ChunkedSender(4).send(new byte[]{1},
                        (source, offset, requestedLength) -> -1));
    }

    @Test
    void chunkSizeMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkedSender(0));
        assertThrows(IllegalArgumentException.class, () -> new ChunkedSender(-1));
    }

    @Test
    void nullArgumentsAreRejected() {
        ChunkedSender sender = new ChunkedSender(4);
        assertThrows(NullPointerException.class, () -> sender.send(null, (source, offset, size) -> size));
        assertThrows(NullPointerException.class, () -> sender.send(new byte[]{1}, null));
    }
}
