package bg.hristomanov.education.jfr.service;

import bg.hristomanov.education.jfr.domain.LabResults.FileIoResult;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/**
 * Генерира контролирани file read/write операции.
 *
 * <p>Целта е да видим разликата между CPU-bound работа (процесорът работи) и I/O-bound работа
 * (thread-ът прекарва значителна част от времето в операции към файлова система).</p>
 */
@Service
public class FileIoService {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int BYTES_PER_MIB = 1024 * 1024;
    private static final int MIN_MIB = 1;
    private static final int MAX_MIB = 64;

    public FileIoResult writeAndRead(int requestedMegabytes) {
        int megabytes = Math.max(MIN_MIB, Math.min(requestedMegabytes, MAX_MIB));
        long expectedBytes = (long) megabytes * BYTES_PER_MIB;

        Path directory = Path.of("target", "jfr-lab");
        Path temporaryFile = directory.resolve("io-" + UUID.randomUUID() + ".bin");

        byte[] buffer = new byte[BUFFER_SIZE];
        Arrays.fill(buffer, (byte) 0x5A);

        long startedAt = System.nanoTime();
        long bytesWritten = 0L;
        long bytesRead = 0L;

        try {
            Files.createDirectories(directory);

            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(temporaryFile))) {
                while (bytesWritten < expectedBytes) {
                    int bytesToWrite = (int) Math.min(buffer.length, expectedBytes - bytesWritten);
                    output.write(buffer, 0, bytesToWrite);
                    bytesWritten += bytesToWrite;
                }
            }

            try (InputStream input = new BufferedInputStream(Files.newInputStream(temporaryFile))) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    bytesRead += read;
                }
            }

            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            return new FileIoResult(
                    megabytes,
                    bytesWritten,
                    bytesRead,
                    durationMillis,
                    temporaryFile.toString());
        } catch (IOException e) {
            throw new IllegalStateException("File I/O scenario failed", e);
        } finally {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException ignored) {
                /*
                 * Cleanup failure не трябва да прикрива основния резултат/exception.
                 * Файлът е в target/ и може безопасно да бъде изтрит от mvn clean.
                 */
            }
        }
    }
}
