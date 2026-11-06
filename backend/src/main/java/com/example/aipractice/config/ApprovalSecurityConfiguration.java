package com.example.aipractice.config;

import com.example.aipractice.service.ApprovalAuthorizer;
import com.example.aipractice.service.SpringSecurityApprovalAuthorizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;

/** Development-only bearer-token boundary; no real escalation gateway is provisioned. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Profile("approval-demo")
public class ApprovalSecurityConfiguration {

    private static final String PROPOSALS = "/api/agents/tickets/escalation-proposals";

    @Bean
    public SecurityFilterChain approvalSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(PROPOSALS, PROPOSALS + "/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PROPOSALS)
                        .hasAuthority("TICKET_REVIEW")
                        .requestMatchers(HttpMethod.POST,
                                PROPOSALS + "/*/approve", PROPOSALS + "/*/reject")
                        .hasAuthority("TICKET_ESCALATE")
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // Only this bearer-only chain is exempt: no session/cookie authentication fallback.
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(approvalJwtAuthenticationConverter())))
                .build();
    }

    public JwtAuthenticationConverter approvalJwtAuthenticationConverter() {
        Map<String, String> authorities = Map.of(
                "SCOPE_ticket.review", "TICKET_REVIEW",
                "SCOPE_ticket.escalate", "TICKET_ESCALATE"
        );
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> scopes.convert(jwt).stream()
                .map(scope -> authorities.get(scope.getAuthority()))
                .filter(java.util.Objects::nonNull)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList());
        return converter;
    }

    @Bean
    public JwtDecoder approvalJwtDecoder(
            @Value("${ai.approval.jwt.issuer-uri}") String issuer,
            @Value("${ai.approval.jwt.audience}") String audience) {
        if (issuer.isBlank() || audience.isBlank()) {
            throw new IllegalArgumentException("Approval JWT issuer and audience must be configured");
        }
        // Discover trusted public keys lazily; never accept signing keys supplied by the token.
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
            OAuth2TokenValidator<Jwt> audienceAndExpiry = jwt ->
                    jwt.getAudience() != null && jwt.getAudience().contains(audience)
                            && jwt.getExpiresAt() != null
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                    "invalid_token", "Required audience or expiration is missing", null));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuer), audienceAndExpiry));
            return decoder;
        });
    }

    @Bean
    public ApprovalAuthorizer approvalAuthorizer() {
        return new SpringSecurityApprovalAuthorizer();
    }

}
