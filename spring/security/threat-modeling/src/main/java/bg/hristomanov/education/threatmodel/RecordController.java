package bg.hristomanov.education.threatmodel;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Демонстрира BOLA/IDOR защита: автентикацията на URL ниво не гарантира,
 * че потребителят има право върху конкретния запис.
 *
 * Няма DB persistence/audit в тази изолирана лаборатория: "approved" е
 * примерен response, а не реално извършено бизнес действие.
 */
@RestController
@RequestMapping("/api/records")
class RecordController {

    private final RecordAccessPolicy policy;

    RecordController(RecordAccessPolicy policy) {
        this.policy = policy;
    }

    @GetMapping("/{owner}")
    Map<String, String> read(@PathVariable String owner, Authentication authentication) {
        // Умишлено НЕ приемаме клиентски X-User-Id като actor identity.
        if (!policy.canRead(authentication, owner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return Map.of("owner", owner, "status", "visible");
    }

    @PostMapping("/{owner}/approve")
    Map<String, String> approve(@PathVariable String owner, Authentication authentication) {
        if (!policy.canApprove(authentication, owner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return Map.of("owner", owner, "result", "approved");
    }
}
