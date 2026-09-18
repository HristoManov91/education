package bg.hristomanov.education.jfr.service;

import bg.hristomanov.education.jfr.domain.LabResults.MemoryResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Симулира leak-like retention (обекти, които продължават да са достижими и затова GC не може да освободи).
 *
 * <p>Това НЕ е истински безкраен memory leak. Лабораторията има твърд safety limit от 256 MiB,
 * за да не превръщаме учебния endpoint в лесен начин да съборим локалната JVM с OutOfMemoryError.</p>
 *
 * <p>Root cause-ът е същият като при реален leak: {@code retainedBlocks} пази strong references
 * (силни референции) към масивите. Докато тези референции съществуват, garbage collector-ът
 * няма право да освободи паметта.</p>
 */
@Service
public class MemoryLeakService {

    private static final int BYTES_PER_MIB = 1024 * 1024;
    private static final int MIN_ALLOCATION_MIB = 1;
    private static final int MAX_ALLOCATION_PER_REQUEST_MIB = 32;
    private static final long MAX_RETAINED_BYTES = 256L * BYTES_PER_MIB;

    private final List<byte[]> retainedBlocks = new ArrayList<>();
    private long retainedBytes;

    public synchronized MemoryResult retain(int requestedMegabytes) {
        int megabytes = Math.max(
                MIN_ALLOCATION_MIB,
                Math.min(requestedMegabytes, MAX_ALLOCATION_PER_REQUEST_MIB));

        long requestedBytes = (long) megabytes * BYTES_PER_MIB;
        if (retainedBytes + requestedBytes > MAX_RETAINED_BYTES) {
            throw new IllegalStateException(
                    "Safety limit reached. Clear retained memory before allocating more than 256 MiB.");
        }

        byte[] block = new byte[Math.toIntExact(requestedBytes)];

        /*
         * Докосваме по един byte на memory page (страница памет), за да не разчитаме само
         * на lazy OS allocation (отложено физическо заделяне на памет от операционната система).
         * Така memory pressure-ът се вижда по-надеждно по време на лабораторията.
         */
        for (int offset = 0; offset < block.length; offset += 4096) {
            block[offset] = (byte) (offset & 0x7F);
        }

        retainedBlocks.add(block);
        retainedBytes += requestedBytes;

        return snapshot(megabytes);
    }

    public synchronized MemoryResult status() {
        return snapshot(0);
    }

    public synchronized MemoryResult clear() {
        retainedBlocks.clear();
        retainedBytes = 0L;
        return snapshot(0);
    }

    private MemoryResult snapshot(int lastAllocationMiB) {
        return new MemoryResult(retainedBlocks.size(), retainedBytes, lastAllocationMiB);
    }
}
