package bg.hristomanov.education.bootifulsecurity;

import java.security.Principal;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DemoController {

    @GetMapping("/api/public")
    Map<String, String> publicInformation() {
        return Map.of("visibility", "public");
    }

    @GetMapping("/api/me")
    Map<String, String> currentUser(Principal principal) {
        return Map.of("username", principal.getName());
    }

    @GetMapping("/api/admin/report")
    Map<String, String> adminReport(Principal principal) {
        return Map.of("report", "confidential", "requestedBy", principal.getName());
    }

    @PostMapping("/api/admin/operation")
    Map<String, String> sensitiveOperation() {
        return Map.of("result", "executed");
    }

    @GetMapping("/ott/sent")
    String tokenSent() {
        return "LOCAL DEMO ONLY: check the server console for the one-time token.";
    }
}
