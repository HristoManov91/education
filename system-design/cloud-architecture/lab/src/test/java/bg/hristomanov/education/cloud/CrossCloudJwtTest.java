package bg.hristomanov.education.cloud;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class CrossCloudJwtTest {
    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "fleet-api";
    private static final String CLIENT = "fleet-web";
    private static RSAKey oldKey;
    private static RSAKey newKey;
    private static RSAKey rogueKey;
    private HttpServer server;
    private String base;
    private NimbusJwtDecoder decoder;
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicInteger jwksCalls = new AtomicInteger();
    private final AtomicInteger introspectionCalls = new AtomicInteger();
    private final AtomicReference<String> jwks = new AtomicReference<>();

    @BeforeAll
    static void keys() throws Exception {
        oldKey = new RSAKeyGenerator(2048).keyID("old").generate();
        newKey = new RSAKeyGenerator(2048).keyID("new").generate();
        rogueKey = new RSAKeyGenerator(2048).keyID("old").generate();
    }

    @BeforeEach
    void start() throws IOException {
        jwks.set(new JWKSet(oldKey.toPublicJWK()).toString());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            jwksCalls.incrementAndGet();
            respond(exchange, available.get() ? 200 : 503, available.get() ? jwks.get() : "{}");
        });
        server.createContext("/introspect", exchange -> {
            introspectionCalls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            respond(exchange, available.get() ? 200 : 503,
                    active.get() ? "{\"active\":true,\"sub\":\"driver-1\",\"scope\":\"fleet.read\"}"
                            : "{\"active\":false}");
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        decoder = decoder();
    }

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    private NimbusJwtDecoder decoder() {
        return FleetJwtDecoder.create(ISSUER, base + "/jwks", AUDIENCE, CLIENT);
    }

    private static void respond(HttpExchange exchange, int status, String content) throws IOException {
        try (exchange) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private String token(RSAKey key) throws Exception {
        var now = Instant.now();
        return token(key, ISSUER, AUDIENCE, "at+jwt", now.minusSeconds(10), now.plusSeconds(300), now.minusSeconds(10));
    }

    private String token(RSAKey key, String issuer, String audience, String type,
                         Instant issued, Instant expires, Instant notBefore) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("driver-1")
                .audience(audience).claim("client_id", CLIENT).claim("scope", "fleet.read")
                .jwtID(UUID.randomUUID().toString()).issueTime(Date.from(issued))
                .expirationTime(Date.from(expires)).notBeforeTime(Date.from(notBefore)).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType(type)).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @Test
    void warmedKnownKeyWorksWithoutOnlineIdpButIntrospectionDoesNot() throws Exception {
        var signed = token(oldKey);
        assertEquals("driver-1", decoder.decode(signed).getSubject());
        assertTrue(jwksCalls.get() > 0);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(2_000);
        var rest = new RestTemplate(factory);
        rest.getInterceptors().add(new BasicAuthenticationInterceptor("lab-client", "lab-secret"));
        var introspector = new SpringOpaqueTokenIntrospector(base + "/introspect", rest);
        assertEquals("driver-1", introspector.introspect("opaque-fixture").getName());
        int before = jwksCalls.get();
        available.set(false);
        assertEquals("driver-1", decoder.decode(signed).getSubject());
        assertEquals(before, jwksCalls.get(), "known cached key should not call JWKS");
        assertThrows(OAuth2IntrospectionException.class, () -> introspector.introspect("opaque-fixture"));
        assertEquals(2, introspectionCalls.get());
    }

    @Test
    void coldCacheAndUnknownKeyFailClosedDuringOutage() throws Exception {
        var old = token(oldKey);
        var unknown = token(newKey);
        decoder.decode(old);
        available.set(false);
        assertThrows(JwtException.class, () -> decoder.decode(unknown));
        var cold = decoder();
        assertThrows(JwtException.class, () -> cold.decode(old));
    }

    @Test
    void rotationFetchesNewKeyWhenEndpointIsAvailable() throws Exception {
        decoder.decode(token(oldKey));
        int before = jwksCalls.get();
        jwks.set(new JWKSet(List.of(oldKey.toPublicJWK(), newKey.toPublicJWK())).toString());
        assertEquals("driver-1", decoder.decode(token(newKey)).getSubject());
        assertTrue(jwksCalls.get() > before);
    }

    @Test
    void wrongAudienceIssuerTypeAndForgedSignatureAreRejected() throws Exception {
        decoder.decode(token(oldKey));
        var now = Instant.now();
        var issued = now.minusSeconds(10);
        var expiry = now.plusSeconds(300);
        assertThrows(JwtException.class, () -> decoder.decode(token(oldKey, ISSUER, "other-api", "at+jwt", issued, expiry, issued)));
        assertThrows(JwtException.class, () -> decoder.decode(token(oldKey, "https://other.example.test", AUDIENCE, "at+jwt", issued, expiry, issued)));
        assertThrows(JwtException.class, () -> decoder.decode(token(oldKey, ISSUER, AUDIENCE, "JWT", issued, expiry, issued)));
        assertThrows(JwtException.class, () -> decoder.decode(token(rogueKey)));
    }

    @Test
    void cachedKeyCannotExtendExpirationOrBypassNotBefore() throws Exception {
        decoder.decode(token(oldKey));
        available.set(false);
        var now = Instant.now();
        assertThrows(JwtException.class, () -> decoder.decode(token(oldKey, ISSUER, AUDIENCE, "at+jwt",
                now.minusSeconds(600), now.minusSeconds(300), now.minusSeconds(600))));
        assertThrows(JwtException.class, () -> decoder.decode(token(oldKey, ISSUER, AUDIENCE, "at+jwt",
                now.minusSeconds(10), now.plusSeconds(600), now.plusSeconds(300))));
    }
}
