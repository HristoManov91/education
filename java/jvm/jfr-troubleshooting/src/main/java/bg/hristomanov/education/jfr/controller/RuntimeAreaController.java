package bg.hristomanov.education.jfr.controller;

import bg.hristomanov.education.jfr.service.RuntimeAreaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Допълнителен endpoint към съществуващия JFR lab, не нов Spring курс.
 * Умишлено не връща "ns/op": HTTP latency трябва да се измерва отвън.
 */
@RestController
@RequestMapping("/api/jfr/runtime")
public class RuntimeAreaController {

    private final RuntimeAreaService service;

    public RuntimeAreaController(RuntimeAreaService service) {
        this.service = service;
    }

    @GetMapping("/areas")
    public RuntimeAreaService.Result calculate(
            @RequestParam(defaultValue = "1") int shapeTypes,
            @RequestParam(defaultValue = "5000") int rounds) {
        try {
            return service.calculate(shapeTypes, rounds);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
