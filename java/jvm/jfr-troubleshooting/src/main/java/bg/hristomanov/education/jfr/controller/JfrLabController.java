package bg.hristomanov.education.jfr.controller;

import bg.hristomanov.education.jfr.domain.LabResults.CpuResult;
import bg.hristomanov.education.jfr.domain.LabResults.FileIoResult;
import bg.hristomanov.education.jfr.domain.LabResults.LabInfo;
import bg.hristomanov.education.jfr.domain.LabResults.LockResult;
import bg.hristomanov.education.jfr.domain.LabResults.MemoryResult;
import bg.hristomanov.education.jfr.domain.LabResults.OrderResult;
import bg.hristomanov.education.jfr.domain.LabResults.ScenarioResult;
import bg.hristomanov.education.jfr.service.CpuHotspotService;
import bg.hristomanov.education.jfr.service.DemoScenarioService;
import bg.hristomanov.education.jfr.service.FileIoService;
import bg.hristomanov.education.jfr.service.LockContentionService;
import bg.hristomanov.education.jfr.service.MemoryLeakService;
import bg.hristomanov.education.jfr.service.OrderProcessingService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary за възпроизводимите JFR сценарии.
 *
 * <p>Всеки endpoint създава различен тип runtime симптом. Това е по-полезно от един
 * "магически" demo метод, защото можем да записваме и анализираме проблемите поотделно.</p>
 */
@RestController
@RequestMapping("/api/jfr")
public class JfrLabController {

    private final CpuHotspotService cpuHotspotService;
    private final MemoryLeakService memoryLeakService;
    private final LockContentionService lockContentionService;
    private final FileIoService fileIoService;
    private final OrderProcessingService orderProcessingService;
    private final DemoScenarioService demoScenarioService;

    public JfrLabController(
            CpuHotspotService cpuHotspotService,
            MemoryLeakService memoryLeakService,
            LockContentionService lockContentionService,
            FileIoService fileIoService,
            OrderProcessingService orderProcessingService,
            DemoScenarioService demoScenarioService) {
        this.cpuHotspotService = cpuHotspotService;
        this.memoryLeakService = memoryLeakService;
        this.lockContentionService = lockContentionService;
        this.fileIoService = fileIoService;
        this.orderProcessingService = orderProcessingService;
        this.demoScenarioService = demoScenarioService;
    }

    @GetMapping("/info")
    public LabInfo info() {
        return new LabInfo(
                ProcessHandle.current().pid(),
                Runtime.version().toString(),
                System.getProperty("java.vm.name"),
                System.getProperty("os.name"));
    }

    @PostMapping("/scenario")
    public ScenarioResult runScenario() {
        return demoScenarioService.run();
    }

    @GetMapping("/cpu")
    public CpuResult cpu(@RequestParam(defaultValue = "5000000") int iterations) {
        return cpuHotspotService.burnCpu(iterations);
    }

    @PostMapping("/memory/retain")
    public MemoryResult retainMemory(@RequestParam(defaultValue = "16") int megabytes) {
        return memoryLeakService.retain(megabytes);
    }

    @GetMapping("/memory/status")
    public MemoryResult memoryStatus() {
        return memoryLeakService.status();
    }

    @DeleteMapping("/memory")
    public MemoryResult clearMemory() {
        return memoryLeakService.clear();
    }

    @PostMapping("/locks")
    public LockResult locks(
            @RequestParam(defaultValue = "6") int workers,
            @RequestParam(defaultValue = "100") int holdMillis) {
        return lockContentionService.createContention(workers, holdMillis);
    }

    @PostMapping("/io")
    public FileIoResult io(@RequestParam(defaultValue = "8") int megabytes) {
        return fileIoService.writeAndRead(megabytes);
    }

    @PostMapping("/order")
    public OrderResult order(@RequestParam(defaultValue = "8") int itemCount) {
        return orderProcessingService.process(itemCount);
    }
}
