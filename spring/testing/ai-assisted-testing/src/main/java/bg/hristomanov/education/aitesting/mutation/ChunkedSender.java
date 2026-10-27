package bg.hristomanov.education.aitesting.mutation;

import java.util.Objects;

/**
 * A small, framework-free example of a protocol that permits partial writes.
 * The loop is required by the writer contract, not by an implementation detail.
 */
public final class ChunkedSender {

    @FunctionalInterface
    public interface ChunkWriter {
        /**
         * Writes between one and requestedLength bytes, or reports invalid progress.
         *
         * @return the number of bytes actually consumed from source
         */
        int write(byte[] source, int offset, int requestedLength);
    }

    private final int maxChunkSize;

    public ChunkedSender(int maxChunkSize) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        this.maxChunkSize = maxChunkSize;
    }

    public void send(byte[] message, ChunkWriter writer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(writer, "writer");

        int offset = 0;
        while (offset < message.length) {
            int requestedLength = Math.min(maxChunkSize, message.length - offset);
            int written = writer.write(message, offset, requestedLength);
            if (written <= 0 || written > requestedLength) {
                // Avoid silently dropping bytes or spinning forever on no progress.
                throw new IllegalStateException("Writer made invalid progress");
            }
            offset += written;
        }
    }
}
