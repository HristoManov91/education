package bg.hristomanov.education.jfr.service;

import bg.hristomanov.education.jfr.domain.LabResults.CpuResult;
import org.springframework.stereotype.Service;

/**
 * Създава контролиран CPU hotspot (метод, в който процесорът прекарва осезаемо време).
 *
 * <p>В реална система подобен hotspot може да е JSON transformation, encryption, compression,
 * template rendering или тежко business изчисление. Тук използваме математически операции,
 * защото са deterministic (повтаряеми) и не зависят от външна инфраструктура.</p>
 *
 * <p>Checksum-ът се връща нарочно. Ако резултатът от loop-а не се използва, JIT compiler-ът
 * (компилаторът, който оптимизира Java кода по време на изпълнение) би могъл да премахне част
 * от безполезната работа и лабораторията да стане подвеждаща.</p>
 */
@Service
public class CpuHotspotService {

    private static final int MIN_ITERATIONS = 1_000;
    private static final int MAX_ITERATIONS = 50_000_000;

    public CpuResult burnCpu(int requestedIterations) {
        int iterations = Math.max(MIN_ITERATIONS, Math.min(requestedIterations, MAX_ITERATIONS));

        long startedAt = System.nanoTime();
        double checksum = 0.0d;

        for (int i = 1; i <= iterations; i++) {
            checksum += Math.sqrt(i) * Math.sin(i);
        }

        long durationMillis = nanosToMillis(System.nanoTime() - startedAt);
        return new CpuResult(iterations, durationMillis, checksum);
    }

    private long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
