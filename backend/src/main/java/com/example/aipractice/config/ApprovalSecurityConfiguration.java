package com.example.aipractice.config;

import com.example.aipractice.service.ApprovalAuthorizer;
import com.example.aipractice.service.SpringSecurityApprovalAuthorizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/** Development-only security boundary; no users or real escalation gateway are provisioned. */
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
                .httpBasic(withDefaults())
                // Keep session/cookie-compatible CSRF protection enabled, including for HTTP Basic.
                .build();
    }

    @Bean
    public ApprovalAuthorizer approvalAuthorizer() {
        return new SpringSecurityApprovalAuthorizer();
    }

    @Bean
    public UserDetailsService approvalUsers() {
        // Intentionally empty. Tests supply identities through Spring Security test support.
        // A verified user store must replace this before enabling interactive authentication.
        return new InMemoryUserDetailsManager();
    }
}
