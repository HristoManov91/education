package bg.hristomanov.education.boot4;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Два съвместими външни договора върху един URL. Не променяме V1, за да не
 * счупим клиенти, които вече десериализират предишната форма.
 */
@RestController
public class DogCatalogController {

    public record DogV1(int id, String name) {}
    public record DogV2(int id, String name, String description) {}

    @GetMapping(path = "/dogs", version = "1.0")
    public Map<String, List<DogV1>> dogsV1() {
        return Map.of("dogs", List.of(new DogV1(1, "Rex")));
    }

    @GetMapping(path = "/dogs", version = "2.0")
    public Map<String, List<DogV2>> dogsV2() {
        return Map.of("dogs", List.of(new DogV2(1, "Rex", "Rescued dog")));
    }
}
