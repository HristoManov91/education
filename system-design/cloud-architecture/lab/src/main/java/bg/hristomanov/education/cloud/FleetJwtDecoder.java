package bg.hristomanov.education.cloud;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/** Decoder-level example for an RFC 9068 issuer with preconfigured trusted JWKS endpoint.
 * A real resource server must also enforce scopes and resource/tenant ownership.
 */
public final class FleetJwtDecoder {
    private FleetJwtDecoder() {}

    public static NimbusJwtDecoder create(String issuer, String jwksUri, String audience, String clientId) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(2_000);
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(factory))
                // Explicitly require the access-token type used by this issuer, not an ID token.
                .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("at+jwt"))))
                .build();
        decoder.setJwtValidator(JwtValidators.createAtJwtValidator()
                .issuer(issuer).audience(audience).clientId(clientId).build());
        return decoder;
    }
}
