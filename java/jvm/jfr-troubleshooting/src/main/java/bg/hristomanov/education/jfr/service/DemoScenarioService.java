package bg.hristomanov.education.jfr.service;

import bg.hristomanov.education.jfr.domain.LabResults.CpuResult;
import bg.hristomanov.education.jfr.domain.LabResults.FileIoResult;
import bg.hristomanov.education.jfr.domain.LabResults.LockResult;
import bg.hristomanov.education.jfr.domain.LabResults.OrderResult;
import bg.hristomanov.education.jfr.domain.LabResults.ScenarioResult;
import org.springframework.stereotype.Service;

/**
 * Една удобна orchestration точка за първия JFR recording.
 *
 * <p>Вместо начинаещият да пуска четири endpoint-а и да се чуди дали recording-ът е активен,
 * този сценарий генерира CPU, lock contention, file I/O и custom business event последователно.
 * Memory retention е оставен отделно, защото той нарочно променя heap-а и трябва да се чисти
 * съзнателно след упражнението.</p>
 */
@Service
public class DemoScenarioService {

    private final CpuHotspotService cpuHotspotService;
    private final LockContentionService lockContentionService;
    private final FileIoService fileIoService;
    private final OrderProcessingService orderProcessingService;

    public DemoScenarioService(
            CpuHotspotService cpuHotspotService,
            LockContentionService lockContentionService,
            FileIoService fileIoService,
            OrderProcessingService orderProcessingService) {
        this.cpuHotspotService = cpuHotspotService;
        this.lockContentionService = lockContentionService;
        this.fileIoService = fileIoService;
        this.orderProcessingService = orderProcessingService;
    }

    public ScenarioResult run() {
        CpuResult cpu = cpuHotspotService.burnCpu(7_000_000);
        LockResult locks = lockContentionService.createContention(6, 100);
        FileIoResult fileIo = fileIoService.writeAndRead(8);
        OrderResult order = orderProcessingService.process(8);

        return new ScenarioResult(cpu, locks, fileIo, order);
    }
}
