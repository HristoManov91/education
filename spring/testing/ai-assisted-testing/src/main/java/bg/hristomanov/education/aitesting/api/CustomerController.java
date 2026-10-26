package bg.hristomanov.education.aitesting.api;

import bg.hristomanov.education.aitesting.domain.RegisteredCustomer;
import bg.hristomanov.education.aitesting.domain.RegistrationCommand;
import bg.hristomanov.education.aitesting.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    public record CreateRequest(@NotBlank @Email String email,
                                @NotNull LocalDate birthDate,
                                boolean acceptedTerms) {
    }

    @PostMapping
    public ResponseEntity<RegisteredCustomer> register(@Valid @RequestBody CreateRequest request) {
        var saved = service.register(new RegistrationCommand(
                request.email(), request.birthDate(), request.acceptedTerms()));
        return ResponseEntity.created(URI.create("/api/customers/" + saved.id())).body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegisteredCustomer> read(@PathVariable UUID id) {
        return service.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
