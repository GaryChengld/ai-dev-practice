package com.example.aipractice.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringSecurityApprovalAuthorizerTests {

    private final ApprovalAuthorizer authorizer = new SpringSecurityApprovalAuthorizer();
    private final Principal suppliedPrincipal = () -> "approver";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void suppliedPrincipalCannotGrantPermissionWithoutTrustedAuthentication() {
        assertThatThrownBy(() -> authorizer.requireCanEscalate(suppliedPrincipal))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void evenAnAuthenticatedTokenSuppliedAsPrincipalCannotPopulateTheSecurityContext() {
        var forgedPrincipal = UsernamePasswordAuthenticationToken.authenticated(
                "approver", "unused", List.of(new SimpleGrantedAuthority("TICKET_ESCALATE"))
        );

        assertThatThrownBy(() -> authorizer.requireCanEscalate(forgedPrincipal))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void anonymousAuthenticationCannotGrantPermission() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test", "anonymous", List.of(new SimpleGrantedAuthority("TICKET_ESCALATE"))
        ));

        assertThatThrownBy(() -> authorizer.requireCanEscalate(suppliedPrincipal))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void unauthenticatedSecurityContextCannotGrantPermission() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("approver", "unused")
        );

        assertThatThrownBy(() -> authorizer.requireCanEscalate(suppliedPrincipal))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void reviewerCannotBypassUrlRulesByCallingTheAuthorizerDirectly() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("reviewer", "unused",
                        List.of(new SimpleGrantedAuthority("TICKET_REVIEW")))
        );

        assertThatThrownBy(() -> authorizer.requireCanEscalate(suppliedPrincipal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void trustedAuthenticatedEscalatorIsAllowed() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("approver", "unused",
                        List.of(new SimpleGrantedAuthority("TICKET_ESCALATE")))
        );

        assertThatCode(() -> authorizer.requireCanEscalate(suppliedPrincipal))
                .doesNotThrowAnyException();
    }
}
