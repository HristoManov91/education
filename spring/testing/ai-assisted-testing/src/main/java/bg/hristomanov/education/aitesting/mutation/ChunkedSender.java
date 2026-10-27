package bg.hristomanov.education.aitesting.mutation;

import java.util.Objects;

/**
 * Минимален Java пример за протокол, който допуска частично записване.
 * Цикълът е необходим заради договора на writer-а, не заради случайна особеност на имплементацията.
 */
public final class ChunkedSender {

    @FunctionalInterface
    public interface ChunkWriter {
        /**
         * Опитва да запише най-много requestedLength байта; може да запише само част от тях.
         * Нулев, отрицателен или прекалено голям резултат се отхвърля от ChunkedSender.
         *
         * @return броят действително записани байтове от source, започвайки от offset
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
