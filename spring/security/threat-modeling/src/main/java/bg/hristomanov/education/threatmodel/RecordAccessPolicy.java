package bg.hristomanov.education.threatmodel;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Прилага object-level authorization независимо от route/gateway конфигурацията.
 * Не вярваме на X-User-Id или друг клиентски header за определяне на собственика.
 */
@Component
class RecordAccessPolicy {

    boolean canRead(Authentication actor, String owner) {
        return actor != null
                && actor.isAuthenticated()
                && !(actor instanceof AnonymousAuthenticationToken)
                && owner != null
                && owner.equals(actor.getName());
    }

    boolean canApprove(Authentication actor, String owner) {
        return canRead(actor, owner)
                && actor.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_REVIEWER".equals(a.getAuthority()));
    }
}
