package com.example.aipractice.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises production issuer discovery, public-key verification and claim validators locally. */
class ApprovalJwtDecoderTests {
    private static HttpServer server;
    private static RSAKey trustedKey;
    private static String issuer;
    private static JwtDecoder decoder;
    private static final String AUDIENCE = "ticket-approval-api";

    @BeforeAll
    static void setUpIssuer() throws Exception {
        trustedKey = new RSAKeyGenerator(2048).keyID("trusted-key").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        issuer = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String body = exchange.getRequestURI().getPath().equals("/jwks")
                    ? new JWKSet(trustedKey.toPublicJWK()).toString()
                    : "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        decoder = new ApprovalSecurityConfiguration().approvalJwtDecoder(issuer, AUDIENCE);
    }

    @AfterAll
    static void stopIssuer() {
        if (server != null) { server.stop(0); }
    }

    private JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer(issuer).subject("approver")
                .audience(AUDIENCE).issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("scope", "ticket.review ticket.escalate");
    }

    private String signed(RSAKey key, JWTClaimsSet claims) throws Exception {
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(trustedKey.getKeyID()).build(), claims);
        token.sign(new RSASSASigner(key));
        return token.serialize();
    }

    @Test
    void acceptsValidSignedTokenAndMapsRecognizedScopes() throws Exception {
        var jwt = decoder.decode(signed(trustedKey, validClaims().build()));
        var authentication = new ApprovalSecurityConfiguration()
                .approvalJwtAuthenticationConverter().convert(jwt);
        // Spring Security also records the bearer authentication factor independently of scopes.
        assertThat(authentication.getAuthorities())
                .filteredOn(authority -> authority.getAuthority().startsWith("TICKET_"))
                .extracting("authority")
                .containsExactlyInAnyOrder("TICKET_REVIEW", "TICKET_ESCALATE");
    }

    @Test
    void rejectsInvalidSignatureEvenWithMatchingKeyIdAndClaims() throws Exception {
        RSAKey attackerKey = new RSAKeyGenerator(2048).generate();
        String token = signed(attackerKey, validClaims().build());
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "missing-audience", "expiry", "missing-expiry", "not-before"})
    void rejectsInvalidClaims(String invalidClaim) throws Exception {
        var claims = validClaims();
        switch (invalidClaim) {
            case "issuer" -> claims.issuer("https://untrusted.example");
            case "audience" -> claims.audience("some-other-api");
            case "missing-audience" -> claims.claim("aud", null);
            case "expiry" -> claims.expirationTime(Date.from(Instant.now().minusSeconds(300)));
            case "missing-expiry" -> claims.expirationTime(null);
            case "not-before" -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(180)));
            default -> throw new AssertionError(invalidClaim);
        }
        String token = signed(trustedKey, claims.build());
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnsignedToken() {
        String token = new PlainJWT(validClaims().build()).serialize();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void mapsScpArrayAndIgnoresUnrelatedScopes() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256")
                .claim("scp", java.util.List.of("ticket.review", "unrelated.scope")).build();
        var authentication = new ApprovalSecurityConfiguration()
                .approvalJwtAuthenticationConverter().convert(jwt);
        assertThat(authentication.getAuthorities())
                .filteredOn(authority -> authority.getAuthority().startsWith("TICKET_"))
                .extracting("authority")
                .containsExactly("TICKET_REVIEW");
    }
}
